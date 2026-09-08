package com.example

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Overlay Dialog Activity that appears directly over whatever app the user is currently using,
 * including when the phone screen is locked or turned off.
 *
 * Allows responding YES, NO, or REMIND ME LATER directly without opening the main app.
 */
class PrayerAlertDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure window to wake the screen and display over the lockscreen
        setupLockscreenAndWake()

        val prayerId = intent?.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_ID) ?: "fajr"
        val prayer = PrayerType.fromId(prayerId)

        setContent {
            MyApplicationTheme {
                PrayerAlertOverlayScreen(
                    prayer = prayer,
                    onDismiss = { finish() },
                    onYes = {
                        PrayerActionHelper.handleYes(this, prayer)
                        Toast.makeText(this, "Alhamdulillah! ${prayer.displayName} marked as prayed.", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onNo = {
                        PrayerActionHelper.handleNo(this, prayer)
                        Toast.makeText(this, "${prayer.displayName} added to Qadha Book (+1).", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onRemindLater = { reminderMs ->
                        PrayerActionHelper.handleRemindLater(this, prayer, reminderMs)
                        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(reminderMs))
                        Toast.makeText(this, "Reminder scheduled for ${prayer.displayName} at $timeFmt.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }

    private fun setupLockscreenAndWake() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }
}

@Composable
fun PrayerAlertOverlayScreen(
    prayer: PrayerType,
    onDismiss: () -> Unit,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onRemindLater: (Long) -> Unit
) {
    val context = LocalContext.current
    var showReminderView by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf("15m") } // "15m", "30m", "1h", "custom"

    val now = Calendar.getInstance()
    val defaultHour = (now.get(Calendar.HOUR_OF_DAY) % 12).let { if (it == 0) 12 else it }
    val defaultMinute = ((now.get(Calendar.MINUTE) + 15) / 5 * 5) % 60
    val defaultAmPm = if (now.get(Calendar.HOUR_OF_DAY) >= 12) "PM" else "AM"

    var customHour by remember { mutableIntStateOf(defaultHour) }
    var customMinute by remember { mutableIntStateOf(defaultMinute) }
    var customAmPm by remember { mutableStateOf(defaultAmPm) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val prayerStartTime = remember(prayer) {
        PrayerAlarmScheduler.getPrayerStartTimeForToday(context, prayer)
    }
    val prayerStartTimeFormatted = remember(prayer) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(prayerStartTime))
    }

    // Full screen dimmed background overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* prevent accidental dismiss on click outside */ },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .testTag("overlay_prayer_alert_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header Banner with Islamic Styling & Dismiss Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1B4D3E))
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_app_icon),
                            contentDescription = "Qadha logo",
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (showReminderView) "Set Reminder" else "Salat Alert",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (showReminderView) "REMIND ME LATER FOR ${prayer.displayName.uppercase()}" else "WAQT STARTED • $prayerStartTimeFormatted",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f),
                            letterSpacing = 1.2.sp
                        )
                    }

                    // Top-right close button to dismiss if user wants to close without choosing
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(36.dp)
                            .testTag("overlay_dismiss_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (!showReminderView) {
                    // MAIN PROMPT: YES | NO | REMIND ME LATER
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Have you prayed your",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF5C635E),
                                textAlign = TextAlign.Center
                            )
                        )
                        Text(
                            text = "${prayer.displayName} Salah?",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B4D3E),
                                textAlign = TextAlign.Center
                            )
                        )
                        Text(
                            text = "YES: Mark as prayed & build streak\nNO: Add +1 Qadha to ${prayer.displayName} & reset streak\nREMIND: Snooze without penalty",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF7F8C8D),
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            ),
                            modifier = Modifier
                                .background(Color(0xFFF7F9F9), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }

                    // Action Buttons
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // YES Button
                        Button(
                            onClick = onYes,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("overlay_btn_yes"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B4D3E)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "YES (Prayed)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        }

                        // NO Button
                        OutlinedButton(
                            onClick = onNo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("overlay_btn_no"),
                            border = BorderStroke(1.5.dp, Color(0xFFC0392B)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC0392B)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color(0xFFC0392B), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "NO (Add +1 Qadha)",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC0392B),
                                fontSize = 15.sp
                            )
                        }

                        // REMIND ME LATER Button
                        FilledTonalButton(
                            onClick = { showReminderView = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("overlay_btn_remind_later"),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFFFF3CD),
                                contentColor = Color(0xFF856404)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF856404), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "REMIND ME LATER",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF856404),
                                fontSize = 15.sp
                            )
                        }
                    }
                } else {
                    // REMINDER OPTIONS SCREEN (15m, 30m, 1h, Custom)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "When should we remind you for ${prayer.displayName}?",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF2C3E50)
                        )

                        val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                        val currentTimeMs = System.currentTimeMillis()

                        // Preset 1: 15 minutes
                        val t15 = currentTimeMs + 15 * 60 * 1000L
                        ReminderOptionTile(
                            title = "15 minutes",
                            subtitle = "At ${timeFormat.format(Date(t15))}",
                            isSelected = selectedOption == "15m",
                            onClick = {
                                selectedOption = "15m"
                                validationError = null
                            },
                            testTag = "overlay_reminder_opt_15m"
                        )

                        // Preset 2: 30 minutes
                        val t30 = currentTimeMs + 30 * 60 * 1000L
                        ReminderOptionTile(
                            title = "30 minutes",
                            subtitle = "At ${timeFormat.format(Date(t30))}",
                            isSelected = selectedOption == "30m",
                            onClick = {
                                selectedOption = "30m"
                                validationError = null
                            },
                            testTag = "overlay_reminder_opt_30m"
                        )

                        // Preset 3: 1 hour
                        val t60 = currentTimeMs + 60 * 60 * 1000L
                        ReminderOptionTile(
                            title = "1 hour",
                            subtitle = "At ${timeFormat.format(Date(t60))}",
                            isSelected = selectedOption == "1h",
                            onClick = {
                                selectedOption = "1h"
                                validationError = null
                            },
                            testTag = "overlay_reminder_opt_1h"
                        )

                        // Preset 4: Custom time
                        ReminderOptionTile(
                            title = "Custom time",
                            subtitle = "Pick specific time today (after $prayerStartTimeFormatted)",
                            isSelected = selectedOption == "custom",
                            onClick = {
                                selectedOption = "custom"
                            },
                            testTag = "overlay_reminder_opt_custom"
                        )

                        // Custom time picker UI when selected
                        if (selectedOption == "custom") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F6F6)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Hour stepper
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    customHour = if (customHour <= 1) 12 else customHour - 1
                                                    validationError = null
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Hour down", modifier = Modifier.size(16.dp))
                                            }
                                            Text(
                                                text = String.format(Locale.US, "%02d", customHour),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                            IconButton(
                                                onClick = {
                                                    customHour = if (customHour >= 12) 1 else customHour + 1
                                                    validationError = null
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Hour up", modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Text(":", fontWeight = FontWeight.Bold, fontSize = 20.sp)

                                        // Minute stepper (5-min steps)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    customMinute = if (customMinute <= 0) 55 else (customMinute - 5) / 5 * 5
                                                    validationError = null
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Minute down", modifier = Modifier.size(16.dp))
                                            }
                                            Text(
                                                text = String.format(Locale.US, "%02d", customMinute),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                            IconButton(
                                                onClick = {
                                                    customMinute = if (customMinute >= 55) 0 else (customMinute + 5) / 5 * 5
                                                    validationError = null
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Minute up", modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        // AM/PM toggle
                                        Button(
                                            onClick = {
                                                customAmPm = if (customAmPm == "AM") "PM" else "AM"
                                                validationError = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B4D3E)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(customAmPm, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        // Strict Salah time validation error banner
                        if (validationError != null) {
                            Text(
                                text = validationError ?: "",
                                color = Color(0xFFC0392B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFDEDEC), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Confirm & Back buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showReminderView = false
                                    validationError = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Back")
                            }

                            Button(
                                onClick = {
                                    val triggerMs: Long = when (selectedOption) {
                                        "15m" -> System.currentTimeMillis() + 15 * 60 * 1000L
                                        "30m" -> System.currentTimeMillis() + 30 * 60 * 1000L
                                        "1h" -> System.currentTimeMillis() + 60 * 60 * 1000L
                                        "custom" -> {
                                            val cal = Calendar.getInstance()
                                            var hour24 = if (customAmPm == "PM") {
                                                if (customHour == 12) 12 else customHour + 12
                                            } else {
                                                if (customHour == 12) 0 else customHour
                                            }
                                            cal.set(Calendar.HOUR_OF_DAY, hour24)
                                            cal.set(Calendar.MINUTE, customMinute)
                                            cal.set(Calendar.SECOND, 0)
                                            cal.set(Calendar.MILLISECOND, 0)

                                            // If chosen time is already past for today, reject it
                                            if (cal.timeInMillis <= System.currentTimeMillis()) {
                                                validationError = "Custom reminder cannot be set in the past. Please pick a future time."
                                                return@Button
                                            }
                                            cal.timeInMillis
                                        }
                                        else -> System.currentTimeMillis() + 15 * 60 * 1000L
                                    }

                                    // STRICT SALAH TIME RULE:
                                    // Custom reminders cannot be scheduled before the relevant Salah time!
                                    if (triggerMs < prayerStartTime) {
                                        validationError = "${prayer.displayName} starts at $prayerStartTimeFormatted. Custom reminder cannot be scheduled before $prayerStartTimeFormatted."
                                        return@Button
                                    }

                                    onRemindLater(triggerMs)
                                },
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(48.dp)
                                    .testTag("overlay_confirm_reminder_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B4D3E)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Confirm", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
