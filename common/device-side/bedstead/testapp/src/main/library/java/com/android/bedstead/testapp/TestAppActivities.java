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

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.android.bedstead.nene.TestApis;
import com.android.queryable.info.ActivityInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * Entry point to activity querying.
 */
public final class TestAppActivities {

    private static final TestApis sTestApis = new TestApis();

    final TestAppInstanceReference mInstance;
    Set<ActivityInfo> mActivities = new HashSet<>();

    static TestAppActivities create(TestAppInstanceReference instance) {
        TestAppActivities activities = new TestAppActivities(instance);
        activities.initActivities();
        return activities;
    }


    private TestAppActivities(TestAppInstanceReference instance) {
        mInstance = instance;
    }

    private void initActivities() {
        mActivities = new HashSet<>();

        PackageManager p = sTestApis.context().instrumentedContext().getPackageManager();
        try {
            PackageInfo packageInfo = p.getPackageInfo(mInstance.testApp().packageName(), /* flags= */ PackageManager.GET_ACTIVITIES);
            for (android.content.pm.ActivityInfo activityInfo : packageInfo.activities) {
                mActivities.add(new com.android.queryable.info.ActivityInfo(activityInfo));
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException("Cannot query activities if app is not installed");
        }
    }

    /**
     * Return any activity included in the test app Manifest.
     *
     * <p>Currently, this will always return the same activity.
     */
    public TestAppActivityReference any() {
        return query().get();
    }

    public TestAppActivitiesQueryBuilder query() {
        return new TestAppActivitiesQueryBuilder(this);
    }
}
