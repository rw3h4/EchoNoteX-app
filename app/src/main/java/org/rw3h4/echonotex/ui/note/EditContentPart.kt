package org.rw3h4.echonotex.ui.note

import androidx.compose.ui.text.input.TextFieldValue
import java.util.UUID

sealed interface EditContentPart {
    val id: UUID

    data class Text(
        override val id: UUID = UUID.randomUUID(),
        val value: TextFieldValue
    ) : EditContentPart

    data class Image(
        override val id: UUID = UUID.randomUUID(),
        val uri: String,
        val sizeFraction: Float = 1.0f
    ) : EditContentPart
}
