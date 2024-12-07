package com.zeus.thunderbolt.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.zeus.thunderbolt.services.ZeusThunderbolt
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.JPanel

class ThunderSettingsConfigurable : Configurable {
    private var settingsComponent: ThunderSettingsComponent? = null

    override fun getDisplayName(): String = "Zeus Thunderbolt"

    override fun createComponent(): JComponent {
        settingsComponent = ThunderSettingsComponent()
        return settingsComponent!!.panel
    }

    override fun isModified(): Boolean = settingsComponent?.themeIndex != ZeusThunderbolt.getCurrentThemeIndex()

    override fun apply() {
        settingsComponent?.let { component ->
            ZeusThunderbolt.setTheme(component.themeIndex)
        }
    }

    override fun reset() {
        settingsComponent?.themeIndex = ZeusThunderbolt.getCurrentThemeIndex()
    }

    override fun disposeUIResources() {
        settingsComponent = null
    }
}

class ThunderSettingsComponent {
    private val themeCombo = JComboBox(arrayOf("None", "Winter", "Autumn"))
    val panel: JPanel

    init {
        themeCombo.selectedIndex = ZeusThunderbolt.getCurrentThemeIndex() + 1
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Theme:"), themeCombo)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    var themeIndex: Int
        get() = themeCombo.selectedIndex - 1
        set(value) {
            themeCombo.selectedIndex = value + 1
        }
}