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
import static android.car.media.CarAudioManager.INVALID_AUDIO_ZONE;
import static android.car.media.CarAudioManager.INVALID_REQUEST_ID;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.car.test.mocks.JavaMockitoHelper.await;
import static android.car.test.mocks.JavaMockitoHelper.silentAwait;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.media.CarAudioManager;
import android.car.media.CarVolumeGroupInfo;
import android.car.media.MediaAudioRequestStatusCallback;
import android.car.media.PrimaryZoneMediaAudioRequestCallback;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant Apps cannot get car related permissions")
public final class CarAudioManagerTest extends AbstractCarTestCase {

    private static final String TAG = CarAudioManagerTest.class.getSimpleName();

    private static final long WAIT_TIMEOUT_MS = 5_000;

    @Rule
    public final PermissionsCheckerRule mPermissionsCheckerRule = new PermissionsCheckerRule();

    private static final UiAutomation UI_AUTOMATION =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private CarAudioManager mCarAudioManager;
    private SyncCarVolumeCallback mCallback;
    private int mZoneId = -1;
    private int mVolumeGroupId = -1;
    private CarOccupantZoneManager mCarOccupantZoneManager;
    private TestPrimaryZoneMediaAudioRequestStatusCallback mRequestCallback;
    private long mMediaRequestId = INVALID_REQUEST_ID;

    // TODO(b/242350638): add missing annotations, remove (on child bug of 242350638)
    @Override
    protected void configApiCheckerRule(Builder builder) {
        Log.w(TAG, "Disabling API requirements check");
        builder.disableAnnotationsCheck();
    }

    @Before
    public void setUp() throws Exception {
        mCarAudioManager = getCar().getCarManager(CarAudioManager.class);
        mCarOccupantZoneManager = getCar().getCarManager(CarOccupantZoneManager.class);
    }

