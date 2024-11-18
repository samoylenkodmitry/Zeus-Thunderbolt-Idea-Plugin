package org.jetbrains.plugins.template.services

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.project.Project
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.geom.GeneralPath
import java.awt.geom.Point2D
import java.awt.geom.CubicCurve2D
import java.util.*
import javax.swing.JComponent
import kotlin.concurrent.thread
import kotlin.math.absoluteValue

@Service(Service.Level.PROJECT)
class MyProjectService(project: Project) {

    object Singleton {
        init {
            initPlugin()
        }

        private fun initPlugin() {
            thisLogger()
            val editorFactory = EditorFactory.getInstance()
            val editors = mutableListOf<Editor>()
            val elements = Collections.synchronizedList(mutableListOf<PhysicsElement>())
            val addParticles = { editor: Editor ->
                if (editor.isActualEditor()) {
                    val scrollOffsetX = editor.scrollingModel.horizontalScrollOffset.toFloat()
                    val scrollOffsetY = editor.scrollingModel.verticalScrollOffset.toFloat()
                    val caretPositions = editor.caretModel.allCarets.map { it.getPoint() }
                    for (caretPosition in caretPositions) {
                        val newParticles = generateParticles(x0 = scrollOffsetX, y0 = scrollOffsetY, caretPosition)
                        thisLogger().debug("Generated ${newParticles.size} particles")
                        elements.addAll(newParticles)
                    }
                }
            }
            val caretListener = object : CaretListener {

                override fun caretAdded(event: CaretEvent) {
                    super.caretAdded(event)
                }

                override fun caretPositionChanged(event: CaretEvent) {
                    super.caretPositionChanged(event)
                    addParticles(event.editor)
                }

                override fun caretRemoved(event: CaretEvent) {
                    super.caretRemoved(event)
                }
            }
            EditorFactory.getInstance().eventMulticaster.addCaretListener(caretListener) {
                // onDispose
            }

            class ElementsContainer(val editor: Editor) : JComponent() {
                var lastPositions = listOf<Point>()

                init {
                    val parent = editor.contentComponent
                    setBounds(parent.bounds)
                    setVisible(true)
                    parent.addComponentListener(object : ComponentAdapter() {
                        override fun componentMoved(e: ComponentEvent) = adjustBounds()
                        override fun componentResized(e: ComponentEvent) = adjustBounds()
                        fun adjustBounds() {
                            val area = editor.scrollingModel.visibleArea

                            bounds = Rectangle(area.x, area.y, area.width, area.height)
                        }
                    })
                    val trackCarets = {
                        val scrollOffsetX = editor.scrollingModel.horizontalScrollOffset.toFloat()
                        val scrollOffsetY = editor.scrollingModel.verticalScrollOffset.toFloat()
                        val newPositions = editor.caretModel.allCarets.map { it.getPoint() }
                        // Create chain particles for big jumps
                        for (lastPos in lastPositions) {
                            for (newPos in newPositions) {
                                val dx = newPos.x - lastPos.x
                                val dy = newPos.y - lastPos.y
                                val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
                                if (distance > 50) { // Only for significant movements
                                    elements.addAll(
                                        generateChainParticles(
                                            x0 = scrollOffsetX,
                                            y0 = scrollOffsetY,
                                            lastPos, newPos
                                        )
                                    )
                                }
                            }
                        }
                        lastPositions = newPositions
                    }
                    editor.caretModel.addCaretListener(object : CaretListener {
                        override fun caretAdded(event: CaretEvent) = trackCarets()
                        override fun caretPositionChanged(event: CaretEvent) = trackCarets()
                        override fun caretRemoved(event: CaretEvent) = trackCarets()
                    })
                }

                override fun paint(g: Graphics) {
                    super.paint(g)
                    val scrollOffsetX = editor.scrollingModel.horizontalScrollOffset.toFloat()
                    val scrollOffsetY = editor.scrollingModel.verticalScrollOffset.toFloat()
                    val elementsCopy = elements.toList()
                    for (e in elementsCopy) {
                        val dx = e.x0 - scrollOffsetX
                        val dy = e.y0 - scrollOffsetY
                        g.translate(dx.toInt(), dy.toInt())
                        e.render(g, elementsCopy)
                        g.translate(-dx.toInt(), -dy.toInt())
                    }
                }
            }

            val containers = mutableMapOf<Editor, ElementsContainer>()
            val renderThread = thread {
                var frameCount = 0
                while (true) {
                    frameCount++
                    if (frameCount % 60 == 0) { // Log every second at 60 FPS
                        thisLogger().debug("Active elements: ${elements.size}")
                    }
                    val elementsCopy = elements.toList()
                    // Update all elements
                    for (e in elementsCopy) {
                        e.update(elementsCopy)
                    }
                    // Clean up dead elements in a separate pass
                    elementsCopy
                        .filter { it is ChainParticle && it.isDead || it is Particle && it.isDead }
                        .forEach { deadElement ->
                            thisLogger().debug("Removing dead element at (${deadElement.x}, ${deadElement.y})")
                            elements.remove(deadElement)
                        }
                    containers.values.forEach { it.repaint() }
                    Thread.sleep(16) // ~60 FPS
                }
            }

            editorFactory.addEditorFactoryListener(object : EditorFactoryListener {
                override fun editorCreated(event: EditorFactoryEvent) {
                    super.editorCreated(event)
                    val editor = event.editor
                    val isActualEditor = editor.isActualEditor()
                    thisLogger().debug("Editor created: ${editor.virtualFile?.name}, isActual: $isActualEditor")
                    if (isActualEditor) {
                        editors.add(editor)
                        val container = ElementsContainer(editor)
                        editor.contentComponent.add(container)
                        containers[editor] = container
                    }
                }

                override fun editorReleased(event: EditorFactoryEvent) {
                    thisLogger().debug("Editor released: ${event.editor.virtualFile?.name}")
                    super.editorReleased(event)
                    editors.remove(event.editor)
                    val container = containers.remove(event.editor)
                    event.editor.contentComponent.remove(container)
                }

            }) {
                // onDispose
                editors.clear()
                renderThread.interrupt()
                renderThread.join()
            }
            val typedAction = TypedAction.getInstance()
            val defaultHandler = typedAction.rawHandler
            val typedActionHandler = object : TypedActionHandler {

                override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
                    // addParticles(editor)
                    try {
                        defaultHandler.execute(editor, charTyped, dataContext)
                    } catch (e: Exception) {
                        thisLogger().error(e)
                    }
                }

            }
            typedAction.setupRawHandler(typedActionHandler)
        }
    }
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
    val x0: Float
    val y0: Float
    var x: Float
    var y: Float
    var chainStrength: Float   // New property for chain connections
    fun update(elements: List<PhysicsElement>)
    fun render(g: Graphics, elements: List<PhysicsElement>)
}

