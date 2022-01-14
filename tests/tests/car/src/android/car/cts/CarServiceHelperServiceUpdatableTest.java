/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.fail;

import android.car.Car;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.os.NewUserRequest;
import android.os.NewUserResponse;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class CarServiceHelperServiceUpdatableTest extends CarApiTestBase {

    private static final String TAG = CarServiceHelperServiceUpdatableTest.class.getSimpleName();

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testSendUserLifecycleEvent() throws Exception {
        // Add listener to check if user started
        CarUserManager carUserManager = (CarUserManager) getCar()
                .getCarManager(Car.CAR_USER_SERVICE);
        LifecycleListener listener = new LifecycleListener();
        carUserManager.addListener(Runnable::run, listener);

        NewUserResponse response = null;
        UserManager userManager = null;
        try {
            // get create User permissions
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(android.Manifest.permission.CREATE_USERS);

            // CreateUser
            userManager = mContext.getSystemService(UserManager.class);
            response = userManager.createUser(new NewUserRequest.Builder().build());
            assertThat(response.isSuccessful()).isTrue();

            int userId = response.getUser().getIdentifier();
            startUser(userId);
            listener.assertEventReceived(userId, CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING,
                    /* timeoutMs= */ 60_000);
        } finally {
            userManager.removeUser(response.getUser());
            carUserManager.removeListener(listener);
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    // TODO(214100537): Improve listener by removing sleep.
    private final class LifecycleListener implements UserLifecycleListener {

        private final List<UserLifecycleEvent> mEvents =
                new ArrayList<CarUserManager.UserLifecycleEvent>();

        private final Object mLock = new Object();

        @Override
        public void onEvent(UserLifecycleEvent event) {
            Log.d(TAG, "Event received: " + event);
            synchronized (mLock) {
                mEvents.add(event);
            }
        }

        public void assertEventReceived(int userId, int eventType, int timeoutMs)
                throws InterruptedException {
            long startTime = SystemClock.elapsedRealtime();
            while (SystemClock.elapsedRealtime() - startTime < timeoutMs) {
                boolean result = checkEvent(userId, eventType);
                if (result) return;
                Thread.sleep(1000);
            }

            fail("Event" + eventType + " was not received within timeoutMs: " + timeoutMs);
        }

        private boolean checkEvent(int userId, int eventType) {
            synchronized (mLock) {
                for (int i = 0; i < mEvents.size(); i++) {
                    if (mEvents.get(i).getUserHandle().getIdentifier() == userId
                            && mEvents.get(i).getEventType() == eventType) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
