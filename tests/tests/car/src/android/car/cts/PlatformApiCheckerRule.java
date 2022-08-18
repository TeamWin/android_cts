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

package android.car.cts;

import static org.junit.Assert.assertThrows;

import android.car.Car;
import android.car.PlatformVersion;
import android.car.PlatformVersionMismatchException;
import android.car.cts.TestApiRequirements.TestPlatformVersion;
import android.util.Log;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;

/**
 * Rule for running CTS tests against different platform version. If a test have annotation
 * {@link TestApiRequirements}, then tests should be passed for all platform version great than or
 * equal to {@link TestApiRequirements#minPlatformVersion()}, also test should throw
 * {@link PlatformVersionMismatchException} for all platform lower than
 * {@link TestApiRequirements#minPlatformVersion()}.
 *
 * @deprecated use {@code android.car.test.ApiCheckerRule} instead
 */
@Deprecated
public final class PlatformApiCheckerRule implements TestRule {

    private static final String TAG = PlatformApiCheckerRule.class.getSimpleName();

    private static final boolean DEBUG = false;

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                TestApiRequirements apiRequirement = null;
                for (Annotation annotation : description.getAnnotations()) {
                    if (annotation instanceof TestApiRequirements) {
                        apiRequirement = (TestApiRequirements) annotation;
                        break;
                    }
                }

                if (apiRequirement == null) {
                    if (DEBUG) {
                        Log.d(TAG, "Didn't find TestApiRequirements annotation. Running test: "
                                + description.getMethodName());
                    }
                    base.evaluate();
                    return;
                }

                TestPlatformVersion expectedVersion = apiRequirement.minPlatformVersion();
                PlatformVersion currentPlatformApiVersion = Car.getPlatformVersion();
                if (expectedVersion.isAtLeast(currentPlatformApiVersion)) {
                    if (DEBUG) {
                        Log.d(TAG, "Found TestApiRequirements annotation. Expected platform: "
                                + expectedVersion + ", current platform: "
                                + currentPlatformApiVersion + ". Running test: "
                                + description.getMethodName());
                    }
                } else {
                    if (DEBUG) {
                        Log.d(TAG, "Found TestApiRequirements annotation. Expected platform: "
                                + expectedVersion + ", current platform: "
                                + currentPlatformApiVersion + ". Expecting exception while "
                                + "running test: " + description.getMethodName());
                    }
                    assertThrows(PlatformVersionMismatchException.class, () -> base.evaluate());
                }
            }
        };
    }
}
