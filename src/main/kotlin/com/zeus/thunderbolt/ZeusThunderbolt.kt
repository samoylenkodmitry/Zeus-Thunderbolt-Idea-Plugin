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
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import kotlin.math.abs

object ZeusThunderbolt : ApplicationActivationListener {

    private const val TARGET_FPS = 60
    private const val FRAME_TIME_NS = 1_000_000_000L / TARGET_FPS
    private const val maxParticles = 2500
    private const val maxChainParticles = 30
    private const val maxParticlePoolSize = 3000
    private const val WIND_CHANGE_INTERVAL = 2f  // Wind changes direction every 2 seconds
    private const val MAX_WIND_FORCE = 100f
    private var currentWindForce = 0f
    private var targetWindForce = 0f
    private var windTimer = 0f
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var lastFrameTime = System.nanoTime()
    private var dt = 0f
    private val particlePool = ConcurrentLinkedQueue<Particle>()
    private val elements = CopyOnWriteArrayList<PhysicsElement>()
    private val settings: ThunderSettings get() = ThunderSettings.getInstance()
    private val random = Random()
    private val themes = Theme.entries.toTypedArray()
    private var currentTheme = Theme.None

    private const val SNOW_FADE_OUT_TIME = 5f  // Time in seconds after typing stops
    private const val SNOW_SPAWN_RATE = 0.1f   // Time between snowflake spawns
    private const val MAX_ACTIVE_SNOWFLAKES = 100
    private var isSnowing = false
    private var lastTypingTime = 0f
    private var snowSpawnAccumulator = 0f

    private const val TYPING_COOLDOWN = 0.1f  // Shorter cooldown for responsive snow
    private const val MIN_SNOW_SPAWN = 2      // Minimum snowflakes per spawn
    private const val MAX_SNOW_SPAWN = 5      // Maximum snowflakes per spawn
    private const val SNOW_LAYERS = 3         // Number of depth layers for snowflakes
    private var typingSpeed = 0f              // Tracks typing speed
    private var lastTypeTime = 0f             // Last time user typed
    private var typeCount = 0                 // Count of recent keystrokes

    const val DEFAULT_SNOW_ENABLED = false
    private var snowEnabled = DEFAULT_SNOW_ENABLED

    const val DEFAULT_REGULAR_PARTICLES_ENABLED = true

    private var regularParticlesEnabled = DEFAULT_REGULAR_PARTICLES_ENABLED

    fun isRegularParticlesEnabled() = regularParticlesEnabled
    fun setRegularParticlesEnabled(enabled: Boolean) {
        regularParticlesEnabled = enabled
        settings.regularParticlesEnabled = enabled
    }

    const val DEFAULT_STARDUST_PARTICLES_ENABLED = false

    private var stardustParticlesEnabled = DEFAULT_STARDUST_PARTICLES_ENABLED

    fun isStardustParticlesEnabled() = stardustParticlesEnabled
    fun setStardustParticlesEnabled(enabled: Boolean) {
        stardustParticlesEnabled = enabled
        settings.stardustParticlesEnabled = enabled
    }

    const val DEFAULT_REVERSE_PARTICLES_ENABLED = false
    private var reverseParticlesEnabled = DEFAULT_REVERSE_PARTICLES_ENABLED

    fun isReverseParticlesEnabled() = reverseParticlesEnabled
    fun setReverseParticlesEnabled(enabled: Boolean) {
        reverseParticlesEnabled = enabled
        settings.reverseParticlesEnabled = enabled
    }

    const val DEFAULT_BUTTERFLY_PARTICLES_ENABLED = false
    private var butterflyParticlesEnabled = DEFAULT_BUTTERFLY_PARTICLES_ENABLED

    fun isButterfliesEnabled() = butterflyParticlesEnabled
    fun setButterfliesEnabled(enabled: Boolean) {
        butterflyParticlesEnabled = enabled
        settings.butterflyParticlesEnabled = enabled
    }

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

    fun isSnowEnabled() = snowEnabled

    fun setSnowEnabled(enabled: Boolean) {
        snowEnabled = enabled
        settings.snowEnabled = enabled
    }

