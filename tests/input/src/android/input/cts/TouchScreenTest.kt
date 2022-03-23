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

package android.input.cts

import android.Manifest
import android.app.ActivityOptions
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.PixelFormat
import android.graphics.PointF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TouchScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var touchScreen: UinputTouchScreen
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var reader: ImageReader
    private lateinit var activity: CaptureEventActivity
    private lateinit var verifier: EventVerifier

    @Before
    fun setUp() {
        createDisplayAndTouchScreen()
        val displayId = virtualDisplay.display.displayId
        val bundle = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
        val intent = Intent(Intent.ACTION_VIEW)
                .setClass(instrumentation.targetContext, CaptureEventActivity::class.java)
                .setFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
        SystemUtil.runWithShellPermissionIdentity({
            activity = instrumentation.startActivitySync(intent, bundle) as CaptureEventActivity
        }, Manifest.permission.INTERNAL_SYSTEM_WINDOW)
        verifier = EventVerifier(activity::getInputEvent)
    }

    @After
    fun tearDown() {
        releaseDisplay()
    }

    private fun createDisplayAndTouchScreen() {
        val displayManager =
                instrumentation.targetContext.getSystemService(DisplayManager::class.java)

        val displayCreated = CountDownLatch(1)
        displayManager.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                displayCreated.countDown()
                displayManager.unregisterDisplayListener(this)
            }
        }, Handler(Looper.getMainLooper()))
        reader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 2)
        virtualDisplay = displayManager.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME, WIDTH, HEIGHT, DENSITY, reader.surface,
                VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH)

        assertTrue(displayCreated.await(5, TimeUnit.SECONDS))

        touchScreen = UinputTouchScreen(instrumentation, virtualDisplay.display)
        assertNotNull(touchScreen)
    }

    fun releaseDisplay() {
        virtualDisplay.release()
        reader.close()
    }

    @Test
    fun testSingleTouch() {
        val pointer = PointF(100f, 100f)

        touchScreen.use {
            // ACTION_DOWN
            touchScreen.sendBtnTouch(true)
            touchScreen.sendDown(0 /*id*/, pointer)
            verifier.assertReceivedDown()

            // ACTION_MOVE
            pointer.offset(1f, 1f)
            touchScreen.sendMove(0 /*id*/, pointer)
            verifier.assertReceivedMove()

            // ACTION_UP
            touchScreen.sendUp(0 /*id*/)
            verifier.assertReceivedUp()
        }
    }

    @Test
    fun testMultiTouch() {
        val pointer1 = PointF(100f, 100f)
        val pointer2 = PointF(150f, 150f)

        touchScreen.use {
            // ACTION_DOWN
            touchScreen.sendBtnTouch(true)
            touchScreen.sendDown(0 /*id*/, pointer1)
            verifier.assertReceivedDown()

            // ACTION_POINTER_DOWN
            touchScreen.sendDown(1 /*id*/, pointer2)
            verifier.assertReceivedPointerDown(1)

            // ACTION_MOVE
            pointer2.offset(1f, 1f)
            touchScreen.sendMove(1 /*id*/, pointer2)
            verifier.assertReceivedMove()

            // ACTION_POINTER_UP
            touchScreen.sendUp(0 /*id*/)
            verifier.assertReceivedPointerUp(0)

            // ACTION_UP
            touchScreen.sendUp(1 /*id*/)
            verifier.assertReceivedUp()
        }
    }

    @Test
    fun testDeviceCancel() {
        val pointer = PointF(100f, 100f)

        touchScreen.use {
            // ACTION_DOWN
            touchScreen.sendBtnTouch(true)
            touchScreen.sendDown(0 /*id*/, pointer)
            verifier.assertReceivedDown()

            // ACTION_MOVE
            pointer.offset(1f, 1f)
            touchScreen.sendMove(0 /*id*/, pointer)
            verifier.assertReceivedMove()

            // ACTION_CANCEL
            touchScreen.sendToolType(0 /*id*/, UinputTouchScreen.MT_TOOL_PALM)
            verifier.assertReceivedCancel()

            // No event
            touchScreen.sendUp(0 /*id*/)
            activity.assertNoEvents()
        }
    }

    /**
     * Check that pointer cancel is received by the activity via uinput device.
     */
    @Test
    fun testDevicePointerCancel() {
        val pointer1 = PointF(100f, 100f)
        val pointer2 = PointF(150f, 150f)

        touchScreen.use {
            // ACTION_DOWN
            touchScreen.sendDown(0 /*id*/, pointer1)
            verifier.assertReceivedDown()

            // ACTION_MOVE
            pointer1.offset(1f, 1f)
            touchScreen.sendMove(0 /*id*/, pointer1)
            verifier.assertReceivedMove()

            // ACTION_POINTER_DOWN(1)
            touchScreen.sendDown(1 /*id*/, pointer2)
            verifier.assertReceivedPointerDown(1)

            // ACTION_POINTER_UP(1) with cancel flag
            touchScreen.sendToolType(1 /*id*/, UinputTouchScreen.MT_TOOL_PALM)
            verifier.assertReceivedPointerCancel(1)

            // ACTION_UP
            touchScreen.sendUp(0 /*id*/)
            verifier.assertReceivedUp()
        }
    }

companion object {
    const val VIRTUAL_DISPLAY_NAME = "CtsVirtualDisplay"
    const val WIDTH = 480
    const val HEIGHT = 800
    const val DENSITY = 160

    /** See [DisplayManager.VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH].  */
    const val VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH = 1 shl 6
}
}
