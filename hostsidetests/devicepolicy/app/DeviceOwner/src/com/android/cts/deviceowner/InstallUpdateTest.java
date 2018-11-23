/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.cts.deviceowner;

import android.app.admin.DevicePolicyManager;
import android.net.Uri;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link android.app.admin.DevicePolicyManager#installSystemUpdate}
 */
public class InstallUpdateTest extends BaseDeviceOwnerTest {

    public static final String TEST_SYSTEM_UPDATES_DIR =
            "/data/local/tmp/cts/deviceowner/";
    public static final int TIMEOUT = 5;

    public void testInstallUpdate_failFileNotFound() throws InterruptedException {
        assertUpdateError(
                "random",
                DevicePolicyManager.InstallUpdateCallback.UPDATE_ERROR_FILE_NOT_FOUND);

    }

    public void testInstallUpdate_failWrongVersion() throws InterruptedException {
        assertUpdateError(
                "wrongVersion.zip",
                DevicePolicyManager.InstallUpdateCallback.UPDATE_ERROR_INCORRECT_OS_VERSION);
    }

    public void testInstallUpdate_failNoZipOtaFile() throws InterruptedException {
        assertUpdateError("notZip.zi",
                DevicePolicyManager.InstallUpdateCallback.UPDATE_ERROR_UPDATE_FILE_INVALID);
    }

    public void testInstallUpdate_failWrongPayloadFile() throws InterruptedException {
        assertUpdateError("wrongPayload.zip",
                DevicePolicyManager.InstallUpdateCallback.UPDATE_ERROR_UPDATE_FILE_INVALID);
    }

    public void testInstallUpdate_failEmptyOtaFile() throws InterruptedException {
        assertUpdateError("empty.zip",
                DevicePolicyManager.InstallUpdateCallback.UPDATE_ERROR_UPDATE_FILE_INVALID);
    }

    public void testInstallUpdate_failWrongHash() throws InterruptedException {
        assertUpdateError("wrongHash.zip",
                DevicePolicyManager.InstallUpdateCallback.UPDATE_ERROR_UPDATE_FILE_INVALID);
    }

    public void testInstallUpdate_failWrongSize() throws InterruptedException {
        assertUpdateError("wrongSize.zip",
                DevicePolicyManager.InstallUpdateCallback.UPDATE_ERROR_UPDATE_FILE_INVALID);
    }

    private void assertUpdateError(String fileName, int expectedErrorCode)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Uri uri = Uri.fromFile(new File(TEST_SYSTEM_UPDATES_DIR, fileName));
        mDevicePolicyManager.installSystemUpdate(getWho(), uri,
                Runnable::run, new DevicePolicyManager.InstallUpdateCallback() {
                    @Override
                    public void onInstallUpdateError(int errorCode, String errorMessage) {
                        try {
                            assertEquals(expectedErrorCode, errorCode);
                        } finally {
                            latch.countDown();
                        }
                    }
                });
        assertTrue(latch.await(TIMEOUT, TimeUnit.MINUTES));
    }
}