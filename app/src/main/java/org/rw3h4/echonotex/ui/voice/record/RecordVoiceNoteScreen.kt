package org.rw3h4.echonotex.ui.voice.record

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.rw3h4.echonotex.ui.note.AddEditNoteScreen
import org.rw3h4.echonotex.ui.note.CategorySelectionBottomSheet
import org.rw3h4.echonotex.ui.theme.DarkBlue
import org.rw3h4.echonotex.ui.theme.LightBlue
import org.rw3h4.echonotex.ui.theme.LightPurple
import org.rw3h4.echonotex.ui.theme.OffWhite
import org.rw3h4.echonotex.ui.theme.EchoNoteTheme
import org.rw3h4.echonotex.viewmodel.RecordUiState
import org.rw3h4.echonotex.viewmodel.RecordVoiceNoteViewModel
import org.rw3h4.echonotex.viewmodel.RecordingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordVoiceNoteScreen(
    onClose: () -> Unit,
    onSaveFinished: () -> Unit
) {
    val viewModel: RecordVoiceNoteViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val categories by viewModel.allCategories.observeAsState(initial = emptyList())

    val bottomSheetState = rememberModalBottomSheetState()
    var showCategoryBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saveFinished) {
        if (uiState.saveFinished) {
            onSaveFinished()
            viewModel.onSaveComplete()
        }
    }

    EchoNoteTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TopBar(onClose = onClose)
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = uiState.formattedTime,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light
                )

                Spacer(modifier = Modifier.height(32.dp))

                VoiceWaveform(
                    amplitudes = uiState.amplitudes,
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    barColor = MaterialTheme.colorScheme.secondary
                )

                if (uiState.recordingState == RecordingState.STOPPED) {
                    AfterRecordingControls(
                        uiState = uiState,
                        onTitleChange = viewModel::updateTitle,
                        onCategoryClick = { showCategoryBottomSheet = true }
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                RecordingControls(
                    state = uiState.recordingState,
                    onRecord = { viewModel.startRecording() },
                    onStop = { viewModel.stopRecording() },
                    onSave = { viewModel.saveVoiceNote() },
                    onDiscard = { viewModel.discardRecording() }
                )
            }
        }
    }

}

@Composable
fun TopBar(onClose: ()  -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = OffWhite)
        }
    }
}

@Composable
fun VoiceWaveform(amplitudes: List<Float>, modifier: Modifier = Modifier, barColor: Color) {
    val barWidth = 9f
    val barGap = 6f

    Canvas(modifier = modifier) {
        val viewWidth = size.width
        val viewHeight = size.height
        val maxBars = (viewWidth /  (barWidth + barGap)).toInt()
        val cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)

        val visibleAmplitudes = amplitudes.takeLast(maxBars)

        for ((i, amplitude) in visibleAmplitudes.withIndex()) {
            val barHeight = (amplitude * viewHeight).coerceAtLeast(barWidth)
            val top = (viewHeight - barHeight) / 2
            val left = viewWidth - (i * (barWidth + barGap)) - barWidth

            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius
            )
        }
    }
}

@Composable
fun ColumnScope.AfterRecordingControls(
    uiState: RecordUiState,
    onTitleChange: (String) -> Unit,
    onCategoryClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = uiState.noteTitle,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            placeholder = { Text("Voice Note Title...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = LightPurple,
                focusedBorderColor = OffWhite,
                unfocusedLabelColor = LightPurple,
                focusedLabelColor = OffWhite,
                cursorColor = OffWhite,
                unfocusedTextColor = OffWhite,
                focusedTextColor = OffWhite,
                focusedContainerColor = Color.Transparent
            )
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent, RoundedCornerShape(12.dp))
                .border(1.dp, LightPurple, RoundedCornerShape(12.dp))
                .clickable { onCategoryClick() }
                .padding(16.dp)
        ) {
            Text(
                text = uiState.selectedCategoryName.ifEmpty { "Select Category" },
                color = if (uiState.selectedCategoryName.isEmpty()) LightPurple else OffWhite
            )
        }
    }
}

@Composable
fun RecordingControls(
    state: RecordingState,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(visible = state  == RecordingState.STOPPED) {
            FloatingActionButton(
                onClick = onDiscard,
                containerColor = LightPurple,
                contentColor = DarkBlue
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Discard")
            }
        }

        RecordStopButton(isRecording = state == RecordingState.RECORDING) {
            if (state == RecordingState.RECORDING) onStop() else onRecord()
        }

        AnimatedVisibility(visible = state == RecordingState.STOPPED) {
            FloatingActionButton(
                onClick = onSave,
                containerColor = LightBlue,
                contentColor = DarkBlue,
            ) {
                Icon(Icons.Default.Check, contentDescription = "Save")
            }
        }
    }
}

@Composable
fun RecordStopButton(isRecording: Boolean, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        shape = CircleShape,
        containerColor = OffWhite,
        contentColor = DarkBlue
    ) {
        AnimatedContent(
            targetState = isRecording,
            transitionSpec = {
                scaleIn() togetherWith scaleOut()
            },
            label = ""
        ) { recording ->
            if (recording) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Stop",
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Record",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}