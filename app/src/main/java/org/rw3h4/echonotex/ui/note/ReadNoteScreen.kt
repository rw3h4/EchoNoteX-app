package org.rw3h4.echonotex.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.rw3h4.echonotex.data.local.model.Note
import org.rw3h4.echonotex.ui.theme.DarkBlue
import org.rw3h4.echonotex.ui.theme.LightBlue
import org.rw3h4.echonotex.ui.theme.LightPurple
import org.rw3h4.echonotex.ui.theme.OffWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class NoteContentPart {
    data class Text(val text: String) : NoteContentPart()
    data class Image(val uri: String) : NoteContentPart()
}

fun parseNoteContent(html: String?): List<NoteContentPart> {
    if (html.isNullOrEmpty()) return emptyList()
    val parts = mutableListOf<NoteContentPart>()
    val regex = "<img src=\"(.*?)\".*?>".toRegex()
    var lastIndex = 0

    regex.findAll(html).forEach { matchResult ->
        val textSegment = html.substring(lastIndex, matchResult.range.first)
        if (textSegment.isNotBlank()) {
            parts.add(NoteContentPart.Text(android.text.Html.fromHtml(textSegment, android.text.Html.FROM_HTML_MODE_LEGACY).toString()))
        }

        parts.add(NoteContentPart.Image(matchResult.groupValues[1]))
        lastIndex = matchResult.range.last + 1
    }

    if (lastIndex < html.length) {
        val remainingText = html.substring(lastIndex)
        if (remainingText.isNotBlank()) {
            parts.add(NoteContentPart.Text(android.text.Html.fromHtml(remainingText, android.text.Html.FROM_HTML_MODE_LEGACY).toString()))
        }
    }
    return parts
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadNoteScreen(
    note: Note,
    categoryName: String,
    onNavigateUp: () -> Unit,
    onEditClick: () -> Unit
) {
    val contentParts = parseNoteContent(note.content)
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Scaffold(
        containerColor = OffWhite,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = note.title,
                        maxLines = 1,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onEditClick) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Note",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OffWhite,
                    titleContentColor = DarkBlue,
                    navigationIconContentColor = DarkBlue,
                    actionIconContentColor = DarkBlue
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LightPurple),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = DarkBlue,
                            lineHeight = 32.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = categoryName,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = LightBlue,
                                    labelColor = DarkBlue
                                )
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = "Date",
                                    tint = DarkBlue.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = dateFormatter.format(Date(note.timestamp)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DarkBlue.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }


            items(contentParts) { part ->
                when (part) {
                    is NoteContentPart.Text -> {
                        if (part.text.isNotBlank()) {
                            Text(
                                text = part.text.trim(),
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 28.sp,
                                color = DarkBlue.copy(alpha = 0.9f),
                                modifier = Modifier.padding(horizontal = 4.dp) // Add slight horizontal padding
                            )
                        }
                    }
                    is NoteContentPart.Image -> {
                        AsyncImage(
                            model = part.uri,
                            contentDescription = "Note Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }


            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}