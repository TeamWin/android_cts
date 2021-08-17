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

import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.IBinder;
import android.util.ArrayMap;
import android.window.TaskFragmentAppearedInfo;
import android.window.TaskFragmentInfo;
import android.window.TaskFragmentOrganizer;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;

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

    public static class BasicTaskFragmentOrganizer extends TaskFragmentOrganizer {
        private final static int WAIT_TIMEOUT_IN_SECOND = 10;

        private final Map<IBinder, TaskFragmentInfo> mInfos = new ArrayMap<>();
        private final Map<IBinder, TaskFragmentInfo> mRemovedInfos = new ArrayMap<>();
        private IBinder mTaskFragToken;
        private Configuration mParentConfig;
        private final List<WindowContainerToken> mKnownTaskFragments = new ArrayList<>();

        private CountDownLatch mAppearedLatch = new CountDownLatch(1);
        private CountDownLatch mChangedLatch = new CountDownLatch(1);
        private CountDownLatch mVanishedLatch = new CountDownLatch(1);
        private CountDownLatch mParentChangedLatch = new CountDownLatch(1);

        BasicTaskFragmentOrganizer() {
            super(Runnable::run);
        }

        public TaskFragmentInfo getTaskFragmentInfo(IBinder taskFragToken) {
            return mInfos.get(taskFragToken);
        }

        public TaskFragmentInfo getRemovedTaskFragmentInfo(IBinder taskFragToken) {
            return mRemovedInfos.get(taskFragToken);
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
    }
}
