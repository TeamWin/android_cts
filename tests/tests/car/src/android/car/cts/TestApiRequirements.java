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

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import android.car.PlatformVersion;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Tells in which version of car API this method / type / field was added.
 *
 * <p> For items marked with this, the client need to make sure to check car API version using
 * {@link android.car.Car#getCarApiVersion()}. This annotation will only be used by Car-lib APIs.
 *
 * @hide
 */
@Retention(RUNTIME)
@Target({ANNOTATION_TYPE, FIELD, TYPE, METHOD})
public @interface TestApiRequirements {

    // TODO(b/237015981): Later this annotation may not be mandatory as this information can be
    // extracted from ApiTest annotation.
    TestPlatformVersion minPlatformVersion();

    enum TestPlatformVersion {
        /**
         * For TIRAMISU main release.
         */
        TIRAMISU_0(PlatformVersion.VERSION_CODES.TIRAMISU_0),
        /**
         * For first minor release after TIRAMISU.
         */
        TIRAMISU_1(PlatformVersion.VERSION_CODES.TIRAMISU_1),
        /**
         * For UPSIDE DOWN CAKE main release.
         */
        UPSIDE_DOWN_CAKE_0(PlatformVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0);

        private final PlatformVersion mVersion;

        TestPlatformVersion(PlatformVersion version) {
            mVersion = version;
        }

        public boolean isAtLeast(PlatformVersion currentPlatformApiVersion) {
            return currentPlatformApiVersion.isAtLeast(mVersion);
        }
    }
}
