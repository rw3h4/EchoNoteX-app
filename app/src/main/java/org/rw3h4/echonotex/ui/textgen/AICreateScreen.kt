package org.rw3h4.echonotex.ui.textgen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.rw3h4.echonotex.ui.theme.*
import org.rw3h4.echonotex.viewmodel.TextGenViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AICreateScreen(
    onNavigateBack: () -> Unit = {},
    onUseText: (String) -> Unit = {},
    viewModel: TextGenViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var promptText by remember { mutableStateOf("") }
    var selectedTone by remember { mutableStateOf("casual") }
    val tones = listOf("casual", "formal", "creative", "concise")
    val maxChars = 2000

    // TODO: Placeholder. To be removed as app is refined.
    val recentPrompts = remember {
        listOf(
            "Blog post about productivity hacks",
            "Email to client about project delay",
            "Social media caption for product launch",
        )
    }

    // Effect to handle generated text completion
    LaunchedEffect(uiState.generatedText) {
        if (uiState.generatedText.isNotEmpty() && !uiState.isGenerating) {
            // In a real app, we might want to show the result first, 
            // but for now we'll just keep it in the prompt field or a separate area.
            // For now, let's just make it available to the "Use" action.
        }
    }

    Scaffold(
        containerColor = OffWhite,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = OffWhite,
                    titleContentColor = DarkBlue,
                    navigationIconContentColor = DarkBlue,
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(LightPurple),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = DarkBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                title = {
                    Text("AI Write", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                },
                actions = {
                    // local model indicator badge
                    Row(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (uiState.isModelLoaded) LightPurple else TextDim.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isModelLoaded) DarkBlue else TextDim)
                        )
                        Text(
                            if (uiState.isModelLoaded) "local model" else "loading model...",
                            fontSize = 10.sp,
                            color = if (uiState.isModelLoaded) TextSecondary else TextDim,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Header ───────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "COMPOSE",
                    fontSize = 10.sp,
                    color = TextDim,
                    letterSpacing = 1.2.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "Generate text\nwith AI",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 26.sp
                )
                Text(
                    "Describe what you want to write and choose a tone",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }

            // ── Prompt field card ─────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardWhite)
                    .border(0.5.dp, BorderMed, RoundedCornerShape(14.dp))
            ) {
                // Text area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    if (uiState.isGenerating) {
                         CircularProgressIndicator(
                             modifier = Modifier.align(Alignment.Center).size(24.dp),
                             color = DarkBlue,
                             strokeWidth = 2.dp
                         )
                    }

                    OutlinedTextField(
                        value = if (uiState.generatedText.isNotEmpty() && !uiState.isGenerating) uiState.generatedText else promptText,
                        onValueChange = { 
                            if (it.length <= maxChars) {
                                promptText = it 
                                // Reset generated text if user starts typing again? 
                                // Or we could have a separate result area.
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 130.dp),
                        enabled = !uiState.isGenerating,
                        placeholder = {
                            Text(
                                "e.g. \"A short summary of my meeting notes on project X…\"",
                                color = TextDim,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 20.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor   = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor   = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = DarkBlue,
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            lineHeight = 20.sp
                        ),
                        maxLines = 10
                    )
                    // Character counter
                    Text(
                        "${promptText.length} / $maxChars",
                        modifier = Modifier.align(Alignment.BottomEnd),
                        fontSize = 10.sp,
                        color = TextDim,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Tone chip strip
                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .background(OffWhite)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tones.forEach { tone ->
                        val active = tone == selectedTone
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (active) DarkBlue else CardWhite)
                                .border(
                                    0.5.dp,
                                    if (active) Color.Transparent else BorderMed,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedTone = tone }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                tone,
                                fontSize = 10.sp,
                                color = if (active) OffWhite else TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // ── Action buttons ────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { 
                        val finalPrompt = "Tone: $selectedTone. Task: Summarize the following: $promptText"
                        viewModel.generateText(finalPrompt)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.isModelLoaded && !uiState.isGenerating && promptText.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkBlue,
                        contentColor   = OffWhite
                    )
                ) {
                    Text("Summarize", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
                
                // If we have generated text, show "Use" button, otherwise "Refine"
                if (uiState.generatedText.isNotEmpty() && !uiState.isGenerating) {
                    Button(
                        onClick = { onUseText(uiState.generatedText) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LightBlue,
                            contentColor   = DarkBlue
                        )
                    ) {
                        Text("Use Text", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = { 
                            val finalPrompt = "Tone: $selectedTone. Task: Refine and improve the following text: $promptText"
                            viewModel.generateText(finalPrompt)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = uiState.isModelLoaded && !uiState.isGenerating && promptText.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = DarkBlue
                        ),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderMed)
                    ) {
                        Text("Refine", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                }
            }

            // ── Recent prompts ────────────────────────────────────
            // TODO: Placeholder. To be removed as app is refined.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardWhite)
                    .border(0.5.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text(
                    "RECENT",
                    fontSize = 10.sp,
                    color = TextDim,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(8.dp))

                recentPrompts.forEachIndexed { index, prompt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { promptText = prompt }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (index == 0) DarkBlue else LightBlue)
                        )
                        Text(
                            prompt,
                            fontSize = 11.sp,
                            color = if (index == 0) TextSecondary else TextDim,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (index < recentPrompts.lastIndex) {
                        HorizontalDivider(
                            color = BorderSubtle,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
