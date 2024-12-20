package com.zeus.thunderbolt

import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import com.intellij.util.xmlb.XmlSerializerUtil
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.geom.GeneralPath
import java.awt.geom.Point2D
import java.awt.geom.CubicCurve2D
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*

object ZeusThunderbolt : ApplicationActivationListener {

    private const val TARGET_FPS = 60
    private const val FRAME_TIME_NS = 1_000_000_000L / TARGET_FPS
    private const val maxParticles = 2500
    private const val maxChainParticles = 30
    private const val maxParticlePoolSize = 3000
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var lastFrameTime = System.nanoTime()
    private var dt = 0f
    private val particlePool = ConcurrentLinkedQueue<Particle>()
    private val elements = CopyOnWriteArrayList<PhysicsElement>()
    private val settings = ThunderSettings.getInstance()
    private val random = Random()
    private val themes = Theme.entries.toTypedArray()
    private var currentTheme = Theme.None

    private fun trimParticles() {
        if (elements.size > maxParticles) {
            elements.subList(0, elements.size - maxParticles).clear()
        }
    }

    override fun applicationActivated(ideFrame: IdeFrame) {
        ZeusThunderbolt
    }

    init {
        initPlugin()
    }

    fun setTheme(index: Int) {
        currentTheme = themes.getOrElse(index) { Theme.None }
        settings.themeIndex = index
    }

    fun getCurrentThemeIndex() = themes.indexOf(currentTheme)

    fun getCurrentThemeColors() = currentTheme.colors

    fun getThemeNames() = themes.map { it.name }

    private fun initPlugin() {
        val editorFactory = EditorFactory.getInstance()
        val editors = mutableListOf<Editor>()
        val lastPositions = mutableMapOf<Caret, Point>()
        val currEditorObj = AtomicInteger(0)
        val trackCarets = lambda@{ event: CaretEvent ->
            val editor = event.editor
            currEditorObj.set(System.identityHashCode(editor))
            val caret = event.caret ?: return@lambda
            val scrollOffsetX = editor.scrollingModel.horizontalScrollOffset.toFloat()
            val scrollOffsetY = editor.scrollingModel.verticalScrollOffset.toFloat()
            val newPos = caret.getPoint()
            val lastPos = lastPositions[caret]
            val distance = lastPos?.distance(newPos)
            if (distance == null || distance > 1) {
                val newParticles = generateParticles(x0 = scrollOffsetX, y0 = scrollOffsetY, newPos)
                elements.addAll(newParticles)
            }
            // Create chain particles for big jumps
            if (distance != null && distance > 50) {
                val chainCount = elements.count { it is ChainParticle }
                if (chainCount < maxChainParticles) {
                    elements += generateChainParticles(
                        x0 = scrollOffsetX,
                        y0 = scrollOffsetY,
                        lastPos, newPos
                    )
                }
            }
            trimParticles()
            lastPositions[caret] = caret.getPoint()
        }
        val caretListener = object : CaretListener {

            override fun caretAdded(event: CaretEvent) {
                super.caretAdded(event)
                trackCarets(event)
            }

            override fun caretPositionChanged(event: CaretEvent) {
                super.caretPositionChanged(event)
                trackCarets(event)
            }

            override fun caretRemoved(event: CaretEvent) {
                super.caretRemoved(event)
                trackCarets(event)
            }
        }
        val app = ApplicationManager.getApplication()
        EditorFactory.getInstance().eventMulticaster.addCaretListener(caretListener, app)

        class ElementsContainer(val editor: Editor) : JComponent() {

            init {
                val parent = editor.contentComponent
                bounds = parent.bounds
                isVisible = true
                parent.addComponentListener(object : ComponentAdapter() {
                    override fun componentMoved(e: ComponentEvent) = adjustBounds()
                    override fun componentResized(e: ComponentEvent) = adjustBounds()
                    fun adjustBounds() {
                        val area = editor.scrollingModel.visibleArea

                        bounds = Rectangle(area.x, area.y, area.width, area.height)
                    }
                })
            }

            override fun paint(g: Graphics) {
                super.paint(g)
                if (editor.isDisposed) return
                if (System.identityHashCode(editor) != currEditorObj.get()) return
                val scrollOffsetX = editor.scrollingModel.horizontalScrollOffset.toFloat()
                val scrollOffsetY = editor.scrollingModel.verticalScrollOffset.toFloat()
                for (e in elements) {
                    val dx = e.x0 - scrollOffsetX
                    val dy = e.y0 - scrollOffsetY
                    g.translate(dx.toInt(), dy.toInt())
                    e.render(g)
                    g.translate(-dx.toInt(), -dy.toInt())
                }
            }
        }

        val containers = mutableMapOf<Editor, ElementsContainer>()
        val renderJob = coroutineScope.launch {
            while (isActive) {
                val currentTime = System.nanoTime()
                dt = ((currentTime - lastFrameTime) / 1_000_000_000f).coerceAtMost(0.032f)
                lastFrameTime = currentTime

                val deadElements = HashSet<PhysicsElement>()
                val deadParticles = mutableListOf<Particle>()

                for (e in elements) {
                    e.update()
                    if (e.isDead) {
                        deadElements += e
                        if (e is Particle) {
                            deadParticles += e
                        }
                    }
                }

                // Clean up in batch
                elements.removeAll(deadElements)
                if (particlePool.size < maxParticlePoolSize)
                    particlePool.addAll(deadParticles)

                // repaint only visible containers
                for (c in containers.values)
                    if (System.identityHashCode(c.editor) == currEditorObj.get() && c.isShowing && c.isVisible)
                        c.repaint()

                // next frame delay
                val frameTime = System.nanoTime() - currentTime
                val remainingTime = (FRAME_TIME_NS - frameTime) / 1_000_000 // Convert to ms
                if (remainingTime > 0) {
                    delay(remainingTime)
                }
            }
        }

        val factoryListener = object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                super.editorCreated(event)
                val editor = event.editor
                val isActualEditor = editor.isActualEditor()
                if (isActualEditor) addEditor(editor)
            }

