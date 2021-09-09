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

package android.app.cts

import android.app.StatusBarManager
import android.app.stubs.TestTileService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.service.quicksettings.TileService
import androidx.test.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor

/**
 * Test that the request fails in all the expected ways.
 *
 * These tests are for [StatusBarManager.requestAddTileService].
 */
@RunWith(AndroidJUnit4::class)
class RequestTileServiceAddTest {

    companion object {
        private const val LABEL = "label"
    }

    private lateinit var statusBarService: StatusBarManager
    private lateinit var context: Context
    private lateinit var icon: Icon
    private lateinit var executor: StoreExecutor

    @Before
    fun setUp() {
        Assume.assumeTrue(TileService.isQuickSettingsSupported())

        context = InstrumentationRegistry.getTargetContext()
        statusBarService = context.getSystemService(StatusBarManager::class.java)!!

        icon = Icon.createWithResource(context, R.drawable.ic_android)
        executor = StoreExecutor()
    }

    @Test
    fun testRequestBadPackageFails() {
        val componentName = ComponentName("test_pkg", "test_cls")

        val answer = statusBarService.requestAddTileService(
                componentName,
                LABEL,
                icon,
                executor
        ) {}

        assertThat(answer)
                .isEqualTo(StatusBarManager.TILE_ADD_REQUEST_ANSWER_FAILED_MISMATCHED_PACKAGE)
        assertThat(executor.runnables).isEmpty()
    }

    @Test
    fun testRequestBadComponentName() {
        val componentName = ComponentName(context, "test_cls")
        val answer = statusBarService.requestAddTileService(
                componentName,
                LABEL,
                icon,
                executor
        ) {}

        assertThat(answer).isEqualTo(StatusBarManager.TILE_ADD_REQUEST_ANSWER_FAILED_BAD_COMPONENT)
        assertThat(executor.runnables).isEmpty()
    }

    @Test
    fun testDisabledComponent() {
        val componentName = TestTileService.getComponentName()
        context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.SYNCHRONOUS or PackageManager.DONT_KILL_APP
        )

        val answer = statusBarService.requestAddTileService(
                componentName,
                LABEL,
                icon,
                executor
        ) {}

        assertThat(answer).isEqualTo(StatusBarManager.TILE_ADD_REQUEST_ANSWER_FAILED_BAD_COMPONENT)
        assertThat(executor.runnables).isEmpty()
    }

    private class StoreExecutor : Executor {
        var runnables = mutableListOf<Runnable>()

        override fun execute(command: Runnable) {
            runnables.add(command)
        }
    }
}