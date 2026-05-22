package com.secondream.keeper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondream.keeper.viewmodel.NoteViewModel

@Composable
fun AccountSwitchDialog(viewModel: NoteViewModel) {
    val req by viewModel.accountSwitchRequest.collectAsState()
    val pending = req ?: return

    AlertDialog(
        onDismissRequest = { viewModel.cancelAccountSwitch() },
        icon = {
            Icon(
                imageVector = Icons.Rounded.SwapHoriz,
                contentDescription = null,
                tint = Color(0xFFFFCA28),
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Cambiare account?",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "Stai passando da",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = pending.previousEmail,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "a",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = pending.newEmail,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Le note locali del vecchio account verranno eliminate per evitare di mescolarle. Le note dell'altro account sono al sicuro sul tuo Drive.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.confirmAccountSwitch() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFCA28),
                    contentColor = Color(0xFF1A1A1A)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Procedi", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.cancelAccountSwitch() }) {
                Text("Annulla")
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}
