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
package android.cloudsearch.cts;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertNotNull;

import android.app.cloudsearch.CloudSearchManager;
import android.app.cloudsearch.SearchRequest;
import android.app.cloudsearch.SearchResponse;
import android.content.Context;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredServiceRule;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link CloudSearchManager}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class CloudSearchManagerTest implements CloudSearchManager.CallBack {

    private static final String TAG = "CloudSearchManagerTest";
    private static final boolean DEBUG = true;

    private static final long SERVICE_LIFECYCLE_TIMEOUT_MS = 40_000;

    @Rule
    public final RequiredServiceRule mRequiredServiceRule =
            new RequiredServiceRule(Context.CLOUDSEARCH_SERVICE);

    private CloudSearchManager mManager;
    private CtsCloudSearchService.Watcher mWatcher;

    @Before
    public void setUp() throws Exception {
        mWatcher = CtsCloudSearchService.setWatcher();
        mManager = getContext().getSystemService(CloudSearchManager.class);
        setService(CtsCloudSearchService.SERVICE_NAME);

        mManager.search(CloudSearchTestUtils.getBasicSearchRequest(""),
                Executors.newSingleThreadExecutor(), this);
        await(mWatcher.created, "Waiting for onCreate()");
    }

    @After
    public void tearDown() throws Exception {
        Log.d(TAG, "Starting tear down, watcher is: " + mWatcher);
        setService(null);
        mWatcher = null;
        CtsCloudSearchService.clearWatcher();
    }

    @Test
    public void testCloudSearchServiceConnection() {
        assertNotNull(mManager);
        await(mWatcher.queried, "Waiting for search()");
    }

    @Test
    public void testSuccessfulSearch() {
        assertNotNull(mManager);
        await(mWatcher.queried, "Waiting for search()");
        mManager.search(CloudSearchTestUtils.getBasicSearchRequest("Successful"),
                Executors.newSingleThreadExecutor(), this);
        await(mWatcher.succeeded, "Waiting for successful search");
    }

    @Test
    public void testUnsuccessfulSearch() {
        assertNotNull(mManager);
        await(mWatcher.queried, "Waiting for search()");
        mManager.search(CloudSearchTestUtils.getBasicSearchRequest("Unsuccessful"),
                Executors.newSingleThreadExecutor(), this);
        await(mWatcher.failed, "Waiting for unsuccessful search");
    }

    @Test
    public void testMultipleCallbacksSearch() {
        assertNotNull(mManager);
        await(mWatcher.queried, "Waiting for search()");
        mManager.search(CloudSearchTestUtils.getBasicSearchRequest("Unsuccessful and Successful"),
                Executors.newSingleThreadExecutor(), this);
        // TODO(216520546) add a condition to send a SearchRequest without
        //  CtsCloudSearchServiceas a provider.
        await(mWatcher.failed, "Waiting for unsuccessful search");
        await(mWatcher.succeeded, "Waiting for successful search");
    }

    private void setService(String service) {
        Log.d(TAG, "Setting cloudsearch service to " + service);
        int userId = Process.myUserHandle().getIdentifier();
        if (service != null) {
            runShellCommand("cmd cloudsearch set temporary-service "
                    + userId + " " + service + " 60000");
        } else {
            runShellCommand("cmd cloudsearch set temporary-service " + userId);
        }
    }

    private void await(@NonNull CountDownLatch latch, @NonNull String message) {
        try {
            assertWithMessage(message).that(
                    latch.await(SERVICE_LIFECYCLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while: " + message);
        }
    }

    private void runShellCommand(String command) {
        Log.d(TAG, "runShellCommand(): " + command);
        try {
            SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(), command);
        } catch (Exception e) {
            throw new RuntimeException("Command '" + command + "' failed: ", e);
        }
    }

    @Override
    public void onSearchSucceeded(SearchRequest request, SearchResponse response) {
        if (response.getStatusCode() == SearchResponse.SEARCH_STATUS_OK) {
            mWatcher.succeeded.countDown();
        }
    }

    @Override
    public void onSearchFailed(SearchRequest request, SearchResponse response) {
        if (response.getStatusCode() == SearchResponse.SEARCH_STATUS_NO_INTERNET) {
            mWatcher.failed.countDown();
        }
    }
}
