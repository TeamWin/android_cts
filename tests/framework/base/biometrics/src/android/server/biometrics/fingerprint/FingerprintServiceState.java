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

package android.server.biometrics.fingerprint;

import android.util.SparseArray;

import androidx.annotation.NonNull;

import com.android.server.biometrics.fingerprint.nano.FingerprintServiceStateProto;
import com.android.server.biometrics.fingerprint.nano.SensorStateProto;
import com.android.server.biometrics.fingerprint.nano.UserStateProto;

public class FingerprintServiceState {

    @NonNull final SparseArray<SensorState> sensorStates;

    public static class SensorState {
        private final boolean mIsBusy;
        @NonNull private final SparseArray<UserState> mUserStates;

        public SensorState(boolean isBusy, @NonNull SparseArray<UserState> userStates) {
            this.mIsBusy = isBusy;
            this.mUserStates = userStates;
        }

        boolean isBusy() {
            return mIsBusy;
        }

        @NonNull SparseArray<UserState> getUserStates() {
            return mUserStates;
        }
    }

    public static class UserState {
        final int numEnrolled;

        public UserState(int numEnrolled) {
            this.numEnrolled = numEnrolled;
        }
    }

    @NonNull
    public static FingerprintServiceState parseFrom(@NonNull FingerprintServiceStateProto proto) {
        final SparseArray<SensorState> sensorStates = new SparseArray<>();

        for (SensorStateProto sensorStateProto : proto.sensorStates) {
            final SparseArray<UserState> userStates = new SparseArray<>();
            for (UserStateProto userStateProto : sensorStateProto.userStates) {
                userStates.put(userStateProto.userId, new UserState(userStateProto.numEnrolled));
            }

            final SensorState sensorState = new SensorState(sensorStateProto.isBusy, userStates);
            sensorStates.put(sensorStateProto.sensorId, sensorState);
        }

        return new FingerprintServiceState(sensorStates);
    }

    public boolean areAllSensorsIdle() {
        for (int i = 0; i < sensorStates.size(); i++) {
            if (sensorStates.valueAt(i).isBusy()) {
                return false;
            }
        }
        return true;
    }

    private FingerprintServiceState(@NonNull SparseArray<SensorState> sensorStates) {
        this.sensorStates = sensorStates;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sensorStates.size(); i++) {
            sb.append("SensorId: ").append(sensorStates.keyAt(i));
            sb.append(", busy: ").append(sensorStates.get(i).isBusy());

            final SparseArray<UserState> userStates = sensorStates.get(i).getUserStates();
            for (int j = 0; j < userStates.size(); j++) {
                sb.append(", UserId: ").append(userStates.keyAt(j));
                sb.append(", NumEnrolled: ").append(userStates.get(j).numEnrolled);
            }

            sb.append(" | ");
        }
        return sb.toString();
    }
}
