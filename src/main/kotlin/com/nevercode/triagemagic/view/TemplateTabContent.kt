package com.nevercode.triagemagic.view

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.nevercode.triagemagic.notification.TriagemagicNotificationManager
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import org.jdesktop.swingx.VerticalLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*

class TemplateTabContent : JTabbedPane(), Disposable {

    init {
        buildContent()
    }

    private fun buildContent() {
        val json = Json { allowStructuredMapKeys = true }
        val templateString = TemplateTabContent::class.java
            .getResource("/data/template.json")!!.readText()

        val data: TemplateData = json.decodeFromString(TemplateData.serializer(), templateString)

        add("Flutter", createTab(data.flutter))
        add("Tool", createTab(data.tool))
        add("Plugins", createTab(data.plugins))
        add("IDE", createTab(data.ide))
        add("Extras", createTab(data.extras))
    }

    private fun createTab(extras: List<Extra>): JComponent {
        val tab = JPanel(VerticalLayout())
        tab.preferredSize = Dimension(200, 2000)
        preferredSize = Dimension(200, 2000)

        val columns = arrayOf("Use Case", "Response", "Action")
        val data = Array(extras.size) {
            val extra = extras[it]
            return@Array arrayOf(
                toHtmlFormat(extra.useCase),
                toHtmlFormat(extra.response),
                toHtmlFormat(extra.action, getHtmlColor(extra.action))
            )
        }

        val list = object: JTable(data, columns) {
            override fun editCellAt(row: Int, column: Int, e: EventObject?): Boolean = false
        }
        list.rowHeight = 118
        list.columnModel.getColumn(0).maxWidth = 100
        list.columnModel.getColumn(2).maxWidth = 100
        list.cellSelectionEnabled = false

        list.addMouseListener( object: MouseAdapter() {
            override fun mouseClicked(event: MouseEvent?) {
                val row = list.rowAtPoint(event!!.point)
                val col = list.columnAtPoint(event.point)

                // We allow copy with double click only.
                if (event.clickCount != 2) return

                // We only care about clicks in the "Response" column.
                if (col == 1) {
                    val item = extras[row]
                    val useCase = item.useCase
                    val clickedResponse = item.response

                    // 1. Paste the response into the Clipboard.
                    val selection = StringSelection(clickedResponse)
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)

                    // 2. Show the success notification.
                    TriagemagicNotificationManager.showNotification(
                        title = "Template Copied!",
                        message = useCase
                    )
                }
            }
        })

        val s = JBScrollPane(list)
        s.preferredSize = Dimension(200,
            (tab.preferredSize.height - (tab.preferredSize.height * 0.6).toInt()))
        tab.add(s)
        return tab
    }

    private fun getHtmlColor(str: String): String {
        return when (str) {
            "waiting" -> "green"
            "close" -> "red"
            else -> "#1A02A2"
        }
    }

    private fun toHtmlFormat(text: String, htmlColor: String = "#B3B6B7"): String {
        return "<html><font color='$htmlColor'>$text</font></html>"
    }

    override fun dispose() = Disposer.dispose(this)
}

@Serializable
private data class TemplateData (
    val flutter: List<Extra>,
    val tool: List<Extra>,
    val plugins: List<Extra>,

    @SerialName("IDE")
    val ide: List<Extra>,

    @SerialName("Extras")
    val extras: List<Extra>
)

@Serializable
private data class Extra (
    @SerialName("use_case")
    val useCase: String,
    val action: String,
    val response: String
)