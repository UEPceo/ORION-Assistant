package com.orion.voicelab.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orion.voicelab.R
import com.orion.voicelab.data.DeviceMetrics
import com.orion.voicelab.data.ListeningState
import com.orion.voicelab.data.ModelInstallState
import com.orion.voicelab.data.TranscriptEntry
import com.orion.voicelab.data.VoiceLabUiState
import com.orion.voicelab.ui.theme.OrionNavyGlow
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun OrionVoiceLabScreen(
    viewModel: VoiceLabViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val reportCopiedMsg = stringResource(R.string.report_copied)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startListening()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    val onStartClicked: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val onCopyReportClicked: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ORION informe", viewModel.buildReport()))
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(reportCopiedMsg)
        }
    }

    val bgColor = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OrionNavyGlow, Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.14f),
                    radius = size.maxDimension * 0.55f
                )
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val isWide = maxWidth >= 720.dp
                if (isWide) {
                    WideVoiceLabLayout(
                        state = state,
                        audioLevelFlow = viewModel.audioLevel,
                        onDownloadClick = viewModel::downloadModel,
                        onStartClick = onStartClicked,
                        onStopClick = viewModel::stopListening,
                        onMeasureClick = viewModel::refreshMetrics,
                        onCopyReportClick = onCopyReportClicked,
                        onClearClick = viewModel::resetMeasurements
                    )
                } else {
                    CompactVoiceLabLayout(
                        state = state,
                        audioLevelFlow = viewModel.audioLevel,
                        onDownloadClick = viewModel::downloadModel,
                        onStartClick = onStartClicked,
                        onStopClick = viewModel::stopListening,
                        onMeasureClick = viewModel::refreshMetrics,
                        onCopyReportClick = onCopyReportClicked,
                        onClearClick = viewModel::resetMeasurements
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactVoiceLabLayout(
    state: VoiceLabUiState,
    audioLevelFlow: StateFlow<Float>,
    onDownloadClick: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onMeasureClick: () -> Unit,
    onCopyReportClick: () -> Unit,
    onClearClick: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.transcripts.size) {
        if (state.transcripts.isNotEmpty()) {
            listState.animateScrollToItem(4 + state.transcripts.size - 1)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                OrionHeader(state = state, audioLevelFlow = audioLevelFlow)
            }
            item(key = "controls") {
                ControlsCard(
                    state = state,
                    onDownloadClick = onDownloadClick,
                    onStartClick = onStartClick,
                    onStopClick = onStopClick
                )
            }
            item(key = "tools") {
                ToolsActionRow(
                    onMeasureClick = onMeasureClick,
                    onCopyReportClick = onCopyReportClick,
                    onClearClick = onClearClick
                )
            }
            item(key = "telemetry") {
                TelemetryPanel(state = state)
            }
            item(key = "transcripts_header") {
                SectionLabel(text = stringResource(R.string.section_transcripts))
            }
            if (state.transcripts.isEmpty()) {
                item(key = "transcripts_empty") {
                    EmptyTranscriptCard()
                }
            } else {
                items(
                    items = state.transcripts,
                    key = { it.id }
                ) { entry ->
                    TranscriptCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun WideVoiceLabLayout(
    state: VoiceLabUiState,
    audioLevelFlow: StateFlow<Float>,
    onDownloadClick: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onMeasureClick: () -> Unit,
    onCopyReportClick: () -> Unit,
    onClearClick: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.transcripts.size) {
        if (state.transcripts.isNotEmpty()) {
            listState.animateScrollToItem(state.transcripts.size - 1)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OrionHeader(state = state, audioLevelFlow = audioLevelFlow)
            ControlsCard(
                state = state,
                onDownloadClick = onDownloadClick,
                onStartClick = onStartClick,
                onStopClick = onStopClick
            )
            ToolsActionRow(
                onMeasureClick = onMeasureClick,
                onCopyReportClick = onCopyReportClick,
                onClearClick = onClearClick
            )
            TelemetryPanel(state = state)
        }

        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionLabel(text = stringResource(R.string.section_transcripts))
            if (state.transcripts.isEmpty()) {
                EmptyTranscriptCard(modifier = Modifier.fillMaxWidth())
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        items = state.transcripts,
                        key = { it.id }
                    ) { entry ->
                        TranscriptCard(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrionHeader(
    state: VoiceLabUiState,
    audioLevelFlow: StateFlow<Float>
) {
    val audioLevel by audioLevelFlow.collectAsStateWithLifecycle()
    val indicatorColor by animateColorAsState(
        targetValue = when (state.listeningState) {
            ListeningState.VOICE_DETECTED -> MaterialTheme.colorScheme.secondary
            ListeningState.LISTENING -> MaterialTheme.colorScheme.primary
            ListeningState.LOADING_MODELS, ListeningState.STOPPING -> MaterialTheme.colorScheme.tertiary
            ListeningState.IDLE -> MaterialTheme.colorScheme.outline
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "voiceIndicatorColor"
    )
    val animatedLevel by animateFloatAsState(
        targetValue = if (state.isRunning) audioLevel else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "speechLevel"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            Text(
                text = stringResource(R.string.brand_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.brand_subtitle),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        AnimatedVisibility(visible = state.isRunning) {
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .height(14.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val multipliers = listOf(0.45f, 0.75f, 1.0f, 0.75f, 0.45f)
                multipliers.forEach { weight ->
                    val barHeight = (4f + 10f * animatedLevel * weight).dp
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(indicatorColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlsCard(
    state: VoiceLabUiState,
    onDownloadClick: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = state.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_message")
            )

            AnimatedVisibility(visible = state.isDownloading) {
                val modelState = state.modelState
                if (modelState is ModelInstallState.Downloading && modelState.progressPercent > 0) {
                    LinearProgressIndicator(
                        progress = { modelState.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .testTag("download_progress"),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .testTag("download_progress"),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            FilledTonalButton(
                onClick = onDownloadClick,
                enabled = !state.isModelReady && !state.isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("download_model_button"),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = stringResource(R.string.cd_download_icon),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isModelReady) {
                        stringResource(R.string.btn_model_installed)
                    } else {
                        stringResource(R.string.btn_download_model)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStartClick,
                    enabled = state.isModelReady && !state.isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("start_recognition_button"),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.cd_mic_icon),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_start_recognition),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onStopClick,
                    enabled = state.isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("stop_listening_button"),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.cd_stop_icon),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_stop_listening),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolsActionRow(
    onMeasureClick: () -> Unit,
    onCopyReportClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onMeasureClick,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .minimumInteractiveComponentSize()
                .testTag("measure_now_button"),
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Icon(
                imageVector = Icons.Outlined.Speed,
                contentDescription = stringResource(R.string.cd_measure_icon),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.btn_measure_now),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedButton(
            onClick = onCopyReportClick,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .minimumInteractiveComponentSize()
                .testTag("copy_report_button"),
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.cd_copy_icon),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.btn_copy_report),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedButton(
            onClick = onClearClick,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .minimumInteractiveComponentSize()
                .testTag("clear_data_button"),
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteSweep,
                contentDescription = stringResource(R.string.cd_clear_icon),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.btn_clear_data),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TelemetryPanel(state: VoiceLabUiState) {
    val snap = state.metricsSnapshot
    val metricsText = remember(snap, state.modelSizeMb, state.modelLoadMs, state.asrStats) {
        buildString {
            if (snap != null) {
                append(DeviceMetrics.describe(snap))
            } else {
                append("Leyendo métricas del sistema…")
            }
            append("\nModelo en disco: ${state.modelSizeMb} MB")
            if (state.modelLoadMs >= 0) {
                append(" · carga modelo+VAD: ${state.modelLoadMs} ms")
            }
            append("\n")
            append(state.asrStats.summaryLine())
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("telemetry_panel"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.section_telemetry),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = metricsText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun EmptyTranscriptCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("empty_transcript_card"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = stringResource(R.string.empty_transcript),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TranscriptCard(entry: TranscriptEntry) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transcript_item_${entry.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "• ${entry.text}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = String.format(
                    Locale.getDefault(),
                    "audio %.1f s · decodificación %d ms",
                    entry.audioMs / 1000.0,
                    entry.decodeMs
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
