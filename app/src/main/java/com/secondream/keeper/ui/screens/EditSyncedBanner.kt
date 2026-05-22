package com.secondream.keeper.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondream.keeper.viewmodel.NoteViewModel

/**
 * Bottom banner tied to REAL upload progress. Surfaces only after a user
 * edit (text/checklist/attachment changes) — pin/archive/color tweaks don't
 * trigger it. Two phases:
 *   SYNCING — animated linear progress driven by upload bytes
 *   DONE — full bar + check icon, auto-dismissed after a brief moment
 */
@Composable
fun EditSyncedBanner(viewModel: NoteViewModel, modifier: Modifier = Modifier) {
    val banner by viewModel.editBanner.collectAsState()

    val visible = banner.phase != NoteViewModel.EditBannerPhase.HIDDEN
    val isDone = banner.phase == NoteViewModel.EditBannerPhase.DONE

    val animatedProgress by animateFloatAsState(
        targetValue = banner.progress,
        animationSpec = tween(220),
        label = "edit_sync_progress"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(120)),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(180)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 220.dp, max = 280.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isDone) Icons.Rounded.Check else Icons.Rounded.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isDone) "Modifica salvata su Drive"
                               else "Salvataggio su Drive in corso",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
