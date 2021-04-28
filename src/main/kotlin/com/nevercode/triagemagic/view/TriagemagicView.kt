/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.nevercode.triagemagic.view

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.util.EventDispatcher
import com.intellij.util.xmlb.annotations.Attribute
import io.flutter.pub.PubRoots
import io.flutter.run.daemon.DeviceService
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Label
import javax.swing.JPanel
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

@com.intellij.openapi.components.State(
    name = "TriagemagicView",
    storages = [Storage("\$WORKSPACE_FILE$")]
)
class TriagemagicView : PersistentStateComponent<TriagemagicViewState>, Disposable {

    private val viewState: TriagemagicViewState = TriagemagicViewState()

    override fun loadState(state: TriagemagicViewState) {
        this.viewState.copyFrom(state)
    }

    override fun getState(): TriagemagicViewState = viewState

    override fun dispose() {
        Disposer.dispose(this)
    }

    fun initToolWindow(project: Project, toolWindow: ToolWindow) {
        val deviceService = DeviceService.getInstance(project)
        val pubProjectRoot = PubRoots.forProject(project).first()

        if (toolWindow.isDisposed) return

        val contentManager = toolWindow.contentManager

        val triageTabContent = TriageTabContent(
            project,
            deviceService,
            pubProjectRoot
        )

        contentManager.addContent(createTriageTab(contentManager, triageTabContent))
        contentManager.addContent(createTemplateTab(contentManager))
    }

    private fun createTriageTab(contentManager: ContentManager, tabContent: Component): Content {
        val tab = JPanel(BorderLayout())
        tab.add(tabContent)

        val content = contentManager.factory.createContent(null, "Triage", false)
        content.component = tab
        content.putUserData(ToolWindow.SHOW_CONTENT_ICON, java.lang.Boolean.TRUE)
        content.icon = AllIcons.General.AddJdk
        return content
    }

    private fun createTemplateTab(contentManager: ContentManager): Content {
        val tabContainer = JPanel(BorderLayout())
        tabContainer.add(Label("Add triage templates."), BorderLayout.CENTER)

        val content: Content = contentManager.factory.createContent(null, "Template", false)
        content.component = tabContainer
        content.putUserData(ToolWindow.SHOW_CONTENT_ICON, java.lang.Boolean.TRUE)
        content.icon = AllIcons.Actions.ListFiles
        return content
    }
}

inline fun <reified String> Collection<String>.toArrayList(): ArrayList<String> {
    val output = arrayListOf<String>()
    this.forEach { output.add(it) }
    return output
}

class TriagemagicViewState {
    private val dispatcher = EventDispatcher.create(ChangeListener::class.java)

    @Attribute(value = "splitter-proportion")
    var splitterProportion = 0f

    @JvmName("getSplitterProportion1")
    fun getSplitterProportion(): Float {
        return if (splitterProportion <= 0.0f) 0.7f else splitterProportion
    }

    @JvmName("setSplitterProportion1")
    fun setSplitterProportion(value: Float) {
        splitterProportion = value
        dispatcher.multicaster.stateChanged(ChangeEvent(this))
    }

    fun addListener(listener: ChangeListener) {
        dispatcher.addListener(listener)
    }

    fun removeListener(listener: ChangeListener) {
        dispatcher.removeListener(listener)
    }

    // This attribute exists only to silence the "com.intellij.util.xmlb.Binding - no accessors for class" warning.
    @Attribute(value = "placeholder")
    var placeholder: String? = null
    fun copyFrom(other: TriagemagicViewState) {
        placeholder = other.placeholder
        splitterProportion = other.splitterProportion
    }
}