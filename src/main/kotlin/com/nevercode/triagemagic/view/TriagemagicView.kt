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
import io.flutter.console.FlutterConsoles
import io.flutter.pub.PubRoot
import io.flutter.pub.PubRoots
import io.flutter.run.FlutterDevice
import io.flutter.run.FlutterLaunchMode
import io.flutter.run.common.RunMode
import io.flutter.run.daemon.DeviceService
import io.flutter.sdk.FlutterSdk
import io.flutter.sdk.FlutterSdkUtil
import org.jdesktop.swingx.HorizontalLayout
import org.jdesktop.swingx.VerticalLayout
import java.awt.*
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

@com.intellij.openapi.components.State(
    name = "TriagemagicView",
    storages = [Storage("\$WORKSPACE_FILE$")]
)
class TriagemagicView : PersistentStateComponent<TriagemagicViewState>, Disposable {

    private val viewState: TriagemagicViewState = TriagemagicViewState()

    private val triageTab = JPanel(BorderLayout())

    private lateinit var project: Project
    private lateinit var pubProjectRoot: PubRoot
    private lateinit var deviceService: DeviceService
    private var selectedChannels = mutableSetOf<String>()
    private var availableDevices = mutableListOf<FlutterDevice>()
    private var knownFlutterSdkPaths = arrayListOf<String>()
    private var selectedDevice: FlutterDevice? = null

    override fun loadState(state: TriagemagicViewState) {
        this.viewState.copyFrom(state)
    }

    override fun getState(): TriagemagicViewState = viewState

    override fun dispose() {
        Disposer.dispose(this)
    }

    fun initToolWindow(project: Project, toolWindow: ToolWindow) {
        this.project = project
        this.deviceService = DeviceService.getInstance(project)
        this.pubProjectRoot = PubRoots.forProject(project).first()

        if (toolWindow.isDisposed) return

        onRefresh()
        updateForEmptyContent(toolWindow)
    }

    private fun updateForEmptyContent(toolWindow: ToolWindow) {
        // There's a possible race here where the tool window gets disposed while we're displaying contents.
        if (toolWindow.isDisposed) {
            return
        }

        val contentManager = toolWindow.contentManager
        contentManager.addContent(createTriageTab(contentManager))
        contentManager.addContent(createTemplateTab(contentManager))
    }

    private fun createTriageTab(contentManager: ContentManager): Content {
        // Add channels Content
        triageTab.add(buildChannelsContent())

        val content: Content = contentManager.factory.createContent(null, "Triage", false)
        content.component = triageTab
        content.putUserData(ToolWindow.SHOW_CONTENT_ICON, java.lang.Boolean.TRUE)
        content.icon = AllIcons.General.AddJdk
        return content
    }

    private fun buildChannelsContent(): Component {
        val group = JPanel(VerticalLayout())
        group.maximumSize = Dimension(180, 2400)

        val header = Label("Run on Multiple Channels")
        header.font = Font("", 70, 16)
        group.border = BorderFactory.createEmptyBorder(8, 32, 0, 32)

        val refreshBtn = Button("Refresh")
        refreshBtn.addActionListener { onRefresh() }

        group.add(refreshBtn)
        group.add(gapComponent())
        group.add(gapComponent())
        group.add(header)
        group.add(gapComponent())

        val horizontalLayout = JPanel(HorizontalLayout())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(buildChannelsGroup())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(buildDevicesGroup())
        group.add(horizontalLayout)

        val runButton = Button("Run on selected channels")
        runButton.maximumSize = Dimension(150, 100)
        runButton.addActionListener {
            if (selectedChannels.isEmpty()) return@addActionListener

            if (selectedDevice == null) return@addActionListener

            // Run project on all selected channels
            selectedChannels.forEach { channelHomePath ->
                runProject(channelHomePath, selectedDevice!!)
            }
        }
        group.add(gapComponent())
        group.add(runButton)
        return group
    }

    private fun buildDevicesGroup(): Component {
        val group = JPanel(VerticalLayout())
        group.add(Label("Select a Device"))
        group.add(gapComponent())
        val g = ButtonGroup()

        if (availableDevices.isEmpty()) {
            group.add(Label("No Devices!"))
        } else {
            availableDevices.forEach { device ->
                val radio = JRadioButton(device.deviceName())
                radio.addActionListener {
                    selectedDevice = device
                    g.setSelected(radio.model, true)
                }
                group.add(radio)
                g.add(radio)
            }
        }
        return group
    }

    private fun buildChannelsGroup(): Component {
        val group = JPanel(VerticalLayout())
        group.add(Label("Select Channels"))
        group.add(gapComponent())

        if (knownFlutterSdkPaths.isEmpty()) {
            group.add(Label("No Channel!"))
        } else {
            knownFlutterSdkPaths.forEach { channelPath ->
                val name = channelPath.substring(channelPath.lastIndexOf('_') + 1)
                val btn = JRadioButton(name.capitalize())
                btn.addChangeListener {
                    when (btn.isSelected) {
                        true -> selectedChannels.add(channelPath)
                        false -> selectedChannels.remove(channelPath)
                    }
                }
                group.add(btn)
            }
        }
        return group
    }

    private fun onRefresh() {
        knownFlutterSdkPaths.clear()
        knownFlutterSdkPaths.addAll(FlutterSdkUtil.getKnownFlutterSdkPaths())

        availableDevices.clear()
        if (deviceService.connectedDevices != null) {
            availableDevices.addAll(deviceService.connectedDevices)
        }

        EventQueue.invokeLater {
            triageTab.removeAll()
            triageTab.add(buildChannelsContent())
            triageTab.revalidate()
            triageTab.repaint()
        }
    }

    private fun getFlutterSdk(path: String? = null): FlutterSdk? {
        if (path != null)
            return FlutterSdk.forPath(path)
        return null
    }

    private fun runProject(flutterChannelHomePath: String, device: FlutterDevice) {
        FlutterConsoles.displayMessage(
            project, null,
            "Triagemagic running with $flutterChannelHomePath..."
        )

        val sdk = getFlutterSdk(flutterChannelHomePath)
        sdk?.flutterPackagesGet(pubProjectRoot)?.startInConsole(project)
        sdk?.flutterRun(
            pubProjectRoot,
            pubProjectRoot.libMain!!,
            device,
            RunMode.DEBUG,
            FlutterLaunchMode.DEBUG,
            project,
        )?.startInConsole(project)
    }

    private fun gapComponent(): Component {
        val gap = Label("")
        return gap
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