/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.nevercode.triagemagic.view

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import io.flutter.pub.PubRoots
import io.flutter.run.daemon.DeviceService
import org.jdesktop.swingx.VerticalLayout
import java.awt.Component
import javax.swing.JPanel

@com.intellij.openapi.components.State(
    name = "TriagemagicView",
    storages = [Storage("\$WORKSPACE_FILE$")]
)
class TriagemagicView : Disposable {

    override fun dispose() {
        Disposer.dispose(this)
    }

    fun initToolWindow(project: Project, toolWindow: ToolWindow) {
        val deviceService = DeviceService.getInstance(project)
        val pubProjectRoot = PubRoots.forProject(project).first()

        if (toolWindow.isDisposed) return

        val contentManager = toolWindow.contentManager

        val triageTabContent = TriageTabContent(project, deviceService, pubProjectRoot)

        contentManager.addContent(createTab("Triage", contentManager, triageTabContent))
        contentManager.addContent(createTab("Format", contentManager, FormatTabContent()))
        contentManager.addContent(createTab("Template", contentManager, TemplateTabContent()))
    }

    private fun createTab(name: String, contentManager: ContentManager, tabContent: Component): Content {
        val tabContainer = JPanel(VerticalLayout())
        tabContainer.add(tabContent)
        return contentManager.factory.createContent(tabContainer, name, false)
    }
}

inline fun <reified String> Collection<String>.toArrayList(): ArrayList<String> {
    val output = arrayListOf<String>()
    this.forEach { output.add(it) }
    return output
}
