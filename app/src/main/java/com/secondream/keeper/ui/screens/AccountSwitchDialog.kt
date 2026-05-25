package com.secondream.keeper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondream.keeper.viewmodel.NoteViewModel
import androidx.compose.ui.res.stringResource
import com.secondream.keeper.R

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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Cambio account",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                LabelRow(label = "Account attuale", value = pending.previousEmail)
                Spacer(modifier = Modifier.height(10.dp))
                LabelRow(label = "Nuovo account", value = pending.newEmail)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.account_switch_intro),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                BulletStep("1.", stringResource(R.string.account_switch_step1, pending.previousEmail))
                BulletStep("2.", stringResource(R.string.account_switch_step2))
                BulletStep("3.", stringResource(R.string.account_switch_step3, pending.newEmail))
                BulletStep("4.", stringResource(R.string.account_switch_step4, pending.newEmail))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.account_switch_safe_note),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 17.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.confirmAccountSwitch() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.account_switch_proceed), fontWeight = FontWeight.Bold)
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

@Composable
private fun LabelRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BulletStep(number: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = number,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(20.dp)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        )
    }
}
