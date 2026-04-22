package com.zeus.thunderbolt

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.JSlider
import javax.swing.JPanel

class SettingsUI : Configurable {
    private var settingsComponent: ThunderSettingsComponent? = null

    override fun getDisplayName(): String = "Zeus Thunderbolt"

    override fun createComponent(): JComponent {
        settingsComponent = ThunderSettingsComponent()
        return settingsComponent!!.panel
    }

    override fun isModified(): Boolean = 
        settingsComponent?.let { component ->
            component.themeIndex != ZeusThunderbolt.getCurrentThemeIndex() ||
            component.snowEnabled != ZeusThunderbolt.isSnowEnabled() ||
            component.regularParticlesEnabled != ZeusThunderbolt.isRegularParticlesEnabled() ||
            component.regularParticlesIntensity != ZeusThunderbolt.getRegularParticlesIntensity() ||
            component.stardustParticlesEnabled != ZeusThunderbolt.isStardustParticlesEnabled() ||
            component.stardustParticlesIntensity != ZeusThunderbolt.getStardustParticlesIntensity() ||
            component.reverseParticlesEnabled != ZeusThunderbolt.isReverseParticlesEnabled() ||
            component.reverseParticlesIntensity != ZeusThunderbolt.getReverseParticlesIntensity() ||
            component.butterflyParticlesEnabled != ZeusThunderbolt.isButterfliesEnabled() ||
            component.butterflyParticlesIntensity != ZeusThunderbolt.getButterflyParticlesIntensity() ||
            component.grassEnabled != ZeusThunderbolt.isGrassEnabled() ||
            component.grassIntensity != ZeusThunderbolt.getGrassIntensity() ||
            component.snowIntensity != ZeusThunderbolt.getSnowIntensity()
        } ?: false

    override fun apply() {
        settingsComponent?.let { component ->
            ZeusThunderbolt.setTheme(component.themeIndex)
            ZeusThunderbolt.setSnowEnabled(component.snowEnabled)
            ZeusThunderbolt.setRegularParticlesEnabled(component.regularParticlesEnabled)
            ZeusThunderbolt.setRegularParticlesIntensity(component.regularParticlesIntensity)
            ZeusThunderbolt.setStardustParticlesEnabled(component.stardustParticlesEnabled)
            ZeusThunderbolt.setStardustParticlesIntensity(component.stardustParticlesIntensity)
            ZeusThunderbolt.setReverseParticlesEnabled(component.reverseParticlesEnabled)
            ZeusThunderbolt.setReverseParticlesIntensity(component.reverseParticlesIntensity)
            ZeusThunderbolt.setButterfliesEnabled(component.butterflyParticlesEnabled)
            ZeusThunderbolt.setButterflyParticlesIntensity(component.butterflyParticlesIntensity)
            ZeusThunderbolt.setGrassEnabled(component.grassEnabled)
            ZeusThunderbolt.setGrassIntensity(component.grassIntensity)
            ZeusThunderbolt.setSnowIntensity(component.snowIntensity)
        }
    }

    override fun reset() {
        settingsComponent?.let { component ->
            component.themeIndex = ZeusThunderbolt.getCurrentThemeIndex()
            component.snowEnabled = ZeusThunderbolt.isSnowEnabled()
            component.regularParticlesEnabled = ZeusThunderbolt.isRegularParticlesEnabled()
            component.regularParticlesIntensity = ZeusThunderbolt.getRegularParticlesIntensity()
            component.stardustParticlesEnabled = ZeusThunderbolt.isStardustParticlesEnabled()
            component.stardustParticlesIntensity = ZeusThunderbolt.getStardustParticlesIntensity()
            component.reverseParticlesEnabled = ZeusThunderbolt.isReverseParticlesEnabled()
            component.reverseParticlesIntensity = ZeusThunderbolt.getReverseParticlesIntensity()
            component.butterflyParticlesEnabled = ZeusThunderbolt.isButterfliesEnabled()
            component.butterflyParticlesIntensity = ZeusThunderbolt.getButterflyParticlesIntensity()
            component.grassEnabled = ZeusThunderbolt.isGrassEnabled()
            component.grassIntensity = ZeusThunderbolt.getGrassIntensity()
            component.snowIntensity = ZeusThunderbolt.getSnowIntensity()
        }
    }

    override fun disposeUIResources() {
        settingsComponent = null
    }
}

