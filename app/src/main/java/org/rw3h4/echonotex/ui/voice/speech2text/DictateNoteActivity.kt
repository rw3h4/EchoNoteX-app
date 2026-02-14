package org.rw3h4.echonotex.ui.voice.speech2text

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.rw3h4.echonotex.ui.theme.DarkBlue
import org.rw3h4.echonotex.ui.theme.LightBlue
import org.rw3h4.echonotex.ui.theme.LightPurple
import org.rw3h4.echonotex.ui.theme.OffWhite
import org.rw3h4.echonotex.ui.theme.EchoNoteTheme
import org.rw3h4.echonotex.viewmodel.DictationState
import org.rw3h4.echonotex.viewmodel.Speech2TextViewModel

class DictateNoteActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            EchoNoteTheme {
                val viewModel: Speech2TextViewModel = viewModel()
                val state by viewModel.state.collectAsState()

                DictateScreen(
                    state = state,
                    onRecordTapped = viewModel::startListening,
                    onDoneTapped = {
                        viewModel.stopListening()
                        val resultIntent = Intent().apply {
                            putExtra("transcribed_text", state.transcribedText)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    },
                    onBackTapped = { finish() }
                )
            }
        }
    }
}

@Composable
fun DictateScreen(
    state: DictationState,
    onRecordTapped: () -> Unit,
    onDoneTapped: () -> Unit,
    onBackTapped: () -> Unit
) {
    var showListeningScreen by remember { mutableStateOf(false) }

    LaunchedEffect(state.isListening) {
        if (state.isListening) {
            showListeningScreen = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(OffWhite, LightPurple.copy(alpha = 0.3f))
                )
            )
    ) {
        AnimatedContent(
            targetState = showListeningScreen,
            transitionSpec = {
                if (targetState) {
                    slideInHorizontally(initialOffsetX = {  it }) togetherWith slideOutHorizontally(targetOffsetX = { -it })
                } else {
                    slideInHorizontally(initialOffsetX = { -it }) togetherWith slideOutHorizontally(targetOffsetX = { it })
                }
            },
            label = "screen_transition"
        ) { isListeningScreen ->
            if (isListeningScreen) {
                ListeningUI(
                    state = state,
                    onDone = onDoneTapped,
                    onBack = { showListeningScreen = false }
                )
            } else {
                InitialRecordUI(onRecordTapped = onRecordTapped)
            }
        }
    }
}

@Composable
fun InitialRecordUI(onRecordTapped: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedRecordButton(onClick = onRecordTapped)
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Tap to start dictating",
            style = MaterialTheme.typography.headlineSmall,
            color = DarkBlue,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Speak clearly and your words will be converted to text.",
            style = MaterialTheme.typography.bodyLarge,
            color = DarkBlue.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ListeningUI(state: DictationState, onDone: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = DarkBlue)
            }
            Text(
                text = if (state.isListening) "Listening..." else if (state.transcribedText.isNotEmpty()) "Finished" else "Tap to Start",
                style = MaterialTheme.typography.titleLarge,
                color = DarkBlue,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(48.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        ListeningAnimation(isListening = state.isListening)
        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = OffWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Transcription",
                    style = MaterialTheme.typography.titleMedium,
                    color = DarkBlue,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))
                AnimatedContent(
                    targetState = state.transcribedText,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    },
                    label = "text_animation"
                ) { text ->
                    Text(
                        text = text.ifEmpty { "Waiting for you to speak..." },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (text.isEmpty()) DarkBlue.copy(alpha = 0.5f) else DarkBlue,
                        lineHeight = 24.sp
                    )
                }
            }
        }

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkBlue),
            shape = RoundedCornerShape(16.dp),
            enabled = state.transcribedText.isNotEmpty()
        ) {
            Text(
                text = "Done",
                style = MaterialTheme.typography.titleMedium,
                color = OffWhite
            )
        }
    }
}


@Composable
fun AnimatedRecordButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label =  "record_button_transition")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(

            modifier = Modifier
                .size(200.dp)
                .scale(scale)
                .background(LightPurple.copy(alpha = glowAlpha), CircleShape)
        )
        Card(
            modifier = Modifier.size(160.dp).clickable { onClick() },
            shape = CircleShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkBlue)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Record",
                    tint = OffWhite,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}

@Composable
fun ListeningAnimation(isListening: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "listening_transition")
    val animatedValues = (0..4).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, delayMillis = index * 100),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        animatedValues.forEach { animatedValues ->
            val height = if (isListening) (20 + (animatedValues.value * 40)).dp else 20.dp
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(height)
                    .background(LightBlue, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}