    @After
    public void cleanUp() {
        if (mCallback != null) {
            // Unregistering the last callback requires PERMISSION_CAR_CONTROL_AUDIO_VOLUME
            runWithCarControlAudioVolumePermission(
                    () -> mCarAudioManager.unregisterCarVolumeCallback(mCallback));
        }

        if (mMediaRequestId != INVALID_REQUEST_ID) {
            Log.w(TAG, "Cancelling media request " +  mMediaRequestId);
            mCarAudioManager.cancelMediaAudioOnPrimaryZone(mMediaRequestId);
        }

        if (mRequestCallback != null) {
            Log.w(TAG, "Releasing media request callback");
            mCarAudioManager.clearPrimaryZoneMediaAudioRequestCallback();
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
            int minIndex = mCarAudioManager.getGroupMinVolume(PRIMARY_AUDIO_ZONE, index);
            int maxIndex = mCarAudioManager.getGroupMaxVolume(PRIMARY_AUDIO_ZONE, index);
            CarVolumeGroupInfo info =
                    mCarAudioManager.getVolumeGroupInfo(PRIMARY_AUDIO_ZONE, index);
            expectWithMessage("Car volume group id for info %s and group %s", info, index)
                    .that(info.getId()).isEqualTo(index);
            expectWithMessage("Car volume group info zone for info %s and group %s",
                    info, index).that(info.getZoneId()).isEqualTo(PRIMARY_AUDIO_ZONE);
            expectWithMessage("Car volume group info max index for info %s and group %s",
                    info, index).that(info.getMaxVolumeGainIndex()).isEqualTo(maxIndex);
            expectWithMessage("Car volume group info min index for info %s and group %s",
                    info, index).that(info.getMinVolumeGainIndex()).isEqualTo(minIndex);
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

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#getAudioAttributesForVolumeGroup(CarVolumeGroupInfo)"})
    public void getAudioAttributesForVolumeGroup() {
        assumeDynamicRoutingIsEnabled();
        CarVolumeGroupInfo info =
                mCarAudioManager.getVolumeGroupInfo(PRIMARY_AUDIO_ZONE, /* groupId= */ 0);

        expectWithMessage("Car volume audio attributes")
                .that(mCarAudioManager.getAudioAttributesForVolumeGroup(info))
                .isNotEmpty();
    }

    @Test
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#getAudioAttributesForVolumeGroup(CarVolumeGroupInfo)"})
    public void getAudioAttributesForVolumeGroup_withoutPermission() {
        assumeDynamicRoutingIsEnabled();
        CarVolumeGroupInfo info;

        UI_AUTOMATION.adoptShellPermissionIdentity(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        try {
            info =  mCarAudioManager.getVolumeGroupInfo(PRIMARY_AUDIO_ZONE, /* groupId= */ 0);
        } finally {
            UI_AUTOMATION.dropShellPermissionIdentity();
        }

        Exception exception = assertThrows(SecurityException.class,
                () -> mCarAudioManager.getAudioAttributesForVolumeGroup(info));

        assertWithMessage("Car volume group audio attributes without permission exception")
                .that(exception).hasMessageThat().contains(PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#isMediaAudioAllowedInPrimaryZone(OccupantZoneInfo)"})
    public void isMediaAudioAllowedInPrimaryZone_byDefault() {
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        OccupantZoneInfo info =
                        mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);

        assertWithMessage("Default media allowed status")
                .that(mCarAudioManager.isMediaAudioAllowedInPrimaryZone(info)).isFalse();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#isMediaAudioAllowedInPrimaryZone(OccupantZoneInfo)"})
    public void isMediaAudioAllowedInPrimaryZone_afterAllowed() throws Exception {
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        setupMediaAudioRequestCallback();
        OccupantZoneInfo info =
                mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);
        requestToPlayMediaInPrimaryZone(info);
        mRequestCallback.receivedMediaRequest();
        mCarAudioManager.allowMediaAudioOnPrimaryZone(mMediaRequestId, /* allow= */ true);

        boolean approved = mCarAudioManager.isMediaAudioAllowedInPrimaryZone(info);

        assertWithMessage("Approved media allowed status")
                .that(approved).isTrue();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#isMediaAudioAllowedInPrimaryZone(OccupantZoneInfo)"})
    public void isMediaAudioAllowedInPrimaryZone_afterRejected() throws Exception {
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        setupMediaAudioRequestCallback();
        OccupantZoneInfo info =
                mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);
        requestToPlayMediaInPrimaryZone(info);
        mRequestCallback.receivedMediaRequest();
        mCarAudioManager.allowMediaAudioOnPrimaryZone(mMediaRequestId, /* allow= */ false);

        boolean approved = mCarAudioManager.isMediaAudioAllowedInPrimaryZone(info);

        assertWithMessage("Unapproved media allowed status")
                .that(approved).isFalse();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#setPrimaryZoneMediaAudioRequestCallback(Executor,"
            + "PrimaryZoneMediaAudioRequestCallback)"})
    public void setPrimaryZoneMediaAudioRequestCallback() {
        assumePassengerWithValidAudioZone();
        Executor executor = Executors.newFixedThreadPool(1);
        TestPrimaryZoneMediaAudioRequestStatusCallback
                requestCallback = new TestPrimaryZoneMediaAudioRequestStatusCallback();

        boolean registered =
                mCarAudioManager.setPrimaryZoneMediaAudioRequestCallback(executor, requestCallback);

        assertWithMessage("Set status of media request callback")
                .that(registered).isTrue();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#requestMediaAudioOnPrimaryZone(OccupantZoneInfo, Executor, "
            + "MediaAudioRequestStatusCallback)"})
    public void requestMediaAudioOnPrimaryZone() throws Exception {
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        setupMediaAudioRequestCallback();
        OccupantZoneInfo info =
                mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);
        Executor callbackExecutor = Executors.newFixedThreadPool(1);
        TestMediaAudioRequestStatusCallback callback = new TestMediaAudioRequestStatusCallback();

        mMediaRequestId =
                mCarAudioManager.requestMediaAudioOnPrimaryZone(info, callbackExecutor, callback);

        mRequestCallback.receivedMediaRequest();
        assertWithMessage("Received request id").that(mRequestCallback.mRequestId)
                .isEqualTo(mMediaRequestId);
        assertWithMessage("Received occupant info").that(mRequestCallback.mOccupantZoneInfo)
                .isEqualTo(info);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#requestMediaAudioOnPrimaryZone(OccupantZoneInfo,"
            + "Executor, MediaAudioRequestStatusCallback)"})
    public void requestMediaAudioOnPrimaryZone_withoutApprover() throws Exception {
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        OccupantZoneInfo info =
                mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);
        Executor callbackExecutor = Executors.newFixedThreadPool(1);
        TestMediaAudioRequestStatusCallback callback = new TestMediaAudioRequestStatusCallback();

        mMediaRequestId =
                mCarAudioManager.requestMediaAudioOnPrimaryZone(info, callbackExecutor, callback);

