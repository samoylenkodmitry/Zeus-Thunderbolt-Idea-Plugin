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
            component.fireParticlesEnabled != ZeusThunderbolt.isFireParticlesEnabled()
        } ?: false

    override fun apply() {
        settingsComponent?.let { component ->
            ZeusThunderbolt.setTheme(component.themeIndex)
            ZeusThunderbolt.setSnowEnabled(component.snowEnabled)
            ZeusThunderbolt.setRegularParticlesEnabled(component.regularParticlesEnabled)
            ZeusThunderbolt.setFireParticlesEnabled(component.fireParticlesEnabled)
        }
    }

    override fun reset() {
        settingsComponent?.let { component ->
            component.themeIndex = ZeusThunderbolt.getCurrentThemeIndex()
            component.snowEnabled = ZeusThunderbolt.isSnowEnabled()
            component.regularParticlesEnabled = ZeusThunderbolt.isRegularParticlesEnabled()
            component.fireParticlesEnabled = ZeusThunderbolt.isFireParticlesEnabled()
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
    private val fireParticlesCheckbox = JBCheckBox("Enable Fire Particles", ZeusThunderbolt.isFireParticlesEnabled())
    val panel: JPanel

    init {
        themeCombo.selectedIndex = ZeusThunderbolt.getCurrentThemeIndex()
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Effect Theme:"), themeCombo)
            .addComponent(regularParticlesCheckbox)
            .addComponent(fireParticlesCheckbox)
            .addComponent(snowCheckbox)
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

    var fireParticlesEnabled: Boolean
        get() = fireParticlesCheckbox.isSelected
        set(value) {
            fireParticlesCheckbox.isSelected = value
        }
}