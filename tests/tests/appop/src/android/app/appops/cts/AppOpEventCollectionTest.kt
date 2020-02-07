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
import android.app.AppOpsManager.MAX_PRIORITY_UID_STATE
import android.app.AppOpsManager.MIN_PRIORITY_UID_STATE
import android.app.AppOpsManager.OPSTR_WIFI_SCAN
import android.app.AppOpsManager.OP_FLAGS_ALL
import android.app.AppOpsManager.OP_FLAG_SELF
import android.app.AppOpsManager.OP_FLAG_TRUSTED_PROXIED
import android.app.AppOpsManager.OP_FLAG_TRUSTED_PROXY
import android.app.AppOpsManager.OP_FLAG_UNTRUSTED_PROXIED
import android.app.AppOpsManager.UID_STATE_TOP
import android.content.Intent
import android.content.Intent.ACTION_APPLICATION_PREFERENCES
import android.os.SystemClock
import android.platform.test.annotations.AppModeFull
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.Thread.sleep

class AppOpEventCollectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val appOpsManager = context.getSystemService(AppOpsManager::class.java)

    private val myUid = android.os.Process.myUid()
    private val myPackage = context.packageName

    // Start an activity to make sure this app counts as being in the foreground
    @Rule
    @JvmField
    var activityRule = ActivityTestRule(UidStateForceActivity::class.java)

    @Before
    fun wakeScreenUp() {
        val uiDevice = UiDevice.getInstance(instrumentation)
        uiDevice.wakeUp()
        uiDevice.executeShellCommand("wm dismiss-keyguard")
    }

    @Before
    fun makeSureTimeStampsAreDistinct() {
        sleep(1)
    }

    @Test
    fun switchUidStateWhileOpsAreRunning() {
        val before = System.currentTimeMillis()

        // Start twice to also test switching uid state with nested starts running
        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, null, null)
        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, null, null)

        val beforeUidChange = System.currentTimeMillis()
        sleep(1)

        try {
            activityRule.activity.finish()
            UidStateForceActivity.waitForDestroyed()
        } finally {
            appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, null)
            appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, null)
        }

        // The system remembers the time before and after the uid change as separate events
        val opEntry = getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!
        assertThat(opEntry.getLastAccessTime(MAX_PRIORITY_UID_STATE, UID_STATE_TOP, OP_FLAGS_ALL))
                .isIn(before..beforeUidChange)
        assertThat(opEntry.getLastAccessTime(UID_STATE_TOP + 1, MIN_PRIORITY_UID_STATE,
                OP_FLAGS_ALL)).isAtLeast(beforeUidChange)
    }

    @Test
    fun noteWithFeatureAndCheckOpEntries() {
        val before = System.currentTimeMillis()
        appOpsManager.noteOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_FEATURE_ID, null)
        val after = System.currentTimeMillis()

        val opEntry = getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!
        val featureOpEntry = opEntry.features[TEST_FEATURE_ID]!!

        assertThat(featureOpEntry.getLastAccessForegroundTime(OP_FLAG_SELF)).isIn(before..after)

        // Access should should also show up in the combined state for all op-flags
        assertThat(featureOpEntry.getLastAccessForegroundTime(OP_FLAGS_ALL)).isIn(before..after)
        assertThat(opEntry.getLastAccessTime(OP_FLAGS_ALL)).isIn(before..after)

        // Foreground access should should also show up in the combined state for fg and bg
        assertThat(featureOpEntry.getLastAccessTime(OP_FLAG_SELF)).isIn(before..after)
        assertThat(opEntry.getLastAccessTime(OP_FLAG_SELF)).isIn(before..after)

        // The access was in foreground, hence there is no background access
        assertThat(featureOpEntry.getLastBackgroundDuration(OP_FLAG_SELF)).isLessThan(before)
        assertThat(opEntry.getLastBackgroundDuration(OP_FLAG_SELF)).isLessThan(before)

        // The access was for a feature, hence there is no access for the default feature
        if (null in opEntry.features) {
            assertThat(opEntry.features[null]!!.getLastAccessForegroundTime(OP_FLAG_SELF))
                    .isLessThan(before)
        }

        // The access does not show up for other op-flags
        assertThat(featureOpEntry.getLastAccessForegroundTime(
                OP_FLAGS_ALL and OP_FLAG_SELF.inv())).isLessThan(before)
        assertThat(opEntry.getLastAccessForegroundTime(
                OP_FLAGS_ALL and OP_FLAG_SELF.inv())).isLessThan(before)
    }

    @Test
    fun noteSelfAndTrustedAccessAndCheckOpEntries() {
        val before = System.currentTimeMillis()

        // Using the shell identity causes a trusted proxy note
        runWithShellPermissionIdentity {
            appOpsManager.noteOp(OPSTR_WIFI_SCAN, myUid, myPackage, null, null)
        }
        val afterTrusted = System.currentTimeMillis()

        // Make sure timestamps are distinct
        sleep(1)

        // self note
        appOpsManager.noteOp(OPSTR_WIFI_SCAN, myUid, myPackage, null, null)
        val after = System.currentTimeMillis()

        val opEntry = getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!
        val featureOpEntry = opEntry.features[null]!!

        assertThat(featureOpEntry.getLastAccessTime(OP_FLAG_TRUSTED_PROXY))
                .isIn(before..afterTrusted)
        assertThat(featureOpEntry.getLastAccessTime(OP_FLAG_SELF)).isIn(afterTrusted..after)
        assertThat(opEntry.getLastAccessTime(OP_FLAG_TRUSTED_PROXY)).isIn(before..afterTrusted)
        assertThat(opEntry.getLastAccessTime(OP_FLAG_SELF)).isIn(afterTrusted..after)

        // When asked for any flags, the second access overrides the first
        assertThat(featureOpEntry.getLastAccessTime(OP_FLAGS_ALL)).isIn(afterTrusted..after)
        assertThat(opEntry.getLastAccessTime(OP_FLAGS_ALL)).isIn(afterTrusted..after)
    }

    @Test
    fun noteForTwoFeaturesCheckOpEntries() {
        val before = System.currentTimeMillis()
        appOpsManager.noteOp(OPSTR_WIFI_SCAN, myUid, myPackage, "firstFeature", null)
        val afterFirst = System.currentTimeMillis()

        // Make sure timestamps are distinct
        sleep(1)

        // self note
        appOpsManager.noteOp(OPSTR_WIFI_SCAN, myUid, myPackage, "secondFeature", null)
        val after = System.currentTimeMillis()

        val opEntry = getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!
        val firstFeatureOpEntry = opEntry.features["firstFeature"]!!
        val secondFeatureOpEntry = opEntry.features["secondFeature"]!!

        assertThat(firstFeatureOpEntry.getLastAccessTime(OP_FLAG_SELF)).isIn(before..afterFirst)
        assertThat(secondFeatureOpEntry.getLastAccessTime(OP_FLAG_SELF)).isIn(afterFirst..after)

        // When asked for any feature, the second access overrides the first
        assertThat(opEntry.getLastAccessTime(OP_FLAG_SELF)).isIn(afterFirst..after)
    }

    @AppModeFull(reason = "instant apps cannot see other packages")
    @Test
    fun noteFromTwoProxiesAndVerifyProxyInfo() {
        // Find another app to blame
        val otherAppInfo = context.packageManager
                .resolveActivity(Intent(ACTION_APPLICATION_PREFERENCES), 0)!!
                .activityInfo.applicationInfo
        val otherPkg = otherAppInfo.packageName
        val otherUid = otherAppInfo.uid

        // Using the shell identity causes a trusted proxy note
        runWithShellPermissionIdentity {
            context.createFeatureContext("firstProxyFeature")
                    .getSystemService(AppOpsManager::class.java)
                    .noteProxyOp(OPSTR_WIFI_SCAN, otherPkg, otherUid, null, null)
        }

        // Make sure timestamps are distinct
        sleep(1)

        // untrusted proxy note
        context.createFeatureContext("secondProxyFeature")
                .getSystemService(AppOpsManager::class.java)
                .noteProxyOp(OPSTR_WIFI_SCAN, otherPkg, otherUid, null, null)

        val opEntry = getOpEntry(otherUid, otherPkg, OPSTR_WIFI_SCAN)!!
        val featureOpEntry = opEntry.features[null]!!

        assertThat(featureOpEntry.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)?.packageName)
                .isEqualTo(myPackage)
        assertThat(opEntry.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)?.packageName)
                .isEqualTo(myPackage)
        assertThat(featureOpEntry.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)?.uid).isEqualTo(myUid)
        assertThat(opEntry.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)?.uid).isEqualTo(myUid)

        assertThat(featureOpEntry.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)?.packageName)
                .isEqualTo(myPackage)
        assertThat(opEntry.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)?.packageName)
                .isEqualTo(myPackage)
        assertThat(featureOpEntry.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)?.uid).isEqualTo(myUid)
        assertThat(opEntry.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)?.uid).isEqualTo(myUid)

        assertThat(featureOpEntry.getLastProxyInfo(OP_FLAG_TRUSTED_PROXIED)?.featureId)
                .isEqualTo("firstProxyFeature")
        assertThat(featureOpEntry.getLastProxyInfo(OP_FLAG_UNTRUSTED_PROXIED)?.featureId)
                .isEqualTo("secondProxyFeature")

        // If asked for all op-flags the second feature overrides the first
        assertThat(featureOpEntry.getLastProxyInfo(OP_FLAGS_ALL)?.featureId)
                .isEqualTo("secondProxyFeature")
    }

    @Test
    fun startStopMultipleOpsAndVerifyIsRunning() {
        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, null, null)

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(features[null]!!.isRunning).isTrue()
            features[TEST_FEATURE_ID]?.let { assertThat(it.isRunning).isFalse() }
            assertThat(isRunning).isTrue()
        }

        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_FEATURE_ID, null)

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(features[null]!!.isRunning).isTrue()
            assertThat(features[TEST_FEATURE_ID]!!.isRunning).isTrue()
            assertThat(isRunning).isTrue()
        }

        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_FEATURE_ID, null)

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(features[null]!!.isRunning).isTrue()
            assertThat(features[TEST_FEATURE_ID]!!.isRunning).isTrue()
            assertThat(isRunning).isTrue()
        }

        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_FEATURE_ID)

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(features[null]!!.isRunning).isTrue()
            assertThat(features[TEST_FEATURE_ID]!!.isRunning).isTrue()
            assertThat(isRunning).isTrue()
        }

        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_FEATURE_ID)

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(features[null]!!.isRunning).isTrue()
            assertThat(features[TEST_FEATURE_ID]!!.isRunning).isFalse()
            assertThat(isRunning).isTrue()
        }

        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, null)

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(features[null]!!.isRunning).isFalse()
            assertThat(features[TEST_FEATURE_ID]!!.isRunning).isFalse()
            assertThat(isRunning).isFalse()
        }
    }

    @Test
    fun startStopMultipleOpsAndVerifyLastAccess() {
        val beforeNullFeatureStart = System.currentTimeMillis()
        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, null, null)
        val afterNullFeatureStart = System.currentTimeMillis()

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(features[null]!!.getLastAccessTime(OP_FLAGS_ALL))
                    .isIn(beforeNullFeatureStart..afterNullFeatureStart)
            features[TEST_FEATURE_ID]?.let {
                assertThat(it.getLastAccessTime(OP_FLAGS_ALL)).isAtMost(beforeNullFeatureStart)
            }
            assertThat(getLastAccessTime(OP_FLAGS_ALL))
                    .isIn(beforeNullFeatureStart..afterNullFeatureStart)
        }

        val beforeFirstFeatureStart = System.currentTimeMillis()
        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_FEATURE_ID, null)
        val afterFirstFeatureStart = System.currentTimeMillis()

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(features[null]!!.getLastAccessTime(OP_FLAGS_ALL))
                    .isIn(beforeNullFeatureStart..afterNullFeatureStart)
            assertThat(features[TEST_FEATURE_ID]!!.getLastAccessTime(OP_FLAGS_ALL))
                    .isIn(beforeFirstFeatureStart..afterFirstFeatureStart)
            assertThat(getLastAccessTime(OP_FLAGS_ALL))
                    .isIn(beforeFirstFeatureStart..afterFirstFeatureStart)
        }

        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_FEATURE_ID, null)

        // Nested startOps do _not_ count as another access
        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(features[null]!!.getLastAccessTime(OP_FLAGS_ALL))
                    .isIn(beforeNullFeatureStart..afterNullFeatureStart)
            assertThat(features[TEST_FEATURE_ID]!!.getLastAccessTime(OP_FLAGS_ALL))
                    .isIn(beforeFirstFeatureStart..afterFirstFeatureStart)
            assertThat(getLastAccessTime(OP_FLAGS_ALL))
                    .isIn(beforeFirstFeatureStart..afterFirstFeatureStart)
        }

        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_FEATURE_ID)
        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_FEATURE_ID)
        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, null)
    }

    @Test
    fun startStopMultipleOpsAndVerifyDuration() {
        val beforeNullFeatureStart = SystemClock.elapsedRealtime()
        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, null, null)
        val afterNullFeatureStart = SystemClock.elapsedRealtime()

        run {
            val beforeGetOp = SystemClock.elapsedRealtime()
            with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
                val afterGetOp = SystemClock.elapsedRealtime()

                assertThat(features[null]!!.getLastDuration(OP_FLAGS_ALL))
                        .isIn(beforeGetOp - afterNullFeatureStart
                                ..afterGetOp - beforeNullFeatureStart)
                assertThat(getLastDuration(OP_FLAGS_ALL))
                        .isIn(beforeGetOp - afterNullFeatureStart
                                ..afterGetOp - beforeNullFeatureStart)
            }
        }

        val beforeFeatureStart = SystemClock.elapsedRealtime()
        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_FEATURE_ID, null)
        val afterFeatureStart = SystemClock.elapsedRealtime()

        run {
            val beforeGetOp = SystemClock.elapsedRealtime()
            with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
                val afterGetOp = SystemClock.elapsedRealtime()

                assertThat(features[null]!!.getLastDuration(OP_FLAGS_ALL))
                        .isIn(beforeGetOp - afterNullFeatureStart
                                ..afterGetOp - beforeNullFeatureStart)
                assertThat(features[TEST_FEATURE_ID]!!.getLastDuration(OP_FLAGS_ALL))
                        .isIn(beforeGetOp - afterFeatureStart..afterGetOp - beforeFeatureStart)

                // The last duration is the duration of the last started feature
                assertThat(getLastDuration(OP_FLAGS_ALL))
                        .isIn(beforeGetOp - afterFeatureStart..afterGetOp - beforeFeatureStart)
            }
        }

        appOpsManager.startOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_FEATURE_ID, null)

        // Nested startOps do _not_ start another duration counting, hence the nested
        // startOp and finishOp calls have no affect
        run {
            val beforeGetOp = SystemClock.elapsedRealtime()
            with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
                val afterGetOp = SystemClock.elapsedRealtime()

                assertThat(features[null]!!.getLastDuration(OP_FLAGS_ALL))
                        .isIn(beforeGetOp - afterNullFeatureStart
                                ..afterGetOp - beforeNullFeatureStart)
                assertThat(features[TEST_FEATURE_ID]!!.getLastDuration(OP_FLAGS_ALL))
                        .isIn(beforeGetOp - afterFeatureStart..afterGetOp - beforeFeatureStart)
                assertThat(getLastDuration(OP_FLAGS_ALL))
                        .isIn(beforeGetOp - afterFeatureStart..afterGetOp - beforeFeatureStart)
            }
        }

        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_FEATURE_ID)

        run {
            val beforeGetOp = SystemClock.elapsedRealtime()
            with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
                val afterGetOp = SystemClock.elapsedRealtime()

                assertThat(features[null]!!.getLastDuration(OP_FLAGS_ALL))
                        .isIn(beforeGetOp - afterNullFeatureStart
                                ..afterGetOp - beforeNullFeatureStart)
                assertThat(features[TEST_FEATURE_ID]!!.getLastDuration(OP_FLAGS_ALL))
                        .isIn(beforeGetOp - afterFeatureStart..afterGetOp - beforeFeatureStart)
                assertThat(getLastDuration(OP_FLAGS_ALL))
                        .isIn(beforeGetOp - afterFeatureStart..afterGetOp - beforeFeatureStart)
            }
        }

        val beforeFeatureStop = SystemClock.elapsedRealtime()
        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, TEST_FEATURE_ID)
        val afterFeatureStop = SystemClock.elapsedRealtime()

        run {
            val beforeGetOp = SystemClock.elapsedRealtime()
            with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
                val afterGetOp = SystemClock.elapsedRealtime()

                assertThat(features[null]!!.getLastDuration(OP_FLAGS_ALL))
                        .isIn(beforeGetOp - afterNullFeatureStart
                                ..afterGetOp - beforeNullFeatureStart)
                assertThat(features[TEST_FEATURE_ID]!!.getLastDuration(OP_FLAGS_ALL))
                        .isIn(beforeFeatureStop - afterFeatureStart
                                ..afterFeatureStop - beforeFeatureStart)
                assertThat(getLastDuration(OP_FLAGS_ALL))
                        .isIn(beforeFeatureStop - afterFeatureStart
                                ..afterFeatureStop - beforeFeatureStart)
            }
        }

        val beforeNullFeatureStop = SystemClock.elapsedRealtime()
        appOpsManager.finishOp(OPSTR_WIFI_SCAN, myUid, myPackage, null)
        val afterNullFeatureStop = SystemClock.elapsedRealtime()

        with(getOpEntry(myUid, myPackage, OPSTR_WIFI_SCAN)!!) {
            assertThat(features[null]!!.getLastDuration(OP_FLAGS_ALL))
                    .isIn(beforeNullFeatureStop - afterNullFeatureStart
                            ..afterNullFeatureStop - beforeNullFeatureStart)
            assertThat(features[TEST_FEATURE_ID]!!.getLastDuration(OP_FLAGS_ALL))
                    .isIn(beforeFeatureStop - afterFeatureStart
                            ..afterFeatureStop - beforeFeatureStart)
            assertThat(getLastDuration(OP_FLAGS_ALL))
                    .isIn(beforeFeatureStop - afterFeatureStart
                            ..afterFeatureStop - beforeFeatureStart)
        }
    }
}
