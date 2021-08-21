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

package android.server.wm;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.IBinder;
import android.util.ArrayMap;
import android.window.TaskFragmentAppearedInfo;
import android.window.TaskFragmentCreationParams;
import android.window.TaskFragmentInfo;
import android.window.TaskFragmentOrganizer;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TaskFragmentOrganizerTestBase extends WindowManagerTestBase {
    public BasicTaskFragmentOrganizer mTaskFragmentOrganizer;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mTaskFragmentOrganizer = new BasicTaskFragmentOrganizer();
        mTaskFragmentOrganizer.registerOrganizer();
    }

    @After
    public void tearDown() {
        mTaskFragmentOrganizer.unregisterOrganizer();
    }

    public static IBinder getActivityToken(@NonNull Activity activity) {
        return activity.getWindow().getAttributes().token;
    }

    public static void assertEmptyTaskFragment(TaskFragmentInfo info,
            IBinder expectedTaskFragToken) {
        assertTaskFragmentInfoValidity(info, expectedTaskFragToken);
        assertWithMessage("TaskFragment must be empty").that(info.isEmpty()).isTrue();
        assertWithMessage("TaskFragmentInfo#getActivities must be empty")
                .that(info.getActivities()).isEmpty();
        assertWithMessage("TaskFragment must not contain any running Activity")
                .that(info.hasRunningActivity()).isFalse();
        assertWithMessage("TaskFragment must not be visible").that(info.isVisible()).isFalse();
    }

    public static void assertNotEmptyTaskFragment(TaskFragmentInfo info,
            IBinder expectedTaskFragToken, @Nullable IBinder ... expectedActivityTokens) {
        assertTaskFragmentInfoValidity(info, expectedTaskFragToken);
        assertWithMessage("TaskFragment must not be empty").that(info.isEmpty()).isFalse();
        assertWithMessage("TaskFragment must contain running Activity")
                .that(info.hasRunningActivity()).isTrue();
        if (expectedActivityTokens != null) {
            assertWithMessage("TaskFragmentInfo#getActivities must be empty")
                    .that(info.getActivities()).containsAtLeastElementsIn(expectedActivityTokens);
        }
    }

    private static void assertTaskFragmentInfoValidity(TaskFragmentInfo info,
            IBinder expectedTaskFragToken) {
        assertWithMessage("TaskFragmentToken must match the token from "
                + "TaskFragmentCreationParams#getFragmentToken")
                .that(info.getFragmentToken()).isEqualTo(expectedTaskFragToken);
        assertWithMessage("WindowContainerToken must not be null")
                .that(info.getToken()).isNotNull();
        assertWithMessage("TaskFragmentInfo#getPositionInParent must not be null")
                .that(info.getPositionInParent()).isNotNull();
        assertWithMessage("Configuration must not be empty")
                .that(info.getConfiguration()).isNotEqualTo(new Configuration());
    }

    public static class BasicTaskFragmentOrganizer extends TaskFragmentOrganizer {
        private final static int WAIT_TIMEOUT_IN_SECOND = 10;

        private final Map<IBinder, TaskFragmentInfo> mInfos = new ArrayMap<>();
        private final Map<IBinder, TaskFragmentInfo> mRemovedInfos = new ArrayMap<>();
        private IBinder mTaskFragToken;
        private Configuration mParentConfig;
        private IBinder mErrorToken;
        private Throwable mThrowable;
        private final List<WindowContainerToken> mKnownTaskFragments = new ArrayList<>();

        private CountDownLatch mAppearedLatch = new CountDownLatch(1);
        private CountDownLatch mChangedLatch = new CountDownLatch(1);
        private CountDownLatch mVanishedLatch = new CountDownLatch(1);
        private CountDownLatch mParentChangedLatch = new CountDownLatch(1);
        private CountDownLatch mErrorLatch = new CountDownLatch(1);

        BasicTaskFragmentOrganizer() {
            super(Runnable::run);
        }

        public TaskFragmentInfo getTaskFragmentInfo(IBinder taskFragToken) {
            return mInfos.get(taskFragToken);
        }

        public TaskFragmentInfo getRemovedTaskFragmentInfo(IBinder taskFragToken) {
            return mRemovedInfos.get(taskFragToken);
        }

        public Throwable getThrowable() {
            return mThrowable;
        }

        public IBinder getErrorCallbackToken() {
            return mErrorToken;
        }

        /**
         * Generates a {@link TaskFragmentCreationParams} with {@code ownerToken} specified.
         *
         * @param ownerToken The token of {@link Activity} to create a TaskFragment under its parent
         *                   Task
         * @return the generated {@link TaskFragmentCreationParams}
         */
        @NonNull
        public TaskFragmentCreationParams generateTaskFragParams(@NonNull IBinder ownerToken) {
            return new TaskFragmentCreationParams.Builder(getOrganizerToken(), new Binder(),
                    ownerToken).build();
        }

        public void waitForTaskFragmentCreated() {
            try {
                assertThat(mAppearedLatch.await(WAIT_TIMEOUT_IN_SECOND, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                fail("Assertion failed because of" + e);
            }
        }

        public void waitForTaskFragmentChanged() {
            try {
                assertThat(mChangedLatch.await(WAIT_TIMEOUT_IN_SECOND, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                fail("Assertion failed because of" + e);
            }
        }

        public void waitForTaskFragmentRemoved() {
            try {
                assertThat(mVanishedLatch.await(WAIT_TIMEOUT_IN_SECOND, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                fail("Assertion failed because of" + e);
            }
        }

        public void waitForParentConfigChanged() {
            try {
                assertThat(mParentChangedLatch.await(WAIT_TIMEOUT_IN_SECOND, TimeUnit.SECONDS))
                        .isTrue();
            } catch (InterruptedException e) {
                fail("Assertion failed because of" + e);
            }
        }

        public void waitForTaskFragmentError() {
            try {
                assertThat(mErrorLatch.await(WAIT_TIMEOUT_IN_SECOND, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                fail("Assertion failed because of" + e);
            }
        }

        private void removeAllTaskFragments() {
            if (mKnownTaskFragments.isEmpty()) {
                // Skip if there's no organized TaskFragment.
                return;
            }
            mVanishedLatch = new CountDownLatch(mKnownTaskFragments.size());
            final WindowContainerTransaction wct = new WindowContainerTransaction();
            for (WindowContainerToken token : mKnownTaskFragments) {
                wct.deleteTaskFragment(token);
            }
            applyTransaction(wct);
            waitForTaskFragmentRemoved();
        }

        @Override
        public void unregisterOrganizer() {
            removeAllTaskFragments();
            mRemovedInfos.clear();
            super.unregisterOrganizer();
        }

        @Override
        public void onTaskFragmentAppeared(
                @NonNull TaskFragmentAppearedInfo taskFragmentAppearedInfo) {
            super.onTaskFragmentAppeared(taskFragmentAppearedInfo);
            final TaskFragmentInfo info = taskFragmentAppearedInfo.getTaskFragmentInfo();
            mInfos.put(info.getFragmentToken(), info);
            mKnownTaskFragments.add(info.getToken());
            mAppearedLatch.countDown();
        }

        @Override
        public void onTaskFragmentInfoChanged(@NonNull TaskFragmentInfo taskFragmentInfo) {
            super.onTaskFragmentInfoChanged(taskFragmentInfo);
            mInfos.put(taskFragmentInfo.getFragmentToken(), taskFragmentInfo);
            mChangedLatch.countDown();
        }

        @Override
        public void onTaskFragmentVanished(@NonNull TaskFragmentInfo taskFragmentInfo) {
            super.onTaskFragmentVanished(taskFragmentInfo);
            mInfos.remove(taskFragmentInfo.getFragmentToken());
            mRemovedInfos.put(taskFragmentInfo.getFragmentToken(), taskFragmentInfo);
            mKnownTaskFragments.remove(taskFragmentInfo.getToken());
            mVanishedLatch.countDown();
        }

        @Override
        public void onTaskFragmentParentInfoChanged(@NonNull IBinder fragmentToken,
                @NonNull Configuration parentConfig) {
            super.onTaskFragmentParentInfoChanged(fragmentToken, parentConfig);
            mTaskFragToken = fragmentToken;
            mParentConfig = parentConfig;
            mParentChangedLatch.countDown();
        }

        @Override
        public void onTaskFragmentError(@NonNull IBinder errorCallbackToken,
                @NonNull Throwable exception) {
            super.onTaskFragmentError(errorCallbackToken, exception);
            mErrorToken = errorCallbackToken;
            mThrowable = exception;
            mErrorLatch.countDown();
        }
    }
}
