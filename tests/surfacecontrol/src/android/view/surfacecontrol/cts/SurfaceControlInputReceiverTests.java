/*
 * Copyright 2024 The Android Open Source Project
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

package android.view.surfacecontrol.cts;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.server.wm.CtsWindowInfoUtils.assertAndDumpWindowState;
import static android.server.wm.CtsWindowInfoUtils.sendTap;
import static android.server.wm.CtsWindowInfoUtils.tapOnWindowCenter;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowOnTop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.CtsWindowInfoUtils;
import android.util.Log;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.cts.surfacevalidator.EmbeddedSCVHService;
import android.view.cts.surfacevalidator.IAttachEmbeddedWindow;
import android.view.cts.surfacevalidator.IMotionEventReceiver;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.window.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SurfaceControlInputReceiverTests {
    private static final String TAG = "SurfaceControlInputReceiverTests";
    private static final long WAIT_TIME_S = 5L * HW_TIMEOUT_MULTIPLIER;

    private static final Rect sBounds = new Rect(0, 0, 100, 100);

    @Rule
    public ActivityScenarioRule<TestActivity> mActivityRule =
            new ActivityScenarioRule<>(TestActivity.class);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private TestActivity mActivity;

    private WindowManager mWm;

    @Before
    public void setUp() throws InterruptedException {
        mActivityRule.getScenario().onActivity(a -> mActivity = a);
        mWm = mActivity.getWindowManager();
    }

    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER)
    @Test
    public void testLocalSurfaceControlReceivesInput() throws InterruptedException {
        SurfaceControl sc = new SurfaceControl.Builder()
                .setName("Local Child SurfaceControl")
                .setBufferSize(sBounds.width(), sBounds.height())
                .build();

        Surface surface = new Surface(sc);
        Canvas canvas = surface.lockHardwareCanvas();
        canvas.drawColor(Color.RED);
        surface.unlockCanvasAndPost(canvas);

        AnchorView anchorView = new AnchorView(mActivity.getApplicationContext(), sc);

        Choreographer[] choreographer = new Choreographer[1];
        CountDownLatch choreographerLatch = new CountDownLatch(1);
        mActivity.runOnUiThread(() -> {
            mActivity.setContentView(anchorView);
            choreographer[0] = Choreographer.getInstance();
            choreographerLatch.countDown();
        });
        anchorView.waitForDrawn();
        assertTrue("Failed to get Choreographer",
                choreographerLatch.await(WAIT_TIME_S, TimeUnit.SECONDS));

        try {
            final LinkedBlockingQueue<MotionEvent> motionEvents = new LinkedBlockingQueue<>();
            mWm.registerBatchedSurfaceControlInputReceiver(
                    mActivity.getDisplayId(),
                    mActivity.getWindow().getRootSurfaceControl().getInputTransferToken(), sc,
                    choreographer[0], event -> {
                        if (event instanceof MotionEvent) {
                            try {
                                motionEvents.put(MotionEvent.obtain((MotionEvent) event));
                            } catch (InterruptedException e) {
                                Log.e(TAG, "Failed to add input event to queue", e);
                            }
                        }
                        return false;
                    });

            IBinder clientToken = mWm.getSurfaceControlInputClientToken(sc);
            assertAndDumpWindowState(TAG,
                    "Failed to wait for SurfaceControl with Input to be on top",
                    waitForWindowOnTop(WAIT_TIME_S, TimeUnit.SECONDS, () -> clientToken));
            Point tappedCoords = new Point();
            tapOnWindowCenter(InstrumentationRegistry.getInstrumentation(),
                    () -> clientToken, false /* useGlobalInject */, tappedCoords);

            MotionEvent motionEvent = motionEvents.poll(WAIT_TIME_S, TimeUnit.SECONDS);
            assertAndDumpWindowState(TAG, "Failed to receive touch", motionEvent != null);
            assertMotionEvent(motionEvent, tappedCoords);

        } finally {
            mActivity.runOnUiThread(() -> mWm.unregisterSurfaceControlInputReceiver(sc));
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER)
    @Test
    public void testRemoteSurfaceControlReceivesInput()
            throws InterruptedException, RemoteException {
        SurfaceControl sc = new SurfaceControl.Builder()
                .setName("Anchor SurfaceControl")
                .build();
        AnchorView anchorView = new AnchorView(mActivity.getApplicationContext(), sc);

        CountDownLatch mEmbeddedServiceReady = new CountDownLatch(1);
        IAttachEmbeddedWindow[] attachEmbeddedWindow = new IAttachEmbeddedWindow[1];
        mActivity.runOnUiThread(() -> {
            ServiceConnection mConnection = new ServiceConnection() {
                // Called when the connection with the service is established
                public void onServiceConnected(ComponentName className, IBinder service) {
                    attachEmbeddedWindow[0] = IAttachEmbeddedWindow.Stub.asInterface(service);
                    mEmbeddedServiceReady.countDown();
                }

                public void onServiceDisconnected(ComponentName className) {
                    attachEmbeddedWindow[0] = null;
                }
            };

            Intent intent = new Intent(mActivity, EmbeddedSCVHService.class);
            intent.setAction(IAttachEmbeddedWindow.class.getName());
            mActivity.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

            mActivity.setContentView(anchorView);
        });

        assertTrue("Failed to wait for embedded service to bind",
                mEmbeddedServiceReady.await(WAIT_TIME_S, TimeUnit.SECONDS));

        anchorView.waitForDrawn();

        final LinkedBlockingQueue<MotionEvent> motionEvents = new LinkedBlockingQueue<>();
        try {
            String embeddedName = attachEmbeddedWindow[0].attachEmbeddedSurfaceControl(sc,
                    mActivity.getDisplayId(),
                    mActivity.getWindow().getRootSurfaceControl().getInputTransferToken(),
                    sBounds.width(),
                    sBounds.height(), new IMotionEventReceiver.Stub() {
                        @Override
                        public void onMotionEventReceived(MotionEvent motionEvent) {
                            try {
                                motionEvents.put(motionEvent);
                            } catch (InterruptedException e) {
                                Log.e(TAG, "Failed to add input event to queue", e);
                            }
                        }
                    });

            assertNotNull("Failed to receive embedded client name", embeddedName);

            Rect bounds = new Rect();
            boolean success = waitForWindowOnTop(WAIT_TIME_S, TimeUnit.SECONDS,
                    windowInfo -> {
                        if (!windowInfo.name.contains(embeddedName)) {
                            return false;
                        }
                        if (!windowInfo.bounds.isEmpty()) {
                            bounds.set(windowInfo.bounds);
                            return true;
                        }

                        return false;
                    });
            CtsWindowInfoUtils.dumpWindowsOnScreen(TAG, "embedded SC");
            assertAndDumpWindowState(TAG, "Failed to find embedded SC on top", success);

            final Point coord = new Point(bounds.left + bounds.width() / 2,
                    bounds.top + bounds.height() / 2);
            sendTap(InstrumentationRegistry.getInstrumentation(), coord,
                    false /* useGlobalInjection */);

            MotionEvent motionEvent = motionEvents.poll(WAIT_TIME_S, TimeUnit.SECONDS);
            assertAndDumpWindowState(TAG, "Failed to receive touch", motionEvent != null);
            assertMotionEvent(motionEvent, coord);
        } finally {
            attachEmbeddedWindow[0].tearDownEmbeddedSurfaceControl();
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER)
    @Test
    public void testNonBatchedSurfaceControlReceivesInput() throws InterruptedException {
        SurfaceControl sc = new SurfaceControl.Builder()
                .setName("Local Child SurfaceControl")
                .setBufferSize(sBounds.width(), sBounds.height())
                .build();

        Surface surface = new Surface(sc);
        Canvas canvas = surface.lockHardwareCanvas();
        canvas.drawColor(Color.RED);
        surface.unlockCanvasAndPost(canvas);

        AnchorView anchorView = new AnchorView(mActivity.getApplicationContext(), sc);

        CountDownLatch choreographerLatch = new CountDownLatch(1);
        mActivity.runOnUiThread(() -> {
            mActivity.setContentView(anchorView);
            choreographerLatch.countDown();
        });
        anchorView.waitForDrawn();
        assertTrue("Failed to get Choreographer",
                choreographerLatch.await(WAIT_TIME_S, TimeUnit.SECONDS));

        try {
            final LinkedBlockingQueue<MotionEvent> motionEvents = new LinkedBlockingQueue<>();
            mWm.registerUnbatchedSurfaceControlInputReceiver(
                    mActivity.getDisplayId(),
                    mActivity.getWindow().getRootSurfaceControl().getInputTransferToken(), sc,
                    mActivity.getMainLooper(), event -> {
                        if (event instanceof MotionEvent) {
                            try {
                                motionEvents.put(MotionEvent.obtain((MotionEvent) event));
                            } catch (InterruptedException e) {
                                Log.e(TAG, "Failed to add input event to queue", e);
                            }
                        }
                        return false;
                    });

            IBinder clientToken = mWm.getSurfaceControlInputClientToken(sc);
            assertAndDumpWindowState(TAG,
                    "Failed to wait for SurfaceControl with Input to be on top",
                    waitForWindowOnTop(WAIT_TIME_S, TimeUnit.SECONDS,
                            () -> clientToken));
            Point tappedCoords = new Point();
            tapOnWindowCenter(InstrumentationRegistry.getInstrumentation(),
                    () -> clientToken, false /* useGlobalInject */, tappedCoords);

            MotionEvent motionEvent = motionEvents.poll(WAIT_TIME_S, TimeUnit.SECONDS);
            assertAndDumpWindowState(TAG, "Failed to receive input", motionEvent != null);
            assertMotionEvent(motionEvent, tappedCoords);
        } finally {
            mActivity.runOnUiThread(
                    () -> mActivity.getWindowManager().unregisterSurfaceControlInputReceiver(sc));
        }
    }

    private static void assertMotionEvent(MotionEvent event, Point coords) {
        assertEquals(
                "Expected ACTION_DOWN. Received " + MotionEvent.actionToString(event.getAction()),
                MotionEvent.ACTION_DOWN, event.getAction());
        Point receivedCoords = new Point((int) event.getX(), (int) event.getY());
        assertEquals("Expected " + coords + ". Received " + receivedCoords, coords, receivedCoords);
    }

    private static class AnchorView extends View {
        SurfaceControl mChild;

        private final CountDownLatch mDrawCompleteLatch = new CountDownLatch(1);

        private boolean mChildScAttached;

        AnchorView(Context c, SurfaceControl child) {
            super(c, null, 0, 0);
            mChild = child;
            getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    attachChildSc();
                    getViewTreeObserver().removeOnPreDrawListener(this);
                    return true;
                }
            });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attachChildSc();
        }

        private void attachChildSc() {
            if (mChildScAttached) {
                return;
            }

            SurfaceControl.Transaction t =
                    getRootSurfaceControl().buildReparentTransaction(mChild);

            if (t == null) {
                // TODO (b/286406553) SurfaceControl was not yet setup. Wait until the draw request
                // to attach since the SurfaceControl will be created by that point. This can be
                // cleaned up when the bug is fixed.
                return;
            }

            t.setLayer(mChild, 1).setVisibility(mChild, true).setCrop(mChild, sBounds)
                    .setPosition(mChild, (float) getWidth() / 2, (float) getHeight() / 2);
            t.addTransactionCommittedListener(Runnable::run, mDrawCompleteLatch::countDown);
            getRootSurfaceControl().applyTransactionOnDraw(t);
            mChildScAttached = true;
        }

        @Override
        protected void onDetachedFromWindow() {
            new SurfaceControl.Transaction().reparent(mChild, null).apply();
            mChild.release();
            mChildScAttached = false;

            super.onDetachedFromWindow();
        }

        public void waitForDrawn() throws InterruptedException {
            assertTrue("Failed to wait for frame to draw",
                    mDrawCompleteLatch.await(WAIT_TIME_S, TimeUnit.SECONDS));
        }
    }
}
