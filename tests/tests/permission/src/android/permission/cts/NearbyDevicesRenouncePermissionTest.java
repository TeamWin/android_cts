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

package android.permission.cts;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.app.AppOpsManager.OPSTR_FINE_LOCATION;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import android.app.AppOpsManager;
import android.app.AsyncNotedAppOp;
import android.app.SyncNotedAppOp;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.ContextParams;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.util.ArraySet;
import android.util.Base64;
import android.util.Log;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests behaviour when performing bluetooth scans with renounced location permission.
 */
public class NearbyDevicesRenouncePermissionTest {

    private static final String TAG = "NearbyDevicesRenouncePermissionTest";
    private static final String OPSTR_BLUETOOTH_SCAN = "android:bluetooth_scan";

    private AppOpsManager mAppOpsManager;
    private int mLocationNoteCount;
    private int mScanNoteCount;

    private enum Result {
        UNKNOWN, EXCEPTION, EMPTY, FILTERED, FULL
    }

    @BeforeClass
    public static void enableTestMode() {
        runShellCommand("dumpsys activity service"
                + " com.android.bluetooth/.btservice.AdapterService set-test-mode enabled");
    }

    @AfterClass
    public static void disableTestMode() {
        runShellCommand("dumpsys activity service"
                + " com.android.bluetooth/.btservice.AdapterService set-test-mode disabled");
    }

    @Before
    public void setUp() {
        mAppOpsManager = getApplicationContext().getSystemService(AppOpsManager.class);
        mAppOpsManager.setOnOpNotedCallback(getApplicationContext().getMainExecutor(),
                new AppOpsManager.OnOpNotedCallback() {
                    @Override
                    public void onNoted(SyncNotedAppOp op) {
                        switch (op.getOp()) {
                            case OPSTR_FINE_LOCATION:
                                mLocationNoteCount++;
                                break;
                            case OPSTR_BLUETOOTH_SCAN:
                                mScanNoteCount++;
                                break;
                            default:
                        }
                    }

                    @Override
                    public void onSelfNoted(SyncNotedAppOp op) {
                    }

                    @Override
                    public void onAsyncNoted(AsyncNotedAppOp asyncOp) {
                    }
                });
    }

    @After
    public void tearDown() {
        mAppOpsManager.setOnOpNotedCallback(null, null);
    }

    private void clearNoteCounts() {
        mLocationNoteCount = 0;
        mScanNoteCount = 0;
    }

    @AppModeFull
    @Test
    public void scanWithoutRenouncingNotesBluetoothAndLocation() {
        clearNoteCounts();
        assertThat(performScan(false)).isEqualTo(Result.FULL);
        assertThat(mLocationNoteCount).isGreaterThan(0);
        assertThat(mScanNoteCount).isGreaterThan(0);
    }

    @AppModeFull
    @Test
    public void scanRenouncingLocationNotesBluetoothButNotLocation() {
        clearNoteCounts();
        assertThat(performScan(true)).isEqualTo(Result.FILTERED);
        assertThat(mLocationNoteCount).isEqualTo(0);
        assertThat(mScanNoteCount).isGreaterThan(0);
    }

    private Result performScan(boolean renounce) {
        try {
            Context context = renounce ? createRenouncingContext(getApplicationContext())
                    : getApplicationContext();

            final BluetoothManager bm = context.getSystemService(BluetoothManager.class);
            final BluetoothLeScanner scanner = bm.getAdapter().getBluetoothLeScanner();

            final HashSet<String> observed = new HashSet<>();

            ScanCallback callback = new ScanCallback() {
                public void onScanResult(int callbackType, ScanResult result) {
                    Log.v(TAG, String.valueOf(result));
                    observed.add(Base64.encodeToString(result.getScanRecord().getBytes(), 0));
                }

                public void onBatchScanResults(List<ScanResult> results) {
                    for (ScanResult result : results) {
                        onScanResult(0, result);
                    }
                }
            };
            scanner.startScan(callback);

            // Wait a few seconds to figure out what we actually observed
            SystemClock.sleep(3000);
            scanner.stopScan(callback);
            switch (observed.size()) {
                case 0: return Result.EMPTY;
                case 1: return Result.FILTERED;
                case 5: return Result.FULL;
                default: return Result.UNKNOWN;
            }
        } catch (Throwable t) {
            Log.v(TAG, "Failed to scan", t);
            return Result.EXCEPTION;
        }
    }

    private Context createRenouncingContext(Context context) throws Exception {
        ContextParams contextParams = context.getParams();

        Set<String> renouncedPermissions = new ArraySet<>();
        renouncedPermissions.add(ACCESS_FINE_LOCATION);

        return SystemUtil.callWithShellPermissionIdentity(() -> {
            if (contextParams == null) {
                return context.createContext(
                        new ContextParams.Builder()
                                .setRenouncedPermissions(renouncedPermissions)
                                .setAttributionTag(context.getAttributionTag())
                                .build());
            } else {
                return context.createContext(
                        new ContextParams.Builder(contextParams)
                                .setRenouncedPermissions(renouncedPermissions)
                                .build());
            }
        });
    }

}