        callback.receivedApproval();
        assertWithMessage("Request id for rejected request")
                .that(callback.mRequestId).isEqualTo(mMediaRequestId);
        assertWithMessage("Rejected request status").that(callback.mStatus)
                .isEqualTo(CarAudioManager.AUDIO_REQUEST_STATUS_REJECTED);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#allowMediaAudioOnPrimaryZone(long, boolean)"})
    public void allowMediaAudioOnPrimaryZone() throws Exception {
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        setupMediaAudioRequestCallback();
        OccupantZoneInfo info =
                mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);
        TestMediaAudioRequestStatusCallback callback =
                requestToPlayMediaInPrimaryZone(info);
        mRequestCallback.receivedMediaRequest();

        boolean succeeded = mCarAudioManager.allowMediaAudioOnPrimaryZone(mMediaRequestId,
                /* allow= */ true);

        callback.receivedApproval();
        assertWithMessage("Approved results for request in primary zone")
                .that(succeeded).isTrue();
        assertWithMessage("Approved request id in primary zone")
                .that(callback.mRequestId).isEqualTo(mMediaRequestId);
        assertWithMessage("Approved request occuapnt in primary zone")
                .that(callback.mOccupantZoneInfo).isEqualTo(info);
        assertWithMessage("Audio status in primary zone").that(callback.mStatus)
                .isEqualTo(CarAudioManager.AUDIO_REQUEST_STATUS_APPROVED);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#allowMediaAudioOnPrimaryZone(long, boolean)"})
    public void allowMediaAudioOnPrimaryZone_withReject() throws Exception {
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        setupMediaAudioRequestCallback();
        OccupantZoneInfo info =
                mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);
        TestMediaAudioRequestStatusCallback callback = requestToPlayMediaInPrimaryZone(info);
        mRequestCallback.receivedMediaRequest();

        boolean succeeded = mCarAudioManager.allowMediaAudioOnPrimaryZone(mMediaRequestId,
                /* allow= */ false);

        callback.receivedApproval();
        long tempRequestId = mMediaRequestId;
        mMediaRequestId = INVALID_REQUEST_ID;
        assertWithMessage("Unapproved results for request in primary zone")
                .that(succeeded).isTrue();
        assertWithMessage("Unapproved request id in primary zone")
                .that(callback.mRequestId).isEqualTo(tempRequestId);
        assertWithMessage("Unapproved audio status in primary zone").that(callback.mStatus)
                .isEqualTo(CarAudioManager.AUDIO_REQUEST_STATUS_REJECTED);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#allowMediaAudioOnPrimaryZone(long, boolean)"})
    public void allowMediaAudioOnPrimaryZone_withInvalidRequestId() throws Exception {
        assumePassengerWithValidAudioZone();
        setupMediaAudioRequestCallback();

        boolean succeeded = mCarAudioManager
                        .allowMediaAudioOnPrimaryZone(INVALID_REQUEST_ID, /* allow= */ true);

        assertWithMessage("Invalid request id allowed results")
                .that(succeeded).isFalse();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#cancelMediaAudioOnPrimaryZone(long)"})
    public void cancelMediaAudioOnPrimaryZone() throws Exception {
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        setupMediaAudioRequestCallback();
        OccupantZoneInfo info =
                mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);
        TestMediaAudioRequestStatusCallback callback = requestToPlayMediaInPrimaryZone(info);
        mRequestCallback.receivedMediaRequest();

        mCarAudioManager.cancelMediaAudioOnPrimaryZone(mMediaRequestId);

        mMediaRequestId = INVALID_REQUEST_ID;
        callback.receivedApproval();
        assertWithMessage("Cancelled audio status in primary zone")
                .that(callback.mStatus).isEqualTo(CarAudioManager.AUDIO_REQUEST_STATUS_CANCELLED);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#resetMediaAudioOnPrimaryZone(OccupantZoneInfo)"})
    public void resetMediaAudioOnPrimaryZone() throws Exception {
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        setupMediaAudioRequestCallback();
        OccupantZoneInfo info =
                mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);
        TestMediaAudioRequestStatusCallback callback = requestToPlayMediaInPrimaryZone(info);
        mRequestCallback.receivedMediaRequest();
        mCarAudioManager.allowMediaAudioOnPrimaryZone(mMediaRequestId, /* allow= */ true);
        callback.receivedApproval();
        callback.reset();

        mCarAudioManager.resetMediaAudioOnPrimaryZone(info);

        long tempRequestId = mMediaRequestId;
        mMediaRequestId = INVALID_REQUEST_ID;
        callback.receivedApproval();
        assertWithMessage("Reset request id in primary zone")
                .that(callback.mRequestId).isEqualTo(tempRequestId);
        assertWithMessage("Reset audio status in primary zone")
                .that(callback.mStatus).isEqualTo(CarAudioManager.AUDIO_REQUEST_STATUS_STOPPED);
    }

