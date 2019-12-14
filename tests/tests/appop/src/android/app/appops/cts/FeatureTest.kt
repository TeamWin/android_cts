/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.app.appops.cts

import android.app.AppOpsManager
import android.app.AppOpsManager.OPSTR_WIFI_SCAN
import android.app.AppOpsManager.OP_FLAGS_ALL
import android.platform.test.annotations.AppModeFull
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.lang.AssertionError
import java.lang.Thread.sleep

private const val APK_PATH = "/data/local/tmp/cts/appops/"

private const val APP_PKG = "android.app.appops.cts.apptoblame"

private const val FEATURE_1 = "feature1"
private const val FEATURE_2 = "feature2"
private const val FEATURE_3 = "feature3"
private const val FEATURE_4 = "feature4"
private const val FEATURE_5 = "feature5"
private const val FEATURE_6 = "feature6"
private const val FEATURE_7 = "feature7"

@AppModeFull(reason = "Test relies on seeing other apps. Instant apps can't see other apps")
class FeatureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val appOpsManager = context.getSystemService(AppOpsManager::class.java)
    private val appUid by lazy { context.packageManager.getPackageUid(APP_PKG, 0) }

    private fun installApk(apk: String) {
        val result = runCommand("pm install -r $APK_PATH$apk")
        assertThat(result.trim()).isEqualTo("Success")
    }

    @Before
    fun resetTestApp() {
        runCommand("pm uninstall $APP_PKG")
        installApk("CtsAppToBlame1.apk")
    }

    private fun noteForFeature(feature: String) {
        // Make sure note times as distinct
        sleep(1)

        runWithShellPermissionIdentity {
            appOpsManager.noteOpNoThrow(OPSTR_WIFI_SCAN, appUid, APP_PKG, feature, null)
        }
    }

    @Test
    fun inheritNotedAppOpsOnUpgrade() {
        noteForFeature(FEATURE_1)
        noteForFeature(FEATURE_2)
        noteForFeature(FEATURE_3)
        noteForFeature(FEATURE_4)
        noteForFeature(FEATURE_5)

        val beforeUpdate = getOpEntry(appUid, APP_PKG, OPSTR_WIFI_SCAN)!!
        installApk("CtsAppToBlame2.apk")

        eventually {
            val afterUpdate = getOpEntry(appUid, APP_PKG, OPSTR_WIFI_SCAN)!!

            // Feature 1 is unchanged
            assertThat(afterUpdate.features[FEATURE_1]!!.getLastAccessTime(OP_FLAGS_ALL))
                    .isEqualTo(beforeUpdate.features[FEATURE_1]!!.getLastAccessTime(OP_FLAGS_ALL))

            // Feature 3 disappeared (i.e. was added into "null" feature)
            assertThat(afterUpdate.features[null]!!.getLastAccessTime(OP_FLAGS_ALL))
                    .isEqualTo(beforeUpdate.features[FEATURE_3]!!.getLastAccessTime(OP_FLAGS_ALL))

            // Feature 6 inherits from feature 2
            assertThat(afterUpdate.features[FEATURE_6]!!.getLastAccessTime(OP_FLAGS_ALL))
                    .isEqualTo(beforeUpdate.features[FEATURE_2]!!.getLastAccessTime(OP_FLAGS_ALL))

            // Feature 7 inherits from feature 4 and 5. 5 was noted after 4, hence 4 is removed
            assertThat(afterUpdate.features[FEATURE_7]!!.getLastAccessTime(OP_FLAGS_ALL))
                    .isEqualTo(beforeUpdate.features[FEATURE_5]!!.getLastAccessTime(OP_FLAGS_ALL))
        }
    }

    @Test(expected = AssertionError::class)
    fun cannotInheritFromSelf() {
        installApk("AppWithFeatureInheritingFromSelf.apk")
    }

    @Test(expected = AssertionError::class)
    fun noDuplicateFeatures() {
        installApk("AppWithDuplicateFeature.apk")
    }

    @Test(expected = AssertionError::class)
    fun cannotInheritFromExisting() {
        installApk("AppWithFeatureInheritingFromExisting.apk")
    }

    @Test(expected = AssertionError::class)
    fun cannotInheritFromSameAsOther() {
        installApk("AppWithFeatureInheritingFromSameAsOther.apk")
    }
}