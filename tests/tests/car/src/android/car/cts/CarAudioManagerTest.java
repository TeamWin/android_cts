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
import static android.car.media.CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_MUTING;
import static android.car.media.CarAudioManager.CarVolumeCallback;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.car.Car;
import android.car.media.CarAudioManager;
import android.car.media.CarVolumeGroupInfo;
import android.car.test.ApiCheckerRule.Builder;
import android.car.test.PermissionsCheckerRule;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant Apps cannot get car related permissions")
public final class CarAudioManagerTest extends AbstractCarTestCase {

    private static final String TAG = CarAudioManagerTest.class.getSimpleName();

    private static long WAIT_TIMEOUT_SECS = 5;

    @Rule
    public final PermissionsCheckerRule mPermissionsCheckerRule = new PermissionsCheckerRule();

    private static final UiAutomation UI_AUTOMATION =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private CarAudioManager mCarAudioManager;
    private SyncCarVolumeCallback mCallback;
    private int mZoneId = -1;
    private int mVolumeGroupId = -1;

    // TODO(b/242350638): add missing annotations, remove (on child bug of 242350638)
    @Override
    protected void configApiCheckerRule(Builder builder) {
        Log.w(TAG, "Disabling API requirements check");
        builder.disableAnnotationsCheck();
    }

    @Before
    public void setUp() throws Exception {
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
                        AUDIO_FEATURE_VOLUME_GROUP_MUTING);

