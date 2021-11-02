/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.safetycenter.cts

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.support.test.uiautomator.By
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.filters.SdkSuppress
import android.os.Build.VERSION_CODES.TIRAMISU

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterActivityTest {
    private val context: Context = getApplicationContext()

    @Test
    fun launchActivity_showsSafetyCenterText() {
        // TODO(b/203098031): Replace with intent action string from `Intent` class once available.
        context.startActivity(
            Intent("android.intent.action.SAFETY_CENTER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        waitFindObject(By.text("SafetyCenter"))
    }
}
