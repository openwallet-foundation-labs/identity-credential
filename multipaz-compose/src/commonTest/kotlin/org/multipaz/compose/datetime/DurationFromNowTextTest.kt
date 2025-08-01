package org.multipaz.compose.datetime

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DurationFromNowTextTest {

    @Test
    fun past_seconds() = runComposeUiTest {
        // setContent here and below is only needed to be able to call @Composable functions.
        setContent {
            val instant = Instant.parse("2021-11-25T15:20:00.2Z")
            val now = Instant.parse("2021-11-25T15:20:04.4Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("just now", text)
        }
    }

    @Test
    fun past_tens_of_seconds() = runComposeUiTest {
        setContent {
            val instant = Instant.parse("2021-11-25T15:20:00Z")
            val now = Instant.parse("2021-11-25T15:20:22Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("less than a minute ago", text)
        }
    }

    @Test
    fun past_minutes() = runComposeUiTest {
        setContent {
            val instant = Instant.parse("2021-11-25T15:20:00Z")
            val now = Instant.parse("2021-11-25T15:21:01Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("1 minute ago", text)
        }
    }

    @Test
    fun past_hours_only() = runComposeUiTest {
        setContent {
            val instant = Instant.parse("2021-11-25T15:20:00Z")
            val now = Instant.parse("2021-11-25T17:20:59Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("2 hours ago", text)
        }
    }

    @Test
    fun past_hours_and_minutes() = runComposeUiTest {
        setContent {
            val instant = Instant.parse("2021-11-25T15:20:00Z")
            val now = Instant.parse("2021-11-25T17:28:59Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("2 hours and 8 minutes ago", text)
        }
    }

    @Test
    fun past_days_only() = runComposeUiTest {
        setContent {
            val instant = Instant.parse("2021-11-25T15:20:00Z")
            val now = Instant.parse("2021-11-28T15:28:59Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("3 days ago", text)
        }
    }

    @Test
    fun past_days_and_hours() = runComposeUiTest {
        setContent {
            val instant = Instant.parse("2021-11-25T15:20:00Z")
            val now = Instant.parse("2021-11-28T20:28:59Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("3 days and 5 hours ago", text)
        }
    }

    @Test
    fun past_weeks_only() = runComposeUiTest {
        setContent {
            val instant = Instant.parse("2024-02-25T15:20:00Z")
            val now = Instant.parse("2024-03-04T12:20:00Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("1 week ago", text)
        }
    }

    @Test
    fun past_weeks_and_days() = runComposeUiTest {
        setContent {
            val instant = Instant.parse("2021-12-02T15:20:00Z")
            val now = Instant.parse("2022-01-01T20:28:59Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("4 weeks and 2 days ago", text)
        }
    }

    @Test
    fun past_months_only() = runComposeUiTest {
        setContent {
            val instant = Instant.parse("2021-11-25T15:20:00Z")
            val now = Instant.parse("2021-12-25T20:28:59Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("1 month ago", text)
        }
    }

    @Test
    fun past_months_and_days() = runComposeUiTest {
        setContent {
            val instant = Instant.parse("2021-02-25T15:20:00Z")
            val now = Instant.parse("2021-04-26T20:28:59Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("2 months and 1 day ago", text)
        }
    }

    @Test
    fun past_weeks_or_month() = runComposeUiTest {
        // corner case of 4 weeks == 1 month
        setContent {
            val instant = Instant.parse("2023-02-25T15:20:00Z")
            val now = Instant.parse("2023-03-25T16:20:00Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("1 month ago", text)
        }
    }

    @Test
    fun past_months_and_days_month_length() = runComposeUiTest {
        setContent {
            val instant = Instant.parse("2021-02-28T15:20:00Z")
            val now = Instant.parse("2021-04-30T20:28:59Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("2 months ago", text)
        }
    }

    @Test
    fun past_months_and_days_leap() = runComposeUiTest {
        setContent {
            val instant = Instant.parse("2024-02-25T15:20:00Z")
            val now = Instant.parse("2024-04-28T20:28:59Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("2 months and 3 days ago", text)
        }
    }

    @Test
    fun past_years_only() = runComposeUiTest {
        setContent {
            val instant = Instant.parse("2021-11-25T15:20:00Z")
            val now = Instant.parse("2023-11-26T20:28:59Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("2 years ago", text)
        }
    }

    @Test
    fun past_years_and_months() = runComposeUiTest {
        setContent {
            val instant = Instant.parse("1910-11-25T15:20:00Z")
            val now = Instant.parse("2023-12-26T20:28:59Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("113 years and 1 month ago", text)
        }
    }

    @Test
    fun future_seconds() = runComposeUiTest {
        setContent {
            val now = Instant.parse("2021-11-25T15:20:00.2Z")
            val instant = Instant.parse("2021-11-25T15:20:00.4Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("in a few moments", text)
        }
    }

    @Test
    fun future_months_and_days_month_length() = runComposeUiTest {
        setContent {
            val now = Instant.parse("2021-02-28T15:20:00Z")
            val instant = Instant.parse("2021-04-30T20:28:59Z")
            val text = durationFromNowTextCore(instant = instant, now = now).first
            assertEquals("2 months and 2 days from now", text)
        }
    }

    @Test
    fun past_update_time_seconds() = runComposeUiTest {
        setContent {
            val instant = Instant.parse("2021-11-25T15:20:00.2Z")
            val now = Instant.parse("2021-11-25T15:20:00.4Z")

            val (_, updateAt) = durationFromNowTextCore(instant, now)
            assertEquals(Instant.parse("2021-11-25T15:20:10.2Z"), updateAt)
        }
    }

    @Test
    fun past_update_time_minutes() = runComposeUiTest {
        setContent {
            val instant = Instant.parse("2021-11-25T15:20:00.5Z")
            val now = Instant.parse("2021-11-25T15:24:50Z")

            val (_, updateAt) = durationFromNowTextCore(instant, now)
            assertEquals(Instant.parse("2021-11-25T15:25:00.5Z"), updateAt)
        }
    }

    @Test
    fun past_update_time_hours_and_minutes() = runComposeUiTest {
        setContent {
            val instant = Instant.parse("2021-11-25T15:20:00.5Z")
            val now = Instant.parse("2021-11-25T19:24:50Z")

            val (_, updateAt) = durationFromNowTextCore(instant, now)
            assertEquals(Instant.parse("2021-11-25T19:25:00.5Z"), updateAt)
        }
    }

    @Test
    fun future_update_time_seconds() = runComposeUiTest {
        setContent {
            val now = Instant.parse("2021-11-25T15:20:00Z")
            val instant = Instant.parse("2021-11-25T15:20:09Z")

            val (_, updateAt) = durationFromNowTextCore(instant, now)
            assertEquals(instant, updateAt)
        }
    }

    @Test
    fun future_update_time_tens_of_seconds() = runComposeUiTest {
        setContent {
            val now = Instant.parse("2021-11-25T15:20:00Z")
            val instant = Instant.parse("2021-11-25T15:20:49Z")

            val (_, updateAt) = durationFromNowTextCore(instant, now)
            assertEquals(Instant.parse("2021-11-25T15:20:39Z"), updateAt)
        }
    }

    @Test
    fun future_update_time_weeks_and_days() = runComposeUiTest {
        setContent {
            val now = Instant.parse("1990-11-03T15:20:00Z")
            val instant = Instant.parse("1990-11-25T15:34:50Z")

            val (_, updateAt) = durationFromNowTextCore(instant, now)
            assertEquals(Instant.parse("1990-11-03T15:34:50Z"), updateAt)
        }
    }
}