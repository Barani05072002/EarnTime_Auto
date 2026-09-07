package com.example.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One credit buys one minute of foreground time, matching the "min" unit on the wallet. */
val UNLOCK_DURATION_OPTIONS = listOf(5, 10, 15, 30, 60)

/**
 * Duration picker shared by the dashboard and the block screen, so spending credits works the
 * same way wherever the user hits a lock.
 */
@Composable
fun UnlockDurationChips(
    balance: Int,
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UNLOCK_DURATION_OPTIONS.forEach { minutes ->
            val affordable = minutes <= balance
            FilterChip(
                selected = selectedMinutes == minutes,
                enabled = affordable,
                onClick = { onSelect(minutes) },
                label = { Text("$minutes min") },
                colors = FilterChipDefaults.filterChipColors()
            )
        }
    }
}

/**
 * Confirmation dialog for spending credits on an app. Returns the chosen minute count.
 */
@Composable
fun UnlockAppDialog(
    appName: String,
    balance: Int,
    isUnlocking: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val affordableDefault = UNLOCK_DURATION_OPTIONS.firstOrNull { it <= balance }
        ?: UNLOCK_DURATION_OPTIONS.first()
    var selectedMinutes by remember(appName, affordableDefault) { mutableIntStateOf(affordableDefault) }
    val canAfford = selectedMinutes <= balance

    AlertDialog(
        onDismissRequest = { if (!isUnlocking) onDismiss() },
        title = { Text("Unlock $appName") },
        text = {
            Column {
                Text(
                    text = "You have $balance credits. Unlocking spends 1 credit per minute, " +
                        "and the timer only runs while $appName is on screen.",
                    style = MaterialTheme.typography.bodyMedium
                )
                UnlockDurationChips(
                    balance = balance,
                    selectedMinutes = selectedMinutes,
                    onSelect = { selectedMinutes = it },
                    modifier = Modifier.padding(top = 16.dp)
                )
                if (!canAfford) {
                    Text(
                        text = "Not enough credits. Complete tasks or habits to earn more.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedMinutes) },
                enabled = canAfford && !isUnlocking
            ) {
                Text(
                    text = if (isUnlocking) "UNLOCKING..." else "SPEND $selectedMinutes CREDITS",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUnlocking) { Text("CANCEL") }
        }
    )
}
