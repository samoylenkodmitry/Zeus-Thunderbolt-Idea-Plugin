package com.zeus.thunderbolt

import com.intellij.openapi.options.Configurable
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
    private val themeCombo = JComboBox(ZeusThunderbolt.getThemeNames())
    val panel: JPanel

    init {
        themeCombo.selectedIndex = ZeusThunderbolt.getCurrentThemeIndex() + 1
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Effect Theme:"), themeCombo)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    var themeIndex: Int
        get() = themeCombo.selectedIndex - 1
        set(value) {
            themeCombo.selectedIndex = value + 1
        }
}