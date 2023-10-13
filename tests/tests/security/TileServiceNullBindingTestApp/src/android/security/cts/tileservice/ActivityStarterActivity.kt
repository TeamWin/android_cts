/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.security.cts.tileservice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.widget.FrameLayout

class ActivityStarterActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this))
    }

    override fun onResume() {
        super.onResume()
        Handler().postDelayed({
            startActivity(Intent(applicationContext, BackgroundLaunchActivity::class.java))
        }, DELAY)
        minimizeApp()
    }

    private fun minimizeApp() {
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(startMain)
    }

    companion object {
        private const val DELAY = 15000L
    }
}
