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

package com.android.bedstead.remotedpc.managers;

import android.content.ComponentName;

import androidx.annotation.NonNull;

import com.android.bedstead.nene.exceptions.NeneException;

import com.google.android.enterprise.connectedapps.CrossProfileConnector;
import com.google.android.enterprise.connectedapps.exceptions.ProfileRuntimeException;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;


/**
 * Implementation of {@link RemoteDevicePolicyManager} which uses the Connected Apps SDK internally.
 */
public class RemoteDevicePolicyManagerWrapper implements RemoteDevicePolicyManager {

    private final ProfileRemoteDevicePolicyManager mProfileRemoteDevicePolicyManager;
    private final CrossProfileConnector mConnector;

    public RemoteDevicePolicyManagerWrapper(CrossProfileConnector connector) {
        mConnector = connector;
        mProfileRemoteDevicePolicyManager = ProfileRemoteDevicePolicyManager.create(connector);
    }

    @Override
    public boolean isUsingUnifiedPassword(@NonNull ComponentName admin) {
        try {
            mConnector.connect();
            return mProfileRemoteDevicePolicyManager.other().isUsingUnifiedPassword(admin);
        } catch (UnavailableProfileException e) {
            e.printStackTrace();
            throw new NeneException("Error connecting to RemoteDPC", e);
        } catch (ProfileRuntimeException e) {
            throw (RuntimeException) e.getCause();
        }
    }

    @Override
    public boolean isUsingUnifiedPassword() {
        try {
            mConnector.connect();
            return mProfileRemoteDevicePolicyManager.other().isUsingUnifiedPassword();
        } catch (UnavailableProfileException e) {
            e.printStackTrace();
            throw new NeneException("Error connecting to RemoteDPC", e);
        } catch (ProfileRuntimeException e) {
            throw (RuntimeException) e.getCause();
        }
    }

    @Override
    public int getCurrentFailedPasswordAttempts() {
        try {
            mConnector.connect();
            return mProfileRemoteDevicePolicyManager.other().getCurrentFailedPasswordAttempts();
        } catch (UnavailableProfileException e) {
            e.printStackTrace();
            throw new NeneException("Error connecting to RemoteDPC", e);
        } catch (ProfileRuntimeException e) {
            throw (RuntimeException) e.getCause();
        }
    }
}
