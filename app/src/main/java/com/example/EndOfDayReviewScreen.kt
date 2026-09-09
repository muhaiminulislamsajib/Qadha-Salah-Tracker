package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class EndOfDayChoice {
    PRAYED,
    MISSED,
    SKIP
}

/**
 * Prominent Dashboard Card alerting user about the End-of-Day Salah Review
 */
@Composable
fun EndOfDayReviewCard(
    onOpenReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("end_of_day_review_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "End-of-Day Salah Review",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Daily Reconciliation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "\"Have you performed all your Salah today?\"",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Review and confirm your 5 daily prayers before ending your day to keep your spiritual ledger completely accurate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onOpenReview,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_open_end_of_day_review"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Review Today's Salah", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * End-of-Day Salah Review Dialog
 * Displays Fajr, Dhuhr, Asr, Maghrib, and Isha with:
 * ✓ Prayed | ✕ Missed | − Skip
 * Prevents double-counting and reconciles records cleanly.
 */
@Composable
fun EndOfDaySalahReviewDialog(
    uiState: TrackerState,
    onDismiss: () -> Unit,
    onReconcile: (PrayerType, EndOfDayChoice) -> Unit
) {
    // Initial choices: default to existing todayStatus or SKIP
    val choices = remember {
        mutableStateMapOf<PrayerType, EndOfDayChoice>().apply {
            PrayerType.values().forEach { prayer ->
                val status = uiState.todayStatus[prayer] ?: TodayStatus.UNTRACKED
                this[prayer] = when (status) {
                    TodayStatus.COMPLETED -> EndOfDayChoice.PRAYED
                    TodayStatus.MISSED -> EndOfDayChoice.MISSED
                    TodayStatus.UNTRACKED -> EndOfDayChoice.SKIP
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .testTag("end_of_day_review_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Daily Salah Review",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Have you performed all your Salah today?",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_end_of_day_review")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Review each Waqt below. Mark ✓ Prayed, ✕ Missed, or − Skip. Any updates will cleanly reconcile your ledger without double-counting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                // 5 Waqts list
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PrayerType.values().forEach { prayer ->
                        val currentStatus = uiState.todayStatus[prayer] ?: TodayStatus.UNTRACKED
                        val currentChoice = choices[prayer] ?: EndOfDayChoice.SKIP

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("review_card_${prayer.id}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "${prayer.displayName} (${prayer.rakah} Rakah Farz)",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val statusText = when (currentStatus) {
                                            TodayStatus.COMPLETED -> "Earlier: Marked as Prayed ✓"
                                            TodayStatus.MISSED -> "Earlier: Marked as Missed ✕ (+1 Qadha)"
                                            TodayStatus.UNTRACKED -> "Earlier: Not Recorded Yet"
                                        }
                                        val statusColor = when (currentStatus) {
                                            TodayStatus.COMPLETED -> Color(0xFF2E7D32)
                                            TodayStatus.MISSED -> MaterialTheme.colorScheme.error
                                            TodayStatus.UNTRACKED -> MaterialTheme.colorScheme.secondary
                                        }
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // 3 Choice Buttons: ✓ Prayed | ✕ Missed | − Skip
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 1. Prayed
                                    val isPrayed = currentChoice == EndOfDayChoice.PRAYED
                                    OutlinedButton(
                                        onClick = { choices[prayer] = EndOfDayChoice.PRAYED },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("btn_review_prayed_${prayer.id}"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isPrayed) Color(0xFF2E7D32) else Color.Transparent,
                                            contentColor = if (isPrayed) Color.White else MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isPrayed) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Prayed", fontSize = 12.sp, fontWeight = if (isPrayed) FontWeight.Bold else FontWeight.Normal)
                                    }

                                    // 2. Missed
                                    val isMissed = currentChoice == EndOfDayChoice.MISSED
                                    OutlinedButton(
                                        onClick = { choices[prayer] = EndOfDayChoice.MISSED },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("btn_review_missed_${prayer.id}"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isMissed) MaterialTheme.colorScheme.error else Color.Transparent,
                                            contentColor = if (isMissed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Missed", fontSize = 12.sp, fontWeight = if (isMissed) FontWeight.Bold else FontWeight.Normal)
                                    }

                                    // 3. Skip
                                    val isSkip = currentChoice == EndOfDayChoice.SKIP
                                    OutlinedButton(
                                        onClick = { choices[prayer] = EndOfDayChoice.SKIP },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("btn_review_skip_${prayer.id}"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSkip) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                            contentColor = if (isSkip) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSkip) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Skip", fontSize = 12.sp, fontWeight = if (isSkip) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_cancel_end_of_day_review"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            // Reconcile each prayer with selected choice
                            choices.forEach { (prayer, choice) ->
                                onReconcile(prayer, choice)
                            }
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                            .testTag("btn_save_end_of_day_review"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Save & Finish Review", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
