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

import android.content.Context;
import android.util.Log;
import android.os.Bundle;

import com.android.bedstead.nene.TestApis;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/** Entry point to Test App. Used for querying for {@link TestApp} instances. */
public final class TestAppProvider {

    private static final String TAG = TestAppProvider.class.getSimpleName();

    private static final TestApis sTestApis = new TestApis();
    // Must be instrumentation context to access resources
    private static final Context sContext = sTestApis.context().instrumentationContext();

    private boolean mTestAppsInitialised = false;
    private final Set<TestAppDetails> mTestApps = new HashSet<>();

    /** Begin a query for a {@link TestApp}. */
    public TestAppQueryBuilder query() {
        return new TestAppQueryBuilder(this);
    }

    /** Get any {@link TestApp}. */
    public TestApp any() {
        TestApp testApp = query().get();
        Log.d(TAG, "any(): returning " + testApp);
        return testApp;
    }

    Set<TestAppDetails> testApps() {
        initTestApps();
        Log.d(TAG, "testApps(): returning " + mTestApps.size() + " apps (" + mTestApps + ")");
        return mTestApps;
    }

    private void initTestApps() {
        if (mTestAppsInitialised) {
            return;
        }
        mTestAppsInitialised = true;

        int indexId = sContext.getResources().getIdentifier(
                "raw/index", /* defType= */ null, sContext.getPackageName());

        try (InputStream inputStream = sContext.getResources().openRawResource(indexId)) {
            TestappProtos.TestAppIndex index = TestappProtos.TestAppIndex.parseFrom(inputStream);
            for (int i = 0; i < index.getAppsCount(); i++) {
                loadApk(index.getApps(i));
            }
        } catch (IOException e) {
            throw new RuntimeException("Error loading testapp index", e);
        }
    }

    private void loadApk(TestappProtos.AndroidApp app) {
        TestAppDetails details = new TestAppDetails();
        details.mApp = app;
        details.mResourceIdentifier = sContext.getResources().getIdentifier(
                "raw/" + app.getApkName().split("\\.", 2)[0], /* defType= */ null, sContext.getPackageName());

        // TODO(scottjonathan): Actually index the metadata -
        //  right now this is hardcoded for remoteDPC
        details.mMetadata = new Bundle();
        if (details.mApp.getPackageName().equals("com.android.RemoteDPC")) {
            details.mMetadata.putBoolean("testapp-package-query-only", true);
        }

        mTestApps.add(details);
    }

    void markTestAppUsed(TestAppDetails testApp) {
        mTestApps.remove(testApp);
    }
}
