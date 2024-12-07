
package com.zeus.thunderbolt.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "org.jetbrains.plugins.template.settings.ThunderSettings",
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