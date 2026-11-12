package org.rw3h4.echonotex.ui.textgen

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.rw3h4.echonotex.service.textgen.catalog.ModelCatalogEntry
import org.rw3h4.echonotex.service.textgen.download.ModelDownloadState
import org.rw3h4.echonotex.ui.theme.BorderMed
import org.rw3h4.echonotex.ui.theme.CardWhite
import org.rw3h4.echonotex.ui.theme.DarkBlue
import org.rw3h4.echonotex.ui.theme.LightPurple
import org.rw3h4.echonotex.ui.theme.OffWhite
import org.rw3h4.echonotex.ui.theme.TextDim
import org.rw3h4.echonotex.ui.theme.TextPrimary
import org.rw3h4.echonotex.ui.theme.TextSecondary
import org.rw3h4.echonotex.viewmodel.CatalogModelUi
import org.rw3h4.echonotex.viewmodel.ModelCompatibility
import org.rw3h4.echonotex.viewmodel.ModelPickerUiState
import org.rw3h4.echonotex.viewmodel.ModelPickerViewModel

// Badge color pairs (background / text) for the three compatibility states.
private val BadgeGreenBg = Color(0xFFDDEEDC)
private val BadgeGreenText = Color(0xFF1E5E2A)
private val BadgeAmberBg = Color(0xFFFBF0CC)
private val BadgeAmberText = Color(0xFF7A5C00)
private val BadgeRedBg = Color(0xFFF9DCD8)
private val BadgeRedText = Color(0xFF8B2318)
private val BadgeInstalledBg = Color(0xFFDDEEDC)
private val BadgeInstalledText = Color(0xFF1E5E2A)
private val FailedText = Color(0xFF8B2318)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ModelPickerViewModel = viewModel(
        factory = ModelPickerViewModel.factory(LocalContext.current)
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = OffWhite,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OffWhite,
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
                    Text("Choose a model", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                }
            )
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is ModelPickerUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = DarkBlue, strokeWidth = 2.dp)
                }
            }

            is ModelPickerUiState.Error -> {
                CatalogErrorCard(
                    message = state.message,
                    onRetry = viewModel::load,
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is ModelPickerUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "wifi_toggle") {
                        WifiOnlyRow(
                            wifiOnly = state.wifiOnly,
                            onToggle = viewModel::setWifiOnly,
                        )
                    }
                    items(state.models, key = { it.entry.id }) { model ->
                        ModelCard(
                            model = model,
                            onStartDownload = viewModel::startDownload,
                            onCancel = viewModel::cancelDownload,
                            onRetry = viewModel::retryDownload,
                            onDelete = viewModel::deleteInstalled,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiOnlyRow(
    wifiOnly: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .border(0.5.dp, BorderMed, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Download over Wi-Fi only",
            fontSize = 13.sp,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = wifiOnly,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OffWhite,
                checkedTrackColor = DarkBlue,
            )
        )
    }
}

@Composable
private fun CatalogErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardWhite)
                .border(0.5.dp, BorderMed, RoundedCornerShape(14.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Couldn't load models",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                message,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkBlue,
                    contentColor = OffWhite
                )
            ) {
                Text("Retry", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: CatalogModelUi,
    onStartDownload: (ModelCatalogEntry) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (ModelCatalogEntry) -> Unit,
    onDelete: (String) -> Unit,
) {
    val context = LocalContext.current
    val entry = model.entry
    val sizeText = Formatter.formatShortFileSize(context, entry.sizeBytes)
    val state = model.downloadState

    val compatibilityText = when (model.compatibility) {
        ModelCompatibility.WORKS -> "works on your phone"
        ModelCompatibility.TIGHT -> "may be slow on your phone"
        ModelCompatibility.TOO_BIG -> "not recommended for your phone"
    }
    val tooBig = model.compatibility == ModelCompatibility.TOO_BIG

    // Tapping the card only starts a download when nothing is in flight.
    val tappable = state is ModelDownloadState.NotStarted || state is ModelDownloadState.Cancelled

    var showTooBigDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val onCardTap = {
        if (tappable) {
            if (tooBig) showTooBigDialog = true else onStartDownload(entry)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .alpha(if (tooBig && tappable) 0.6f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .border(0.5.dp, BorderMed, RoundedCornerShape(14.dp))
            .let { if (tappable) it.clickable(onClick = onCardTap) else it }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            entry.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        Text(
            entry.description,
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                sizeText,
                fontSize = 11.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.size(10.dp))
            Text(
                entry.quant,
                fontSize = 10.sp,
                color = TextDim,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.weight(1f))
            // The trailing slot shows either the compatibility badge or the
            // download-state affordance, depending on where the model is.
            StatusSlot(
                state = state,
                compatibility = model.compatibility,
                onCancel = { onCancel(entry.id) },
                onRetry = { onRetry(entry) },
                onDelete = { showDeleteDialog = true },
            )
        }

        DownloadProgress(context = context, state = state)
    }

    if (showTooBigDialog) {
        ConfirmDialog(
            title = "This model may not run on your phone",
            body = "Your device may not have enough memory for ${entry.name}. Download anyway?",
            confirmLabel = "Download anyway",
            onConfirm = {
                showTooBigDialog = false
                onStartDownload(entry)
            },
            onDismiss = { showTooBigDialog = false },
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Delete ${entry.name}?",
            body = "This removes the downloaded file to free up space. You can download it again later.",
            confirmLabel = "Delete",
            onConfirm = {
                showDeleteDialog = false
                onDelete(entry.id)
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun StatusSlot(
    state: ModelDownloadState,
    compatibility: ModelCompatibility,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    when (state) {
        is ModelDownloadState.NotStarted,
        is ModelDownloadState.Cancelled -> CompatibilityBadge(compatibility)

        is ModelDownloadState.Queued -> StatusText("Queued…")

        is ModelDownloadState.Verifying -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                color = DarkBlue,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.size(6.dp))
            StatusText("Verifying…")
        }

        is ModelDownloadState.Downloading -> IconButton(
            onClick = onCancel,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Cancel download",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }

        is ModelDownloadState.Installed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("Installed", BadgeInstalledBg, BadgeInstalledText)
            Spacer(Modifier.size(6.dp))
            TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Delete", fontSize = 11.sp, color = FailedText, fontWeight = FontWeight.Medium)
            }
        }

        is ModelDownloadState.Failed -> if (state.canRetry) {
            TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Retry", fontSize = 11.sp, color = DarkBlue, fontWeight = FontWeight.Medium)
            }
        } else {
            StatusText("Failed")
        }
    }
}

@Composable
private fun DownloadProgress(
    context: android.content.Context,
    state: ModelDownloadState,
) {
    when (state) {
        is ModelDownloadState.Downloading -> {
            Spacer(Modifier.height(2.dp))
            val fraction = if (state.totalBytes > 0) {
                (state.bytesDownloaded.toFloat() / state.totalBytes).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = DarkBlue,
                trackColor = LightPurple,
            )
            val downloaded = Formatter.formatShortFileSize(context, state.bytesDownloaded)
            val total = Formatter.formatShortFileSize(context, state.totalBytes)
            Text(
                "${state.progressPercent}% · $downloaded/$total",
                fontSize = 10.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )
        }

        is ModelDownloadState.Failed -> {
            Text(
                failureMessage(state.reason),
                fontSize = 10.sp,
                color = FailedText,
            )
        }

        else -> Unit
    }
}

private fun failureMessage(reason: String): String = when (reason) {
    "sha256_mismatch" -> "Download was corrupted. Please try a different model."
    "unknown_model" -> "This model is no longer available."
    else -> "Download failed. Check your connection and retry."
}

@Composable
private fun StatusText(text: String) {
    Text(text, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
}

@Composable
private fun Pill(label: String, bg: Color, text: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, fontSize = 10.sp, color = text, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary) },
        text = { Text(body, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = DarkBlue, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = CardWhite,
    )
}

@Composable
private fun CompatibilityBadge(compatibility: ModelCompatibility) {
    val (bg, text, label) = when (compatibility) {
        ModelCompatibility.WORKS -> Triple(BadgeGreenBg, BadgeGreenText, "Works on your phone")
        ModelCompatibility.TIGHT -> Triple(BadgeAmberBg, BadgeAmberText, "May be slow")
        ModelCompatibility.TOO_BIG -> Triple(BadgeRedBg, BadgeRedText, "Not recommended")
    }
    Pill(label, bg, text)
}
