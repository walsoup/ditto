package com.walsoup.ditto.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.walsoup.ditto.core.audio.AudioFileManager
import com.walsoup.ditto.core.audio.AudioPlayerHelper
import com.walsoup.ditto.core.audio.AudioRecorderEngine
import com.walsoup.ditto.core.audio.VoiceFilter
import com.walsoup.ditto.data.HistoryManager
import com.walsoup.linen.Linen
import com.walsoup.linen.ActionButton
import com.walsoup.linen.ActionStyle
import com.walsoup.linen.RoundIconButton
import com.walsoup.ditto.ui.design.VoicePills
import com.walsoup.linen.Waveform
import com.walsoup.linen.formatClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface RecordStage {
    data object Idle : RecordStage
    data object Recording : RecordStage
    data class Review(val rawFile: File, val durationMs: Long, val levels: List<Float>) : RecordStage
}

/**
 * Scrim plus bottom sheet that hosts the recording and "Take ready" steps.
 * The sheet grows out of the dock; Idle renders nothing.
 */
@Composable
fun BoxScope.RecordSheetHost(
    stage: RecordStage,
    onStageChange: (RecordStage) -> Unit,
    recorderEngine: AudioRecorderEngine,
    playerHelper: AudioPlayerHelper,
    historyManager: HistoryManager,
    lastTargetPackage: String?
) {
    val c = Linen.colors
    var keepOnDismiss by remember { mutableStateOf<(() -> Unit)?>(null) }

    BackHandler(enabled = stage !is RecordStage.Idle) {
        if (stage is RecordStage.Review) keepOnDismiss?.invoke()
    }

    AnimatedVisibility(
        visible = stage !is RecordStage.Idle,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(200)),
        modifier = Modifier.matchParentSize()
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { if (stage is RecordStage.Review) keepOnDismiss?.invoke() }
        )
    }

    AnimatedVisibility(
        visible = stage !is RecordStage.Idle,
        enter = slideInVertically(tween(250, easing = LinearOutSlowInEasing)) { it },
        exit = slideOutVertically(tween(200, easing = FastOutLinearInEasing)) { it },
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(c.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 18.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.inactive)
            )
            Spacer(Modifier.height(16.dp))
            AnimatedContent(
                targetState = stage,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "recordStage"
            ) { s ->
                when (s) {
                    is RecordStage.Recording -> RecordingContent(recorderEngine, onStageChange)
                    is RecordStage.Review -> ReviewContent(
                        review = s,
                        playerHelper = playerHelper,
                        historyManager = historyManager,
                        lastTargetPackage = lastTargetPackage,
                        registerKeep = { keepOnDismiss = it },
                        close = { onStageChange(RecordStage.Idle) }
                    )
                    RecordStage.Idle -> Spacer(Modifier.height(1.dp))
                }
            }
        }
    }
}

@Composable
private fun RecordingContent(
    recorderEngine: AudioRecorderEngine,
    onStageChange: (RecordStage) -> Unit
) {
    val c = Linen.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val durationMs by recorderEngine.durationMs.collectAsState()
    val levels by recorderEngine.levels.collectAsState()
    val isPaused by recorderEngine.isPaused.collectAsState()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isPaused) c.muted else c.rec)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = if (isPaused) "PAUSED" else "RECORDING",
                style = Linen.type.overline.copy(fontWeight = Linen.type.label.fontWeight),
                color = if (isPaused) c.muted else c.rec
            )
        }
        Text(formatClock(durationMs), style = Linen.type.monoHero, color = c.text, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        Waveform(
            levels = levels,
            color = if (isPaused) c.inactive else c.rec,
            alignEnd = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        )
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIconButton(
                icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (isPaused) "Resume recording" else "Pause recording",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    recorderEngine.togglePause()
                }
            )
            ActionButton(
                text = "Done",
                icon = Icons.Default.Stop,
                style = ActionStyle.Record,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val duration = recorderEngine.durationMs.value
                    val levelsSnapshot = recorderEngine.recordedLevels()
                    val file = recorderEngine.stopRecording()
                    if (file != null && file.exists()) {
                        onStageChange(RecordStage.Review(file, duration, levelsSnapshot))
                    } else {
                        Toast.makeText(context, "Nothing was recorded", Toast.LENGTH_SHORT).show()
                        onStageChange(RecordStage.Idle)
                    }
                },
                modifier = Modifier.weight(1f)
            )
            RoundIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Cancel recording",
                container = c.containerLow,
                tint = c.textSecondary,
                onClick = {
                    recorderEngine.cancelRecording()
                    onStageChange(RecordStage.Idle)
                }
            )
        }
    }
}

