/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.widget.cts;

import android.app.Instrumentation;
import android.content.Context;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.ContextThemeWrapper;
import android.widget.Chronometer;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Test {@link Chronometer}.
 */
public class ChronometerTest extends ActivityInstrumentationTestCase2<ChronometerCtsActivity> {
    private Instrumentation mInstrumentation;
    private ChronometerCtsActivity mActivity;

    public ChronometerTest() {
        super("android.widget.cts", ChronometerCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
    }

    @UiThreadTest
    public void testConstructor() {
        new Chronometer(mActivity);

        new Chronometer(mActivity, null);

        new Chronometer(mActivity, null, 0);
    }

    @UiThreadTest
    public void testConstructorFromAttr() {
        final Context context = new ContextThemeWrapper(mActivity, R.style.ChronometerAwareTheme);
        final Chronometer chronometer = new Chronometer(context, null, R.attr.chronometerStyle);
        assertTrue(chronometer.isCountDown());
        assertEquals(mActivity.getString(R.string.chronometer_format), chronometer.getFormat());
    }

    @UiThreadTest
    public void testConstructorFromStyle() {
        final Chronometer chronometer = new Chronometer(mActivity, null, 0,
                R.style.ChronometerStyle);
        assertTrue(chronometer.isCountDown());
        assertEquals(mActivity.getString(R.string.chronometer_format), chronometer.getFormat());
    }

    @UiThreadTest
    public void testAccessBase() {
        Chronometer chronometer = mActivity.getChronometer();
        CharSequence oldText = chronometer.getText();

        int expected = 100000;
        chronometer.setBase(expected);
        assertEquals(expected, chronometer.getBase());
        assertNotSame(oldText, chronometer.getText());

        expected = 100;
        oldText = chronometer.getText();
        chronometer.setBase(expected);
        assertEquals(expected, chronometer.getBase());
        assertNotSame(oldText, chronometer.getText());

        expected = -1;
        oldText = chronometer.getText();
        chronometer.setBase(expected);
        assertEquals(expected, chronometer.getBase());
        assertNotSame(oldText, chronometer.getText());

        expected = Integer.MAX_VALUE;
        oldText = chronometer.getText();
        chronometer.setBase(expected);
        assertEquals(expected, chronometer.getBase());
        assertNotSame(oldText, chronometer.getText());
    }

    @UiThreadTest
    public void testAccessFormat() {
        Chronometer chronometer = mActivity.getChronometer();
        String expected = "header-%S-trail";

        chronometer.setFormat(expected);
        assertEquals(expected, chronometer.getFormat());

        chronometer.start();
        String text = chronometer.getText().toString();
        assertTrue(text.startsWith("header"));
        assertTrue(text.endsWith("trail"));
    }

    public void testStartAndStop() {
        final Chronometer chronometer = mActivity.getChronometer();

        // we will check the text is really updated every 1000ms after start,
        // so we need sleep a moment to wait wait this time. The sleep code shouldn't
        // in the same thread with UI, that's why we use runOnMainSync here.
        mInstrumentation.runOnMainSync(() -> {
            // the text will update immediately when call start.
            final CharSequence valueBeforeStart = chronometer.getText();
            chronometer.start();
            assertNotSame(valueBeforeStart, chronometer.getText());
        });
        mInstrumentation.waitForIdleSync();

        CharSequence expected = chronometer.getText();
        SystemClock.sleep(1500);
        assertFalse(expected.equals(chronometer.getText()));

        // we will check the text is really NOT updated anymore every 1000ms after stop,
        // so we need sleep a moment to wait wait this time. The sleep code shouldn't
        // in the same thread with UI, that's why we use runOnMainSync here.
        mInstrumentation.runOnMainSync(() -> {
            // the text will never be updated when call stop.
            final CharSequence valueBeforeStop = chronometer.getText();
            chronometer.stop();
            assertSame(valueBeforeStop, chronometer.getText());
        });
        mInstrumentation.waitForIdleSync();

        expected = chronometer.getText();
        SystemClock.sleep(1500);
        assertTrue(expected.equals(chronometer.getText()));
    }

    public void testAccessOnChronometerTickListener() {
        final Chronometer chronometer = mActivity.getChronometer();
        final Chronometer.OnChronometerTickListener mockTickListener =
                mock(Chronometer.OnChronometerTickListener.class);

        mInstrumentation.runOnMainSync(() -> {
            chronometer.setOnChronometerTickListener(mockTickListener);
            chronometer.start();
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(mockTickListener, chronometer.getOnChronometerTickListener());
        verify(mockTickListener, atLeastOnce()).onChronometerTick(chronometer);

        reset(mockTickListener);
        SystemClock.sleep(1500);
        verify(mockTickListener, atLeastOnce()).onChronometerTick(chronometer);
    }

    @LargeTest
    public void testCountDown() {
        final Chronometer chronometer = mActivity.getChronometer();
        final Chronometer.OnChronometerTickListener mockTickListener =
                mock(Chronometer.OnChronometerTickListener.class);

        mInstrumentation.runOnMainSync(() -> {
            chronometer.setCountDown(true);
            chronometer.setOnChronometerTickListener(mockTickListener);
            chronometer.start();
        });
        mInstrumentation.waitForIdleSync();

        assertTrue(chronometer.isCountDown());

        SystemClock.sleep(5000);
        verify(mockTickListener, atLeastOnce()).onChronometerTick(chronometer);
    }

    @LargeTest
    public void testCountUp() {
        final Chronometer chronometer = mActivity.getChronometer();
        final Chronometer.OnChronometerTickListener mockTickListener =
                mock(Chronometer.OnChronometerTickListener.class);

        mInstrumentation.runOnMainSync(() -> {
            chronometer.setCountDown(false);
            chronometer.setOnChronometerTickListener(mockTickListener);
            chronometer.start();
        });
        mInstrumentation.waitForIdleSync();

        assertFalse(chronometer.isCountDown());

        SystemClock.sleep(5000);
        verify(mockTickListener, atLeastOnce()).onChronometerTick(chronometer);
    }
}