fun generateChainParticles(x0: Float, y0: Float, start: Point, end: Point, count: Int = 5): List<ChainParticle> {
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
            maxChainDistance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat() / 1.5f,
            friction = 0.99f,
            originalX = x,    // Remember original position
            originalY = y
        )
    }
}

fun generateParticles(x0: Float, y0: Float, point: Point, count: Int = 10) = (1..count).map {
    val angle = Math.random() * 2 * Math.PI
    val speed = (100..200).random().toFloat()
    Particle(
        x0, y0,
        point.x.toFloat(),
        point.y.toFloat(),
        (2..4).random().toFloat(),
        Color.getHSBColor(Math.random().toFloat(), 0.8f, 1f),
        Force(
            (speed * Math.cos(angle)).toFloat(),
            (speed * Math.sin(angle)).toFloat()
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
    var lastTs = System.currentTimeMillis()
    var isDead = false

    override fun update(elements: List<PhysicsElement>) {
        val ts = System.currentTimeMillis()
        val dt = (ts - lastTs) / 1000f
        lastTs = ts

        // Electric vibration
        vibePhase += vibeFrequency * dt
        val vibeOffsetX = Math.sin(vibePhase.toDouble()) * vibeAmplitude
        val vibeOffsetY = Math.cos(vibePhase * 1.5) * vibeAmplitude

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
        elements
            .filter { it != this && it.chainStrength > 0 }
            .forEach { other ->
                val dx = other.x - x
                val dy = other.y - y
                val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (distance < maxChainDistance) {
                    val strength = (1 - distance / maxChainDistance) * chainStrength
                    force.x += dx * strength * dt
                    force.y += dy * strength * dt
                }
            }

        lifetime -= dt
        if (lifetime <= 0) isDead = true
    }

    override fun render(g: Graphics, elements: List<PhysicsElement>) {
        val g2d = g.create() as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Draw connections to nearby particles
        g2d.stroke = BasicStroke(size / 3f)
        val nearby = elements
            .filter { it != this && it.chainStrength > 0 }
            .filter { other ->
                val dx = other.x - x
                val dy = other.y - y
                Math.sqrt((dx * dx + dy * dy).toDouble()) < maxChainDistance
            }

        nearby.forEach { other ->
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
}

// Update existing Particle class to implement chainStrength
data class Particle(
    override val x0: Float,
    override val y0: Float,
    override var x: Float,
    override var y: Float,
    var size: Float,
    var color: Color,
    var force: Force,
    var friction: Float = 0.95f,
    var lifetime: Float = 2f,
    var glowRadius: Float = size * 4f,  // New property for glow
    var rotation: Float = (Math.random() * Math.PI * 2).toFloat(),
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
    var trail: MutableList<Point2D.Float> = mutableListOf(),
    override var chainStrength: Float = 0f  // Regular particles don't form chains
) : PhysicsElement {
    var lastTs = System.currentTimeMillis()
    var isDead = false

    override fun update(elements: List<PhysicsElement>) {
        val ts = System.currentTimeMillis()
        val dt = (ts - lastTs) / 1000f
        lastTs = ts

        // Add current position to trail
        trail.add(Point2D.Float(x, y))
        if (trail.size > 10) trail.removeAt(0)

        // Update pulse
        pulsePhase += pulseFrequency * dt
        val pulseFactor = 1f + 0.2f * Math.sin(pulsePhase.toDouble()).toFloat()
        glowRadius = size * 4f * pulseFactor

        // Add random wind force
        if (Math.random() < 0.05) { // 5% chance each frame
            force.x += (-50..50).random()
            force.y += (-50..50).random()
        }

        // Sine wave motion
        wobblePhase += wobbleFrequency * dt
        val wobbleForce = Math.sin(wobblePhase.toDouble()) * wobbleAmplitude
        force.x += wobbleForce.toFloat() * dt

        // Update rotation
        rotation += rotationSpeed * dt

        // Original physics
        val oldX = x
        val oldY = y
        x += force.x * dt
        y += force.y * dt

        if ((x - oldX).absoluteValue > 100 || (y - oldY).absoluteValue > 100) {
            thisLogger().warn("Large particle movement: ($oldX, $oldY) -> ($x, $y), force: (${force.x}, ${force.y})")
        }

        // Modified gravity (slightly reduced)
        force.y += 300f * dt

        // Apply friction with some randomness
        val randomFriction = friction * (98..102).random().toFloat() / 100f
        force.x *= randomFriction
        force.y *= randomFriction

        // Update lifetime
        lifetime -= dt
        if (lifetime <= 0) isDead = true

        // Fade out color
        color = Color(
            color.red,
            color.green,
            color.blue,
            (255 * (lifetime / 2f)).toInt().coerceIn(0, 255)
        )

        // Interpolate between colors based on lifetime
        val lifeProgress = (lifetime / 2f).coerceIn(0f, 1f)
        color = Color(
            (startColor.red * lifeProgress + endColor.red * (1 - lifeProgress)).toInt(),
            (startColor.green * lifeProgress + endColor.green * (1 - lifeProgress)).toInt(),
            (startColor.blue * lifeProgress + endColor.blue * (1 - lifeProgress)).toInt(),
            (255 * lifeProgress).toInt()
        )

        // Interact with nearby particles
        for (other in elements) {
            if (other != this) {
                val dx = other.x - x
                val dy = other.y - y
                val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (distance < 50f) {
                    val repulsion = 5f / (distance + 1f)
                    force.x -= dx * repulsion * dt
                    force.y -= dy * repulsion * dt
                }
            }
        }
    }

    override fun render(g: Graphics, elements: List<PhysicsElement>) {
        val g2d = g.create() as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Render trail
        if (trail.size > 1) {
            val trailColor = Color(color.red, color.green, color.blue, (50 * (lifetime / 2f)).toInt().coerceIn(0, 255))
            g2d.color = trailColor
            g2d.stroke = BasicStroke(size / 4f)
            val path = GeneralPath()
            path.moveTo(trail[0].x.toDouble(), trail[0].y.toDouble())
            for (i in 1 until trail.size) {
                path.lineTo(trail[i].x.toDouble(), trail[i].y.toDouble())
            }
            g2d.draw(path)
        }

        // Existing render code with size pulsing
        val pulseFactor = 1f + 0.2f * Math.sin(pulsePhase.toDouble()).toFloat()
        val currentSize = size * pulseFactor
        val currentGlowRadius = glowRadius * pulseFactor

        // Save original transform
        val originalTransform = g2d.transform

        // Translate to particle position and apply rotation
        g2d.translate(x.toDouble(), y.toDouble())
        g2d.rotate(rotation.toDouble())

        // Draw glow layers with rotation
        val numLayers = 3
        for (i in numLayers downTo 1) {
            val alpha = (0.1f * (i.toFloat() / numLayers) * (lifetime / 2f)).coerceIn(0f, 1f)
            g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)

            val layerRadius = currentGlowRadius * (i.toFloat() / numLayers)
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