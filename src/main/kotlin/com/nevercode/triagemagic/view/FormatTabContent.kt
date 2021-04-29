package com.nevercode.triagemagic.view

import org.jdesktop.swingx.VerticalLayout
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.lang.StringBuilder
import javax.swing.BorderFactory
import javax.swing.JPanel

class FormatTabContent : JPanel(VerticalLayout()) {

    private val infoLabel = Label()

    init { buildUI() }

    private fun buildUI() {
        border = BorderFactory.createEmptyBorder(64, 24, 0, 24)

        val header = Label("Format content")
        header.font = Font("", 70, 16)
        add(header)

        val infoPanel = JPanel(VerticalLayout())
        infoPanel.border = BorderFactory.createEmptyBorder(16, 24, 16, 24)
        infoPanel.add(Label("1. Copy the content;"))
        infoPanel.add(Label("2. Click a button bellow;"))
        infoPanel.add(Label("2. Formatted data will be in your clipboard."))
        add(infoPanel)

        FormatKind.values().forEach { formatKind ->
            val btn = Button(formatKind.title)
            btn.addActionListener { onFormat(formatKind) }
            add(btn)
        }

        add(Label()) // Just a empty space.
        add(Label()) // Just a empty space.
        add(infoLabel)
    }

    private fun onFormat(kind: FormatKind) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard

        val builder = StringBuilder()
        builder.append("<details>\n")
        builder.append("<summary>${kind.title}</summary>\n")
        builder.append("\n```${kind.type}\n")
        builder.append(clipboard.getContents(null).getTransferData(DataFlavor.stringFlavor))
        builder.append("\n```\n")
        builder.append("</details>\n\n")

        val selection = StringSelection(builder.toString())
        clipboard.setContents(selection, selection)

        infoLabel.text = "${kind.title} copied!"

        EventQueue.invokeLater {
            infoLabel.revalidate()
            infoLabel.repaint()
        }
    }
}

enum class FormatKind(val title: String, val type: String) {
    CODE_SAMPLE("code sample", "dart"),
    FLUTTER_RUN_V("flutter run -v", "console"),
    LOGS("logs", "console"),
    DOCTOR_V("flutter doctor -v", "console"),
    PUBSPEC("pubspec.yaml", "yaml"),
}
