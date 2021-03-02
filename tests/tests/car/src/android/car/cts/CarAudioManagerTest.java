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

package android.car.cts;

import static android.car.Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;
import static org.testng.Assert.expectThrows;

import android.app.UiAutomation;
import android.car.Car;
import android.car.media.CarAudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class CarAudioManagerTest extends CarApiTestBase {

    private static final UiAutomation UI_AUTOMATION =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private CarAudioManager mCarAudioManager;
    private SyncCarVolumeCallback mCallback;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mCarAudioManager = (CarAudioManager) getCar().getCarManager(Car.AUDIO_SERVICE);
    }

    @After
    public void cleanUp() {
        if (mCallback != null) {
            // Unregistering the last callback requires PERMISSION_CAR_CONTROL_AUDIO_VOLUME
            runWithCarControlAudioVolumePermission(
                    () -> mCarAudioManager.unregisterCarVolumeCallback(mCallback));
        }
    }

    @Test
    public void isAudioFeatureEnabled_withVolumeGroupMuteFeature_succeeds() {
        boolean volumeGroupMutingEnabled = mCarAudioManager.isAudioFeatureEnabled(
                        CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_MUTING);

        assertThat(volumeGroupMutingEnabled).isAnyOf(true, false);
    }

    @Test
    public void isAudioFeatureEnabled_withDynamicRoutingFeature_succeeds() {
        boolean dynamicRoutingEnabled = mCarAudioManager.isAudioFeatureEnabled(
                        CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING);

        assertThat(dynamicRoutingEnabled).isAnyOf(true, false);
    }

    @Test
    public void isAudioFeatureEnabled_withNonAudioFeature_fails() {
        IllegalArgumentException exception = expectThrows(IllegalArgumentException.class,
                () -> mCarAudioManager.isAudioFeatureEnabled(-1));

        assertThat(exception).hasMessageThat().contains("Unknown Audio Feature");
    }

    @Test
    public void registerCarVolumeCallback_nullCallback_throwsNPE() {
        assertThrows(NullPointerException.class,
                () -> mCarAudioManager.registerCarVolumeCallback(null));
    }

    @Test
    public void registerCarVolumeCallback_nonNullCallback_throwsPermissionError() {
        mCallback = new SyncCarVolumeCallback();

        Exception e = expectThrows(SecurityException.class,
                () -> mCarAudioManager.registerCarVolumeCallback(mCallback));

        assertThat(e.getMessage()).contains(PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
    }

    @Test
    public void registerCarVolumeCallback_withPermission_receivesCallback() throws Exception {
        mCallback = new SyncCarVolumeCallback();

        runWithCarControlAudioVolumePermission(
                () -> mCarAudioManager.registerCarVolumeCallback(mCallback));

        injectVolumeDownKeyEvent();
        assertWithMessage("CarVolumeCallback#onGroupVolumeChanged should be called")
                .that(mCallback.received())
                .isTrue();
    }

    @Test
    public void unregisterCarVolumeCallback_nullCallback_throws() {
        assertThrows(NullPointerException.class,
                () -> mCarAudioManager.unregisterCarVolumeCallback(null));
    }

    @Test
    public void unregisterCarVolumeCallback_unregisteredCallback_doesNotReceiveCallback()
            throws Exception {
        mCallback = new SyncCarVolumeCallback();

        mCarAudioManager.unregisterCarVolumeCallback(mCallback);

        assertWithMessage("CarVolumeCallback#onGroupVolumeChanged should not be called")
                .that(mCallback.received())
                .isFalse();
    }

    @Test
    public void unregisterCarVolumeCallback_withoutPermission_throws() {
        mCallback = new SyncCarVolumeCallback();
        runWithCarControlAudioVolumePermission(
                () -> mCarAudioManager.registerCarVolumeCallback(mCallback));

        Exception e = expectThrows(SecurityException.class,
                () -> mCarAudioManager.unregisterCarVolumeCallback(mCallback));

        assertThat(e.getMessage()).contains(PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
    }

    @Test
    public void unregisterCarVolumeCallback_noLongerReceivesCallback() throws Exception {
        SyncCarVolumeCallback callback = new SyncCarVolumeCallback();
        runWithCarControlAudioVolumePermission(() -> {
            mCarAudioManager.registerCarVolumeCallback(callback);
            mCarAudioManager.unregisterCarVolumeCallback(callback);
        });

        injectVolumeDownKeyEvent();

        assertWithMessage("CarVolumeCallback#onGroupVolumeChanged should not be called")
                .that(callback.received())
                .isFalse();
    }

    private void runWithCarControlAudioVolumePermission(Runnable runnable) {
        UI_AUTOMATION.adoptShellPermissionIdentity(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        try {
            runnable.run();
        } finally {
            UI_AUTOMATION.dropShellPermissionIdentity();
        }
    }

    private void injectVolumeDownKeyEvent() {
        long downTime = SystemClock.uptimeMillis();
        KeyEvent volumeDown = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_VOLUME_DOWN, 0);
        UI_AUTOMATION.injectInputEvent(volumeDown, true);
    }

    private static final class SyncCarVolumeCallback extends CarAudioManager.CarVolumeCallback {
        private final CountDownLatch mLatch = new CountDownLatch(1);

        boolean received() throws InterruptedException {
            return mLatch.await(1L, TimeUnit.SECONDS);
        }

        @Override
        public void onGroupVolumeChanged(int zoneId, int groupId, int flags) {
            mLatch.countDown();
        }
    }
}
