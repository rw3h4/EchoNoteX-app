package org.rw3h4.echonotex.ui.note

import androidx.compose.ui.text.input.TextFieldValue
import org.jsoup.Jsoup
import java.util.regex.Pattern

fun parseHtmlToContentPart(html: String?): List<EditContentPart> {
    if (html.isNullOrBlank()) {
        return listOf(EditContentPart.Text(value = TextFieldValue("")))
    }

    val parts = mutableListOf<EditContentPart>()
    val doc = Jsoup.parse(html)

    doc.body().children().forEach { element ->
        when (element.tagName()) {
            "p" -> {
                val text = element.html().replace("<br>", "\n")
                parts.add(EditContentPart.Text(value = TextFieldValue(text)))
            }
            "img" -> {
                val src = element.attr("src")
                val style = element.attr("style")
                val pattern = Pattern.compile("width: \\s*(\\d*\\.?\\d+)%")
                val matcher = pattern.matcher(style)
                val sizeFraction = if (matcher.find()) {
                    matcher.group(1)?.toFloatOrNull()?.div(100f) ?: 1.0f
                } else {
                    1.0f
                }
                if (src.isNotEmpty()) {
                    parts.add(EditContentPart.Image(uri = src, sizeFraction = sizeFraction))
                }
            }
        }
    }

    if (parts.isEmpty() || parts.last() is EditContentPart.Image) {
        parts.add(EditContentPart.Text(value = TextFieldValue("")))
    }
    return parts
}

fun convertCContentPartsToHtml(parts: List<EditContentPart>): String {
    val body = StringBuilder()
    for (part in parts) {
        when (part) {
            is EditContentPart.Text -> {
                val text = part.value.text

                if (text.isNotBlank()) {
                    val htmlText = text.replace("\n", "<br>")
                    body.append("<p>").append(htmlText).append("</p>")
                }
            }
            is EditContentPart.Image -> {
                val widthPercent = (part.sizeFraction * 100).toInt()
                body.append("<img src=\"${part.uri}\" style=\"width:${widthPercent}%; height:auto;\" />")
            }
        }
    }

    return body.toString()
}
