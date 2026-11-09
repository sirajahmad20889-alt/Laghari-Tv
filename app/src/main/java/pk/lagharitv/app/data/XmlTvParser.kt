package pk.lagharitv.app.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class EpgProgramme(
    val channelId: String,
    val title: String,
    val startMillis: Long,
    val stopMillis: Long
)

object XmlTvParser {
    fun parse(xml: String): List<EpgProgramme> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xml.reader())
        val result = mutableListOf<EpgProgramme>()
        var event = parser.eventType
        var channelId = ""
        var title = ""
        var start = 0L
        var stop = 0L
        var inProgramme = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        inProgramme = true
                        channelId = parser.getAttributeValue(null, "channel") ?: ""
                        start = parseDate(parser.getAttributeValue(null, "start"))
                        stop = parseDate(parser.getAttributeValue(null, "stop"))
                        title = ""
                    }
                    "title" -> if (inProgramme && title.isBlank()) title = parser.nextText().trim()
                }
                XmlPullParser.END_TAG -> if (parser.name == "programme" && inProgramme) {
                    if (channelId.isNotBlank() && title.isNotBlank() && stop > start) {
                        result += EpgProgramme(channelId, title, start, stop)
                    }
                    inProgramme = false
                }
            }
            event = parser.next()
        }
        return result.sortedBy { it.startMillis }
    }

    private fun parseDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val formats = listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmss")
        for (pattern in formats) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.isLenient = false
                if (pattern.endsWith("ss")) format.timeZone = TimeZone.getTimeZone("UTC")
                return format.parse(value.trim())?.time ?: 0L
            } catch (_: Exception) { }
        }
        return 0L
    }
}
