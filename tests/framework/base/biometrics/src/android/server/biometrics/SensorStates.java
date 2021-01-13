/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.biometrics;

import android.util.SparseArray;

import androidx.annotation.NonNull;

import com.android.server.biometrics.nano.SensorServiceStateProto;
import com.android.server.biometrics.nano.SensorStateProto;
import com.android.server.biometrics.nano.UserStateProto;

import java.util.List;

/**
 * The overall state for a list of sensors. This could be either:
 *
 * 1) A list of sensors from a single instance of a <Biometric>Service such as
 * {@link com.android.server.biometrics.sensors.fingerprint.FingerprintService} or
 * {@link com.android.server.biometrics.sensors.face.FaceService}, or
 *
 * 2) A list of sensors from multiple instances of <Biometric>Services.
 *
 * Note that a single service may provide multiple sensors.
 */
public class SensorStates {

    @NonNull public final SparseArray<SensorState> sensorStates;

    public static class SensorState {
        private final boolean mIsBusy;
        private final int mModality;
        @NonNull private final SparseArray<UserState> mUserStates;

        public SensorState(boolean isBusy, int modality, @NonNull SparseArray<UserState> userStates) {
            this.mIsBusy = isBusy;
            this.mModality = modality;
            this.mUserStates = userStates;
        }

        public boolean isBusy() {
            return mIsBusy;
        }

        public int getModality() {
            return mModality;
        }

        @NonNull public SparseArray<UserState> getUserStates() {
            return mUserStates;
        }
    }

    public static class UserState {
        public final int numEnrolled;

        public UserState(int numEnrolled) {
            this.numEnrolled = numEnrolled;
        }
    }

    @NonNull
    public static SensorStates parseFrom(@NonNull SensorServiceStateProto proto) {
        final SparseArray<SensorState> sensorStates = new SparseArray<>();

        for (SensorStateProto sensorStateProto : proto.sensorStates) {
            final SparseArray<UserState> userStates = new SparseArray<>();
            for (UserStateProto userStateProto : sensorStateProto.userStates) {
                userStates.put(userStateProto.userId, new UserState(userStateProto.numEnrolled));
            }

            final SensorState sensorState = new SensorState(sensorStateProto.isBusy,
                    sensorStateProto.modality, userStates);
            sensorStates.put(sensorStateProto.sensorId, sensorState);
        }

        return new SensorStates(sensorStates);
    }

    /**
     * Combines multiple {@link SensorStates} into a single instance.
     */
    @NonNull
    public static SensorStates merge(@NonNull List<SensorStates> sensorServiceStates) {
        final SparseArray<SensorState> sensorStates = new SparseArray<>();

        for (SensorStates sensorServiceState : sensorServiceStates) {
            for (int i = 0; i < sensorServiceState.sensorStates.size(); i++) {
                final int sensorId = sensorServiceState.sensorStates.keyAt(i);
                final SensorState sensorState = sensorServiceState.sensorStates.valueAt(i);
                if (sensorStates.contains(sensorId)) {
                    throw new IllegalStateException("Duplicate sensorId found: " + sensorId);
                }

                sensorStates.put(sensorId, sensorState);
            }
        }

        return new SensorStates(sensorStates);
    }

    public boolean areAllSensorsIdle() {
        for (int i = 0; i < sensorStates.size(); i++) {
            if (sensorStates.valueAt(i).isBusy()) {
                return false;
            }
        }
        return true;
    }

    public boolean containsModality(int modality) {
        for (int i = 0; i < sensorStates.size(); i++) {
            if (sensorStates.valueAt(i).getModality() == modality) {
                return true;
            }
        }
        return false;
    }

    private SensorStates(@NonNull SparseArray<SensorState> sensorStates) {
        this.sensorStates = sensorStates;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sensorStates.size(); i++) {
            sb.append("{SensorId: ").append(sensorStates.keyAt(i));
            sb.append(", Busy: ").append(sensorStates.valueAt(i).isBusy());

            final SparseArray<UserState> userStates = sensorStates.valueAt(i).getUserStates();
            for (int j = 0; j < userStates.size(); j++) {
                sb.append(", UserId: ").append(userStates.keyAt(j));
                sb.append(", NumEnrolled: ").append(userStates.get(j).numEnrolled);
            }

            sb.append("} ");
        }
        return sb.toString();
    }
}
