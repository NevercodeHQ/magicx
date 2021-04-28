package com.nevercode.triagemagic.view

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import io.flutter.pub.PubRoot
import io.flutter.run.FlutterDevice
import io.flutter.run.FlutterLaunchMode
import io.flutter.run.common.RunMode
import io.flutter.run.daemon.DeviceService
import io.flutter.sdk.FlutterSdk
import io.flutter.sdk.FlutterSdkUtil
import org.jdesktop.swingx.HorizontalLayout
import org.jdesktop.swingx.VerticalLayout
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JRadioButton

class TriageTabContent(
    private val project: Project,
    private val deviceService: DeviceService,
    private val pubProjectRoot: PubRoot,
) : JPanel(BorderLayout()) {

    private var selectedChannels = mutableSetOf<String>()
    private var availableDevices = mutableListOf<FlutterDevice>()
    private var knownFlutterSdkPaths = arrayListOf<String>()
    private var selectedDevice: FlutterDevice? = null
    private var cmdInfoOutput: StringBuilder = StringBuilder()

    init {
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

        add(buildContent())
    }

    private fun buildContent(): Component {
        val group = JPanel(VerticalLayout())
        group.maximumSize = Dimension(180, 2400)

        group.border = BorderFactory.createEmptyBorder(8, 32, 0, 32)

        val refreshBtn = Button("Refresh")
        refreshBtn.addActionListener { onRefresh() }

        group.add(refreshBtn)
        group.add(gapComponent())
        group.add(gapComponent())
        group.add(gapComponent())
        group.add(createHeader("Select Channel(s) and one device"))
        group.add(gapComponent())

        val horizontalLayout = JPanel(HorizontalLayout())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(buildChannelsGroup())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(gapComponent())
        horizontalLayout.add(buildDevicesGroup())
        group.add(horizontalLayout)
        group.add(gapComponent())
        group.add(buildFlutterDoctorsContent())
        group.add(buildFlutterRunContent())
        return group
    }

    private fun buildDevicesGroup(): Component {
        val group = JPanel(VerticalLayout())
        group.add(Label("Devices"))
        group.add(gapComponent())
        val g = ButtonGroup()

        if (availableDevices.isEmpty()) {
            group.add(Label("No Device!"))
        } else {
            availableDevices.forEach { device ->
                val radio = JRadioButton(device.deviceName())
                radio.isSelected = selectedDevice?.deviceId() == device.deviceId()
                radio.addActionListener {
                    selectedDevice = device
                    g.setSelected(radio.model, true)
                    onRefresh(true)
                }
                group.add(radio)
                g.add(radio)
            }
        }
        return group
    }

    private fun buildChannelsGroup(): Component {
        val group = JPanel(VerticalLayout())
        group.add(Label("Channels"))
        group.add(gapComponent())

        if (knownFlutterSdkPaths.isEmpty()) {
            group.add(Label("No Channel!"))
        } else {
            knownFlutterSdkPaths.forEach { channelPath ->
                val name = channelPath.substring(channelPath.lastIndexOf('_') + 1)
                val btn = JRadioButton(name.capitalize())
                btn.isSelected = selectedChannels.contains(channelPath)
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

    private fun buildFlutterDoctorsContent(): Component {
        val panel = JPanel(VerticalLayout())
        panel.border = BorderFactory.createEmptyBorder(16,0, 0, 0)
        panel.add(createHeader("Generate Flutter Doctor"))
        panel.add(gapComponent())

        if (knownFlutterSdkPaths.isNotEmpty()) {
            val getOnSelectedChannelBtn = Button("For selected channels")
            getOnSelectedChannelBtn.addActionListener { onGenerateFlutterDoctors(selectedChannels.toArrayList()) }
            val runAllButton = Button("For all channels")
            runAllButton.addActionListener { onGenerateFlutterDoctors(knownFlutterSdkPaths) }
            panel.add(getOnSelectedChannelBtn)
            panel.add(runAllButton)
            panel.add(gapComponent())
        } else {
            panel.add(Label("Flutter SDK not available"))
        }
        return panel
    }

    private fun buildFlutterRunContent(): Component {
        val panel = JPanel(VerticalLayout())
        panel.border = BorderFactory.createEmptyBorder(12,0, 0, 0)
        panel.add(createHeader("Flutter Run"))
        panel.add(gapComponent())

        if (knownFlutterSdkPaths.isNotEmpty() && selectedDevice != null) {
            val runDebugBtn = Button("Run on selected channels")
            runDebugBtn.addActionListener { runProject(selectedChannels, selectedDevice!!) }
            val runAllButton = Button("Run all channels on ${selectedDevice?.deviceName() ?: "<select-device>" }")
            runAllButton.addActionListener { onGenerateFlutterDoctors(knownFlutterSdkPaths) }
            panel.add(runDebugBtn)
            panel.add(runAllButton)
            panel.add(gapComponent())
            panel.add(gapComponent())
            if (cmdInfoOutput.isNotEmpty()) {
                panel.add(Label("LOG: $cmdInfoOutput"))
            }
        } else {
            panel.add(Label("Flutter SDK not available"))
        }
        return panel
    }

    private fun createHeader(text: String): Label {
        val header = Label(text)
        header.font = Font("", 70, 16)
        return header
    }

    private fun gapComponent(): Component {
        val gap = Label("")
        return gap
    }

    private fun onRefresh(uiOnly: Boolean = false) {
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
            repaint()
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
                cmdInfoOutput.clear()
                cmdInfoOutput.append("running ${getSdkName(currentChannelPath)} doctor -v...")
                onRefresh(true)
            }

            override fun processTerminated(event: ProcessEvent) {
                cmdInfoOutput.clear()
                cmdInfoOutput.append("Logs copied on Clipboard!")
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
                    doctorContent.append("```bash\n")
                }
                doctorContent.append(event.text)
            }
        })
    }

    private fun getSdkName(sdkPath: String): String {
        return try {
            sdkPath.substring(sdkPath.lastIndexOf('_') + 1).capitalize()
        } catch (e: Exception) {
            sdkPath.substring(sdkPath.lastIndexOf('/'))
        }
    }

    private fun getFlutterSdk(path: String? = null): FlutterSdk? {
        if (path != null)
            return FlutterSdk.forPath(path)
        return null
    }

    val runContent = StringBuilder()
    private fun runProject(flutterChannelHomePaths: MutableSet<String>, device: FlutterDevice, index: Int = 0) {
        if (flutterChannelHomePaths.isEmpty()) return
        if (index > flutterChannelHomePaths.size - 1) return

        val isLastExecution = index == flutterChannelHomePaths.size -1
        val flutterChannelHomePath = flutterChannelHomePaths.elementAt(index)
        val sdk = getFlutterSdk(flutterChannelHomePath)
        sdk?.flutterPackagesGet(pubProjectRoot)?.startInConsole(project)
        sdk?.flutterRun(
            pubProjectRoot,
            pubProjectRoot.libMain!!,
            device,
            RunMode.DEBUG,
            FlutterLaunchMode.DEBUG,
            project,
            "--verbose"
        )?.startInConsole(project)?.addProcessListener(object: ProcessListener {
            override fun startNotified(event: ProcessEvent) {
                cmdInfoOutput.clear()
                cmdInfoOutput.append("Running flutter run on ${sdk.version.versionText}...")
                onRefresh(true)
            }

            override fun processTerminated(event: ProcessEvent) {
                cmdInfoOutput.clear()
                cmdInfoOutput.append("Logs copied to clipboard!")
                onRefresh(true)

                runContent.append("\n```\n")
                runContent.append("</details>\n")

                if (isLastExecution) {
                    val s = StringSelection(runContent.toString())
                    Toolkit.getDefaultToolkit().systemClipboard
                        .setContents(s, s)
                    runContent.clear()
                } else {
                    runProject(flutterChannelHomePaths, device, index + 1)
                }
            }

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (runContent.isEmpty() || runContent.endsWith("</details>\n")) {
                    runContent.append("<details>\n")
                    runContent.append("<summary>flutter doctor -v</summary>\n")
                    runContent.append("\n")
                    runContent.append("```bash\n")
                }
                runContent.append(event.text)
            }
        })
    }

}