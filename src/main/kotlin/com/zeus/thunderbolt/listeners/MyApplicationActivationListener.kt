package com.zeus.thunderbolt.listeners

import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.wm.IdeFrame
import com.zeus.thunderbolt.services.ZeusThunderbolt

internal class MyApplicationActivationListener : ApplicationActivationListener {

    override fun applicationActivated(ideFrame: IdeFrame) {
        ZeusThunderbolt
    }
}