            override fun editorReleased(event: EditorFactoryEvent) {
                super.editorReleased(event)
                removeEditor(event.editor)
            }

            private fun addEditor(editor: Editor) {
                editors.add(editor)
                val container = ElementsContainer(editor)
                editor.contentComponent.add(container)
                containers[editor] = container
            }

            private fun removeEditor(editor: Editor) {
                editors.remove(editor)
                containers.remove(editor)
            }

        }
        val clearOnDispose = {
            // onDispose
            editors.clear()
            renderJob.cancel()
        }
        Disposer.register(app, clearOnDispose)
        editorFactory.addEditorFactoryListener(factoryListener, app)
        val typedAction = TypedAction.getInstance()
        val defaultHandler = typedAction.rawHandler
        val typedActionHandler =
            TypedActionHandler { editor, charTyped, dataContext ->
                try {
                    defaultHandler.execute(editor, charTyped, dataContext)
                } catch (_: Exception) {
                }
            }
        typedAction.setupRawHandler(typedActionHandler)
    }

    private fun Editor.isActualEditor(): Boolean =
        editorKind in setOf(EditorKind.MAIN_EDITOR, EditorKind.DIFF)

    private fun Caret.getPoint(): Point = positionWithOffset(visualPosition)

    private fun Caret.positionWithOffset(offsetToVisualPosition: VisualPosition): Point =
        editor.visualPositionToXY(offsetToVisualPosition).apply {
            val location = editor.scrollingModel.visibleArea.location
            translate(-location.x, -location.y)
        }

    sealed interface PhysicsElement {
        var x0: Float
        var y0: Float
        var x: Float
        var y: Float
        var chainStrength: Float
        var isDead: Boolean
        fun update()
        fun render(g: Graphics)
        fun reset()
    }

    private fun generateChainParticles(
        x0: Float,
        y0: Float,
        start: Point,
        end: Point,
        count: Int = 5
    ): List<ChainParticle> {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val baseColor = Color.getHSBColor(
            0.6f + (Math.random() * 0.3f).toFloat(),
            0.8f,
            1f
        )

        return (0..count).map { i ->
            val progress = i.toFloat() / count
            val x = (start.x + dx * progress + (-10..10).random())
            val y = (start.y + dy * progress + (-10..10).random())
            ChainParticle(
                x0, y0,
                x, y,
                (4..6).random().toFloat(),
                baseColor,
                Force(
                    dx.toFloat() * 0.1f,  // Reduced initial velocity
                    dy.toFloat() * 0.1f
                ),
                maxChainDistance = sqrt((dx * dx + dy * dy).toDouble()).toFloat() / 1.5f,
                friction = 0.99f,
                originalX = x,    // Remember original position
                originalY = y
            )
        }
    }

    private fun generateParticles(x0: Float, y0: Float, point: Point, count: Int = 10): List<Particle> =
        (1..count).map {
            particlePool.poll()?.apply {
                this.x0 = x0
                this.y0 = y0
                this.x = point.x.toFloat()
                this.y = point.y.toFloat()
                reset()
            } ?: Particle(
                x0 = x0, y0 = y0,
                x = point.x.toFloat(),
                y = point.y.toFloat(),
                size = random.nextInt(3) + 2f,
                color = Color.getHSBColor(random.nextFloat(), 0.8f, 1f),
                force = Force(
                    (100 + random.nextInt(101)) * cos(random.nextDouble() * 2 * Math.PI).toFloat(),
                    (100 + random.nextInt(101)) * sin(random.nextDouble() * 2 * Math.PI).toFloat()
                )
            )
        }

    data class Force(var x: Float, var y: Float)

    data class ChainParticle(
        override var x0: Float,
        override var y0: Float,
        override var x: Float,
        override var y: Float,
        var size: Float,
        var color: Color,
        var force: Force,
        var friction: Float = 0.98f,
        var lifetime: Float = 0.5f,
        override var chainStrength: Float = 0.8f,
        var maxChainDistance: Float = 100f,
        var vibePhase: Float = (Math.random() * Math.PI * 2).toFloat(),
        var vibeFrequency: Float = (10..15).random().toFloat(),  // Fast vibration
        var vibeAmplitude: Float = (2..4).random().toFloat(),    // Small but noticeable
        var originalX: Float = x,
        var originalY: Float = y,
        var stayInPlace: Boolean = true
    ) : PhysicsElement {
        override var isDead: Boolean = false

        override fun update() {

            // Electric vibration
            vibePhase += vibeFrequency * dt
            val vibeOffsetX = sin(vibePhase.toDouble()) * vibeAmplitude
            val vibeOffsetY = cos(vibePhase * 1.5) * vibeAmplitude

            if (stayInPlace) {
                // Gradually return to original position with some elasticity
                val returnStrength = 5f
                force.x += (originalX - x) * returnStrength * dt
                force.y += (originalY - y) * returnStrength * dt

                // Almost no gravity
                force.y += 20f * dt
            } else {
                // Original gravity
                force.y += 200f * dt
            }

            // Basic physics with vibration
            x += force.x * dt + vibeOffsetX.toFloat()
            y += force.y * dt + vibeOffsetY.toFloat()

            // Stronger friction to stay in place better
            force.x *= 0.95f
            force.y *= 0.95f

            // Chain behavior
            for (other in elements)
                if (other != this && other.chainStrength > 0) {
                    val dx = other.x - x
                    val dy = other.y - y
                    val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    if (distance < maxChainDistance) {
                        val strength = (1 - distance / maxChainDistance) * chainStrength
                        force.x += dx * strength * dt
                        force.y += dy * strength * dt
                    }
                }

            lifetime -= dt
            if (lifetime <= 0) isDead = true
        }

        override fun render(g: Graphics) {
            val g2d = g.create() as? Graphics2D ?: return
            // Only use antialiasing for visible chains
            if (lifetime > 0.2f) {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            }

            // Draw connections to nearby particles
            g2d.stroke = BasicStroke(size / 3f)

            for (other in elements) {
                val dx = other.x - x
                val dy = other.y - y
                if (other != this && other.chainStrength > 0 &&
                    sqrt(dx * dx + dy * dy) < maxChainDistance
                ) {
                    val alpha = (255 * (lifetime / 3f) * chainStrength).toInt().coerceIn(0, 255)
                    g2d.color = Color(color.red, color.green, color.blue, alpha)

                    // Create bezier curve
                    val midX = (x + other.x) / 2
                    val midY = (y + other.y) / 2
                    val curve = CubicCurve2D.Float(
                        x, y,
                        midX - (other.y - y) / 4, midY + (other.x - x) / 4,
                        midX + (other.y - y) / 4, midY - (other.x - x) / 4,
                        other.x, other.y
                    )
                    g2d.draw(curve)
                }
            }

            // Draw particle
            g2d.color = Color(
                color.red, color.green, color.blue,
                (255 * lifetime / 3f).toInt().coerceIn(0, 255)
            )
            g2d.fillOval(
                (x - size / 2).toInt(),
                (y - size / 2).toInt(),
                size.toInt(),
                size.toInt()
            )

            g2d.dispose()
        }

        override fun reset() {}
    }

    data class Particle(
        override var x0: Float,
        override var y0: Float,
        override var x: Float,
        override var y: Float,
        var size: Float,
        var color: Color,
        var force: Force,
        var friction: Float = 0.95f,
        var lifetime: Float = 2f,
        var glowRadius: Float = size * 4f,
        var rotationSpeed: Float = (-2..2).random().toFloat(),
        var wobblePhase: Float = (Math.random() * Math.PI * 2).toFloat(),
        var wobbleFrequency: Float = (2..5).random().toFloat(),
        var wobbleAmplitude: Float = (20..40).random().toFloat(),
        var startColor: Color = color,
        var endColor: Color = Color(
            (color.red + (-20..20).random()).coerceIn(0, 255),
            (color.green + (-20..20).random()).coerceIn(0, 255),
            (color.blue + (-20..20).random()).coerceIn(0, 255)
        ),
        var pulsePhase: Float = (Math.random() * Math.PI * 2).toFloat(),
        var pulseFrequency: Float = (3..6).random().toFloat(),
        override var chainStrength: Float = 0f,  // Regular particles don't form chains
        var isDarkGlow: Boolean = false
    ) : PhysicsElement {
        override var isDead: Boolean = false
        private val trail: Array<Point2D.Float> = Array(10) { Point2D.Float() }
        private var trailIndex: Int = -1
        private var trailSize: Int = 0
        private var cachedColor: Color? = null
        private var cachedLifeProgress = -1f

        override fun reset() {
            isDead = false
            lifetime = 2f
            size = (2..4).random().toFloat()
            color = Color.getHSBColor(Math.random().toFloat(), 0.8f, 1f)
            force = Force(
                (100..200).random().toFloat() * cos(Math.random() * 2 * Math.PI).toFloat(),
                (100..200).random().toFloat() * sin(Math.random() * 2 * Math.PI).toFloat()
            )
            pulsePhase = (Math.random() * Math.PI * 2).toFloat()
            pulseFrequency = (3..6).random().toFloat()
            trailIndex = -1
            trailSize = 0
            startColor = color
            endColor = Color(
                (color.red + (-20..20).random()).coerceIn(0, 255),
                (color.green + (-20..20).random()).coerceIn(0, 255),
                (color.blue + (-20..20).random()).coerceIn(0, 255)
            )
            wobbleFrequency = (2..5).random().toFloat()
            wobbleAmplitude = (20..40).random().toFloat()
        }

        private fun getInterpolatedColor(lifeProgress: Float): Color {
            if (lifeProgress != cachedLifeProgress) {
                cachedColor = Color(
                    (startColor.red * lifeProgress + endColor.red * (1 - lifeProgress)).toInt(),
                    (startColor.green * lifeProgress + endColor.green * (1 - lifeProgress)).toInt(),
                    (startColor.blue * lifeProgress + endColor.blue * (1 - lifeProgress)).toInt(),
                    (255 * lifeProgress).toInt()
                )
                cachedLifeProgress = lifeProgress
            }
            return cachedColor!!
        }

        override fun update() {
            lifetime -= dt
            if (lifetime <= 0) {
                isDead = true
                return
            }

            // Add current position to trail
            trailIndex = (trailIndex + 1) % trail.size
            trail[trailIndex].setLocation(x, y)
            trailSize = (trailSize + 1).coerceAtMost(trail.size)

            // Update pulse
            pulsePhase += pulseFrequency * dt
            val pulseFactor = 1f + 0.2f * sin(pulsePhase)
            glowRadius = size * 4f * pulseFactor

            // Add random wind force
            if (Math.random() < 0.05) { // 5% chance each frame
                force.x += (-50..50).random()
                force.y += (-50..50).random()
            }

            // Sine wave motion
            wobblePhase += wobbleFrequency * dt
            val wobbleForce = sin(wobblePhase) * wobbleAmplitude
            force.x += wobbleForce * dt

            x += force.x * dt
            y += force.y * dt

            // Modified gravity (slightly reduced)
            force.y += 300f * dt

            // Apply friction with some randomness
            val randomFriction = friction * (98..102).random().toFloat() / 100f
            force.x *= randomFriction
            force.y *= randomFriction

            // Interact with nearby particles
            for (other in elements) {
                if (other != this) {
                    val dx = other.x - x
                    val dy = other.y - y
                    val distance = sqrt(dx * dx + dy * dy)
                    if (distance < 50f) {
                        val repulsion = 5f / (distance + 1f)
                        force.x -= dx * repulsion * dt
                        force.y -= dy * repulsion * dt
                    }
                }
            }

            // Apply force field effect
            applyForceField(this)

            // Apply theme colors
            if (currentTheme != Theme.None && 1.6f < lifetime && lifetime < 1.7f) { // Only for new particles
                val themeColors = getCurrentThemeColors()
                startColor = themeColors.random()
                endColor = themeColors.random()
                color = startColor
            } else {
                val lifeProgress = (lifetime / 2f).coerceIn(0f, 1f)
                color = getInterpolatedColor(lifeProgress)
            }
        }

        override fun render(g: Graphics) {
            val g2d = g.create() as? Graphics2D ?: return
            // Enable better quality rendering only for larger particles
            if (size > 4) {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            }

            // Render trail
            if (trailSize > 1) {
                val trailColor =
                    Color(color.red, color.green, color.blue, (50 * (lifetime / 2f)).toInt().coerceIn(0, 255))
                g2d.color = trailColor
                g2d.stroke = BasicStroke(size / 4f)
                val path = GeneralPath()
                var i = trailIndex
                path.moveTo(trail[i].x, trail[i].y)
                repeat(trailSize) {
                    val t = trail[(i-- + trail.size) % trail.size]
                    path.lineTo(t.x, t.y)
                }
                g2d.draw(path)
            }

            // Existing render code with size pulsing
            val pulseFactor = 1f + 0.2f * sin(pulsePhase)
            val currentSize = size * pulseFactor
            val currentGlowRadius = glowRadius * pulseFactor

            // Save original transform
            val originalTransform = g2d.transform

            // Translate to particle position
            g2d.translate(x.toDouble(), y.toDouble())

            // Determine if we should use dark glow
            isDarkGlow = currentTheme == Theme.BlackSnow && color.red < 50 && color.green < 50 && color.blue < 50

            // Modified glow rendering for dark glow
            val numLayers = if (isDarkGlow) 5 else 3 // More layers for dark glow
            for (i in numLayers downTo 1) {
                val baseAlpha = if (isDarkGlow) 0.3f else 0.1f
                val alpha = (baseAlpha * (i.toFloat() / numLayers) * (lifetime / 2f)).coerceIn(0f, 1f)
                g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)

                val layerRadius = currentGlowRadius * (i.toFloat() / numLayers)
                if (isDarkGlow) {
                    // For dark glow, use darker composite mode
                    g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 1.5f)
                }
                g2d.color = color
                g2d.fillOval(
                    (-layerRadius / 2).toInt(),
                    (-layerRadius / 2).toInt(),
                    layerRadius.toInt(),
                    layerRadius.toInt()
                )
            }

            // Draw core
            g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (lifetime / 2f).coerceIn(0f, 1f))
            g2d.color = color
            g2d.fillOval(
                (-currentSize / 2).toInt(),
                (-currentSize / 2).toInt(),
                currentSize.toInt(),
                currentSize.toInt()
            )

            // Restore original transform
            g2d.transform = originalTransform
            g2d.dispose()
        }
    }

    // Add force field effect
    fun applyForceField(particle: Particle) {
        val fieldStrength = 50f
        val fieldFrequency = 0.01f
        val fieldPhase = System.currentTimeMillis() * 0.001f

        // Create a flowing force field effect
        val forceX = sin(particle.y * fieldFrequency + fieldPhase) * fieldStrength
        val forceY = cos(particle.x * fieldFrequency + fieldPhase) * fieldStrength

        particle.force.x += forceX * dt
        particle.force.y += forceY * dt
    }
}