    private fun initPlugin() {
        setTheme(settings.themeIndex)
        setRegularParticlesEnabled(settings.regularParticlesEnabled)
        setStardustParticlesEnabled(settings.stardustParticlesEnabled)
        setReverseParticlesEnabled(settings.reverseParticlesEnabled)
        setSnowEnabled(settings.snowEnabled)
        setButterfliesEnabled(settings.butterflyParticlesEnabled)
        val editorFactory = EditorFactory.getInstance()
        val editors = mutableListOf<Editor>()
        val lastPositions = mutableMapOf<Caret, Point>()
        val currEditorObj = AtomicInteger(0)

        // Add document listener to track deletions
        val documentListener = object : DocumentListener {
            override fun beforeDocumentChange(event: DocumentEvent) {
                if (event.oldLength > 0 && event.newLength == 0 && reverseParticlesEnabled) {
                    // This is a deletion event
                    val editor = editorFactory.getEditors(event.document).firstOrNull() ?: return
                    val offset = event.offset
                    val visualPos = editor.offsetToVisualPosition(offset)
                    val point = editor.visualPositionToXY(visualPos)
                    val scrollOffsetX = editor.scrollingModel.horizontalScrollOffset.toFloat()
                    val scrollOffsetY = editor.scrollingModel.verticalScrollOffset.toFloat()
                    
                    // Create reverse particles at deletion point
                    val reverseParticles = generateReverseParticle(
                        x0 = scrollOffsetX,
                        y0 = scrollOffsetY,
                        point = point
                    )
                    elements += reverseParticles
                    trimParticles()
                }
            }
        }

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
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(documentListener, app)
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
                    e.update(elements)
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

                // Only update wind and snow if snow is enabled
                if (snowEnabled) {
                    updateWind()
                    updateSnow()
                }

                ApplicationManager.getApplication().invokeLater {
                    containers.values.forEach { it.repaint() }
                }
                delay(16)
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

                    if (snowEnabled) {
                        // Update typing stats
                        lastTypingTime = System.nanoTime() * 1e-9f
                        typeCount = (typeCount + 1).coerceAtMost(10)
                        isSnowing = true
                    }

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

    private fun updateWind() {
        windTimer += dt
        if (windTimer >= WIND_CHANGE_INTERVAL) {
            windTimer = 0f
            targetWindForce = (random.nextFloat() * 2 - 1) * MAX_WIND_FORCE
        }
        // Smoothly interpolate to target wind force
        currentWindForce += (targetWindForce - currentWindForce) * dt * 2
    }

    private fun updateTypingIntensity() {
        val currentTime = System.nanoTime() * 1e-9f
        if (currentTime - lastTypeTime > TYPING_COOLDOWN) {
            typeCount = (typeCount - 1).coerceAtLeast(0)
        }
        typingSpeed = typeCount.coerceIn(0, 10) / 10f
    }

    private fun updateSnow() {
        val currentTime = System.nanoTime() * 1e-9f
        updateTypingIntensity()

        // Check if we should stop snowing
        if (isSnowing && currentTime - lastTypingTime > SNOW_FADE_OUT_TIME) {
            isSnowing = false
        }

        // Only spawn snow while snowing
        if (isSnowing) {
            snowSpawnAccumulator += dt
            while (snowSpawnAccumulator >= SNOW_SPAWN_RATE) {
                snowSpawnAccumulator -= SNOW_SPAWN_RATE

                // Count current snowflakes
                val currentSnowflakes = elements.count { it is Snowflake }

                if (currentSnowflakes < MAX_ACTIVE_SNOWFLAKES) {
                    // Spawn amount based on typing intensity
                    val spawnCount = (MIN_SNOW_SPAWN +
                            (MAX_SNOW_SPAWN - MIN_SNOW_SPAWN) * typingSpeed).toInt()

                    repeat(spawnCount) {
                        val randomX = (-100..1100).random().toFloat()
                        val layer = (0 until SNOW_LAYERS).random()
                        elements.add(generateSnowflake(0f, 0f, Point(randomX.toInt(), 0), layer))
                    }
                }
            }
        }
    }

    sealed interface PhysicsElement {
        var x0: Float
        var y0: Float
        var x: Float
        var y: Float
        var chainStrength: Float
        var isDead: Boolean
        fun update(elements: List<PhysicsElement> = emptyList())
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

    private fun generateParticles(x0: Float, y0: Float, point: Point, count: Int = 10): List<PhysicsElement> =
        (1..count).mapNotNull {
            when {
                snowEnabled && random.nextFloat() in 0.7f..0.9f -> generateSnowflake(x0, y0, point)
                stardustParticlesEnabled && random.nextFloat() > 0.8f -> generateStardustParticle(x0, y0, point)
                butterflyParticlesEnabled && random.nextFloat() > 0.95f -> generateButterfly(x0, y0, point)
                regularParticlesEnabled -> generateRegularParticle(x0, y0, point)
                else -> null
            }
        }

    private fun generateRegularParticle(x0: Float, y0: Float, point: Point) =
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

    private fun generateSnowflake(x0: Float, y0: Float, point: Point, layer: Int = 0): Snowflake {
        val size = when (random.nextFloat()) {
            in 0.0f..0.6f -> (2..4).random().toFloat()    // 60% small flakes
            in 0.6f..0.9f -> (4..6).random().toFloat()    // 30% medium flakes
            else -> (6..8).random().toFloat()             // 10% large flakes
        }

        return Snowflake(
            x0 = x0,
            y0 = y0,
            x = point.x.toFloat(),
            y = -size * 2,  // Start just above visible area
            size = size,
            rotationSpeed = (-1..1).random().toFloat() * 0.8f,
            swayFrequency = (0.5f..2f).random(),
            swayAmplitude = (2..5).random().toFloat(),
            descendSpeed = (30 + size * 3).coerceIn(30f, 60f),  // Larger flakes fall faster
            lifetime = (8..12).random().toFloat(),
            windInfluence = (0.8f..1.2f).random(),
            turbulence = (0.5f..1.5f).random(),
            sparklePhase = random.nextFloat() * Math.PI.toFloat() * 2,
            sparkleSpeed = (4..8).random().toFloat(),
            layer = layer,
            spinPhase = random.nextFloat() * Math.PI.toFloat() * 2,
            spinSpeed = (2..4).random().toFloat()
        )
    }

    private fun generateStardustParticle(x0: Float, y0: Float, point: Point) = StardustParticle(
        x0 = x0, y0 = y0,
        x = point.x.toFloat(),
        y = point.y.toFloat(),
        size = (3..6).random().toFloat(),  // Slightly larger size
        baseColor = when (random.nextInt(5)) {  // More color variations
            0 -> Color(255, 223, 170)  // Warm gold
            1 -> Color(200, 255, 255)  // Ice blue
            2 -> Color(255, 200, 255)  // Pink
            3 -> Color(230, 230, 255)  // Starlight white
            else -> Color(180, 230, 255)  // Crystal blue
        },
        color = Color(255, 255, 200),
        trailLength = (15..25).random(),  // Longer trails
        driftSpeed = (8..20).random().toFloat()  // Slower, more graceful movement
    )

    private fun generateButterfly(x0: Float, y0: Float, point: Point): Butterfly {
        val baseColor = when (random.nextInt(5)) {
            0 -> Color(255, 100, 100)  // Red
            1 -> Color(100, 100, 255)  // Blue
            2 -> Color(255, 200, 100)  // Orange
            3 -> Color(200, 100, 255)  // Purple
            else -> Color(100, 255, 100)  // Green
        }
        
        return Butterfly(
            x0 = x0,
            y0 = y0,
            x = point.x.toFloat(),
            y = point.y.toFloat(),
            size = (8..12).random().toFloat(),
            color = baseColor,
            wingSpan = (15..25).random().toFloat(),
            flapSpeed = (4..7).random().toFloat(),
            lifetime = (5..8).random().toFloat()
        )
    }

    data class Snowflake(
        override var x0: Float,
        override var y0: Float,
        override var x: Float,
        override var y: Float,
        var size: Float = (2..5).random().toFloat(),
        var rotationAngle: Float = (Math.random() * Math.PI * 2).toFloat(),
        var rotationSpeed: Float = (-1..1).random().toFloat(),
        var swayPhase: Float = (Math.random() * Math.PI * 2).toFloat(),
        var swayFrequency: Float = (1..3).random().toFloat(),
        var swayAmplitude: Float = (2..5).random().toFloat(),
        var descendSpeed: Float = (30..50).random().toFloat(),
        override var chainStrength: Float = 0f,
        var lifetime: Float = 5f,
        var windInfluence: Float = 1f,
        var turbulence: Float = 1f,
        var localWindOffset: Float = random.nextFloat() * Math.PI.toFloat() * 2,
        var verticalOffset: Float = random.nextFloat() * Math.PI.toFloat() * 2,
        var sparklePhase: Float = 0f,
        var sparkleSpeed: Float = 5f,
        var layer: Int = 0,                    // Depth layer (0 = front, SNOW_LAYERS-1 = back)
        var spinPhase: Float = 0f,            // For wobble effect
        var spinSpeed: Float = 1f,            // Speed of wobble
        var nearbyForce: Force = Force(0f, 0f) // Forces from nearby snowflakes
    ) : PhysicsElement {
        override var isDead: Boolean = false

        override fun update(elements: List<PhysicsElement>) {
            lifetime -= dt
            if (lifetime <= 0) {
                isDead = true
                return
            }

            // Layer-based behavior
            val layerFactor = 1f - (layer.toFloat() / SNOW_LAYERS)
            val baseSpeed = descendSpeed * (0.7f + 0.3f * layerFactor)

            // Update vertical motion with enhanced variation
            verticalOffset += dt * 2
            val verticalVariation = sin(verticalOffset) * 2 * layerFactor
            y += (baseSpeed + verticalVariation) * dt

            // Enhanced wind effect based on layer
            x += currentWindForce * windInfluence * layerFactor * dt

            // Spin effect
            spinPhase += spinSpeed * dt
            val spinOffset = sin(spinPhase) * size * 0.2f
            x += spinOffset * dt

            // Interact with nearby snowflakes
            nearbyForce.x *= 0.95f  // Decay
            nearbyForce.y *= 0.95f
            x += nearbyForce.x * dt
            y += nearbyForce.y * dt

            // Find nearby snowflakes
            for (other in elements) {
                if (other is Snowflake && other != this && other.layer == layer) {
                    val dx = other.x - x
                    val dy = other.y - y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < size * 4) {
                        val force = 1f - (dist / (size * 4))
                        nearbyForce.x -= dx * force * 10f
                        nearbyForce.y -= dy * force * 10f
                    }
                }
            }

            // Reset if below visible area or too far to the sides
            if (y > 1000 || x < -200 || x > 1200) {
                // Reset position above screen with random horizontal position
                y = -10f
                x = (0..1000).random().toFloat()
                lifetime = (8..12).random().toFloat()
                // Randomize motion parameters for variety
                swayPhase = random.nextFloat() * Math.PI.toFloat() * 2
                localWindOffset = random.nextFloat() * Math.PI.toFloat() * 2
                windInfluence = (0.8f..1.2f).random()
                turbulence = (0.5f..1.5f).random()
            }
        }

        override fun render(g: Graphics) {
            val g2d = g.create() as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Layer-based appearance
            sparklePhase += dt * sparkleSpeed * (1f + layer * 0.2f)

            // Save original transform
            val originalTransform = g2d.transform

            // Move to snowflake position and rotate
            g2d.translate(x.toDouble(), y.toDouble())
            g2d.rotate(rotationAngle.toDouble())

            // Draw snowflake
            // Add slight size variation based on vertical speed
            val speedFactor = (1f + (descendSpeed - 30f) / 50f) * 0.2f + 0.8f
            val adjustedSize = size * speedFactor

            // Update points array with new size
            val points = Array(6) { i ->
                val angle = i * (Math.PI / 3)
                Point2D.Float(
                    cos(angle).toFloat() * adjustedSize,
                    sin(angle).toFloat() * adjustedSize
                )
            }

            // Add sparkle effect
            sparklePhase += dt * sparkleSpeed
            val sparkle = (sin(sparklePhase) * 0.3f + 0.7f).coerceIn(0f, 1f)

            // Draw snowflake with sparkle
            val windOpacityFactor = (1f - abs(currentWindForce) / MAX_WIND_FORCE * 0.3f)
            val alpha = ((255 * (lifetime / 5f)) * windOpacityFactor * sparkle).toInt().coerceIn(0, 255)
            g2d.color = Color(255, 255, 255, alpha)

            // Draw main crystal structure
            for (i in points.indices) {
                g2d.drawLine(0, 0, points[i].x.toInt(), points[i].y.toInt())
                // Draw smaller branches
                val midX = points[i].x * 0.5f
                val midY = points[i].y * 0.5f
                g2d.drawLine(
                    midX.toInt(), midY.toInt(),
                    (midX + points[(i + 2) % 6].x * 0.3f).toInt(),
                    (midY + points[(i + 2) % 6].y * 0.3f).toInt()
                )
            }

            // Restore original transform
            g2d.transform = originalTransform
            g2d.dispose()
        }

        override fun reset() {
            isDead = false
            lifetime = 5f
            y = -10f
        }
    }

    private fun generateReverseParticle(x0: Float, y0: Float, point: Point): ReverseParticle {
        // Create a group of particles to simulate together
        val particleGroup = List(10) {
            generateRegularParticle(x0, y0, point)
        }
        val snapshots = mutableListOf<ParticleSnapshot>()

        // Simulate particles together
        val lifetime = 90
        repeat(lifetime) { frame ->
            // Update all particles together so they interact
            for (particle in particleGroup) particle.update(particleGroup)

            // Save snapshot of all particles
            snapshots.add(
                ParticleSnapshot(
                    particles = particleGroup.map { it.makeSnapshot() },
                )
            )
        }

        return ReverseParticle(
            x0 = x0, y0 = y0,
            x = point.x.toFloat(),
            y = point.y.toFloat(),
            snapshots = snapshots.asReversed(),
        )
    }

    data class ParticleSnapshot(val particles: List<Particle>)

    data class ReverseParticle(
        override var x0: Float,
        override var y0: Float,
        override var x: Float,
        override var y: Float,
        val snapshots: List<ParticleSnapshot>,
        override var chainStrength: Float = 0f,
        private var currentSnapshotIndex: Int = 0
    ) : PhysicsElement {
        override var isDead: Boolean = false

        override fun update(elements: List<PhysicsElement>) {
            currentSnapshotIndex++
            if (currentSnapshotIndex >= snapshots.size) {
                isDead = true
                return
            }
        }

        override fun render(g: Graphics) {
            if (currentSnapshotIndex < 0 || currentSnapshotIndex >= snapshots.size) return
            for (particle in snapshots[currentSnapshotIndex].particles) {
                particle.render(g)
            }
        }

        override fun reset() {
            isDead = false
            currentSnapshotIndex = -1
        }
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

        override fun update(elements: List<PhysicsElement>) {

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
        val trail: Array<Point2D.Float> = Array(10) { Point2D.Float() }
        var trailIndex: Int = -1
        var trailSize: Int = 0
        override var isDead: Boolean = false
        private var cachedColor: Color? = null
        private var cachedLifeProgress = -1f

        fun makeSnapshot() = copy().also {
            for ((i, p) in it.trail.withIndex()) p.setLocation(trail[i])
            it.trailIndex = trailIndex
            it.trailSize = trailSize
        }

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

        override fun update(elements: List<PhysicsElement>) {
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

    data class StardustParticle(
        override var x0: Float,
        override var y0: Float,
        override var x: Float,
        override var y: Float,
        var size: Float,
        var color: Color,
        var lifetime: Float = 3f,
        override var chainStrength: Float = 0f,
        var twinklePhase: Float = random.nextFloat() * Math.PI.toFloat() * 2,
        var rotationAngle: Float = random.nextFloat() * Math.PI.toFloat() * 2,
        var driftSpeed: Float = (10..30).random().toFloat(),
        var colorTransitionPhase: Float = random.nextFloat() * Math.PI.toFloat() * 2,
        var trailLength: Int = 20,
        var trailPoints: Array<Point2D.Float> = Array(trailLength) { Point2D.Float() },
        var sparklePoints: Array<Point2D.Float> = Array(4) { Point2D.Float() },
        var sparkleDistance: Float = size * 2f,
        var trailIndex: Int = 0,
        var trailSize: Int = 0,
        var baseColor: Color = when (random.nextInt(3)) {
            0 -> Color(255, 223, 170)  // Warm gold
            1 -> Color(200, 255, 255)  // Ice blue
            else -> Color(255, 200, 255)  // Pink
        }
    ) : PhysicsElement {
        override var isDead: Boolean = false

        override fun update(elements: List<PhysicsElement>) {
            lifetime -= dt
            if (lifetime <= 0) {
                isDead = true
                return
            }

            // Update trail
            trailIndex = (trailIndex + 1) % trailPoints.size
            trailPoints[trailIndex].setLocation(x, y)
            trailSize = (trailSize + 1).coerceAtMost(trailPoints.size)

            // Color transition
            colorTransitionPhase += dt * 0.5f

            // Enhanced twinkling effect
            twinklePhase += (8f + sin(colorTransitionPhase) * 4f) * dt

            // Smooth drifting motion with spiral tendency
            rotationAngle += dt * (0.5f + sin(twinklePhase * 0.5f) * 0.3f)
            val spiralRadius = 30f + sin(twinklePhase * 0.3f) * 10f
            x += (cos(rotationAngle) * spiralRadius + sin(twinklePhase * 2f) * 5f) * dt
            y += (sin(rotationAngle) * spiralRadius + cos(twinklePhase * 2f) * 5f) * dt

            // Interact with nearby stardust particles
            for (other in elements) {
                if (other is StardustParticle && other != this) {
                    val dx = other.x - x
                    val dy = other.y - y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < size * 8) {
                        val force = 1f - (dist / (size * 8))
                        x -= dx * force * dt * 0.5f
                        y -= dy * force * dt * 0.5f
                    }
                }
            }
            // Update sparkle positions
            for (i in sparklePoints.indices) {
                val angle = twinklePhase + (Math.PI * 2 * i / sparklePoints.size)
                sparklePoints[i].setLocation(
                    x + cos(angle / 2) * sparkleDistance + sin(i + angle / 4) * sparkleDistance / 2,
                    y + sin(angle / 2) * sparkleDistance - cos(i + angle / 4) * sparkleDistance / 2
                )
            }
        }

        override fun render(g: Graphics) {
            val g2d = g.create() as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Calculate current color based on transition phase
            val currentColor = getTransitionedColor()

            // Draw trail with gradient
            if (trailSize > 1) {
                renderTrail(g2d, currentColor)
            }

            // Enhanced twinkle effect
            val twinkle = (sin(twinklePhase) * 0.5f + 0.5f)
            val alpha = (255 * lifetime / 3f * twinkle).toInt().coerceIn(0, 255)

            // Multi-layered glow effect
            renderGlowLayers(g2d, currentColor, alpha)

            // Draw star shape with enhanced detail
            renderStarShape(g2d, currentColor, alpha)

            g2d.dispose()
        }

        private fun getTransitionedColor(): Color = Color(
            (baseColor.red + sin(colorTransitionPhase) * 30).toInt().coerceIn(0, 255),
            (baseColor.green + sin(colorTransitionPhase * 1.3f) * 30).toInt().coerceIn(0, 255),
            (baseColor.blue + sin(colorTransitionPhase * 0.7f) * 30).toInt().coerceIn(0, 255)
        )

        private fun renderTrail(g2d: Graphics2D, color: Color) {
            val path = GeneralPath()
            var i = trailIndex
            path.moveTo(trailPoints[i].x, trailPoints[i].y)

            repeat(trailSize) { index ->
                val t = trailPoints[(i-- + trailPoints.size) % trailPoints.size]
                val progress = 1f - (index.toFloat() / trailSize)
                g2d.color = Color(
                    color.red, color.green, color.blue,
                    (40 * progress * (lifetime / 3f)).toInt().coerceIn(0, 255)
                )
                path.lineTo(t.x, t.y)
            }

            g2d.stroke = BasicStroke(size / 4f)
            g2d.draw(path)
        }

        private fun renderGlowLayers(g2d: Graphics2D, color: Color, alpha: Int) {
            val numLayers = 5  // More glow layers
            for (i in numLayers downTo 1) {
                val layerSize = size * (2f + i * 0.8f)
                val layerAlpha = (alpha * (i.toFloat() / numLayers) * 0.4f).toInt()

                val gradient = RadialGradientPaint(
                    x, y, layerSize,
                    floatArrayOf(0f, 0.3f, 0.7f, 1f),  // More gradient stops
                    arrayOf(
                        Color(color.red, color.green, color.blue, layerAlpha),
                        Color(color.red, color.green, color.blue, (layerAlpha * 0.8f).toInt()),
                        Color(color.red, color.green, color.blue, (layerAlpha * 0.3f).toInt()),
                        Color(color.red, color.green, color.blue, 0)
                    )
                )

                g2d.paint = gradient
                g2d.fill(
                    Ellipse2D.Float(
                        x - layerSize, y - layerSize,
                        layerSize * 2, layerSize * 2
                    )
                )
            }

            // Add sparkles
            for (sparkle in sparklePoints) {
                val sparkleSize = size * 0.5f
                val sparkleGlow = RadialGradientPaint(
                    sparkle.x, sparkle.y, sparkleSize * 2,
                    floatArrayOf(0f, 1f),
                    arrayOf(
                        Color(255, 255, 255, (alpha * 0.7f).toInt()),
                        Color(255, 255, 255, 0)
                    )
                )
                g2d.paint = sparkleGlow
                g2d.fill(
                    Ellipse2D.Float(
                        sparkle.x - sparkleSize, sparkle.y - sparkleSize,
                        sparkleSize * 2, sparkleSize * 2
                    )
                )
            }
        }

        private fun renderStarShape(g2d: Graphics2D, color: Color, alpha: Int) {
            val points = 5
            val outerRadius = size
            val innerRadius = size * 0.4f  // Sharper star points
            val extraPoints = 3  // More detailed star shape

            val path = GeneralPath()
            for (i in 0 until points * 2 * extraPoints) {
                val angle = Math.PI * i / (points * extraPoints)
                val radius = if (i % 2 == 0) outerRadius else innerRadius
                val wobble = sin(twinklePhase * 2 + i) * size * 0.1f
                val px = x + cos(angle) * (radius + wobble)
                val py = y + sin(angle) * (radius + wobble)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.closePath()

            // Main star body with gradient
            val starGradient = RadialGradientPaint(
                x, y, outerRadius,
                floatArrayOf(0f, 0.5f, 1f),
                arrayOf(
                    Color(255, 255, 255, alpha),
                    Color(color.red, color.green, color.blue, alpha),
                    Color(color.red, color.green, color.blue, (alpha * 0.5f).toInt())
                )
            )
            g2d.paint = starGradient
            g2d.fill(path)

            // Highlight
            g2d.color = Color(255, 255, 255, (alpha * 0.7f).toInt())
            g2d.stroke = BasicStroke(size / 10f)
            g2d.draw(path)
        }

        override fun reset() {
            isDead = false
            lifetime = 3f
            trailSize = 0
        }
    }

    data class Butterfly(
        override var x0: Float,
        override var y0: Float,
        override var x: Float,
        override var y: Float,
        var size: Float,
        var color: Color,
        var wingSpan: Float,
        var flapSpeed: Float,
        var lifetime: Float,
        override var chainStrength: Float = 0f,
        var flapPhase: Float = random.nextFloat() * Math.PI.toFloat() * 2,
        var pathPhase: Float = random.nextFloat() * Math.PI.toFloat() * 2,
        var pathRadius: Float = (30..50).random().toFloat(),
        var pathSpeed: Float = (1..2).random().toFloat(),
        var verticalPhase: Float = random.nextFloat() * Math.PI.toFloat() * 2,
        var verticalSpeed: Float = (0.5f..1.5f).random()
    ) : PhysicsElement {
        override var isDead: Boolean = false

        override fun update(elements: List<PhysicsElement>) {
            lifetime -= dt
            if (lifetime <= 0) {
                isDead = true
                return
            }

            // Wing flapping animation
            flapPhase += flapSpeed * dt

            // Complex flight path combining circular and vertical motion
            pathPhase += pathSpeed * dt
            verticalPhase += verticalSpeed * dt

            // Calculate new position using Lissajous curve for more interesting motion
            x += (cos(pathPhase) * pathRadius * dt + sin(pathPhase * 0.5f) * 10f * dt)
            y += (sin(pathPhase * 0.7f) * pathRadius * 0.5f * dt + sin(verticalPhase) * 15f * dt)

            // Add slight random movement
            if (random.nextFloat() > 0.95f) {
                x += (-5..5).random()
                y += (-5..5).random()
            }
        }

        override fun render(g: Graphics) {
            val g2d = g.create() as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Calculate alpha based on lifetime
            val alpha = (255 * (lifetime / 8f)).toInt().coerceIn(0, 255)
            
            // Save original transform
            val originalTransform = g2d.transform
            
            // Move to butterfly position
            g2d.translate(x.toDouble(), y.toDouble())

            // Draw wings - flap from center axis
            val leftWingAngle = sin(flapPhase) * Math.PI.toFloat() * 0.3f
            val rightWingAngle = -leftWingAngle  // Opposite flap for symmetric motion

            // Draw left wing
            val leftWing = Path2D.Float()
            g2d.transform = originalTransform
            g2d.translate(x.toDouble(), y.toDouble())
            g2d.rotate(leftWingAngle*1.0)
            createWingPath(leftWing, -1)
            g2d.paint = createWingGradient(alpha)
            g2d.fill(leftWing)
            drawWingPatterns(g2d, leftWing, alpha)

            // Draw right wing
            val rightWing = Path2D.Float()
            g2d.transform = originalTransform
            g2d.translate(x.toDouble(), y.toDouble())
            g2d.rotate(rightWingAngle*1.0)
            createWingPath(rightWing, 1)
            g2d.paint = createWingGradient(alpha)
            g2d.fill(rightWing)
            drawWingPatterns(g2d, rightWing, alpha)

            // Reset transform and draw body
            g2d.transform = originalTransform
            g2d.translate(x.toDouble(), y.toDouble())
            drawBody(g2d, alpha)

            g2d.dispose()
        }

        private fun createWingGradient(alpha: Int): Paint {
            return RadialGradientPaint(
                0f, 0f, wingSpan,
                floatArrayOf(0.4f, 0.7f, 1f),
                arrayOf(
                    Color(color.red, color.green, color.blue, alpha),
                    Color(color.red / 2, color.green / 2, color.blue / 2, alpha),
                    Color(color.red / 3, color.green / 3, color.blue / 3, (alpha * 0.7f).toInt())
                )
            )
        }
        
        private fun createWingPath(path: Path2D.Float, direction: Int) {
            val dir = direction.toFloat()
            
            // Main wing shape
            path.moveTo(0f, 0f)
            path.curveTo(
                dir * wingSpan * 0.2f, -size * 0.5f,  // Control point 1
                dir * wingSpan * 0.6f, -size * 0.8f,  // Control point 2
                dir * wingSpan * 0.8f, -size * 0.3f   // End point
            )
            path.curveTo(
                dir * wingSpan * 0.9f, size * 0.2f,   // Control point 1
                dir * wingSpan * 0.7f, size * 0.6f,   // Control point 2
                dir * wingSpan * 0.3f, size * 0.4f    // End point
            )
            path.curveTo(
                dir * wingSpan * 0.1f, size * 0.2f,   // Control point 1
                0f, size * 0.1f,                      // Control point 2
                0f, 0f                                // Back to start
            )
        }

        private fun drawWingPatterns(g2d: Graphics2D, wing: Path2D.Float, alpha: Int) {
            // Draw spots and patterns
            g2d.color = Color(
                (color.red * 0.7f).toInt(),
                (color.green * 0.7f).toInt(),
                (color.blue * 0.7f).toInt(),
                (alpha * 0.5f).toInt()
            )
            
            // Add circular patterns
            for (i in 0..2) {
                val spotX = wingSpan * (0.3f + i * 0.2f)
                val spotY = size * (0.2f - i * 0.15f)
                g2d.fillOval(
                    (spotX - size * 0.15f).toInt(),
                    (spotY - size * 0.15f).toInt(),
                    (size * 0.3f).toInt(),
                    (size * 0.3f).toInt()
                )
            }
        }

        private fun drawBody(g2d: Graphics2D, alpha: Int) {
            // Draw thorax (main body)
            g2d.color = Color(
                color.red / 3,
                color.green / 3,
                color.blue / 3,
                alpha
            )
            
            // Draw head
            g2d.fillOval(
                (-size / 3).toInt(),
                (-size * 0.8f).toInt(),
                (size * 0.6f).toInt(),
                (size * 0.6f).toInt()
            )
            
            // Draw body segments
            val bodyPath = Path2D.Float()
            bodyPath.moveTo(0f, -size * 0.5f)
            bodyPath.curveTo(
                size * 0.2f, 0f,         // Control point 1
                size * 0.15f, size,      // Control point 2
                0f, size * 1.2f          // End point
            )
            bodyPath.curveTo(
                -size * 0.15f, size,     // Control point 1
                -size * 0.2f, 0f,        // Control point 2
                0f, -size * 0.5f         // Back to start
            )
            g2d.fill(bodyPath)

            // Draw antennae
            g2d.stroke = BasicStroke(size / 8f)
            val antennaPath = Path2D.Float()
            antennaPath.moveTo(-size * 0.2f, -size * 0.7f)
            antennaPath.curveTo(
                -size * 0.4f, -size * 1.2f,
                -size * 0.6f, -size * 1.3f,
                -size * 0.7f, -size * 1.4f
            )
            g2d.draw(antennaPath)
            
            antennaPath.reset()
            antennaPath.moveTo(size * 0.2f, -size * 0.7f)
            antennaPath.curveTo(
                size * 0.4f, -size * 1.2f,
                size * 0.6f, -size * 1.3f,
                size * 0.7f, -size * 1.4f
            )
            g2d.draw(antennaPath)
        }

        override fun reset() {
            isDead = false
            lifetime = (5..8).random().toFloat()
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

private fun ClosedFloatingPointRange<Float>.random(): Float =
    start + Math.random().toFloat() * (endInclusive - start)

@State(
    name = "com.zeus.thunderbolt.services.ZeusThunderbolt",
    storages = [Storage("ThunderSettings.xml")]
)

class ThunderSettings : PersistentStateComponent<ThunderSettings> {
    var themeIndex: Int = -1 // Default to "None" theme
    var regularParticlesEnabled: Boolean = ZeusThunderbolt.DEFAULT_REGULAR_PARTICLES_ENABLED
    var stardustParticlesEnabled: Boolean = ZeusThunderbolt.DEFAULT_STARDUST_PARTICLES_ENABLED
    var snowEnabled: Boolean = ZeusThunderbolt.DEFAULT_SNOW_ENABLED
    var reverseParticlesEnabled: Boolean = ZeusThunderbolt.DEFAULT_REVERSE_PARTICLES_ENABLED
    var butterflyParticlesEnabled: Boolean = ZeusThunderbolt.DEFAULT_BUTTERFLY_PARTICLES_ENABLED

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
