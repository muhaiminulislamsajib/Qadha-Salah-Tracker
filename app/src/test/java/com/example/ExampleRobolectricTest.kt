package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Qadha Tracker", appName)
  }

  @Test
  fun `history entry preserves laterStatus and does not disappear when prayed later`() {
    // 1. Create a historical entry for Fajr that was initially missed
    val initialTime = System.currentTimeMillis() - 3600000
    val entry = QadhaHistoryEntry(
      id = "entry-1",
      prayerId = "fajr",
      dateString = "Sep 08, 2026",
      dayOfWeek = "Tue",
      actionType = "MISSED",
      timestamp = initialTime,
      note = "Missed"
    )

    // Verify serialization roundtrip
    val serialized = entry.toSerialized()
    val deserialized = QadhaHistoryEntry.fromSerialized(serialized)
    assertNotNull(deserialized)
    assertEquals("fajr", deserialized?.prayerId)
    assertEquals("MISSED", deserialized?.actionType)
    assertEquals("Sep 08, 2026", deserialized?.dateString)

    // 2. Later, user marks Fajr as prayed later
    val laterTime = System.currentTimeMillis()
    val updatedEntry = entry.copy(
      laterStatus = "PRAYED_LATER",
      laterTimestamp = laterTime,
      note = "Sep 08, 2026 (Marked Prayed Later)"
    )

    // 3. Verify that the historical record still exists and carries both original and later status
    assertEquals("MISSED", updatedEntry.actionType)
    assertEquals("PRAYED_LATER", updatedEntry.laterStatus)
    assertEquals(initialTime, updatedEntry.timestamp)
    assertEquals(laterTime, updatedEntry.laterTimestamp)

    // Verify serialization with laterStatus & laterTimestamp
    val updatedSerialized = updatedEntry.toSerialized()
    val updatedDeserialized = QadhaHistoryEntry.fromSerialized(updatedSerialized)
    assertNotNull(updatedDeserialized)
    assertEquals("fajr", updatedDeserialized?.prayerId)
    assertEquals("MISSED", updatedDeserialized?.actionType)
    assertEquals("PRAYED_LATER", updatedDeserialized?.laterStatus)
    assertEquals(laterTime, updatedDeserialized?.laterTimestamp)
  }

  @Test
  fun `isEntryMatchingDate matches date by timestamp or formatted note`() {
    val sep8Cal = Calendar.getInstance().apply {
      set(2026, Calendar.SEPTEMBER, 8, 5, 30, 0)
    }
    val sep8Timestamp = sep8Cal.timeInMillis
    val sep8Str = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(sep8Cal.time)

    val entry1 = QadhaHistoryEntry(
      id = "entry-timestamp",
      prayerId = "fajr",
      dateString = sep8Str,
      dayOfWeek = "Tue",
      actionType = "MISSED",
      timestamp = sep8Timestamp
    )

    assertTrue(isEntryMatchingDate(entry1, sep8Str, sep8Timestamp))

    val entryWithNote = QadhaHistoryEntry(
      id = "entry-note",
      prayerId = "dhuhr",
      dateString = "",
      dayOfWeek = "",
      actionType = "MISSED",
      timestamp = 0,
      note = "Logged for $sep8Str"
    )

    assertTrue(isEntryMatchingDate(entryWithNote, sep8Str, sep8Timestamp))
  }

  @Test
  fun `hijri date is calculated accurately and consistently across multiple years`() {
    // Test Sep 8, 2026 -> Safar 1448 AH
    val cal2026 = Calendar.getInstance().apply {
      set(2026, Calendar.SEPTEMBER, 8, 12, 0, 0)
    }
    val hijri2026 = HijriCalendarHelper.getHijriDate("Asia/Riyadh", cal2026.timeInMillis)
    assertEquals(1448, hijri2026.year)
    assertEquals(2, hijri2026.month) // Safar
    assertEquals("SAFAR", hijri2026.monthName)
    assertTrue(hijri2026.day in 1..30)
    assertTrue(hijri2026.format().contains("SAFAR 1448 AH"))

    // Test year 2024 -> 1445 or 1446 AH
    val cal2024 = Calendar.getInstance().apply {
      set(2024, Calendar.MARCH, 15, 12, 0, 0)
    }
    val hijri2024 = HijriCalendarHelper.getHijriDate("UTC", cal2024.timeInMillis)
    assertEquals(1445, hijri2024.year)
    assertEquals("RAMADAN", hijri2024.monthName)

    // Test year 2030 -> 1451 or 1452 AH
    val cal2030 = Calendar.getInstance().apply {
      set(2030, Calendar.JANUARY, 1, 12, 0, 0)
    }
    val hijri2030 = HijriCalendarHelper.getHijriDate("America/New_York", cal2030.timeInMillis)
    assertTrue(hijri2030.year in 1451..1452)
    assertTrue(hijri2030.month in 1..12)
    assertTrue(hijri2030.day in 1..30)
  }

  @Test
  fun `hijri date respects user timezone`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storage = QadhaStorageHelper(context)
    storage.setTimeZoneId("Asia/Tokyo")

    val formattedTokyo = HijriCalendarHelper.getFormattedHijriString(context)
    assertNotNull(formattedTokyo)
    assertTrue(formattedTokyo.contains("AH"))

    storage.setTimeZoneId("America/Los_Angeles")
    val formattedLA = HijriCalendarHelper.getFormattedHijriString(context)
    assertNotNull(formattedLA)
    assertTrue(formattedLA.contains("AH"))
  }

  @Test
  fun `prayer action helper handles YES, NO, and REMIND ME LATER correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storage = QadhaStorageHelper(context)

    // Initial state
    storage.saveState(TrackerState())

    // 1. User clicks YES for Fajr
    PrayerActionHelper.handleYes(context, PrayerType.FAJR)
    val stateAfterYes = storage.loadState()
    assertEquals(TodayStatus.COMPLETED, stateAfterYes.todayStatus[PrayerType.FAJR])
    assertEquals(1, stateAfterYes.activeStreak)
    assertEquals(1, stateAfterYes.totalLoggedCount)
    assertEquals("PRAYED", stateAfterYes.history.first().actionType)

    // 2. User clicks NO for Dhuhr
    PrayerActionHelper.handleNo(context, PrayerType.DHUHR)
    val stateAfterNo = storage.loadState()
    assertEquals(TodayStatus.MISSED, stateAfterNo.todayStatus[PrayerType.DHUHR])
    assertEquals(1, stateAfterNo.backlog[PrayerType.DHUHR])
    assertEquals(0, stateAfterNo.activeStreak) // Streak reset on NO
    assertEquals("MISSED", stateAfterNo.history.first().actionType)

    // 3. User clicks REMIND ME LATER for Asr
    val futureTime = System.currentTimeMillis() + 30 * 60 * 1000L
    PrayerActionHelper.handleRemindLater(context, PrayerType.ASR, futureTime)
    val stateAfterRemind = storage.loadState()
    // Backlog must NOT increase for ASR
    assertEquals(0, stateAfterRemind.backlog[PrayerType.ASR] ?: 0)
    // History must record REMIND_LATER
    val remindEntry = stateAfterRemind.history.find { it.prayerId == "asr" }
    assertNotNull(remindEntry)
    assertEquals("REMIND_LATER", remindEntry?.actionType)
  }

  @Test
  fun `prayer action receiver executes intents without opening main app`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storage = QadhaStorageHelper(context)
    storage.saveState(TrackerState())

    val receiver = PrayerActionReceiver()

    // Test YES intent
    val yesIntent = android.content.Intent(PrayerActionHelper.ACTION_RECORD_YES).apply {
      putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_ID, "maghrib")
    }
    receiver.onReceive(context, yesIntent)
    val stateAfterYes = storage.loadState()
    assertEquals(TodayStatus.COMPLETED, stateAfterYes.todayStatus[PrayerType.MAGHRIB])
    assertEquals(1, stateAfterYes.activeStreak)

    // Test NO intent
    val noIntent = android.content.Intent(PrayerActionHelper.ACTION_RECORD_NO).apply {
      putExtra(PrayerAlarmScheduler.EXTRA_PRAYER_ID, "isha")
    }
    receiver.onReceive(context, noIntent)
    val stateAfterNo = storage.loadState()
    assertEquals(TodayStatus.MISSED, stateAfterNo.todayStatus[PrayerType.ISHA])
    assertEquals(1, stateAfterNo.backlog[PrayerType.ISHA])
    assertEquals(0, stateAfterNo.activeStreak)
  }
}
