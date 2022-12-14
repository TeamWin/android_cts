/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.view.inputmethod.cts.util;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class TestActivity extends Activity {

    public static final String OVERLAY_WINDOW_NAME = "TestActivity.APP_OVERLAY_WINDOW";
    private static final AtomicReference<Function<TestActivity, View>> sInitializer =
            new AtomicReference<>();

    private Function<TestActivity, View> mInitializer = null;

    private AtomicBoolean mIgnoreBackKey = new AtomicBoolean();

    private long mOnBackPressedCallCount;

    private TextView mOverlayView;
    private OnBackInvokedCallback mIgnoreBackKeyCallback = () -> {
        // Ignore back.
    };
    private Boolean mIgnoreBackKeyCallbackRegistered = false;

    /**
     * Controls how {@link #onBackPressed()} behaves.
     *
     * <p>TODO: Use {@link android.app.AppComponentFactory} instead to customise the behavior of
     * {@link TestActivity}.</p>
     *
     * @param ignore {@code true} when {@link TestActivity} should do nothing when
     *               {@link #onBackPressed()} is called
     */
    @AnyThread
    public void setIgnoreBackKey(boolean ignore) {
        mIgnoreBackKey.set(ignore);
        if (ignore) {
            if (!mIgnoreBackKeyCallbackRegistered) {
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT, mIgnoreBackKeyCallback);
                mIgnoreBackKeyCallbackRegistered = true;
            }
        } else {
            if (mIgnoreBackKeyCallbackRegistered) {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                        mIgnoreBackKeyCallback);
                mIgnoreBackKeyCallbackRegistered = false;
            }
        }
    }

    @UiThread
    public long getOnBackPressedCallCount() {
        return mOnBackPressedCallCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mInitializer == null) {
            mInitializer = sInitializer.get();
        }
        // Currently SOFT_INPUT_STATE_UNSPECIFIED isn't appropriate for CTS test because there is no
        // clear spec about how it behaves.  In order to make our tests deterministic, currently we
        // must use SOFT_INPUT_STATE_UNCHANGED.
        // TODO(Bug 77152727): Remove the following code once we define how
        // SOFT_INPUT_STATE_UNSPECIFIED actually behaves.
        setSoftInputState(SOFT_INPUT_STATE_UNCHANGED);
        setContentView(mInitializer.apply(this));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mOverlayView != null) {
            mOverlayView.getContext()
                    .getSystemService(WindowManager.class).removeView(mOverlayView);
            mOverlayView = null;
        }
        if (mIgnoreBackKeyCallbackRegistered) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(mIgnoreBackKeyCallback);
            mIgnoreBackKeyCallbackRegistered = false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBackPressed() {
        ++mOnBackPressedCallCount;
        if (mIgnoreBackKey.get()) {
            return;
        }
        super.onBackPressed();
    }

    public void showOverlayWindow() {
        if (mOverlayView != null) {
            throw new IllegalStateException("can only show one overlay at a time.");
        }
        Context overlayContext = getApplicationContext().createWindowContext(getDisplay(),
                TYPE_APPLICATION_OVERLAY, null);
        mOverlayView = new TextView(overlayContext);
        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(MATCH_PARENT, MATCH_PARENT,
                        TYPE_APPLICATION_OVERLAY, FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.setTitle(OVERLAY_WINDOW_NAME);
        mOverlayView.setLayoutParams(params);
        mOverlayView.setText("IME CTS TestActivity OverlayView");
        mOverlayView.setBackgroundColor(0x77FFFF00);
        overlayContext.getSystemService(WindowManager.class).addView(mOverlayView, params);
    }

    /**
     * Launches {@link TestActivity} with the given initialization logic for content view.
     *
     * <p>As long as you are using {@link androidx.test.runner.AndroidJUnitRunner}, the test
     * runner automatically calls {@link Activity#finish()} for the {@link Activity} launched when
     * the test finished.  You do not need to explicitly call {@link Activity#finish()}.</p>
     *
     * @param activityInitializer initializer to supply {@link View} to be passed to
     *                           {@link Activity#setContentView(View)}
     * @return {@link TestActivity} launched
     */
    public static TestActivity startSync(
            @NonNull Function<TestActivity, View> activityInitializer) {
        return startSync(activityInitializer, 0 /* noAnimation */);
    }

    /**
     * Similar to {@link TestActivity#startSync(Function)}, but with the given display ID to
     * specify the launching target display.
     * @param displayId The ID of the display
     * @param activityInitializer initializer to supply {@link View} to be passed to
     *                            {@link Activity#setContentView(View)}
     * @return {@link TestActivity} launched
     */
    public static TestActivity startSync(int displayId,
            @NonNull Function<TestActivity, View> activityInitializer) throws Exception {
        sInitializer.set(activityInitializer);
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(displayId);
        final Intent intent = new Intent()
                .setAction(Intent.ACTION_MAIN)
                .setClass(InstrumentationRegistry.getInstrumentation().getContext(),
                        TestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return SystemUtil.callWithShellPermissionIdentity(
                () -> (TestActivity) InstrumentationRegistry.getInstrumentation().startActivitySync(
                        intent, options.toBundle()));
    }

    /**
     * Launches {@link TestActivity} with the given initialization logic for content view.
     *
     * <p>As long as you are using {@link androidx.test.runner.AndroidJUnitRunner}, the test
     * runner automatically calls {@link Activity#finish()} for the {@link Activity} launched when
     * the test finished.  You do not need to explicitly call {@link Activity#finish()}.</p>
     *
     * @param activityInitializer initializer to supply {@link View} to be passed to
     *                           {@link Activity#setContentView(View)}
     * @param additionalFlags flags to be set to {@link Intent#setFlags(int)}
     * @return {@link TestActivity} launched
     */
    public static TestActivity startSync(
            @NonNull Function<TestActivity, View> activityInitializer,
            int additionalFlags) {
        sInitializer.set(activityInitializer);
        final Intent intent = new Intent()
                .setAction(Intent.ACTION_MAIN)
                .setClass(InstrumentationRegistry.getInstrumentation().getContext(),
                        TestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .addFlags(additionalFlags);
        return (TestActivity) InstrumentationRegistry
                .getInstrumentation().startActivitySync(intent);
    }

    public static TestActivity startNewTaskSync(
            @NonNull Function<TestActivity, View> activityInitializer) {
        sInitializer.set(activityInitializer);
        final Intent intent = new Intent()
                .setAction(Intent.ACTION_MAIN)
                .setClass(InstrumentationRegistry.getInstrumentation().getContext(),
                        TestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        return (TestActivity) InstrumentationRegistry
                .getInstrumentation().startActivitySync(intent);
    }


    public static TestActivity startSameTaskAndClearTopSync(
            @NonNull Function<TestActivity, View> activityInitializer) {
        sInitializer.set(activityInitializer);
        final Intent intent = new Intent()
                .setAction(Intent.ACTION_MAIN)
                .setClass(InstrumentationRegistry.getInstrumentation().getContext(),
                        TestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return (TestActivity) InstrumentationRegistry
                .getInstrumentation().startActivitySync(intent);
    }

    /**
     * Updates {@link WindowManager.LayoutParams#softInputMode}.
     *
     * @param newState One of {@link WindowManager.LayoutParams#SOFT_INPUT_STATE_UNSPECIFIED},
     *                 {@link WindowManager.LayoutParams#SOFT_INPUT_STATE_UNCHANGED},
     *                 {@link WindowManager.LayoutParams#SOFT_INPUT_STATE_HIDDEN},
     *                 {@link WindowManager.LayoutParams#SOFT_INPUT_STATE_ALWAYS_HIDDEN},
     *                 {@link WindowManager.LayoutParams#SOFT_INPUT_STATE_VISIBLE},
     *                 {@link WindowManager.LayoutParams#SOFT_INPUT_STATE_ALWAYS_VISIBLE}
     */
    private void setSoftInputState(int newState) {
        final Window window = getWindow();
        final int currentSoftInputMode = window.getAttributes().softInputMode;
        final int newSoftInputMode =
                (currentSoftInputMode & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE)
                        | newState;
        window.setSoftInputMode(newSoftInputMode);
    }
}
