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

package com.android.bedstead.testapp;

/** Internal class which will be generated. */
public class RemoteActivityImpl implements RemoteActivity {
    private final String mActivityClassName;
    private final TargetedRemoteActivity mTargetedRemoteActivity;

    public RemoteActivityImpl(
            String activityClassName, TargetedRemoteActivity targetedRemoteActivity) {
        mActivityClassName = activityClassName;
        mTargetedRemoteActivity = targetedRemoteActivity;
    }

    @Override
    public void startLockTask() {
        mTargetedRemoteActivity.startLockTask(mActivityClassName);
    }

    @Override
    public void stopLockTask() {
        mTargetedRemoteActivity.stopLockTask(mActivityClassName);
    }

    @Override
    public boolean isFinishing() {
        return mTargetedRemoteActivity.isFinishing(mActivityClassName);
    }
}
