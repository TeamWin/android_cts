/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.permission3.cts

import android.content.Intent
import android.content.Intent.ACTION_REVIEW_APP_DATA_SHARING_UPDATES
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build
import android.support.test.uiautomator.By
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.modules.utils.build.SdkLevel
import org.junit.Assume
import org.junit.Before
import org.junit.Test

/** Tests the UI that displays information about apps' updates to their data sharing policies. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class AppDataSharingUpdatesTest : BasePermissionTest() {

    @Before
    fun setup() {
        Assume.assumeTrue(
            "Data sharing updates page is only available on U+", SdkLevel.isAtLeastU())
        Assume.assumeFalse(isAutomotive)
        Assume.assumeFalse(isTv)
        Assume.assumeFalse(isWatch)
    }

    @Test
    fun startActivityWithIntent_opensDataSharingUpdatesPage() {
        runWithShellPermissionIdentity {
            context.startActivity(
                Intent(ACTION_REVIEW_APP_DATA_SHARING_UPDATES).apply {
                    addFlags(FLAG_ACTIVITY_NEW_TASK)
                })
        }

        waitFindObject(By.textContains("Data Sharing updates"))
    }
}
