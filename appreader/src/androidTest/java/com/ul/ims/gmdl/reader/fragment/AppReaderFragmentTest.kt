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

package com.ul.ims.gmdl.reader.fragment

import android.Manifest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import androidx.test.runner.AndroidJUnit4
import com.ul.ims.gmdl.reader.R
import com.ul.ims.gmdl.reader.activity.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppReaderFragmentTest {

    // Grant Camera Permission
    @Rule
    @JvmField
    val grantPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

    @Rule
    @JvmField
    val mActivityRule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun testUi() {
        onView(withId(R.id.btn_verifier)).check(matches(isDisplayed()))
        onView(withId(R.id.txt_select_function_label)).check(matches(isDisplayed()))
    }

    @Test
    fun onVerifierSelectedTest() {
        onView(withId(R.id.btn_verifier)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_verifier)).perform(click())

        // Consent Dialog
        onView(withText(R.string.verifier_request_dialog)).check(matches(isDisplayed()))
        onView(withText(android.R.string.ok)).perform(click())
        onView(withText(R.string.txt_scan_qrcode_label)).check(matches(isDisplayed()))
    }
}