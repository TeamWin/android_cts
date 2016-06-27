/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.app.Activity;
import android.cts.util.PollingCheck;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.View;
import android.widget.ImageView;
import android.widget.SlidingDrawer;
import android.widget.TextView;

import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

/**
 * Test {@link SlidingDrawer}.
 */
public class SlidingDrawerTest
        extends ActivityInstrumentationTestCase2<SlidingDrawerCtsActivity> {

    private static final long TEST_TIMEOUT = 5000L;
    private Activity mActivity;

    public SlidingDrawerTest() {
        super("android.widget.cts", SlidingDrawerCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
    }

    @UiThreadTest
    public void testConstructor() throws XmlPullParserException, IOException {
        XmlPullParser parser = mActivity.getResources().getLayout(R.layout.sliding_drawer_layout);
        AttributeSet attrs = Xml.asAttributeSet(parser);

        try {
            new SlidingDrawer(mActivity, attrs);
            fail("did not throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            new SlidingDrawer(mActivity, attrs, 0);
            fail("did not throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testGetHandle() {
        SlidingDrawer drawer = (SlidingDrawer) mActivity.findViewById(R.id.drawer);
        View handle = drawer.getHandle();
        assertTrue(handle instanceof ImageView);
        assertEquals(R.id.handle, handle.getId());
    }

    public void testGetContent() {
        SlidingDrawer drawer = (SlidingDrawer) mActivity.findViewById(R.id.drawer);
        View content = drawer.getContent();
        assertTrue(content instanceof TextView);
        assertEquals(R.id.content, content.getId());
    }

    @UiThreadTest
    public void testOpenAndClose() {
        SlidingDrawer drawer = (SlidingDrawer) mActivity.findViewById(R.id.drawer);
        View content = drawer.getContent();
        assertFalse(drawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());

        drawer.open();
        assertTrue(drawer.isOpened());
        assertEquals(View.VISIBLE, content.getVisibility());

        drawer.close();
        assertFalse(drawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());
    }

    public void testAnimateOpenAndClose() throws Throwable {
        final SlidingDrawer drawer = (SlidingDrawer) mActivity.findViewById(R.id.drawer);
        View content = drawer.getContent();
        assertFalse(drawer.isMoving());
        assertFalse(drawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());

        runTestOnUiThread(() -> drawer.animateOpen());
        assertTrue(drawer.isMoving());
        assertEquals(View.GONE, content.getVisibility());

        PollingCheck.waitFor(() -> !drawer.isMoving());
        PollingCheck.waitFor(() -> drawer.isOpened());
        assertEquals(View.VISIBLE, content.getVisibility());

        runTestOnUiThread(() -> drawer.animateClose());
        assertTrue(drawer.isMoving());
        assertEquals(View.GONE, content.getVisibility());

        PollingCheck.waitFor(() -> !drawer.isMoving());
        PollingCheck.waitFor(() -> !drawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());
    }

    public void testAnimateToggle() throws Throwable {
        final SlidingDrawer drawer = (SlidingDrawer) mActivity.findViewById(R.id.drawer);
        View content = drawer.getContent();
        assertFalse(drawer.isMoving());
        assertFalse(drawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());

        runTestOnUiThread(() -> drawer.animateToggle());
        assertTrue(drawer.isMoving());
        assertEquals(View.GONE, content.getVisibility());

        PollingCheck.waitFor(() -> !drawer.isMoving());
        PollingCheck.waitFor(() -> drawer.isOpened());
        assertEquals(View.VISIBLE, content.getVisibility());

        runTestOnUiThread(() -> drawer.animateToggle());
        assertTrue(drawer.isMoving());
        assertEquals(View.GONE, content.getVisibility());

        PollingCheck.waitFor(() -> !drawer.isMoving());
        PollingCheck.waitFor(() -> !drawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());
    }

    @UiThreadTest
    public void testToggle() {
        SlidingDrawer drawer = (SlidingDrawer) mActivity.findViewById(R.id.drawer);
        View content = drawer.getContent();
        assertFalse(drawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());

        drawer.toggle();
        assertTrue(drawer.isOpened());
        assertEquals(View.VISIBLE, content.getVisibility());

        drawer.toggle();
        assertFalse(drawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());
    }

    @UiThreadTest
    public void testLockAndUnlock() {
        SlidingDrawer drawer = (SlidingDrawer) mActivity.findViewById(R.id.drawer);
        View handle = drawer.getHandle();
        View content = drawer.getContent();
        assertFalse(drawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());

        handle.performClick();
        assertTrue(drawer.isOpened());
        assertEquals(View.VISIBLE, content.getVisibility());

        handle.performClick();
        assertFalse(drawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());

        drawer.lock();
        handle.performClick();
        assertFalse(drawer.isOpened());
        assertEquals(View.GONE, content.getVisibility());

        drawer.unlock();
        handle.performClick();
        assertTrue(drawer.isOpened());
        assertEquals(View.VISIBLE, content.getVisibility());
    }

    @UiThreadTest
    public void testSetOnDrawerOpenListener() {
        SlidingDrawer drawer = (SlidingDrawer) mActivity.findViewById(R.id.drawer);
        SlidingDrawer.OnDrawerOpenListener mockOpenListener =
                mock(SlidingDrawer.OnDrawerOpenListener.class);
        drawer.setOnDrawerOpenListener(mockOpenListener);

        verifyZeroInteractions(mockOpenListener);

        drawer.open();
        verify(mockOpenListener, times(1)).onDrawerOpened();
    }

    @UiThreadTest
    public void testSetOnDrawerCloseListener() {
        SlidingDrawer drawer = (SlidingDrawer) mActivity.findViewById(R.id.drawer);
        SlidingDrawer.OnDrawerCloseListener mockCloseListener =
                mock(SlidingDrawer.OnDrawerCloseListener.class);
        drawer.setOnDrawerCloseListener(mockCloseListener);

        verifyZeroInteractions(mockCloseListener);

        drawer.open();
        verifyZeroInteractions(mockCloseListener);

        drawer.close();
        verify(mockCloseListener, times(1)).onDrawerClosed();
    }

    public void testSetOnDrawerScrollListener() throws Throwable {
        final SlidingDrawer drawer = (SlidingDrawer) mActivity.findViewById(R.id.drawer);

        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final SlidingDrawer.OnDrawerScrollListener mockScrollListener =
                mock(SlidingDrawer.OnDrawerScrollListener.class);
        doAnswer((InvocationOnMock invocation) -> {
            countDownLatch.countDown();
            return null;
        }).when(mockScrollListener).onScrollStarted();
        doAnswer((InvocationOnMock invocation) -> {
            countDownLatch.countDown();
            return null;
        }).when(mockScrollListener).onScrollEnded();
        drawer.setOnDrawerScrollListener(mockScrollListener);

        runTestOnUiThread(() -> drawer.animateOpen());

        try {
            countDownLatch.await(2 * TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            // Do nothing as we're about to verify that both callbacks have been called
            // in any case
        }

        InOrder inOrder = inOrder(mockScrollListener);
        inOrder.verify(mockScrollListener).onScrollStarted();
        inOrder.verify(mockScrollListener).onScrollEnded();
        verifyNoMoreInteractions(mockScrollListener);
    }

    public void testOnLayout() {
        // onLayout() is implementation details, do NOT test
    }

    public void testOnMeasure() {
        // onMeasure() is implementation details, do NOT test
    }

    public void testOnFinishInflate() {
        // onFinishInflate() is implementation details, do NOT test
    }

    public void testDispatchDraw() {
        // dispatchDraw() is implementation details, do NOT test
    }

    public void testOnInterceptTouchEvent() {
        // onInterceptTouchEvent() is implementation details, do NOT test
    }

    public void testOnTouchEvent() {
        // onTouchEvent() is implementation details, do NOT test
    }
}
