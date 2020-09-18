/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.compatibility.common.util.enterprise;

import static org.junit.Assume.assumeTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.enterprise.annotations.EnsureHasSecondaryUser;
import com.android.compatibility.common.util.enterprise.annotations.EnsureHasWorkProfile;
import com.android.compatibility.common.util.enterprise.annotations.RequireFeatures;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnPrimaryUser;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnSecondaryUser;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnWorkProfile;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;


/**
 * A Junit rule which enforces preconditions in annotations from the
 * {@code com.android.comaptibility.common.util.enterprise.annotations} package.
 *
 * {@code assumeTrue} will be used, so tests which do not meet preconditions will be skipped.
 */
public final class Preconditions implements TestRule {

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final DeviceState mDeviceState;

    public Preconditions(DeviceState deviceState) {
        mDeviceState = deviceState;
    }

    @Override public Statement apply(final Statement base,
            final Description description) {
        return new Statement() {
            @Override public void evaluate() throws Throwable {
                if (description.getAnnotation(RequireRunOnPrimaryUser.class) != null) {
                    assumeTrue("@RequireRunOnPrimaryUser tests only run on primary user",
                            mDeviceState.isRunningOnPrimaryUser());
                }
                if (description.getAnnotation(RequireRunOnWorkProfile.class) != null) {
                    assumeTrue("@RequireRunOnWorkProfile tests only run on work profile",
                            mDeviceState.isRunningOnWorkProfile());
                }
                if (description.getAnnotation(RequireRunOnSecondaryUser.class) != null) {
                    assumeTrue("@RequireRunOnSecondaryUser tests only run on secondary user",
                            mDeviceState.isRunningOnSecondaryUser());
                }
                EnsureHasWorkProfile ensureHasWorkAnnotation =
                        description.getAnnotation(EnsureHasWorkProfile.class);
                if (ensureHasWorkAnnotation != null) {
                    mDeviceState.ensureHasWorkProfile(
                            /* installTestApp= */ ensureHasWorkAnnotation.installTestApp(),
                            /* forUser= */ ensureHasWorkAnnotation.forUser()
                    );
                }
                EnsureHasSecondaryUser ensureHasSecondaryUserAnnotation =
                        description.getAnnotation(EnsureHasSecondaryUser.class);
                if (ensureHasSecondaryUserAnnotation != null) {
                    mDeviceState.ensureHasSecondaryUser(
                            /* installTestApp= */ ensureHasSecondaryUserAnnotation.installTestApp()
                    );
                }
                RequireFeatures requireFeaturesAnnotation =
                        description.getAnnotation(RequireFeatures.class);
                if (requireFeaturesAnnotation != null) {
                    for (String feature: requireFeaturesAnnotation.featureNames()) {
                        requireFeature(feature);
                    }
                }

                base.evaluate();
            }
        };
    }

    private void requireFeature(String feature) {
        assumeTrue("Device must have feature " + feature,
                mContext.getPackageManager().hasSystemFeature(feature));
    }
}
