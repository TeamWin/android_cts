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

package android.cts.util;

import android.view.View;
import android.view.ViewTreeObserver;

import java.util.concurrent.CountDownLatch;

/*
 * A DrawWaiter allows one to make changes that will require the View
 * to be redrawn, and then wait for that drawing to be complete.  One
 * calls DrawWaiter.registerDrawCompleteCallback(view) after making
 * the changes to the view, and then DrawWaiter.waitForDrawComplete().
 * The latter call waits until the callback registered by the first
 * call is invoked.  When waitForDrawComplete() returns, the DrawWaiter
 * is reset, allowing another such pair of calls to be issued.
 */
public class DrawWaiter {
    private CountDownLatch mLatch;

    public DrawWaiter() {
        // Ensure that the latch is initialized.
        mLatch = new CountDownLatch(1);
    }

    /* Register a callback to signal the waiter.
     *
     * Registers a callback to be called after the next call to draw
     * on the given View.  Should be called in the UI thread, after some action
     * that will cause the view to be redrawn, but before the UI
     * thread becomes free to perform the draw (so that the
     * registration is guaranteed to happen before the drawing).
     *
     * @param view The View on whose drawing the waiter should be
     * triggered.
     */
    public void registerDrawCompleteCallback(View view) {
        // Copy object pointers into final locals, for use in nested classes.
        final View finalView = view;
        final ViewTreeObserver observer = view.getViewTreeObserver();
        final DrawWaiter drawWaiter = this;
        observer.addOnDrawListener(
            new ViewTreeObserver.OnDrawListener() {
                public void onDraw() {
                    final ViewTreeObserver.OnDrawListener listener = this;
                    finalView.getHandler().postAtFrontOfQueue(new Runnable() {
                            @Override
                            public void run() {
                                observer.removeOnDrawListener(listener);
                                mLatch.countDown();
                            }
                        });
                }});
    }

    /* Wait for the callback registered by
     * registerDrawCompleteCallback to be called.
     */
    public void waitForDrawComplete() {
        try {
            mLatch.await();
        } catch (InterruptedException x) {}
        // Reset the latch for a possible future use of this DrawWaiter.
        mLatch = new CountDownLatch(1);
    }
}
