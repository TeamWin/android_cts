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

package android.view.cts;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Instrumentation;
import android.cts.util.PollingCheck;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.FrameMetrics;
import android.view.Window;
import android.widget.ScrollView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class FrameMetricsListenerTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;

    @Rule
    public ActivityTestRule<MockActivity> mActivityRule =
            new ActivityTestRule<>(MockActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
    }

    private void layout(final int layoutId) throws Throwable {
        mActivityRule.runOnUiThread(() -> mActivity.setContentView(layoutId));
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testReceiveData() throws Throwable {
        layout(R.layout.scrollview_layout);
        final ScrollView scrollView = (ScrollView) mActivity.findViewById(R.id.scroll_view);
        final ArrayList<FrameMetrics> data = new ArrayList<>();
        final Handler handler = new Handler(Looper.getMainLooper());
        final Window myWindow = mActivity.getWindow();
        final Window.OnFrameMetricsAvailableListener listener =
            (Window window, FrameMetrics frameMetrics, int dropCount) -> {
                assertEquals(myWindow, window);
                assertEquals(0, dropCount);
                data.add(new FrameMetrics(frameMetrics));
            };
        mActivityRule.runOnUiThread(() -> mActivity.getWindow().
                addOnFrameMetricsAvailableListener(listener, handler));

        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(() -> scrollView.fling(-100));

        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(() -> data.size() != 0);

        mActivityRule.runOnUiThread(() -> {
            mActivity.getWindow().removeOnFrameMetricsAvailableListener(listener);
        });
        mInstrumentation.waitForIdleSync();

        data.clear();

        mActivityRule.runOnUiThread(() -> {
            scrollView.fling(100);
            assertEquals(0, data.size());
        });

        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testMultipleListeners() throws Throwable {
        layout(R.layout.scrollview_layout);
        final ScrollView scrollView = (ScrollView) mActivity.findViewById(R.id.scroll_view);
        final ArrayList<FrameMetrics> data1 = new ArrayList<>();
        final Handler handler = new Handler(Looper.getMainLooper());
        final Window myWindow = mActivity.getWindow();

        final Window.OnFrameMetricsAvailableListener frameMetricsListener1 =
                (Window window, FrameMetrics frameMetrics, int dropCount) -> {
                    assertEquals(myWindow, window);
                    assertEquals(0, dropCount);
                    data1.add(new FrameMetrics(frameMetrics));
                };
        final ArrayList<FrameMetrics> data2 = new ArrayList<>();
        final Window.OnFrameMetricsAvailableListener frameMetricsListener2 =
                (Window window, FrameMetrics frameMetrics, int dropCount) -> {
                    assertEquals(myWindow, window);
                    assertEquals(0, dropCount);
                    data2.add(new FrameMetrics(frameMetrics));
                };
        mActivityRule.runOnUiThread(() -> {
            mActivity.getWindow().addOnFrameMetricsAvailableListener(
                    frameMetricsListener1, handler);
            mActivity.getWindow().addOnFrameMetricsAvailableListener(
                    frameMetricsListener2, handler);
        });

        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(() -> scrollView.fling(-100));

        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(() -> data1.size() != 0 && data1.size() == data2.size());

        mActivityRule.runOnUiThread(() -> {
            mActivity.getWindow().removeOnFrameMetricsAvailableListener(frameMetricsListener1);
            mActivity.getWindow().removeOnFrameMetricsAvailableListener(frameMetricsListener2);
        });
    }

    @Test
    public void testDropCount() throws Throwable {
        layout(R.layout.scrollview_layout);
        final ScrollView scrollView = (ScrollView) mActivity.findViewById(R.id.scroll_view);

        final AtomicInteger framesDropped = new AtomicInteger();

        final HandlerThread thread = new HandlerThread("Listener");
        thread.start();
        final Window.OnFrameMetricsAvailableListener frameMetricsListener =
                (Window window, FrameMetrics frameMetrics, int dropCount) -> {
                    SystemClock.sleep(100);
                    framesDropped.addAndGet(dropCount);
                };

        mActivityRule.runOnUiThread(() -> mActivity.getWindow().
                addOnFrameMetricsAvailableListener(frameMetricsListener,
                        new Handler(thread.getLooper())));

        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(() -> scrollView.fling(-100));

        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(() -> framesDropped.get() > 0);

        mActivityRule.runOnUiThread(() -> mActivity.getWindow().
                removeOnFrameMetricsAvailableListener(frameMetricsListener));
    }
}


