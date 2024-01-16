/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.view.cts.surfacevalidator;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowVisible;
import static android.view.WindowInsets.Type.statusBars;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Messenger;
import android.provider.Settings;
import android.server.wm.settings.SettingsSession;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.PointerIcon;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.FrameLayout;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.rules.TestName;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CapturedActivity extends Activity {
    public static class TestResult {
        public int passFrames;
        public int failFrames;
        public final SparseArray<Bitmap> failures = new SparseArray<>();
    }

    private static class ImmersiveConfirmationSetting extends SettingsSession<String> {
        ImmersiveConfirmationSetting() {
            super(Settings.Secure.getUriFor(
                Settings.Secure.IMMERSIVE_MODE_CONFIRMATIONS),
                Settings.Secure::getString, Settings.Secure::putString);
        }
    }

    private ImmersiveConfirmationSetting mSettingsSession;

    private static final String TAG = "CapturedActivity";
    private static final int PERMISSION_CODE = 1;
    private MediaProjectionManager mProjectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;

    private SurfacePixelValidator2 mSurfacePixelValidator;

    private static final int PERMISSION_DIALOG_WAIT_MS = 1000;
    private static final int RETRY_COUNT = 2;

    private static final long START_CAPTURE_DELAY_MS = 4000;

    private static final long WAIT_TIMEOUT_S = 5L * HW_TIMEOUT_MULTIPLIER;

    private static final String ACCEPT_RESOURCE_ID = "android:id/button1";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String SPINNER_RESOURCE_ID =
            SYSTEM_UI_PACKAGE + ":id/screen_share_mode_spinner";
    private static final String ENTIRE_SCREEN_STRING_RES_NAME =
            "screen_share_permission_dialog_option_entire_screen";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private volatile boolean mOnEmbedded;
    private volatile boolean mOnWatch;
    private CountDownLatch mMediaProjectionCreatedLatch;

    private final Point mLogicalDisplaySize = new Point();
    private AtomicBoolean mIsSharingScreenDenied;

    private int mResultCode;
    private Intent mResultData;

    private FrameLayout mParentLayout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mIsSharingScreenDenied = new AtomicBoolean(false);
        final PackageManager packageManager = getPackageManager();
        mOnWatch = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH);
        if (mOnWatch) {
            // Don't try and set up test/capture infrastructure - they're not supported
            return;
        }

        mParentLayout = new FrameLayout(this);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        setContentView(mParentLayout, layoutParams);

        // Embedded devices are significantly slower, and are given
        // longer duration to capture the expected number of frames
        mOnEmbedded = packageManager.hasSystemFeature(PackageManager.FEATURE_EMBEDDED);

        mSettingsSession = new ImmersiveConfirmationSetting();
        mSettingsSession.set("confirmed");

        WindowInsetsController windowInsetsController = getWindow().getInsetsController();
        windowInsetsController.hide(
                WindowInsets.Type.navigationBars() | WindowInsets.Type.statusBars());
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        getWindow().setAttributes(params);
        getWindow().setDecorFitsSystemWindows(false);

        // Set the NULL pointer icon so that it won't obstruct the captured image.
        getWindow().getDecorView().setPointerIcon(
                PointerIcon.getSystemIcon(this, PointerIcon.TYPE_NULL));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        mProjectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        mMediaProjectionCreatedLatch = new CountDownLatch(1);

        KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
        if (keyguardManager != null) {
            keyguardManager.requestDismissKeyguard(this, null);
        }

        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), PERMISSION_CODE);
    }

    public void setLogicalDisplaySize(Point logicalDisplaySize) {
        mLogicalDisplaySize.set(logicalDisplaySize.x, logicalDisplaySize.y);
    }

    /** The permission dialog will be auto-opened by the activity - find it and accept */
    public boolean dismissPermissionDialog() {
        // Ensure the device is initialized before interacting with any UI elements.
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        final boolean isWatch = getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
        if (!isWatch) {
            // if not testing on a watch device, then we need to select the entire screen option
            // before pressing "Start recording" button.
            if (!selectEntireScreenOption()) {
                Log.e(TAG, "Couldn't select entire screen option");
            }
        }
        return pressStartRecording(isWatch);
    }

    private boolean selectEntireScreenOption() {
        UiObject2 spinner = waitForObject(By.res(SPINNER_RESOURCE_ID));
        if (spinner == null) {
            Log.e(TAG, "Couldn't find spinner to select projection mode");
            return false;
        }
        spinner.click();

        UiObject2 entireScreenOption = waitForObject(By.text(getEntireScreenString()));
        if (entireScreenOption == null) {
            Log.e(TAG, "Couldn't find entire screen option");
            return false;
        }
        entireScreenOption.click();
        return true;
    }

    private String getEntireScreenString() {
        Resources sysUiResources;
        try {
            sysUiResources = getPackageManager().getResourcesForApplication(SYSTEM_UI_PACKAGE);
        } catch (NameNotFoundException e) {
            return null;
        }
        int resourceId =
                sysUiResources.getIdentifier(
                        ENTIRE_SCREEN_STRING_RES_NAME, /* defType= */ "string", SYSTEM_UI_PACKAGE);
        return sysUiResources.getString(resourceId);
    }

    private boolean pressStartRecording(boolean isWatch) {
        if (isWatch) {
            scrollToStartRecordingButton();
        }
        UiObject2 startRecordingButton = waitForObject(By.res(ACCEPT_RESOURCE_ID));
        if (startRecordingButton == null) {
            Log.e(TAG, "Couldn't find start recording button");
            return false;
        } else {
            Log.d(TAG, "found permission dialog after searching all windows, clicked");
            startRecordingButton.click();
            return true;
        }
    }

    /** When testing on a small screen device, scrolls to a Start Recording button. */
    private void scrollToStartRecordingButton() {
        // Scroll down the dialog; on a device with a small screen the elements may not be visible.
        final UiScrollable scrollable = new UiScrollable(new UiSelector().scrollable(true));
        try {
            if (!scrollable.scrollIntoView(new UiSelector().resourceId(ACCEPT_RESOURCE_ID))) {
                Log.e(TAG, "Didn't find " + ACCEPT_RESOURCE_ID + " when scrolling");
                return;
            }
            Log.d(TAG, "This is a watch; we finished scrolling down to the ui elements");
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "This is a watch, but there was no scrolling (UI may not be scrollable");
        }
    }

    private UiObject2 waitForObject(BySelector selector) {
        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        return uiDevice.wait(Until.findObject(selector), PERMISSION_DIALOG_WAIT_MS);
    }

    /**
     * Request to start a foreground service with type "mediaProjection",
     * it's free to run in either the same process or a different process in the package;
     * passing a messenger object to send signal back when the foreground service is up.
     */
    private void startMediaProjectionService() {
        final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper(), msg -> {
            switch (msg.what) {
                case LocalMediaProjectionService.MSG_START_FOREGROUND_DONE:
                    createMediaProjection();
                    return true;
            }
            Log.e(TAG, "Unknown message from the LocalMediaProjectionService: " + msg.what);
            return false;
        }));
        final Intent intent = new Intent(this, LocalMediaProjectionService.class)
                .putExtra(LocalMediaProjectionService.EXTRA_MESSENGER, messenger);
        startForegroundService(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        restoreSettings();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mOnWatch) return;

        if (requestCode != PERMISSION_CODE) {
            throw new IllegalStateException("Unknown request code: " + requestCode);
        }
        mIsSharingScreenDenied.set(resultCode != RESULT_OK);
        if (mIsSharingScreenDenied.get()) {
            Log.e(TAG, "Failed to start screenshare permission Activity result="
                    + mIsSharingScreenDenied.get());

            return;
        }
        Log.d(TAG, "onActivityResult");
        mResultCode = resultCode;
        mResultData = data;
        startMediaProjectionService();
    }

    private void createMediaProjection() {
        mMediaProjection = mProjectionManager.getMediaProjection(mResultCode, mResultData);
        mMediaProjection.registerCallback(new MediaProjectionCallback(), null);
        mMediaProjectionCreatedLatch.countDown();
    }

    public long getCaptureDurationMs() {
        return mOnEmbedded ? 100000 : 50000;
    }

    public TestResult runTest(ISurfaceValidatorTestCase animationTestCase) throws Throwable {
        TestResult testResult = new TestResult();
        Runnable cleanupRunnable = () -> {
            Log.d(TAG, "Stopping capture and ending test case");
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
                mVirtualDisplay = null;
            }

            animationTestCase.end();
            FrameLayout contentLayout = findViewById(android.R.id.content);
            contentLayout.removeAllViews();
            if (mSurfacePixelValidator != null) {
                mSurfacePixelValidator.finish(testResult);
                mSurfacePixelValidator = null;
            }
        };

        try {
            if (mOnWatch) {
                /**
                 * (TODO b/282204025): Legacy reasons why tests are disabled on wear. Investigate
                 * if enabling is now possible.
                 */
                Log.d(TAG, "Skipping test on watch.");
                testResult.passFrames = 1000;
                testResult.failFrames = 0;
                return testResult;
            }

            final int numFramesRequired = animationTestCase.getNumFramesRequired();
            final long maxCapturedDuration = getCaptureDurationMs();

            int count = 0;
            // Sometimes system decides to rotate the permission activity to another orientation
            // right after showing it. This results in: uiautomation thinks that accept button
            // appears, we successfully click it in terms of uiautomation, but nothing happens,
            // because permission activity is already recreated. Thus, we try to click that
            // button multiple times.
            do {
                // There are some cases where the consent dialog isn't shown because the process
                // already has the additional permissions. In that case, we can skip waiting to
                // dismiss the dialog.
                if (mMediaProjectionCreatedLatch.getCount() == 0) {
                    break;
                }

                if (mIsSharingScreenDenied.get()) {
                    throw new IllegalStateException("User denied screen sharing permission.");
                }
                if (dismissPermissionDialog()) {
                    break;
                }
                count++;
                Thread.sleep(1000);
            } while (count <= RETRY_COUNT);

            assertTrue("Failed to create mediaProjection",
                    mMediaProjectionCreatedLatch.await(20L * HW_TIMEOUT_MULTIPLIER,
                            TimeUnit.SECONDS));

            mHandler.post(() -> {
                Log.d(TAG, "Setting up test case");

                // See b/216583939. On some devices, hiding system bars is disabled. In those cases,
                // adjust the area that is rendering the test content to be outside the status bar
                // margins to ensure capturing and comparing frames skips the status bar area.
                Insets statusBarInsets = getWindow()
                        .getDecorView()
                        .getRootWindowInsets()
                        .getInsets(statusBars());
                FrameLayout.LayoutParams layoutParams =
                        (FrameLayout.LayoutParams) mParentLayout.getLayoutParams();
                layoutParams.setMargins(statusBarInsets.left, statusBarInsets.top,
                        statusBarInsets.right, statusBarInsets.bottom);
                mParentLayout.setLayoutParams(layoutParams);

                animationTestCase.start(getApplicationContext(), mParentLayout);
            });

            assertTrue("Failed to wait for animation to start", animationTestCase.waitForReady());
            boolean[] success = new boolean[1];
            SystemUtil.runWithShellPermissionIdentity(() -> {
                success[0] = waitForWindowVisible(mParentLayout);
            }, Manifest.permission.ACCESS_SURFACE_FLINGER);
            assertTrue("Failed to wait for test window to be visible", success[0]);

            CountDownLatch setupLatch = new CountDownLatch(1);
            mHandler.postDelayed(() -> {
                WindowMetrics metrics = getWindowManager().getCurrentWindowMetrics();
                Log.d(TAG, "Starting capture: metrics=" + metrics);

                int densityDpi = (int) (metrics.getDensity() * DisplayMetrics.DENSITY_DEFAULT);

                int testAreaWidth = mParentLayout.getWidth();
                int testAreaHeight = mParentLayout.getHeight();

                Log.d(TAG, "testAreaWidth: " + testAreaWidth
                        + ", testAreaHeight: " + testAreaHeight);

                Rect boundsToCheck = animationTestCase.getBoundsToCheck(mParentLayout);

                if (boundsToCheck.width() < 40 || boundsToCheck.height() < 40) {
                    fail("capture bounds too small to be a fullscreen activity: " + boundsToCheck);
                }

                mSurfacePixelValidator = new SurfacePixelValidator2(mLogicalDisplaySize,
                        boundsToCheck,
                        animationTestCase.getChecker(), numFramesRequired);
                Log.d("MediaProjection", "Size is " + mLogicalDisplaySize
                        + ", bounds are " + boundsToCheck.toShortString());
                mVirtualDisplay = mMediaProjection.createVirtualDisplay("CtsCapturedActivity",
                        mLogicalDisplaySize.x, mLogicalDisplaySize.y,
                        densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        mSurfacePixelValidator.getSurface(),
                        null /*Callbacks*/,
                        null /*Handler*/);
                setupLatch.countDown();
            }, START_CAPTURE_DELAY_MS);

            setupLatch.await();
            assertTrue("Failed to wait for required number of frames",
                    mSurfacePixelValidator.waitForAllFrames(maxCapturedDuration));
            final CountDownLatch testRunLatch = new CountDownLatch(1);
            mHandler.post(() -> {
                cleanupRunnable.run();
                testRunLatch.countDown();
            });

            assertTrue("Failed to wait for test to complete",
                    testRunLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));

            Log.d(TAG, "Test finished, passFrames " + testResult.passFrames
                    + ", failFrames " + testResult.failFrames);
            return testResult;
        } catch (Throwable throwable) {
            mHandler.post(cleanupRunnable);
            Log.e(TAG, "Test Failed, passFrames " + testResult.passFrames + ", failFrames "
                    + testResult.failFrames);
            throw throwable;
        }
    }

    private void saveFailureCaptures(SparseArray<Bitmap> failFrames, TestName name) {
        if (failFrames.size() == 0) return;

        String directoryName = Environment.getExternalStorageDirectory()
                + "/" + getClass().getSimpleName()
                + "/" + name.getMethodName();
        File testDirectory = new File(directoryName);
        if (testDirectory.exists()) {
            String[] children = testDirectory.list();
            if (children == null) {
                return;
            }
            for (String file : children) {
                new File(testDirectory, file).delete();
            }
        } else {
            testDirectory.mkdirs();
        }

        for (int i = 0; i < failFrames.size(); i++) {
            int frameNr = failFrames.keyAt(i);
            Bitmap bitmap = failFrames.valueAt(i);

            String bitmapName =  "frame_" + frameNr + ".png";
            Log.d(TAG, "Saving file : " + bitmapName + " in directory : " + directoryName);

            File file = new File(directoryName, bitmapName);
            try (FileOutputStream fileStream = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 85, fileStream);
                fileStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void verifyTest(ISurfaceValidatorTestCase testCase, TestName name) throws Throwable {
        if (mIsSharingScreenDenied.get()) {
            throw new IllegalStateException("User denied screen sharing permission.");
        }

        CapturedActivity.TestResult result = runTest(testCase);
        saveFailureCaptures(result.failures, name);

        float failRatio = 1.0f * result.failFrames / (result.failFrames + result.passFrames);
        assertTrue("Error: " + failRatio + " fail ratio - extremely high, is activity obstructed?",
                failRatio < 0.95f);
        assertEquals("Error: " + result.failFrames
                + " incorrect frames observed - incorrect positioning", 0, result.failFrames);
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.d(TAG, "MediaProjectionCallback#onStop");
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
                mVirtualDisplay = null;
            }
        }
    }

    public void restoreSettings() {
        // Adding try/catch due to bug with UiAutomation crashing the test b/272370325
        try {
            if (mSettingsSession != null) {
                mSettingsSession.close();
                mSettingsSession = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Crash occurred when closing settings session. See b/272370325", e);
        }
    }

    public boolean isOnWatch() {
        return mOnWatch;
    }

}
