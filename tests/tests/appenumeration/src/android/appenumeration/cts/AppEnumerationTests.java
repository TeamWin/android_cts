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

package android.appenumeration.cts;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteCallback;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class AppEnumerationTests {

    private static final String TARGET_NO_API_NAME = "android.appenumeration.noapi";
    private static final String TARGET_FORCEQUERYABLE_NAME = "android.appenumeration.forcequeryable";
    private static final String TARGET_FILTERS_NAME = "android.appenumeration.filters";

    private static final String QUERIES_NOTHING_NAME = "android.appenumeration.queries.nothing";

    private Context mContext;
    private Handler mResponseHandler;
    private HandlerThread mResponseThread;

    private boolean mGlobalFeatureEnabled;

    @Before
    public void setup() {
        mGlobalFeatureEnabled = Boolean.parseBoolean(SystemUtil.runShellCommand(
                "device_config get package_manager_service package_query_filtering_enabled"));
        if (!mGlobalFeatureEnabled) return;

        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mResponseThread = new HandlerThread("response");
        mResponseThread.start();
        mResponseHandler = new Handler(mResponseThread.getLooper());

        enableFeatureForPackage(QUERIES_NOTHING_NAME);
    }

    private void enableFeatureForPackage(String packageName) {
        final String response =
                SystemUtil.runShellCommand("am compat enable 135549675 " + packageName);
        assertTrue(response.contains("Enabled"));
    }

    @After
    public void tearDown() {
        if (!mGlobalFeatureEnabled) return;
        mResponseThread.quit();
    }

    @Test
    public void queriesNothing_canSeeForceQueryable() throws Exception {
        if (!mGlobalFeatureEnabled) return;
        final PackageInfo packageInfo =
                getPackageInfo(QUERIES_NOTHING_NAME, TARGET_FORCEQUERYABLE_NAME);
        Assert.assertTrue(packageInfo != null);
    }

    @Test
    public void queriesNothing_cannotSeeNoApi() throws Exception {
        if (!mGlobalFeatureEnabled) return;
        try {
            getPackageInfo(QUERIES_NOTHING_NAME, TARGET_NO_API_NAME);
            fail("App that queries nothing should not see other packages.");
        } catch (PackageManager.NameNotFoundException e) {
        }
    }

    private PackageInfo getPackageInfo(String sourcePackageName, String targetPackageName)
            throws Exception {
        Bundle response = sendCommand(sourcePackageName, targetPackageName,
                "android.appenumeration.cts.action.GET_PACKAGE_INFO");
        return response.getParcelable(Intent.EXTRA_RETURN_RESULT);
    }

    private Bundle sendCommand(String sourcePackageName, String targetPackageName, String action)
            throws Exception {
        Intent intent = new Intent(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, targetPackageName)
                .setComponent(new ComponentName(
                        sourcePackageName, "android.appenumeration.cts.query.TestActivity"));
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicReference<Bundle> resultReference = new AtomicReference<>();
        RemoteCallback callback = new RemoteCallback(
                bundle -> {
                    resultReference.set(bundle);
                    countDownLatch.countDown();
                },
                mResponseHandler);
        intent.putExtra("remoteCallback", callback);
        mContext.startActivity(intent);
        if (!countDownLatch.await(5, TimeUnit.SECONDS)) {
            throw new TimeoutException("Latch timed out!");
        }
        final Bundle bundle = resultReference.get();
        if (bundle != null && bundle.containsKey("error")) {
            throw (Exception) Objects.requireNonNull(bundle.getSerializable("error"));
        }
        return bundle;
    }

}
