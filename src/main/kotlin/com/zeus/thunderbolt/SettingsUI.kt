package com.zeus.thunderbolt

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JComboBox
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
            component.stardustParticlesEnabled != ZeusThunderbolt.isStardustParticlesEnabled() ||
            component.reverseParticlesEnabled != ZeusThunderbolt.isReverseParticlesEnabled()
        } ?: false

    override fun apply() {
        settingsComponent?.let { component ->
            ZeusThunderbolt.setTheme(component.themeIndex)
            ZeusThunderbolt.setSnowEnabled(component.snowEnabled)
            ZeusThunderbolt.setRegularParticlesEnabled(component.regularParticlesEnabled)
            ZeusThunderbolt.setStardustParticlesEnabled(component.stardustParticlesEnabled)
            ZeusThunderbolt.setReverseParticlesEnabled(component.reverseParticlesEnabled)
        }
    }

    override fun reset() {
        settingsComponent?.let { component ->
            component.themeIndex = ZeusThunderbolt.getCurrentThemeIndex()
            component.snowEnabled = ZeusThunderbolt.isSnowEnabled()
            component.regularParticlesEnabled = ZeusThunderbolt.isRegularParticlesEnabled()
            component.stardustParticlesEnabled = ZeusThunderbolt.isStardustParticlesEnabled()
            component.reverseParticlesEnabled = ZeusThunderbolt.isReverseParticlesEnabled()
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
    private val stardustParticlesCheckbox = JBCheckBox("Enable Stardust Particles", ZeusThunderbolt.isStardustParticlesEnabled())
    private val reverseParticlesCheckbox = JBCheckBox("Enable Reverse Particles", ZeusThunderbolt.isReverseParticlesEnabled())
    val panel: JPanel

    init {
        themeCombo.selectedIndex = ZeusThunderbolt.getCurrentThemeIndex()
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Effect Theme:"), themeCombo)
            .addComponent(regularParticlesCheckbox)
            .addComponent(stardustParticlesCheckbox)
            .addComponent(snowCheckbox)
            .addComponent(reverseParticlesCheckbox)
            .addComponentFillVertically(JPanel(), 0)
            .panel
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

    var stardustParticlesEnabled: Boolean
        get() = stardustParticlesCheckbox.isSelected
        set(value) {
            stardustParticlesCheckbox.isSelected = value
        }

    var reverseParticlesEnabled: Boolean
        get() = reverseParticlesCheckbox.isSelected
        set(value) {
            reverseParticlesCheckbox.isSelected = value
        }
}