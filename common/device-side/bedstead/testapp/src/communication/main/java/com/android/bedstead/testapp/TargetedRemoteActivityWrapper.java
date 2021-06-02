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

import com.android.bedstead.nene.exceptions.NeneException;

import com.google.android.enterprise.connectedapps.CrossProfileConnector;
import com.google.android.enterprise.connectedapps.exceptions.ProfileRuntimeException;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;

/** Internal class which will be generated. */
public final class TargetedRemoteActivityWrapper implements TargetedRemoteActivity {

    private final ProfileTargetedRemoteActivity mProfileTargetedRemoteActivity;
    private final CrossProfileConnector mConnector;

    public TargetedRemoteActivityWrapper(CrossProfileConnector connector) {
        mConnector = connector;
        mProfileTargetedRemoteActivity = ProfileTargetedRemoteActivity.create(connector);
    }

    @Override
    public void startLockTask(String activityClassName) {
        try {
            mConnector.connect();
            mProfileTargetedRemoteActivity.other().startLockTask(activityClassName);
        } catch (UnavailableProfileException e) {
            throw new NeneException("Error connecting to test app", e);
        } catch (ProfileRuntimeException e) {
            throw (RuntimeException) e.getCause();
        } finally {
            mConnector.stopManualConnectionManagement();
        }
    }

    @Override
    public void stopLockTask(String activityClassName) {
        try {
            mConnector.connect();
            mProfileTargetedRemoteActivity.other().stopLockTask(activityClassName);
        } catch (UnavailableProfileException e) {
            throw new NeneException("Error connecting to test app", e);
        } catch (ProfileRuntimeException e) {
            throw (RuntimeException) e.getCause();
        } finally {
            mConnector.stopManualConnectionManagement();
        }
    }

    @Override
    public boolean isFinishing(String activityClassName) {
        try {
            mConnector.connect();
            return mProfileTargetedRemoteActivity.other().isFinishing(activityClassName);
        } catch (UnavailableProfileException e) {
            throw new NeneException("Error connecting to test app", e);
        } catch (ProfileRuntimeException e) {
            throw (RuntimeException) e.getCause();
        } finally {
            mConnector.stopManualConnectionManagement();
        }
    }
}
