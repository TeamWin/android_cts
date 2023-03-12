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
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PointF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.input.InputManager
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.server.wm.WindowManagerStateHelper
import android.util.Size
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class TouchScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var touchScreen: UinputTouchScreen
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var reader: ImageReader
    private lateinit var activity: CaptureEventActivity
    private lateinit var verifier: EventVerifier

    private val displayId: Int get() = virtualDisplay.display.displayId

    @Before
    fun setUp() {
        assumeTrue(supportsMultiDisplay())
        createDisplayAndTouchScreen()

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
        if (!supportsMultiDisplay()) {
            return
        }
        releaseDisplayAndTouchScreen()
    }

    private fun supportsMultiDisplay(): Boolean {
        return instrumentation.targetContext.packageManager
                .hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)
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
                VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH or VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT)

        assertTrue(displayCreated.await(5, TimeUnit.SECONDS))

        touchScreen = UinputTouchScreen(instrumentation, virtualDisplay.display,
                Size(WIDTH, HEIGHT))
        assertNotNull(touchScreen)
    }

    private fun releaseDisplayAndTouchScreen() {
        touchScreen.close()
        virtualDisplay.release()
        reader.close()
    }

    @Test
    fun testHostUsiVersionIsNull() {
        assertNull(
            instrumentation.targetContext.getSystemService(InputManager::class.java)
                .getHostUsiVersion(virtualDisplay.display))
    }

    @Test
    fun testSingleTouch() {
        val pointer = Point(100, 100)

        // ACTION_DOWN
        touchScreen.sendBtnTouch(true)
        touchScreen.sendDown(0 /*id*/, pointer)
        verifier.assertReceivedDown()

        // ACTION_MOVE
        pointer.offset(1, 1)
        touchScreen.sendMove(0 /*id*/, pointer)
        verifier.assertReceivedMove()

        // ACTION_UP
        touchScreen.sendBtnTouch(false)
        touchScreen.sendUp(0 /*id*/)
        verifier.assertReceivedUp()
    }

    @Test
    fun testMultiTouch() {
        val pointer1 = Point(100, 100)
        val pointer2 = Point(150, 150)

        // ACTION_DOWN
        touchScreen.sendBtnTouch(true)
        touchScreen.sendDown(0 /*id*/, pointer1)
        verifier.assertReceivedDown()

        // ACTION_POINTER_DOWN
        touchScreen.sendDown(1 /*id*/, pointer2)
        verifier.assertReceivedPointerDown(1)

        // ACTION_MOVE
        pointer2.offset(1, 1)
        touchScreen.sendMove(1 /*id*/, pointer2)
        verifier.assertReceivedMove()

        // ACTION_POINTER_UP
        touchScreen.sendUp(0 /*id*/)
        verifier.assertReceivedPointerUp(0)

        // ACTION_UP
        touchScreen.sendBtnTouch(false)
        touchScreen.sendUp(1 /*id*/)
        verifier.assertReceivedUp()
    }

    @Test
    fun testDeviceCancel() {
        val pointer = Point(100, 100)

        // ACTION_DOWN
        touchScreen.sendBtnTouch(true)
        touchScreen.sendDown(0 /*id*/, pointer)
        verifier.assertReceivedDown()

        // ACTION_MOVE
        pointer.offset(1, 1)
        touchScreen.sendMove(0 /*id*/, pointer)
        verifier.assertReceivedMove()

        // ACTION_CANCEL
        touchScreen.sendToolType(0 /*id*/, UinputTouchScreen.MT_TOOL_PALM)
        verifier.assertReceivedCancel()

        // No event
        touchScreen.sendBtnTouch(false)
        touchScreen.sendUp(0 /*id*/)
        activity.assertNoEvents()
    }

    /**
     * Check that pointer cancel is received by the activity via uinput device.
     */
    @Test
    fun testDevicePointerCancel() {
        val pointer1 = Point(100, 100)
        val pointer2 = Point(150, 150)

        // ACTION_DOWN
        touchScreen.sendBtnTouch(true)
        touchScreen.sendDown(0 /*id*/, pointer1)
        verifier.assertReceivedDown()

        // ACTION_MOVE
        pointer1.offset(1, 1)
        touchScreen.sendMove(0 /*id*/, pointer1)
        verifier.assertReceivedMove()

        // ACTION_POINTER_DOWN(1)
        touchScreen.sendDown(1 /*id*/, pointer2)
        verifier.assertReceivedPointerDown(1)

        // ACTION_POINTER_UP(1) with cancel flag
        touchScreen.sendToolType(1 /*id*/, UinputTouchScreen.MT_TOOL_PALM)
        verifier.assertReceivedPointerCancel(1)

        // ACTION_UP
        touchScreen.sendBtnTouch(false)
        touchScreen.sendUp(0 /*id*/)
        verifier.assertReceivedUp()
    }

    @Test
    fun testTouchScreenPrecisionOrientation0() {
        runInDisplayOrientation(ORIENTATION_0) {
            verifyTapsOnFourCorners(
                arrayOf(
                    PointF(0f, 0f),
                    PointF(WIDTH - 1f, 0f),
                    PointF(WIDTH - 1f, HEIGHT - 1f),
                    PointF(0f, HEIGHT - 1f),
                )
            )
        }
    }

    @Test
    fun testTouchScreenPrecisionOrientation90() {
        runInDisplayOrientation(ORIENTATION_90) {
            verifyTapsOnFourCorners(
                arrayOf(
                    PointF(0f, WIDTH - 1f),
                    PointF(0f, 0f),
                    PointF(HEIGHT - 1f, 0f),
                    PointF(HEIGHT - 1f, WIDTH - 1f),
                )
            )
        }
    }

    @Test
    fun testTouchScreenPrecisionOrientation180() {
        runInDisplayOrientation(ORIENTATION_180) {
            verifyTapsOnFourCorners(
                arrayOf(
                    PointF(WIDTH - 1f, HEIGHT - 1f),
                    PointF(0f, HEIGHT - 1f),
                    PointF(0f, 0f),
                    PointF(WIDTH - 1f, 0f),
                )
            )
        }
    }

    @Test
    fun testTouchScreenPrecisionOrientation270() {
        runInDisplayOrientation(ORIENTATION_270) {
            verifyTapsOnFourCorners(
                arrayOf(
                    PointF(HEIGHT - 1f, 0f),
                    PointF(HEIGHT - 1f, WIDTH - 1f),
                    PointF(0f, WIDTH - 1f),
                    PointF(0f, 0f),
                )
            )
        }
    }

    // Verifies that each of the four corners of the touch screen (lt, rt, rb, lb) map to the
    // given four points by tapping on the corners in order and asserting the location of the
    // received events match the provided values.
    private fun verifyTapsOnFourCorners(expectedPoints: Array<PointF>) {
        for (i in 0 until 4) {
            touchScreen.sendBtnTouch(true)
            touchScreen.sendDown(0 /*id*/, CORNERS[i])
            verifier.assertReceivedDown(expectedPoints[i])

            touchScreen.sendBtnTouch(false)
            touchScreen.sendUp(0 /*id*/)
            verifier.assertReceivedUp()
        }
    }

    private fun runInDisplayOrientation(orientation: Int, runnable: () -> Unit) {
        val initialUserRotation =
                SystemUtil.runShellCommandOrThrow("wm user-rotation -d $displayId")!!
        SystemUtil.runShellCommandOrThrow("wm user-rotation -d $displayId lock $orientation")
        // Ensure the orientation change has propagated.
        WindowManagerStateHelper().waitForAppTransitionIdleOnDisplay(displayId)
        instrumentation.uiAutomation.syncInputTransactions()
        instrumentation.waitForIdleSync()

        try {
            runnable()
        } finally {
            SystemUtil.runShellCommandOrThrow("wm user-rotation -d $displayId $initialUserRotation")
        }
    }

    companion object {
        const val VIRTUAL_DISPLAY_NAME = "CtsTouchScreenTestVirtualDisplay"
        const val WIDTH = 480
        const val HEIGHT = 800
        const val DENSITY = 160

        const val ORIENTATION_0 = Surface.ROTATION_0
        const val ORIENTATION_90 = Surface.ROTATION_90
        const val ORIENTATION_180 = Surface.ROTATION_180
        const val ORIENTATION_270 = Surface.ROTATION_270

        // The four corners of the touchscreen: lt, rt, rb, lb
        val CORNERS = arrayOf(
            Point(0, 0),
            Point(WIDTH - 1, 0),
            Point(WIDTH - 1, HEIGHT - 1),
            Point(0, HEIGHT - 1),
        )

        /** See [DisplayManager.VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH].  */
        const val VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH = 1 shl 6

        /** See [DisplayManager.VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT].  */
        const val VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT = 1 shl 7
    }
}
