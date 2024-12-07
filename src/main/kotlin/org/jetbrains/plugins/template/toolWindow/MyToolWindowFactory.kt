package org.jetbrains.plugins.template.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import org.jetbrains.plugins.template.MyBundle
import org.jetbrains.plugins.template.services.MyProjectService
import javax.swing.JComboBox
import javax.swing.BoxLayout
import java.awt.FlowLayout

class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger()
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(toolWindow: ToolWindow) {

        private val service = toolWindow.project.service<MyProjectService>()

        fun getContent() = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)

            // Theme selector panel
            val themePanel = JBPanel<JBPanel<*>>().apply {
                layout = FlowLayout(FlowLayout.LEFT)
                add(JBLabel(MyBundle.message("themeLabel")))
                
                // Create and add theme combo box
                val themeNames = arrayOf("None", "Winter", "Autumn")
                val themeCombo = JComboBox(themeNames)
                themeCombo.selectedIndex = MyProjectService.Singleton.getCurrentThemeIndex() + 1 // +1 because -1 is None
                themeCombo.addActionListener { 
                    MyProjectService.Singleton.setTheme(themeCombo.selectedIndex - 1) // -1 to convert back to internal index
                }
                add(themeCombo)
            }
            add(themePanel)
        }
    }
}
