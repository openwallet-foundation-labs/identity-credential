package com.android.identity_credential.wallet.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.Instant
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DurationFromNowTextTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun past_subsecond() {
        // setContent here and below is only needed to be able to call @Composable functions.
        composeTestRule.setContent {
            val instant = Instant.parse("2021-11-25T15:20:00.2Z")
            val now = Instant.parse("2021-11-25T15:20:00.4Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("less than a second ago", text)
        }
    }

    @Test
    fun past_seconds_only() {
        composeTestRule.setContent {
            val instant = Instant.parse("2021-11-25T15:20:00Z")
            val now = Instant.parse("2021-11-25T15:20:02Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("2 seconds ago", text)
        }
    }

    @Test
    fun past_minutes_and_seconds() {
        composeTestRule.setContent {
            val instant = Instant.parse("2021-11-25T15:20:00Z")
            val now = Instant.parse("2021-11-25T15:21:01Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("one minute and one second ago", text)
        }
    }

    @Test
    fun past_hours_only() {
        composeTestRule.setContent {
            val instant = Instant.parse("2021-11-25T15:20:00Z")
            val now = Instant.parse("2021-11-25T17:20:59Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("2 hours ago", text)
        }
    }

    @Test
    fun past_hours_and_minutes() {
        composeTestRule.setContent {
            val instant = Instant.parse("2021-11-25T15:20:00Z")
            val now = Instant.parse("2021-11-25T17:28:59Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("2 hours and 8 minutes ago", text)
        }
    }

    @Test
    fun past_days_only() {
        composeTestRule.setContent {
            val instant = Instant.parse("2021-11-25T15:20:00Z")
            val now = Instant.parse("2021-11-28T15:28:59Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("3 days ago", text)
        }
    }

    @Test
    fun past_days_and_hours() {
        composeTestRule.setContent {
            val instant = Instant.parse("2021-11-25T15:20:00Z")
            val now = Instant.parse("2021-11-28T20:28:59Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("3 days and 5 hours ago", text)
        }
    }

    @Test
    fun past_weeks_only() {
        composeTestRule.setContent {
            val instant = Instant.parse("2024-02-25T15:20:00Z")
            val now = Instant.parse("2024-03-04T12:20:00Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("one week ago", text)
        }
    }

    @Test
    fun past_weeks_and_days() {
        composeTestRule.setContent {
            val instant = Instant.parse("2021-12-02T15:20:00Z")
            val now = Instant.parse("2022-01-01T20:28:59Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("4 weeks and 2 days ago", text)
        }
    }

    @Test
    fun past_months_only() {
        composeTestRule.setContent {
            val instant = Instant.parse("2021-11-25T15:20:00Z")
            val now = Instant.parse("2021-12-25T20:28:59Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("one month ago", text)
        }
    }

    @Test
    fun past_months_and_days() {
        composeTestRule.setContent {
            val instant = Instant.parse("2021-02-25T15:20:00Z")
            val now = Instant.parse("2021-04-26T20:28:59Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("2 months and one day ago", text)
        }
    }

    @Test
    fun past_weeks_or_month() {
        // corner case of 4 weeks == 1 month
        composeTestRule.setContent {
            val instant = Instant.parse("2023-02-25T15:20:00Z")
            val now = Instant.parse("2023-03-25T16:20:00Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("one month ago", text)
        }
    }

    @Test
    fun past_months_and_days_month_length() {
        composeTestRule.setContent {
            val instant = Instant.parse("2021-02-28T15:20:00Z")
            val now = Instant.parse("2021-04-30T20:28:59Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("2 months ago", text)
        }
    }

    @Test
    fun past_months_and_days_leap() {
        composeTestRule.setContent {
            val instant = Instant.parse("2024-02-25T15:20:00Z")
            val now = Instant.parse("2024-04-28T20:28:59Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("2 months and 3 days ago", text)
        }
    }

    @Test
    fun past_years_only() {
        composeTestRule.setContent {
            val instant = Instant.parse("2021-11-25T15:20:00Z")
            val now = Instant.parse("2023-11-26T20:28:59Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("2 years ago", text)
        }
    }

    @Test
    fun past_years_and_months() {
        composeTestRule.setContent {
            val instant = Instant.parse("1910-11-25T15:20:00Z")
            val now = Instant.parse("2023-12-26T20:28:59Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("113 years and one month ago", text)
        }
    }

    @Test
    fun future_subsecond() {
        composeTestRule.setContent {
            val now = Instant.parse("2021-11-25T15:20:00.2Z")
            val instant = Instant.parse("2021-11-25T15:20:00.4Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("less than a second from now", text)
        }
    }

    @Test
    fun future_months_and_days_month_length() {
        composeTestRule.setContent {
            val now = Instant.parse("2021-02-28T15:20:00Z")
            val instant = Instant.parse("2021-04-30T20:28:59Z")
            val text = durationFromNowText(instant = instant, now = now)
            Assert.assertEquals("2 months and 2 days from now", text)
        }
    }

    @Test
    fun past_update_time_subsecond() {
        composeTestRule.setContent {
            val instant = Instant.parse("2021-11-25T15:20:00.2Z")
            val now = Instant.parse("2021-11-25T15:20:00.4Z")

            val (_, updateAt) = pastRawTextAndUpdateTime(instant, now)
            Assert.assertEquals(Instant.parse("2021-11-25T15:20:01.2Z"), updateAt)
        }
    }

    @Test
    fun past_update_time_minutes_and_seconds() {
        composeTestRule.setContent {
            val instant = Instant.parse("2021-11-25T15:20:00.5Z")
            val now = Instant.parse("2021-11-25T15:24:50Z")

            val (_, updateAt) = pastRawTextAndUpdateTime(instant, now)
            Assert.assertEquals(Instant.parse("2021-11-25T15:24:50.5Z"), updateAt)
        }
    }

    @Test
    fun past_update_time_hours_and_minutes() {
        composeTestRule.setContent {
            val instant = Instant.parse("2021-11-25T15:20:00.5Z")
            val now = Instant.parse("2021-11-25T19:24:50Z")

            val (_, updateAt) = pastRawTextAndUpdateTime(instant, now)
            Assert.assertEquals(Instant.parse("2021-11-25T19:25:00.5Z"), updateAt)
        }
    }

    @Test
    fun future_update_time_subsecond() {
        composeTestRule.setContent {
            val now = Instant.parse("2021-11-25T15:20:00.2Z")
            val instant = Instant.parse("2021-11-25T15:20:00.4Z")

            val (_, updateAt) = futureRawTextAndUpdateTime(instant, now)
            Assert.assertEquals(instant, updateAt)
        }
    }

    @Test
    fun future_update_time_weeks_and_days() {
        composeTestRule.setContent {
            val now = Instant.parse("1990-11-03T15:20:00Z")
            val instant = Instant.parse("1990-11-25T15:34:50Z")

            val (_, updateAt) = futureRawTextAndUpdateTime(instant, now)
            Assert.assertEquals(Instant.parse("1990-11-03T15:34:50Z"), updateAt)
        }
    }
}