        assertThat(volumeGroupMutingEnabled).isAnyOf(true, false);
    }

    @Test
    public void isAudioFeatureEnabled_withDynamicRoutingFeature_succeeds() {
        boolean dynamicRoutingEnabled = mCarAudioManager.isAudioFeatureEnabled(
                        AUDIO_FEATURE_DYNAMIC_ROUTING);

        assertThat(dynamicRoutingEnabled).isAnyOf(true, false);
    }

    @Test
    public void isAudioFeatureEnabled_withNonAudioFeature_fails() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
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

        Exception e = assertThrows(SecurityException.class,
                () -> mCarAudioManager.registerCarVolumeCallback(mCallback));

        assertThat(e.getMessage()).contains(PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    public void registerCarVolumeCallback_onGroupVolumeChanged() throws Exception {
        assumeDynamicRoutingIsEnabled();
        mCallback = new SyncCarVolumeCallback();

        mCarAudioManager.registerCarVolumeCallback(mCallback);

        injectVolumeDownKeyEvent();
        assertWithMessage("CarVolumeCallback#onGroupVolumeChanged should be called")
                .that(mCallback.receivedGroupVolumeChanged())
                .isTrue();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    public void registerCarVolumeCallback_onMasterMuteChanged() throws Exception {
        assumeDynamicRoutingIsEnabled();
        assumeVolumeGroupMutingIsDisabled();
        mCallback = new SyncCarVolumeCallback();

        mCarAudioManager.registerCarVolumeCallback(mCallback);

        injectVolumeMuteKeyEvent();
        try {
            assertWithMessage("CarVolumeCallback#onMasterMuteChanged should be called")
                .that(mCallback.receivedMasterMuteChanged())
                .isTrue();
        } finally {
            injectVolumeMuteKeyEvent();
        }
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    public void registerCarVolumeCallback_onGroupMuteChanged() throws Exception {
        assumeDynamicRoutingIsEnabled();
        assumeVolumeGroupMutingIsEnabled();
        mCallback = new SyncCarVolumeCallback();

        readFirstZoneAndVolumeGroup();
        mCarAudioManager.registerCarVolumeCallback(mCallback);
        setVolumeGroupMute(mZoneId, mVolumeGroupId, /* mute= */ true);
        setVolumeGroupMute(mZoneId, mVolumeGroupId, /* mute= */ false);

        assertWithMessage("CarVolumeCallback#onGroupMuteChanged should be called")
            .that(mCallback.receivedGroupMuteChanged()).isTrue();
        assertWithMessage("CarVolumeCallback#onGroupMuteChanged wrong zoneId")
            .that(mCallback.zoneId).isEqualTo(mZoneId);
        assertWithMessage("CarVolumeCallback#onGroupMuteChanged wrong groupId")
            .that(mCallback.groupId).isEqualTo(mVolumeGroupId);
    }

    private void readFirstZoneAndVolumeGroup() {
        String dump = ShellUtils.runShellCommand(
            "dumpsys car_service --services CarAudioService");
        Matcher matchZone = Pattern.compile("CarAudioZone\\(.*:(\\d?)\\)").matcher(dump);
        assertWithMessage("No CarAudioZone in dump").that(matchZone.find()).isTrue();
        mZoneId = Integer.parseInt(matchZone.group(1));
        Matcher matchGroup = Pattern.compile("CarVolumeGroup\\((\\d?)\\)").matcher(dump);
        assertWithMessage("No CarVolumeGroup in dump").that(matchGroup.find()).isTrue();
        mVolumeGroupId = Integer.parseInt(matchGroup.group(1));
    }

    private void setVolumeGroupMute(int zoneId, int groupId, boolean mute) {
        ShellUtils.runShellCommand("cmd car_service set-mute-car-volume-group %d %d %s",
            zoneId, groupId, mute ? "mute" : "unmute");
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
                .that(mCallback.receivedGroupVolumeChanged())
                .isFalse();
    }

    @Test
    public void unregisterCarVolumeCallback_withoutPermission_throws() {
        mCallback = new SyncCarVolumeCallback();
        runWithCarControlAudioVolumePermission(
                () -> mCarAudioManager.registerCarVolumeCallback(mCallback));

        Exception e = assertThrows(SecurityException.class,
                () -> mCarAudioManager.unregisterCarVolumeCallback(mCallback));

        assertThat(e.getMessage()).contains(PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    public void unregisterCarVolumeCallback_noLongerReceivesCallback() throws Exception {
        assumeDynamicRoutingIsEnabled();
        SyncCarVolumeCallback callback = new SyncCarVolumeCallback();
        mCarAudioManager.registerCarVolumeCallback(callback);
        mCarAudioManager.unregisterCarVolumeCallback(callback);

        injectVolumeDownKeyEvent();

        assertWithMessage("CarVolumeCallback#onGroupVolumeChanged should not be called")
                .that(callback.receivedGroupVolumeChanged())
                .isFalse();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getVolumeGroupInfo(int, int)"})
    public void getVolumeGroupInfo() {
        assumeDynamicRoutingIsEnabled();
        int groupCount = mCarAudioManager.getVolumeGroupCount(PRIMARY_AUDIO_ZONE);

        for (int index = 0; index < groupCount; index++) {
            CarVolumeGroupInfo info =
                    mCarAudioManager.getVolumeGroupInfo(PRIMARY_AUDIO_ZONE, index);
            expectWithMessage("Car volume group id for info %s and group %s", info, index)
                    .that(info.getId()).isEqualTo(index);
            expectWithMessage("Car volume group info zone for info %s and group %s",
                    info, index).that(info.getZoneId()).isEqualTo(PRIMARY_AUDIO_ZONE);
        }
    }

    @Test
    @ApiTest(apis = {"android.car.media.CarAudioManager#getVolumeGroupInfo(int, int)"})
    public void getVolumeGroupInfo_withoutPermission() {
        Exception exception = assertThrows(SecurityException.class,
                () -> mCarAudioManager.getVolumeGroupInfo(PRIMARY_AUDIO_ZONE, /* groupId= */ 0));

        assertWithMessage("Car volume group info without permission exception")
                .that(exception).hasMessageThat().contains(PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getVolumeGroupInfosForZone(int)"})
    public void getVolumeGroupInfosForZone() {
        assumeDynamicRoutingIsEnabled();
        int groupCount = mCarAudioManager.getVolumeGroupCount(PRIMARY_AUDIO_ZONE);

        List<CarVolumeGroupInfo> infos =
                mCarAudioManager.getVolumeGroupInfosForZone(PRIMARY_AUDIO_ZONE);

        expectWithMessage("Car volume group infos for primary zone")
                .that(infos).hasSize(groupCount);
        for (int index = 0; index < groupCount; index++) {
            CarVolumeGroupInfo info =
                    mCarAudioManager.getVolumeGroupInfo(PRIMARY_AUDIO_ZONE, index);
            expectWithMessage("Car volume group infos for info %s and group %s", info, index)
                    .that(infos).contains(info);
        }
    }

    @Test
    @ApiTest(apis = {"android.car.media.CarAudioManager#getVolumeGroupInfosForZone(int)"})
    public void getVolumeGroupInfosForZone_withoutPermission() {
        Exception exception = assertThrows(SecurityException.class,
                () -> mCarAudioManager.getVolumeGroupInfosForZone(PRIMARY_AUDIO_ZONE));

        assertWithMessage("Car volume groups info without permission exception")
                .that(exception).hasMessageThat().contains(PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
    }

    private void assumeDynamicRoutingIsEnabled() {
        assumeTrue(mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING));
    }

    private void assumeVolumeGroupMutingIsEnabled() {
        assumeTrue(mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_VOLUME_GROUP_MUTING));
    }

    private void assumeVolumeGroupMutingIsDisabled() {
        assumeFalse(mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_VOLUME_GROUP_MUTING));
    }

    private void runWithCarControlAudioVolumePermission(Runnable runnable) {
        UI_AUTOMATION.adoptShellPermissionIdentity(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        try {
            runnable.run();
        } finally {
            UI_AUTOMATION.dropShellPermissionIdentity();
        }
    }

    private void injectKeyEvent(int keyCode) {
        long downTime = SystemClock.uptimeMillis();
        KeyEvent volumeDown = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCode, 0);
        UI_AUTOMATION.injectInputEvent(volumeDown, true);
    }

    private void injectVolumeDownKeyEvent() {
        injectKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN);
    }

    private void injectVolumeMuteKeyEvent() {
        injectKeyEvent(KeyEvent.KEYCODE_VOLUME_MUTE);
    }

    private static final class SyncCarVolumeCallback extends CarVolumeCallback {
        private final CountDownLatch mGroupVolumeChangeLatch = new CountDownLatch(1);
        private final CountDownLatch mGroupMuteChangeLatch = new CountDownLatch(1);
        private final CountDownLatch mMasterMuteChangeLatch = new CountDownLatch(1);

        public int zoneId;
        public int groupId;

        boolean receivedGroupVolumeChanged() throws InterruptedException {
            return mGroupVolumeChangeLatch.await(WAIT_TIMEOUT_SECS, TimeUnit.SECONDS);
        }

        boolean receivedGroupMuteChanged() throws InterruptedException {
            return mGroupMuteChangeLatch.await(WAIT_TIMEOUT_SECS, TimeUnit.SECONDS);
        }

        boolean receivedMasterMuteChanged() throws InterruptedException {
            return mMasterMuteChangeLatch.await(WAIT_TIMEOUT_SECS, TimeUnit.SECONDS);
        }

        @Override
        public void onGroupVolumeChanged(int zoneId, int groupId, int flags) {
            mGroupVolumeChangeLatch.countDown();
        }

        @Override
        public void onMasterMuteChanged(int zoneId, int flags) {
            mMasterMuteChangeLatch.countDown();
        }

        @Override
        public void onGroupMuteChanged(int zoneId, int groupId, int flags) {
            this.zoneId = zoneId;
            this.groupId = groupId;
            mGroupMuteChangeLatch.countDown();
        }
    }
}
