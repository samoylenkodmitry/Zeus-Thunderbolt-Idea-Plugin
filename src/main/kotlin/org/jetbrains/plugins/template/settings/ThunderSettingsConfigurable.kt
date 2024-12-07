package org.jetbrains.plugins.template.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import org.jetbrains.plugins.template.MyBundle
import org.jetbrains.plugins.template.services.MyProjectService
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

    override fun isModified(): Boolean {
        val settings = ThunderSettings.getInstance()
        return settingsComponent?.themeIndex != settings.themeIndex
    }

    override fun apply() {
        val settings = ThunderSettings.getInstance()
        settingsComponent?.let { component ->
            settings.themeIndex = component.themeIndex
            MyProjectService.Singleton.setTheme(component.themeIndex)
        }
    }

    override fun reset() {
        val settings = ThunderSettings.getInstance()
        settingsComponent?.themeIndex = settings.themeIndex
    }

    override fun disposeUIResources() {
        settingsComponent = null
    }
}

class ThunderSettingsComponent {
    private val themeCombo = JComboBox(arrayOf("None", "Winter", "Autumn"))
    val panel: JPanel

    init {
        themeCombo.selectedIndex = ThunderSettings.getInstance().themeIndex + 1
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(MyBundle.message("themeLabel")), themeCombo)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    var themeIndex: Int
        get() = themeCombo.selectedIndex - 1
        set(value) {
            themeCombo.selectedIndex = value + 1
        }
}