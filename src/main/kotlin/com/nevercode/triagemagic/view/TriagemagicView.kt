/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.nevercode.triagemagic.view

import com.intellij.execution.ui.layout.impl.JBRunnerTabs
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.EventDispatcher
import com.intellij.util.xmlb.annotations.Attribute
import io.flutter.console.FlutterConsoles
import io.flutter.pub.PubRoot
import io.flutter.pub.PubRoots
import io.flutter.run.FlutterAppManager
import io.flutter.run.FlutterDevice
import io.flutter.run.FlutterLaunchMode
import io.flutter.run.common.RunMode
import io.flutter.run.daemon.DeviceService
import io.flutter.run.daemon.FlutterApp
import io.flutter.sdk.FlutterSdk
import io.flutter.sdk.FlutterSdkUtil
import org.apache.tools.ant.taskdefs.optional.ssh.Directory
import org.jdesktop.swingx.VerticalLayout
import java.awt.*
import java.io.File
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

  private var windowPanel: SimpleToolWindowPanel = SimpleToolWindowPanel(true)
  private var toolbar: JBTabsImpl.Toolbar? = null
  private lateinit var project: Project
  private lateinit var pubProjectRoot: PubRoot
  private lateinit var deviceService: DeviceService
  private val selectedChannels = mutableSetOf<String>()


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
    updateForEmptyContent(toolWindow)
  }

  private fun updateForEmptyContent(toolWindow: ToolWindow) {
    // There's a possible race here where the tool window gets disposed while we're displaying contents.
    if (toolWindow.isDisposed) {
      return
    }

    val contentManager = toolWindow.contentManager
    val runnerTabs = JBRunnerTabs(project, ActionManager.getInstance(),
        IdeFocusManager.getInstance(project), this)

    contentManager.addContent(createTriageTab(contentManager, runnerTabs))
    contentManager.addContent(createTemplateTab(contentManager, runnerTabs))
  }

  private fun createTriageTab(contentManager: ContentManager, tabs: JBRunnerTabs): Content {
    val tabContainer = JPanel(BorderLayout())

      //flutterSdk?.flutterDoctor()?.startInConsole(project)
      //println(flutterSdk?.homePath)
    val knownFlutterSdkPaths = FlutterSdkUtil.getKnownFlutterSdkPaths()

    // Add channels Content
    tabContainer.add(buildChannelsContent(knownFlutterSdkPaths))

    val content: Content = contentManager.factory.createContent(null, "Triage", false)
    content.component = tabContainer
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, java.lang.Boolean.TRUE)
    content.icon = AllIcons.General.AddJdk
    return  content
  }

  private fun buildChannelsContent(channels: Array<String>): Component {
    val group = JPanel(VerticalLayout())
    group.maximumSize = Dimension(180, 2400)

    val header = Label("Run Project on Multiple Channels")
    header.font = Font("", 70, 16)
    group.add(gapComponent())
    group.add(gapComponent())
    group.add(header)
    group.add(gapComponent())

    channels.forEach { channelPath ->
      val name = channelPath.substring(channelPath.lastIndexOf('_') + 1)
      val btn = JRadioButton(name.capitalize())
      btn.addChangeListener {
        when(btn.isSelected) {
          true -> selectedChannels.add(channelPath)
          false -> selectedChannels.remove(channelPath)
        }
      }
      group.add(btn)
    }

    val runButton = Button("Run on selected channels")
    runButton.maximumSize = Dimension(150, 100)
    runButton.addActionListener {
      if (selectedChannels.isEmpty()) return@addActionListener

      val connectedDevices = deviceService.connectedDevices
      if (connectedDevices.isEmpty()) return@addActionListener

      // For now, let'' just run on the first device available
      val device = connectedDevices.first()

      // Run project on all selected channels
      selectedChannels.forEach { channelHomePath ->
        runProject(channelHomePath, device)
      }
    }
    group.add(gapComponent())
    group.add(runButton)
    return group
  }

  private fun getFlutterSdk(path: String? = null): FlutterSdk? {
    if (path != null)
      return FlutterSdk.forPath(path)
    return null
  }

  private fun runProject(flutterChannelHomePath: String, device: FlutterDevice) {
    FlutterConsoles.displayMessage(project, null,
      "Triagemagic running with $flutterChannelHomePath...")

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

  private fun createTemplateTab(contentManager: ContentManager, tabs: JBRunnerTabs): Content {
    val tabContainer = JPanel(BorderLayout())
    tabContainer.add(Label("Add triage templates."), BorderLayout.CENTER)

    val content: Content = contentManager.factory.createContent(null, "Template", false)
    content.component = tabContainer
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, java.lang.Boolean.TRUE)
    content.icon = AllIcons.Actions.ListFiles
    return  content
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