package com.nevercode.triagemagic.view

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.components.JBScrollPane
import io.flutter.pub.PubRoot
import io.flutter.run.FlutterDevice
import io.flutter.run.FlutterLaunchMode
import io.flutter.run.common.RunMode
import io.flutter.run.daemon.DeviceService
import io.flutter.sdk.FlutterSdk
import io.flutter.sdk.FlutterSdkChannel
import io.flutter.sdk.FlutterSdkUtil
import org.jdesktop.swingx.HorizontalLayout
import org.jdesktop.swingx.VerticalLayout
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.ItemEvent
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JRadioButton

class TriageTabContent(
    private val project: Project,
    private val deviceService: DeviceService,
    private val pubProjectRoot: PubRoot,
) : JPanel(BorderLayout()) {

    private var availableDevices = mutableListOf<FlutterDevice>()
    private var knownFlutterSdkPaths = arrayListOf<String>()

    // Run on Multiple Channels
    private var selectedChannels = mutableSetOf<String>()
    private var selectedDevice: FlutterDevice? = null
    private var multipleChannelsOutput: String = ""
    private var selectedLaunchMode: FlutterLaunchMode = FlutterLaunchMode.DEBUG

    // Run on Multiple Devices
    private var selectedChannel = ""
    private var selectedDevices = mutableSetOf<FlutterDevice>()
    private var multipleDevicesOutput: String = ""
    private var multipleDevicesLaunchMode: FlutterLaunchMode = FlutterLaunchMode.DEBUG

    private var doctorVOutput: String = ""
    private var upgradeChannelsLogs = ""

    init {
        add(buildContent())

        // Listen to available devices changes
        deviceService.addListener {
            availableDevices.clear()
            deviceService.connectedDevices?.forEachIndexed { i, device ->
                if (i == 0)
                    selectedDevice = device
                availableDevices.add(device)
            }
            onRefresh()
        }
    }

    private fun buildContent(): Component {
        autoscrolls = true
        val group = JPanel(VerticalLayout())
        group.autoscrolls = true
        group.preferredSize = Dimension(200, 2000)

        group.border = BorderFactory.createEmptyBorder(8, 32, 0, 32)

        val refreshBtn = Button("Refresh")
        refreshBtn.addActionListener { onRefresh() }

        group.add(refreshBtn)
        group.add(gapComponent())
        group.add(gapComponent())

        if (knownFlutterSdkPaths.isEmpty()) {
            group.add(Label("No Flutter SDK available!"))
            return  group
        }

        group.add(createHeader("Run on Multiple Channels"))
        group.add(gapComponent())

        val horizontalLayout = JPanel(HorizontalLayout())
        horizontalLayout.border = BorderFactory.createEmptyBorder(4, 0,8,0)
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(buildChannelsGroup(false))
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(buildDevicesGroup( false))
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(buildLaunchModeGroup(false))
        group.add(horizontalLayout)

        val runDebugBtn = Button("Run")
        runDebugBtn.isEnabled = selectedDevice != null && selectedChannels.isNotEmpty()
        runDebugBtn.addActionListener {
            runMultipleChannels(selectedChannels, selectedDevice!!, selectedLaunchMode)
        }

        group.add(runDebugBtn)
        if (multipleChannelsOutput.isNotEmpty()) {
            group.add(Label(multipleChannelsOutput))
            group.add(gapComponent())
        }

        group.add(gapComponent())
        group.add(buildFlutterRunOnMultipleDevicesContent())
        group.add(buildFlutterDoctorsContent())
        group.add(upgradeChannelsContent())

        val scrollable = JBScrollPane(group)
        scrollable.autoscrolls = true
        scrollable.preferredSize = Dimension(200,
            (group.preferredSize.height - (group.preferredSize.height * 0.1)).toInt()
        )
        return scrollable
    }

    private fun buildDevicesGroup(forMultipleDevices: Boolean): Component {
        val group = JPanel(VerticalLayout())
        group.add(Label("Devices"))
        group.add(gapComponent())
        val g1 = ButtonGroup()

        if (availableDevices.isEmpty()) {
            group.add(Label("No Device!"))
        } else {
            if (forMultipleDevices) {
                availableDevices.forEach { device ->
                    val radio = JRadioButton(device.deviceName())
                    radio.isSelected = selectedDevices.contains(device)
                    radio.addActionListener {
                        when (radio.isSelected) {
                            true -> selectedDevices.add(device)
                            false -> selectedDevices.remove(device)
                        }
                        onRefresh(true)
                    }
                    group.add(radio)
                }
            } else {
                availableDevices.forEach { device ->
                    val radio = JRadioButton(device.deviceName())
                    radio.isSelected = selectedDevice?.deviceId() == device.deviceId()
                    radio.addItemListener {
                        selectedDevice = device
                        g1.setSelected(radio.model, it.stateChange == ItemEvent.SELECTED)
                        onRefresh(true)
                    }
                    group.add(radio)
                    g1.add(radio)
                }
            }
        }
        return group
    }

    private fun buildLaunchModeGroup(forMultipleDevices: Boolean): Component {
        val group = JPanel(VerticalLayout())
        group.add(Label("Launch Mode"))
        group.add(gapComponent())
        val g1 = ButtonGroup()

        if (forMultipleDevices) {
            FlutterLaunchMode.values().forEach { launchMode ->
                val radio = JRadioButton(launchMode.cliCommand)
                radio.isSelected = multipleDevicesLaunchMode.cliCommand == launchMode.cliCommand
                radio.addItemListener {
                    multipleDevicesLaunchMode = launchMode
                    g1.setSelected(radio.model, it.stateChange == ItemEvent.SELECTED)
                    onRefresh(true)
                }
                group.add(radio)
                g1.add(radio)
            }
        } else {
            FlutterLaunchMode.values().forEach { launchMode ->
                val radio = JRadioButton(launchMode.cliCommand)
                radio.isSelected = selectedLaunchMode.cliCommand == launchMode.cliCommand
                radio.addItemListener {
                    selectedLaunchMode = launchMode
                    g1.setSelected(radio.model, it.stateChange == ItemEvent.SELECTED)
                    onRefresh(true)
                }
                group.add(radio)
                g1.add(radio)
            }
        }
        return group
    }

    private fun buildChannelsGroup(forMultipleDevices: Boolean): Component {
        val group = JPanel(VerticalLayout())
        group.add(Label("Channels"))
        group.add(gapComponent())
        val g = ButtonGroup()

        if (knownFlutterSdkPaths.isEmpty())  {
                group.add(Label("No Channel!"))
        } else {
            if (forMultipleDevices) {
                knownFlutterSdkPaths.forEach { channelPath ->
                    val name = getSdkName(channelPath)
                    val btn = JRadioButton(name)
                    btn.isSelected = selectedChannel == channelPath
                    btn.addItemListener {
                        selectedChannel = channelPath
                        g.setSelected(btn.model, it.stateChange == ItemEvent.SELECTED)
                        onRefresh( true)
                    }
                    group.add(btn)
                    g.add(btn)
                }
            } else {
                knownFlutterSdkPaths.forEach { channelPath ->
                    val name = getSdkName(channelPath)
                    val btn = JRadioButton(name)
                    btn.isSelected = selectedChannels.contains(channelPath)
                    btn.addItemListener {
                        when (it.stateChange == ItemEvent.SELECTED) {
                            true -> selectedChannels.add(channelPath)
                            false -> selectedChannels.remove(channelPath)
                        }
                        onRefresh(true)
                    }
                    group.add(btn)
                }
            }
        }
        return group
    }

    private fun buildFlutterDoctorsContent(): Component {
        val panel = JPanel(VerticalLayout())
        panel.border = BorderFactory.createEmptyBorder(8,0, 0, 0)
        panel.add(createHeader("Generate Flutter Doctor"))

        val horizontalLayout = JPanel(GridLayout(0, 2))
        horizontalLayout.border = BorderFactory.createEmptyBorder(8, 0,0,0)
        knownFlutterSdkPaths.forEach {
            val btn = Button("${getSdkName(it)} doctor -v")
            btn.addActionListener { _ -> onGenerateFlutterDoctors(arrayListOf(it)) }
            horizontalLayout.add(btn)
        }
        panel.add(horizontalLayout)

        val runAllButton = Button("For all channels")
        runAllButton.isEnabled = knownFlutterSdkPaths.isNotEmpty()
        runAllButton.addActionListener { onGenerateFlutterDoctors(knownFlutterSdkPaths) }
        panel.add(runAllButton)

        if (doctorVOutput.isNotEmpty()) {
            panel.add(Label(doctorVOutput))
        }

        panel.add(gapComponent())
        return panel
    }

    private fun upgradeChannelsContent(): Component {
        val panel = JPanel(VerticalLayout())
        panel.border = BorderFactory.createEmptyBorder(8,8, 0, 0)
        panel.add(createHeader("Upgrade Flutter"))

        val runAllButton = Button("Upgrade All")
        runAllButton.isEnabled = knownFlutterSdkPaths.isNotEmpty()
        runAllButton.addActionListener { upgradeChannels(knownFlutterSdkPaths.toMutableSet()) }
        panel.add(runAllButton)

        if (upgradeChannelsLogs.isNotEmpty()) {
            panel.add(Label(upgradeChannelsLogs))
        }
        return panel
    }

    private fun buildFlutterRunOnMultipleDevicesContent(): Component {
        val panel = JPanel(VerticalLayout())
        panel.border = BorderFactory.createEmptyBorder(8,0, 0, 0)
        panel.add(createHeader("Run on Multiple Devices"))
        panel.add(gapComponent())

        val horizontalLayout = JPanel(HorizontalLayout())
        horizontalLayout.border = BorderFactory.createEmptyBorder(4, 0,8,0)
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(buildChannelsGroup(true))
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(buildDevicesGroup(true))
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(buildLaunchModeGroup(true))
        panel.add(horizontalLayout)

        val runDebugBtn = Button("Run")
        runDebugBtn.isEnabled = selectedChannel.isNotEmpty() && selectedDevices.isNotEmpty()
        runDebugBtn.addActionListener {
            runMultipleDevices(selectedChannel, selectedDevices, multipleDevicesLaunchMode)
        }

        panel.add(runDebugBtn)
        if (multipleDevicesOutput.isNotEmpty()) {
            panel.add(Label(multipleDevicesOutput))
            panel.add(gapComponent())
        }

        panel.add(gapComponent())
        return panel
    }

    private fun createHeader(text: String): Label {
        val header = Label(text)
        header.font = Font("", 70, 16)
        return header
    }

    // There is no Padding component, so we just use a empty Label to add some space.
    private fun gapComponent() = Label()

    private var isRefreshing = false
    private fun onRefresh(uiOnly: Boolean = false) {
        if (isRefreshing) return

        isRefreshing = true

        if (!uiOnly) {
            knownFlutterSdkPaths.clear()
            knownFlutterSdkPaths.addAll(FlutterSdkUtil.getKnownFlutterSdkPaths())

            availableDevices.clear()
            if (deviceService.connectedDevices != null) {
                availableDevices.addAll(deviceService.connectedDevices)
            }
        }

        EventQueue.invokeLater {
            removeAll()
            add(buildContent())
            revalidate()
            isRefreshing = false
        }
    }

    val doctorContent = StringBuilder()
    private fun onGenerateFlutterDoctors(channelPaths: ArrayList<String>, index: Int = 0) {
        if (channelPaths.isEmpty()) return
        if (index > channelPaths.lastIndex) return

        val isLastExecution = index == channelPaths.lastIndex

        val currentChannelPath = channelPaths.elementAt(index)
        val sdk = getFlutterSdk(currentChannelPath)!!
        sdk.flutterDoctor().startInConsole(project).addProcessListener(object: ProcessListener {
            // This is not getting called first actually.
            override fun startNotified(event: ProcessEvent) {
                doctorVOutput = "Running flutter doctor -v on ${getSdkName(sdkChannel = sdk.queryFlutterChannel(true))}..."
                onRefresh(true)
            }

            override fun processTerminated(event: ProcessEvent) {
                doctorVOutput = "Logs copied on Clipboard!"
                onRefresh(true)

                doctorContent.append("\n```\n")
                doctorContent.append("</details>\n")

                if (isLastExecution) {
                    val s = StringSelection(doctorContent.toString())
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(s, s)
                    doctorContent.clear()
                } else {
                    onGenerateFlutterDoctors(channelPaths, index + 1)
                }
            }

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                // Hack...
                if (doctorContent.isEmpty() || doctorContent.endsWith("</details>\n")) {
                    doctorContent.append("\n")
                    doctorContent.append("<details>\n")
                    doctorContent.append("<summary>flutter doctor -v</summary>\n")
                    doctorContent.append("\n")
                    doctorContent.append("```console\n")
                }
                doctorContent.append(event.text)
            }
        })
    }

    /// Return either the channel name or the last segment of the path.
    private fun getSdkName(sdkPath: String? = null, sdkChannel: FlutterSdkChannel? = null): String {
        assert(sdkPath == null || sdkChannel == null)
        if (sdkChannel != null) return  sdkChannel.id.name.toLowerCase()

        val channelName = try {
            getFlutterSdk(sdkPath!!)?.queryFlutterChannel(true)?.id?.name
        } catch (e: Exception) {
            null
        }
        if (channelName != null) return channelName.toLowerCase()

        return try {
            sdkPath!!.substring(sdkPath.lastIndexOf('/') + 1).toLowerCase()
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getFlutterSdk(path: String? = null): FlutterSdk? {
        if (path != null)
            return FlutterSdk.forPath(path)
        return null
    }

    val multipleChannelsRunContent = StringBuilder()
    private fun runMultipleChannels(
        flutterChannelHomePaths: MutableSet<String>,
        device: FlutterDevice,
        launchMode: FlutterLaunchMode,
        index: Int = 0) {
        if (flutterChannelHomePaths.isEmpty()) return
        if (index > flutterChannelHomePaths.size - 1) return

        val isLastExecution = index == flutterChannelHomePaths.size -1
        val flutterChannelHomePath = flutterChannelHomePaths.elementAt(index)
        val sdk = getFlutterSdk(flutterChannelHomePath)
        sdk?.flutterRun(
            pubProjectRoot,
            pubProjectRoot.libMain!!,
            device,
            // TODO(pedromassango): remove this once https://github.com/flutter/flutter-intellij/issues/5461 is fixed.
            RunMode.PROFILE,
            launchMode,
            project,
            "--verbose"
        )?.startInConsole(project)?.addProcessListener(object: ProcessListener {
            override fun startNotified(event: ProcessEvent) {
                multipleChannelsOutput = "Running flutter run on ${getSdkName(sdkChannel = sdk.queryFlutterChannel(true))}..."
                onRefresh(true)
            }

            override fun processTerminated(event: ProcessEvent) {
                multipleChannelsOutput = "Logs copied to clipboard!"
                onRefresh(true)

                multipleChannelsRunContent.append("\n```\n")
                multipleChannelsRunContent.append("</details>\n")

                if (isLastExecution) {
                    val s = StringSelection(multipleChannelsRunContent.toString())
                    Toolkit.getDefaultToolkit().systemClipboard
                        .setContents(s, s)
                    multipleChannelsRunContent.clear()
                } else {
                    runMultipleChannels(flutterChannelHomePaths, device, launchMode, index + 1)
                }
            }

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (multipleChannelsRunContent.isEmpty() || multipleChannelsRunContent.endsWith("</details>\n")) {
                    multipleChannelsRunContent.append("<details>\n")
                    multipleChannelsRunContent.append("<summary>logs</summary>\n")
                    multipleChannelsRunContent.append("\n")
                    multipleChannelsRunContent.append("```console\n")
                }
                multipleChannelsRunContent.append(event.text)
            }
        })
    }

    val multipleDevicesRunContent = StringBuilder()
    private fun runMultipleDevices(
        channelPath: String,
        devices: MutableSet<FlutterDevice>,
        launchMode: FlutterLaunchMode,
        index: Int = 0) {
        if (devices.isEmpty()) return
        if (index > devices.size - 1) return

        val isLastExecution = index == devices.size -1
        val currentDevice = devices.elementAt(index)
        val sdk = getFlutterSdk(channelPath)
        sdk?.flutterRun(
            pubProjectRoot,
            pubProjectRoot.libMain!!,
            currentDevice,
            // TODO(pedromassango): remove this once https://github.com/flutter/flutter-intellij/issues/5461 is fixed.
            RunMode.PROFILE,
            launchMode,
            project,
            "--verbose"
        )?.startInConsole(project)?.addProcessListener(object: ProcessListener {
            override fun startNotified(event: ProcessEvent) {
                multipleDevicesOutput = "Running ${getSdkName(sdkChannel = sdk.queryFlutterChannel(true))} on ${currentDevice.deviceName()}..."
                onRefresh(true)
            }

            override fun processTerminated(event: ProcessEvent) {
                multipleDevicesOutput = "Logs copied to clipboard!"
                onRefresh(true)

                multipleDevicesRunContent.append("\n```\n")
                multipleDevicesRunContent.append("</details>\n")

                if (isLastExecution) {
                    val s = StringSelection(multipleDevicesRunContent.toString())
                    Toolkit.getDefaultToolkit().systemClipboard
                        .setContents(s, s)
                    multipleDevicesRunContent.clear()
                } else {
                    runMultipleDevices(channelPath, devices, launchMode,index + 1)
                }
            }

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (multipleDevicesRunContent.isEmpty() || multipleDevicesRunContent.endsWith("</details>\n")) {
                    multipleDevicesRunContent.append("<details>\n")
                    multipleDevicesRunContent.append("<summary>logs</summary>\n")
                    multipleDevicesRunContent.append("\n")
                    multipleDevicesRunContent.append("```bash\n")
                }
                multipleDevicesRunContent.append(event.text)
            }
        })
    }

    private fun upgradeChannels(flutterChannelHomePaths: MutableSet<String>, index: Int = 0) {
        if (flutterChannelHomePaths.isEmpty()) return
        if (index > flutterChannelHomePaths.size - 1) return

        val isLastExecution = index == flutterChannelHomePaths.size -1
        val channelPath = flutterChannelHomePaths.elementAt(index)
        val sdk = getFlutterSdk(channelPath)
        sdk?.flutterUpgrade()?.startInConsole(project)?.addProcessListener(object: ProcessListener {
            override fun startNotified(event: ProcessEvent) {
                upgradeChannelsLogs = "Upgrading ${getSdkName(sdkChannel = sdk.queryFlutterChannel(true))}..."
                onRefresh(true)
            }

            override fun processTerminated(event: ProcessEvent) {
                if (isLastExecution) {
                    upgradeChannelsLogs = "Upgrade completed!"
                    onRefresh(true)
                } else {
                    upgradeChannels(flutterChannelHomePaths, index + 1)
                }
            }

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) { }
        })
    }

}