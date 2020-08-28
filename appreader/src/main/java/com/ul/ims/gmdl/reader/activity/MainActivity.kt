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

package com.ul.ims.gmdl.reader.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import androidx.navigation.ui.setupActionBarWithNavController
import com.ul.ims.gmdl.reader.R
import org.jetbrains.anko.toast

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private val timeInterval = 2000 // # milliseconds passed between two back presses
    private var mBackPressed: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        setupActionBarWithNavController(navController)
    }

    override fun onSupportNavigateUp() =
        findNavController(R.id.nav_host_fragment).navigateUp()

    override fun onBackPressed() {
        if (findNavController(R.id.nav_host_fragment).currentDestination?.id == R.id.scanDeviceEngagementFragment) {
            if (mBackPressed + timeInterval > System.currentTimeMillis()) {
                super.onBackPressed()
                return
            } else {
                toast(getString(R.string.toast_press_back_twice))
            }
            mBackPressed = System.currentTimeMillis()
        } else {
            super.onBackPressed()
        }
    }
}