@State(
    name = "com.zeus.thunderbolt.services.ZeusThunderbolt",
    storages = [Storage("ThunderSettings.xml")]
)

class ThunderSettings : PersistentStateComponent<ThunderSettings> {
    var themeIndex: Int = -1 // Default to "None" theme

    override fun getState(): ThunderSettings = this

    override fun loadState(state: ThunderSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): ThunderSettings = service()
    }
}

enum class Theme(vararg val colors: Color) {
    None,
    NorthernLights(
        Color(0, 255, 127),    // Spring Green
        Color(64, 224, 208),   // Turquoise
        Color(0, 191, 255),    // Deep Sky Blue
        Color(138, 43, 226),   // Blue Violet
        Color(75, 0, 130),     // Indigo
        Color(0, 250, 154)     // Medium Spring Green
    ),
    CyberPunk(
        Color(255, 0, 128),    // Hot Pink
        Color(0, 255, 255),    // Cyan
        Color(255, 255, 0),    // Yellow
        Color(128, 0, 255),    // Purple
        Color(255, 128, 0),    // Orange
        Color(0, 255, 128),    // Spring Green
        Color(255, 0, 255)     // Magenta
    ),
    DeepOcean(
        Color(0, 105, 148),    // Deep Blue
        Color(0, 154, 184),    // Medium Blue
        Color(64, 224, 208),   // Turquoise
        Color(127, 255, 212),  // Aquamarine
        Color(0, 206, 209),    // Dark Turquoise
        Color(70, 130, 180),   // Steel Blue
        Color(173, 216, 230)   // Light Blue
    ),
    Spectrum(
        Color(255, 0, 0),      // Red
        Color(255, 127, 0),    // Orange
        Color(255, 255, 0),    // Yellow
        Color(0, 255, 0),      // Green
        Color(0, 0, 255),      // Blue
        Color(75, 0, 130),     // Indigo
        Color(148, 0, 211)     // Violet
    ),
    LavaFlow(
        Color(255, 0, 0),      // Pure Red
        Color(255, 69, 0),     // Red-Orange
        Color(255, 140, 0),    // Dark Orange
        Color(255, 165, 0),    // Orange
        Color(255, 215, 0),    // Gold
        Color(178, 34, 34),    // Firebrick
        Color(139, 0, 0)       // Dark Red
    ),
    NeonCity(
        Color(255, 0, 102),    // Hot Pink
        Color(0, 255, 255),    // Cyan
        Color(255, 255, 0),    // Yellow
        Color(0, 255, 0),      // Lime
        Color(255, 0, 255),    // Magenta
        Color(255, 165, 0),    // Orange
        Color(0, 255, 127),    // Spring Green
        Color(148, 0, 211)     // Violet
    ),
    Twilight(
        Color(25, 25, 112),    // Midnight Blue
        Color(138, 43, 226),   // Blue Violet
        Color(216, 191, 216),  // Thistle
        Color(221, 160, 221),  // Plum
        Color(148, 0, 211),    // Dark Violet
        Color(75, 0, 130),     // Indigo
        Color(106, 90, 205)    // Slate Blue
    ),
    VaporWave(
        Color(255, 111, 255),  // Pink
        Color(0, 255, 255),    // Cyan
        Color(111, 111, 255),  // Light Blue
        Color(255, 111, 111),  // Light Red
        Color(111, 255, 111),  // Light Green
        Color(255, 182, 193),  // Light Pink
        Color(255, 192, 203),  // Pink
        Color(0, 191, 255)     // Deep Sky Blue
    ),
    Ice(
        Color(66, 197, 255),   // Cyan
        Color(125, 249, 255),  // Light blue
        Color(195, 255, 255)   // White blue
    ),
    Autumn(
        Color(255, 89, 0),     // Orange
        Color(255, 159, 0),    // Light orange
        Color(255, 223, 97)    // Yellow
    ),
    Forest(
        Color(34, 139, 34),    // Forest Green
        Color(50, 205, 50),    // Lime Green
        Color(144, 238, 144)   // Light Green
    ),
    Ocean(
        Color(0, 119, 190),    // Deep Blue
        Color(0, 191, 255),    // Deep Sky Blue
        Color(135, 206, 235)   // Sky Blue
    ),
    Sunset(
        Color(255, 69, 0),     // Red-Orange
        Color(255, 140, 0),    // Dark Orange
        Color(255, 0, 255)     // Magenta
    ),
    Neon(
        Color(255, 0, 102),    // Hot Pink
        Color(0, 255, 255),    // Cyan
        Color(255, 255, 0)     // Yellow
    ),
    BlackSnow(
        Color(0, 0, 0),        // Pure black
        Color(20, 20, 20),     // Dark gray
        Color(40, 40, 40)      // Medium gray
    ),
    BlackAndWhite(
        Color(0, 0, 0),        // Black
        Color(255, 255, 255),  // White
        Color(128, 128, 128)   // Gray
    ),
    Matrix(
        Color(0, 255, 0),      // Bright Green
        Color(0, 200, 0),      // Medium Green
        Color(0, 150, 0)       // Dark Green
    ),
    Plasma(
        Color(255, 0, 255),    // Magenta
        Color(128, 0, 255),    // Purple
        Color(64, 0, 255)      // Blue-Purple
    ),
    Volcano(
        Color(255, 0, 0),      // Red
        Color(255, 128, 0),    // Orange
        Color(255, 255, 0)     // Yellow
    ),
    Arctic(
        Color(200, 255, 255),  // Ice White
        Color(150, 200, 255),  // Light Blue
        Color(100, 150, 255)   // Arctic Blue
    ),
    Rainbow(
        Color(255, 0, 0),      // Red
        Color(0, 255, 0),      // Green
        Color(0, 0, 255)       // Blue
    ),
    Galaxy(
        Color(75, 0, 130),     // Indigo
        Color(138, 43, 226),   // Blue Violet
        Color(255, 192, 203)   // Pink
    ),
    Retro(
        Color(255, 87, 51),    // Coral
        Color(255, 189, 51),   // Golden
        Color(51, 187, 255)    // Sky Blue
    );
}
