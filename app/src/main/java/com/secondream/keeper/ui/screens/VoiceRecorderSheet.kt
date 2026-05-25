package com.secondream.keeper.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.secondream.keeper.R

/**
 * Bottom-sheet voice recorder with the same visual language as
 * AttachmentPickerSheet. Behavior:
 *   - While recording: the text area is READ-ONLY (only transcription shows)
 *   - When stopped: the user can edit the captured text before confirming
 *   - "Conferma" inserts the final text into the parent note
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecorderSheet(
    isRecording: Boolean,
    dictationText: String,
    voiceError: String?,
    onToggleRecording: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (finalText: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local editable text only while NOT recording. While recording we just
    // read whatever the parent's dictationText is.
    var editableText by remember(dictationText, isRecording) {
        mutableStateOf(if (isRecording) "" else dictationText)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) Color(0xFFE53935).copy(alpha = 0.22f)
                            else Color(0xFFFFCA28).copy(alpha = 0.22f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = if (isRecording) Color(0xFFE53935) else Color(0xFFFFCA28),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.voice_dictation_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isRecording)
                            stringResource(R.string.voice_recording_in_progress)
                        else if (dictationText.isBlank())
                            stringResource(R.string.voice_press_to_start)
                        else
                            stringResource(R.string.voice_edit_and_confirm),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            voiceError?.let { err ->
                Text(
                    text = err,
                    color = Color(0xFFE53935),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Waveform while recording
            if (isRecording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "wave")
                    val heights = listOf(1, 2, 3, 4, 5, 6, 7).map { index ->
                        infiniteTransition.animateFloat(
                            initialValue = 8f,
                            targetValue = 42f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(
                                    durationMillis = 280 + (index * 110),
                                    easing = FastOutSlowInEasing
                                ),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "bar_$index"
                        )
                    }
                    heights.forEach { heightVal ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .width(5.dp)
                                .height(heightVal.value.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFE53935))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Text area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp, max = 220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(14.dp)
            ) {
                if (isRecording) {
                    // Read-only: show what's being transcribed
                    Text(
                        text = dictationText.ifBlank { "Inizia a parlare…" },
                        fontSize = 14.sp,
                        color = if (dictationText.isBlank())
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                } else {
                    // Editable text field when not recording
                    BasicEditField(
                        value = editableText,
                        onValueChange = { editableText = it },
                        placeholder = stringResource(R.string.voice_dictated_text_placeholder)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action row: record/stop + confirm
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Record/Stop button
                Button(
                    onClick = onToggleRecording,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) Color(0xFF424242) else Color(0xFFE53935),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRecording) stringResource(R.string.voice_stop_button) else stringResource(R.string.voice_record_button),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Confirm button (only when there's text and we're not recording)
                if (!isRecording && editableText.isNotBlank()) {
                    Button(
                        onClick = { onConfirm(editableText) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFCA28),
                            contentColor = Color(0xFF1A1A1A)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Conferma",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BasicEditField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 20.sp
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFFCA28)),
        modifier = Modifier.fillMaxSize(),
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    lineHeight = 20.sp
                )
            }
            innerTextField()
        }
    )
}
