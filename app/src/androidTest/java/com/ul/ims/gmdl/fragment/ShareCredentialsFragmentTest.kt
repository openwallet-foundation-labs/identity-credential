/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.ul.ims.gmdl.fragment

import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.rule.ActivityTestRule
import androidx.test.runner.AndroidJUnit4
import com.ul.ims.gmdl.R
import com.ul.ims.gmdl.activity.MainActivity
import com.ul.ims.gmdl.utils.waitId
import org.hamcrest.Matchers.anything
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ShareCredentialsFragmentTest {

    @Rule
    @JvmField
    val mActivityRule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun testUi() {
        // Wait up to 8 seconds for this view to show up
        onView(isRoot()).perform(
            waitId(R.id.btn_share_mdl, TimeUnit.SECONDS.toMillis(8))
        )

        // Select the first option (BLE) on transfer method
        onView(withId(R.id.spn_transfer_method)).perform(click())
        onData(anything()).atPosition(0).perform(click())

        onView(withId(R.id.btn_share_mdl)).perform(click())

        // as we test using an AVD, bluetooth capabilities are not available, so we can test only the case if bt is off
        onView(withId(R.id.permission_denied_txt)).check(matches(isDisplayed()))
        onView(withText(R.string.ble_disabled_txt)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_req_enable_ble)).check(matches(isDisplayed()))
    }
}