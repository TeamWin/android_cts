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

package android.security.cts

import android.content.ComponentName
import android.content.Intent
import android.platform.test.annotations.AsbSecurityTest
import android.service.quicksettings.TileService
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.compatibility.common.util.BlockingBroadcastReceiver
import com.android.compatibility.common.util.SystemUtil
import com.android.sts.common.util.StsExtraBusinessLogicTestCase
import org.junit.After
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Bug_300903792 : StsExtraBusinessLogicTestCase() {

    @Before
    fun setUp() {
        assumeTrue(TileService.isQuickSettingsSupported())
        installPackage(TILE_SERVICE_APP_LOCATION)
    }

    @After
    fun tearDown() {
        SystemUtil.runShellCommand(REMOVE_TILE_COMMAND)
        Log.d("TestRunner", "Uninstalling $TILE_SERVICE_PACKAGE")
        uninstallPackage(TILE_SERVICE_PACKAGE)
    }

    @Test
    @AsbSecurityTest(cveBugId = [300903792])
    fun testPocBug_300903792() {
        val context = getInstrumentation().context
        val nullBindingReceiver = BlockingBroadcastReceiver(context, ON_NULL_BINDING)
        // First we add the tile, we should receive a broadcast once it has bound.
        // We expect that the tile will be bound to notify `onTileAdded` and onNullBinding will
        // happen.
        try {
            nullBindingReceiver.register()
            SystemUtil.runShellCommand(ADD_TILE_COMMAND)
            nullBindingReceiver.awaitForBroadcast(ONE_MINUTE_IN_MILLIS)
        } finally {
            nullBindingReceiver.unregisterQuietly()
        }

        val backgroundActivityStarted =
            BlockingBroadcastReceiver(context, BACKGROUND_ACTIVITY_STARTED)
        // We start an activity that will schedule another activity to start and then go home
        // (putting itself in the background). We expect that the backgroundActivity is not started,
        // but if the security issue is not patched, it will.
        try {
            backgroundActivityStarted.register()
            context.startActivity(
                Intent()
                    .setComponent(ACTIVITY_STARTER_COMPONENT)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            val intent = backgroundActivityStarted.awaitForBroadcast(ONE_MINUTE_IN_MILLIS)
            if (intent != null) {
                fail("Vulnerable to b/300903792! Activity started from the background")
            }
        } finally {
            backgroundActivityStarted.unregisterQuietly()
        }
    }

    private fun installPackage(apkPath: String) {
        val result = SystemUtil.runShellCommand("pm install -r $apkPath")
        Log.d("security", "Install result: $result")
    }

    private fun uninstallPackage(packageName: String) {
        SystemUtil.runShellCommand("pm uninstall $packageName")
    }

    companion object {
        private const val TILE_SERVICE_APP_LOCATION =
            "/data/local/tmp/cts/security/TileServiceNullBindingTestApp.apk"
        private const val TILE_SERVICE_PACKAGE = "android.security.cts.tileservice"
        private const val TILE_SERVICE_NAME = ".NullBindingTileService"
        private const val ACTIVITY_STARTER_NAME = ".ActivityStarterActivity"

        private val TILE_SERVICE_COMPONENT =
            ComponentName.createRelative(TILE_SERVICE_PACKAGE, TILE_SERVICE_NAME)
        private val ACTIVITY_STARTER_COMPONENT =
            ComponentName.createRelative(TILE_SERVICE_PACKAGE, ACTIVITY_STARTER_NAME)

        private const val BACKGROUND_ACTIVITY_STARTED =
            "android.security.cts.tileservice.BACKGROUND_ACTIVITY_STARTED"

        private const val ON_NULL_BINDING = "android.security.cts.tileservice.ON_NULL_BINDING"

        private val ADD_TILE_COMMAND =
            "cmd statusbar add-tile ${TILE_SERVICE_COMPONENT.flattenToString()}"
        private val REMOVE_TILE_COMMAND =
            "cmd statusbar remove-tile ${TILE_SERVICE_COMPONENT.flattenToString()}"

        private const val ONE_MINUTE_IN_MILLIS = 60 * 1000L
    }
}
