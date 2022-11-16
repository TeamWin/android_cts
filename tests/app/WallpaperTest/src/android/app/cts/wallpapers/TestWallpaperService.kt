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
package android.app.cts.wallpapers

import android.app.WallpaperColors
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.view.WindowInsets
import com.google.common.truth.Truth.assertWithMessage

/**
 * Wrapper for WallpaperService.
 * This class does not add any logic or change any function.
 * It verifies that the public callback methods from [WallpaperService.Engine]
 * are called by the main thread.
 * The callback methods are the methods overridden by this class.
 *
 * It also includes a few checks to verify that the methods are called in a proper order.
 * For example, many methods should only be called after [WallpaperService.Engine.onSurfaceCreated],
 * which itself should only be called after [WallpaperService.Engine.onCreate].
 */
open class TestWallpaperService : WallpaperService() {

    private val mainThread: Thread = Looper.getMainLooper().thread
    companion object {
        private var assertionError: AssertionError? = null

        /**
         * To be called at the end of tests requiring assertion checks from this class.
         * The first assertion error encountered by this class, if there is any,
         * will be raised when calling this function.
         * We use this to avoid raising errors directly in the callback methods, since errors in
         * callback methods could be raised from the main thread and crash the entire test process.
         */
        fun checkAssertions() {
            assertionError?.let { throw assertionError!! }
            assertionError = null
        }
    }

    override fun onCreateEngine(): Engine {
        assertMainThread()
        return FakeEngine()
    }

    internal inner class FakeEngine : Engine() {
        private var mCreated = false
        private var mSurfaceCreated = false

        private fun draw(holder: SurfaceHolder) {
            val c = holder.lockCanvas()
            c.drawColor(Color.RED)
            holder.unlockCanvasAndPost(c)
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            assertMainThread()
            super.onApplyWindowInsets(insets)
        }

        override fun onCommand(
            action: String,
            x: Int,
            y: Int,
            z: Int,
            extras: Bundle?,
            resultRequested: Boolean
        ): Bundle? {
            assertMainThread()
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        override fun onComputeColors(): WallpaperColors? {
            assertMainThread()
            return super.onComputeColors()
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            assertMainThread()
            assertNotCreated()
            assertSurfaceNotCreated()
            mCreated = true
            super.onCreate(surfaceHolder)
        }

        override fun onDesiredSizeChanged(desiredWidth: Int, desiredHeight: Int) {
            assertMainThread()
            assertCreated()
            assertSurfaceCreated()
            super.onDesiredSizeChanged(desiredWidth, desiredHeight)
        }

        override fun onDestroy() {
            assertMainThread()
            assertCreated()
            mCreated = false
            super.onDestroy()
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            assertMainThread()
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep,
                xPixelOffset, yPixelOffset)
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            assertMainThread()
            assertCreated()
            assertSurfaceCreated()
            super.onSurfaceChanged(holder, format, width, height)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            assertMainThread()
            assertCreated()
            assertSurfaceNotCreated()
            mSurfaceCreated = true
            super.onSurfaceCreated(holder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            assertMainThread()
            assertCreated()
            assertSurfaceCreated()
            super.onSurfaceDestroyed(holder)
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            draw(holder)
            assertMainThread()
            assertCreated()
            assertSurfaceCreated()
            super.onSurfaceRedrawNeeded(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            assertMainThread()
            super.onVisibilityChanged(visible)
        }

        override fun onZoomChanged(zoom: Float) {
            assertMainThread()
            super.onZoomChanged(zoom)
        }

        private fun assertCreated() {
            assertHelper {
                assertWithMessage(
                    "Engine must be created (with onCreate) " +
                    "and not destroyed before calling this function")
                    .that(mCreated).isTrue()
            }
        }

        private fun assertSurfaceCreated() {
            assertHelper {
                assertWithMessage(
                    "Surface must be created (with onSurfaceCreated) " +
                    "and not destroyed before calling this function")
                    .that(mSurfaceCreated).isTrue()
            }
        }

        private fun assertNotCreated() {
            assertHelper {
                assertWithMessage(
                    "Engine must not be created (with onCreate) " +
                    "or must be destroyed before calling this function")
                    .that(mCreated).isFalse()
            }
        }

        private fun assertSurfaceNotCreated() {
            assertHelper {
                assertWithMessage(
                    "Surface must not be created (with onSurfaceCreated) " +
                    "or must be destroyed before calling this function")
                    .that(mSurfaceCreated).isFalse()
            }
        }
    }

    /**
     * Check that the current thread is the main thread
     */
    private fun assertMainThread() {
        assertHelper {
            val callerThread = Thread.currentThread()
            assertWithMessage(
                "Callback methods from WallpaperService.Engine " +
                    "must be called by the main thread; but was called by " + callerThread)
                .that(callerThread).isSameInstanceAs(mainThread)
        }
    }

    /**
     * Run an executable that performs some assertions. If any assertion is raised, and it is the
     * first one raised so far, store it.
     */
    private fun assertHelper(check: Runnable) {
        try {
            check.run()
        } catch (error: AssertionError) {
            assertionError = assertionError ?: error
        }
    }
}