class ThunderSettingsComponent {
    private val themeCombo = JComboBox(ZeusThunderbolt.getThemeNames().toTypedArray())
    private val snowCheckbox = JBCheckBox("Enable Snow Effect", ZeusThunderbolt.isSnowEnabled())
    private val regularParticlesCheckbox = JBCheckBox("Enable Regular Particles", ZeusThunderbolt.isRegularParticlesEnabled())
    private val regularParticlesIntensitySlider = createIntensitySlider(ZeusThunderbolt.getRegularParticlesIntensity())
    private val stardustParticlesCheckbox = JBCheckBox("Enable Stardust Particles", ZeusThunderbolt.isStardustParticlesEnabled())
    private val stardustParticlesIntensitySlider = createIntensitySlider(ZeusThunderbolt.getStardustParticlesIntensity())
    private val reverseParticlesCheckbox = JBCheckBox("Enable Reverse Particles", ZeusThunderbolt.isReverseParticlesEnabled())
    private val reverseParticlesIntensitySlider = createIntensitySlider(ZeusThunderbolt.getReverseParticlesIntensity())
    private val butterflyParticlesCheckbox = JBCheckBox("Enable Butterfly Particles", ZeusThunderbolt.isButterfliesEnabled())
    private val butterflyParticlesIntensitySlider = createIntensitySlider(ZeusThunderbolt.getButterflyParticlesIntensity())
    private val grassCheckbox = JBCheckBox("Enable Caret Grass", ZeusThunderbolt.isGrassEnabled())
    private val grassIntensitySlider = createIntensitySlider(ZeusThunderbolt.getGrassIntensity())
    private val snowIntensitySlider = createIntensitySlider(ZeusThunderbolt.getSnowIntensity())
    val panel: JPanel

    init {
        themeCombo.selectedIndex = ZeusThunderbolt.getCurrentThemeIndex()
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Effect Theme:"), themeCombo)
            .addComponent(createEffectRow(regularParticlesCheckbox, regularParticlesIntensitySlider))
            .addComponent(createEffectRow(stardustParticlesCheckbox, stardustParticlesIntensitySlider))
            .addComponent(createEffectRow(snowCheckbox, snowIntensitySlider))
            .addComponent(createEffectRow(reverseParticlesCheckbox, reverseParticlesIntensitySlider))
            .addComponent(createEffectRow(butterflyParticlesCheckbox, butterflyParticlesIntensitySlider))
            .addComponent(createEffectRow(grassCheckbox, grassIntensitySlider))
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun createIntensitySlider(currentValue: Int) = JSlider(0, 100, currentValue).apply {
        majorTickSpacing = 25
        minorTickSpacing = 5
        paintTicks = true
        toolTipText = "Effect intensity"
    }

    private fun createEffectRow(checkBox: JBCheckBox, slider: JSlider) = JPanel(BorderLayout(12, 0)).apply {
        add(checkBox, BorderLayout.WEST)
        add(slider, BorderLayout.CENTER)
    }

    var themeIndex: Int
        get() = themeCombo.selectedIndex
        set(value) {
            themeCombo.selectedIndex = value
        }

    var snowEnabled: Boolean
        get() = snowCheckbox.isSelected
        set(value) {
            snowCheckbox.isSelected = value
        }

    var regularParticlesEnabled: Boolean
        get() = regularParticlesCheckbox.isSelected
        set(value) {
            regularParticlesCheckbox.isSelected = value
        }

    var regularParticlesIntensity: Int
        get() = regularParticlesIntensitySlider.value
        set(value) {
            regularParticlesIntensitySlider.value = value
        }

    var stardustParticlesEnabled: Boolean
        get() = stardustParticlesCheckbox.isSelected
        set(value) {
            stardustParticlesCheckbox.isSelected = value
        }

    var stardustParticlesIntensity: Int
        get() = stardustParticlesIntensitySlider.value
        set(value) {
            stardustParticlesIntensitySlider.value = value
        }

    var reverseParticlesEnabled: Boolean
        get() = reverseParticlesCheckbox.isSelected
        set(value) {
            reverseParticlesCheckbox.isSelected = value
        }

    var reverseParticlesIntensity: Int
        get() = reverseParticlesIntensitySlider.value
        set(value) {
            reverseParticlesIntensitySlider.value = value
        }

    var butterflyParticlesEnabled: Boolean
        get() = butterflyParticlesCheckbox.isSelected
        set(value) {
            butterflyParticlesCheckbox.isSelected = value
        }

    var butterflyParticlesIntensity: Int
        get() = butterflyParticlesIntensitySlider.value
        set(value) {
            butterflyParticlesIntensitySlider.value = value
        }

    var grassEnabled: Boolean
        get() = grassCheckbox.isSelected
        set(value) {
            grassCheckbox.isSelected = value
        }

    var grassIntensity: Int
        get() = grassIntensitySlider.value
        set(value) {
            grassIntensitySlider.value = value
        }

    var snowIntensity: Int
        get() = snowIntensitySlider.value
        set(value) {
            snowIntensitySlider.value = value
        }
}
