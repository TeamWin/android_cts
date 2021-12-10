/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.app.communal.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.app.UiAutomation;
import android.app.communal.CommunalManager;
import android.app.communal.CommunalManager.CommunalModeListener;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CommunalManagerTest {
    private CommunalManager mCommunalManager;
    private Context mContext;
    private UiAutomation mUiAutomation;

    private boolean hasCommunal() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_COMMUNAL_MODE);
    }

    @Before
    public void setUp() throws Exception {
        mContext = getInstrumentation().getTargetContext();
        assumeTrue("Communal mode not available", hasCommunal());
        mCommunalManager = mContext.getSystemService(CommunalManager.class);
        mUiAutomation = getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.READ_COMMUNAL_STATE,
                Manifest.permission.WRITE_COMMUNAL_STATE);

        ensureCleanState();
    }

    @Test
    public void testIsCommunalMode_returnsCorrectValue() throws RemoteException {
        assertThat(mCommunalManager.isCommunalMode()).isFalse();

        setCommunalViewShowingAndWait(true);
        assertThat(mCommunalManager.isCommunalMode()).isTrue();
    }

    @Test
    public void testAddCommunalModeListener_callbackInvoked() throws RemoteException {
        final CountDownLatch latch = new CountDownLatch(3);
        CommunalModeListener counter = isCommunalMode -> {
            latch.countDown();
        };

        CommunalModeListener listener = getTestCommunalModeListener();
        mCommunalManager.addCommunalModeListener(mContext.getMainExecutor(), listener);

        mCommunalManager.addCommunalModeListener(mContext.getMainExecutor(), counter);
        try {
            mCommunalManager.setCommunalViewShowing(true);
            mCommunalManager.setCommunalViewShowing(true);  // communal mode not changed
            mCommunalManager.setCommunalViewShowing(false);
            mCommunalManager.setCommunalViewShowing(false);  // communal mode not changed
            mCommunalManager.setCommunalViewShowing(true);
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Registered listener not invoked");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        verify(listener, times(2)).onCommunalModeChanged(true);
        verify(listener, times(1)).onCommunalModeChanged(false);
        mCommunalManager.removeCommunalModeListener(listener);
        mCommunalManager.removeCommunalModeListener(counter);
    }

    @Test
    public void testAddRemoveCommunalModeListener_callbackNotInvoked() throws RemoteException {
        final CountDownLatch latch = new CountDownLatch(3);
        CommunalModeListener counter = isCommunalMode -> {
            latch.countDown();
        };

        CommunalModeListener listener = getTestCommunalModeListener();

        mCommunalManager.addCommunalModeListener(mContext.getMainExecutor(), listener);
        mCommunalManager.removeCommunalModeListener(listener);

        mCommunalManager.addCommunalModeListener(mContext.getMainExecutor(), counter);
        try {
            mCommunalManager.setCommunalViewShowing(true);
            mCommunalManager.setCommunalViewShowing(true);  // communal mode not changed
            mCommunalManager.setCommunalViewShowing(false);
            mCommunalManager.setCommunalViewShowing(false);  // communal mode not changed
            mCommunalManager.setCommunalViewShowing(true);
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Registered listener not invoked");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        verify(listener, never()).onCommunalModeChanged(anyBoolean());
        mCommunalManager.removeCommunalModeListener(listener);
        mCommunalManager.removeCommunalModeListener(counter);
    }

    /**
     * Helper method to make sure the communal mode is false.
     *
     * This is necessary to clean up previous test states.
     */
    private void ensureCleanState() throws RemoteException {
        if (mCommunalManager.isCommunalMode()) {
            setCommunalViewShowingAndWait(false);
        }
    }

    private void setCommunalViewShowingAndWait(boolean isShowing) {
        final CountDownLatch latch = new CountDownLatch(1);
        CommunalModeListener counter = isCommunalMode -> {
            latch.countDown();
        };

        mCommunalManager.addCommunalModeListener(mContext.getMainExecutor(), counter);

        try {
            mCommunalManager.setCommunalViewShowing(true);
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Registered listener not invoked");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        mCommunalManager.removeCommunalModeListener(counter);
    }

    public CommunalModeListener getTestCommunalModeListener() {
        return spy(new TestCommunalModeListener());
    }

    public class TestCommunalModeListener implements CommunalModeListener {
        @Override
        public void onCommunalModeChanged(boolean isCommunalMode) {
        }
    }
}