@Composable
private fun ReviewContent(
    review: RecordStage.Review,
    playerHelper: AudioPlayerHelper,
    historyManager: HistoryManager,
    lastTargetPackage: String?,
    registerKeep: ((() -> Unit)?) -> Unit,
    close: () -> Unit
) {
    val c = Linen.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val format = historyManager.selectedFormat
    var filter by remember { mutableStateOf(historyManager.defaultFilter) }
    val prepared = remember { mutableStateMapOf<VoiceFilter, File>() }
    var busy by remember { mutableStateOf(false) }
    val isPlaying by playerHelper.isPlaying.collectAsState()
    val progressMs by playerHelper.progressMs.collectAsState()
    val playDurationMs by playerHelper.durationMs.collectAsState()
    val targetLabel = remember(lastTargetPackage) { AudioFileManager.appLabel(context, lastTargetPackage) }

    suspend fun prepare(f: VoiceFilter): File = prepared[f] ?: withContext(Dispatchers.IO) {
        AudioFileManager.generateFilteredAudio(context, review.rawFile, f, format)
    }.also { prepared[f] = it }

    fun finish(targetPackage: String? = null, commit: (File) -> Unit) {
        if (busy) return
        busy = true
        playerHelper.stop()
        scope.launch {
            val file = prepare(filter)
            commit(file)
            historyManager.addRecording(file, review.durationMs, filter, format, targetPackage)
            registerKeep(null)
            close()
        }
    }

    DisposableEffect(review) {
        registerKeep { finish { } }
        onDispose { registerKeep(null) }
    }

    Column {
        Text("Take ready", style = Linen.type.title.copy(fontSize = Linen.type.title.fontSize.times(1.05f)), color = c.text)
        Text(
            text = "${formatClock(review.durationMs)} · ${format.name}",
            style = Linen.type.monoTiny,
            color = c.textSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp, bottom = 14.dp)
        ) {
            RoundIconButton(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause preview" else "Play preview",
                container = c.primary,
                tint = c.onPrimary,
                onClick = {
                    when {
                        isPlaying -> playerHelper.pause()
                        progressMs > 0 -> playerHelper.resume()
                        else -> scope.launch { playerHelper.playFile(prepare(filter)) }
                    }
                }
            )
            Spacer(Modifier.width(12.dp))
            Waveform(
                levels = review.levels,
                progress = if (playDurationMs > 0) progressMs.toFloat() / playDurationMs else 0f,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
            )
        }
        VoicePills(
            selected = filter,
            onSelect = { f ->
                filter = f
                playerHelper.stop()
                scope.launch { playerHelper.playFile(prepare(f)) }
            }
        )
        Spacer(Modifier.height(18.dp))
        if (targetLabel != null && lastTargetPackage != null) {
            ActionButton(
                text = "Paste into $targetLabel",
                icon = Icons.Default.ContentPaste,
                enabled = !busy,
                onClick = {
                    finish(lastTargetPackage) { file ->
                        AudioFileManager.pasteIntoApp(context, file, lastTargetPackage, historyManager.isAutoPasteEnabled)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            ActionButton(
                text = "Copy",
                icon = Icons.Default.ContentCopy,
                enabled = !busy,
                onClick = {
                    finish { file -> AudioFileManager.copyAudioToClipboard(context, file, autoPaste = false) }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                text = "Share",
                icon = Icons.Default.Share,
                style = ActionStyle.Tonal,
                enabled = !busy,
                onClick = {
                    finish { file -> context.startActivity(AudioFileManager.createShareIntent(context, file)) }
                },
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                text = "Discard",
                style = ActionStyle.Ghost,
                enabled = !busy,
                onClick = {
                    playerHelper.stop()
                    review.rawFile.delete()
                    prepared.values.forEach { it.delete() }
                    registerKeep(null)
                    close()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
