/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.cts.rollback.lib;

import android.content.Context;
import android.content.Intent;
import android.content.pm.VersionedPackage;
import android.content.rollback.PackageRollbackInfo;
import android.content.rollback.RollbackInfo;
import android.content.rollback.RollbackManager;

import androidx.test.InstrumentationRegistry;

import com.android.cts.install.lib.LocalIntentSender;
import com.android.cts.install.lib.TestApp;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilities to facilitate testing rollbacks.
 */
public class RollbackUtils {

    /**
     * Gets the RollbackManager for the instrumentation context.
     */
    public static RollbackManager getRollbackManager() {
        Context context = InstrumentationRegistry.getContext();
        RollbackManager rm = (RollbackManager) context.getSystemService(Context.ROLLBACK_SERVICE);
        if (rm == null) {
            throw new AssertionError("Failed to get RollbackManager");
        }
        return rm;
    }

    /**
     * Returns a rollback for the given package name in the list of
     * rollbacks. Returns null if there are no available rollbacks, and throws
     * an assertion if there is more than one.
     */
    private static RollbackInfo getRollback(List<RollbackInfo> rollbacks, String packageName) {
        RollbackInfo found = null;
        for (RollbackInfo rollback : rollbacks) {
            for (PackageRollbackInfo info : rollback.getPackages()) {
                if (packageName.equals(info.getPackageName())) {
                    if (found != null) {
                        throw new AssertionError("Multiple available matching rollbacks found");
                    }
                    found = rollback;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Returns a rollback for the given rollback Id, if found. Otherwise, returns null.
     */
    private static RollbackInfo getRollbackById(List<RollbackInfo> rollbacks, int rollbackId) {
        for (RollbackInfo rollback :rollbacks) {
            if (rollback.getRollbackId() == rollbackId) {
                return rollback;
            }
        }
        return null;
    }

    /**
     * Returns an available rollback for the given package name. Returns null
     * if there are no available rollbacks, and throws an assertion if there
     * is more than one.
     */
    public static RollbackInfo getAvailableRollback(String packageName) {
        RollbackManager rm = getRollbackManager();
        return getRollback(rm.getAvailableRollbacks(), packageName);
    }

    /**
     * Returns a recently committed rollback for the given package name. Returns null
     * if there are no available rollbacks, and throws an assertion if there
     * is more than one.
     */
    public static RollbackInfo getCommittedRollback(String packageName) {
        RollbackManager rm = getRollbackManager();
        return getRollback(rm.getRecentlyCommittedRollbacks(), packageName);
    }

    /**
     * Returns a recently committed rollback for the given rollback Id.
     * Returns null if no committed rollback with a matching Id was found.
     */
    public static RollbackInfo getCommittedRollbackById(int rollbackId) {
        RollbackManager rm = getRollbackManager();
        return getRollbackById(rm.getRecentlyCommittedRollbacks(), rollbackId);
    }

    /**
     * Commit the given rollback.
     * @throws AssertionError if the rollback fails.
     */
    public static void rollback(int rollbackId, TestApp... causePackages)
            throws InterruptedException {
        List<VersionedPackage> causes = new ArrayList<>();
        for (TestApp cause : causePackages) {
            causes.add(cause.getVersionedPackage());
        }

        RollbackManager rm = getRollbackManager();
        rm.commitRollback(rollbackId, causes, LocalIntentSender.getIntentSender());
        Intent result = LocalIntentSender.getIntentSenderResult();
        int status = result.getIntExtra(RollbackManager.EXTRA_STATUS,
                RollbackManager.STATUS_FAILURE);
        if (status != RollbackManager.STATUS_SUCCESS) {
            String message = result.getStringExtra(RollbackManager.EXTRA_STATUS_MESSAGE);
            throw new AssertionError(message);
        }
    }
}