    private TestMediaAudioRequestStatusCallback requestToPlayMediaInPrimaryZone(
            OccupantZoneInfo info) {
        Executor callbackExecutor = Executors.newFixedThreadPool(1);
        TestMediaAudioRequestStatusCallback callback = new TestMediaAudioRequestStatusCallback();
        mMediaRequestId =
                mCarAudioManager.requestMediaAudioOnPrimaryZone(info, callbackExecutor, callback);
        return callback;
    }

    private void setupMediaAudioRequestCallback() {
        Executor requestExecutor = Executors.newFixedThreadPool(1);
        mRequestCallback = new TestPrimaryZoneMediaAudioRequestStatusCallback();
        mCarAudioManager.setPrimaryZoneMediaAudioRequestCallback(requestExecutor, mRequestCallback);
    }

    private int assumePassengerWithValidAudioZone() {
        int passengerAudioZoneId = getAvailablePassengerAudioZone();
        assumeTrue("Need passenger with audio zone id to share audio",
                passengerAudioZoneId != INVALID_AUDIO_ZONE);

        return passengerAudioZoneId;
    }

    private int getAvailablePassengerAudioZone() {
        return mCarOccupantZoneManager.getAllOccupantZones().stream()
                .map(occupant -> mCarOccupantZoneManager.getAudioZoneIdForOccupant(occupant))
                .filter(audioZoneId -> audioZoneId != INVALID_AUDIO_ZONE
                        && audioZoneId != PRIMARY_AUDIO_ZONE)
                .findFirst().orElse(INVALID_AUDIO_ZONE);
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

        boolean receivedGroupVolumeChanged() {
            return silentAwait(mGroupVolumeChangeLatch, WAIT_TIMEOUT_MS);
        }

        boolean receivedGroupMuteChanged() {
            return silentAwait(mGroupMuteChangeLatch, WAIT_TIMEOUT_MS);
        }

        boolean receivedMasterMuteChanged() {
            return silentAwait(mMasterMuteChangeLatch, WAIT_TIMEOUT_MS);
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

    private static final class TestPrimaryZoneMediaAudioRequestStatusCallback implements
            PrimaryZoneMediaAudioRequestCallback {

        private final CountDownLatch mRequestAudioLatch = new CountDownLatch(1);
        private long mRequestId;
        private OccupantZoneInfo mOccupantZoneInfo;

        @Override
        public void onRequestMediaOnPrimaryZone(OccupantZoneInfo info, long requestId) {
            mOccupantZoneInfo = info;
            mRequestId = requestId;
            Log.v(TAG, "onRequestMediaOnPrimaryZone info " + info + " request id " + requestId);
            mRequestAudioLatch.countDown();
        }

        @Override
        public void onMediaAudioRequestStatusChanged(OccupantZoneInfo info,
                long requestId, int status) {
        }

        void receivedMediaRequest() throws InterruptedException {
            await(mRequestAudioLatch, WAIT_TIMEOUT_MS);
        }
    }

    private static final class TestMediaAudioRequestStatusCallback implements
            MediaAudioRequestStatusCallback {

        private CountDownLatch mRequestAudioLatch = new CountDownLatch(1);
        private long mRequestId;
        private OccupantZoneInfo mOccupantZoneInfo;
        private int mStatus;

        void receivedApproval() throws InterruptedException {
            await(mRequestAudioLatch, WAIT_TIMEOUT_MS);
        }

        void reset() {
            mRequestAudioLatch = new CountDownLatch(1);
            mRequestId = INVALID_REQUEST_ID;
            mOccupantZoneInfo = null;
            mStatus = 0;
        }

        @Override
        public void onMediaAudioRequestStatusChanged(OccupantZoneInfo info,
                long requestId, int status) {
            mOccupantZoneInfo = info;
            mRequestId = requestId;
            mStatus = status;
            Log.v(TAG, "onMediaAudioRequestStatusChanged info " + info + " request id "
                    + requestId + " status " + status);
            mRequestAudioLatch.countDown();
        }
    }
}
