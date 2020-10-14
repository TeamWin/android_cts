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

package com.android.compatibility.common.util.enterprise.annotations;

import com.android.compatibility.common.util.enterprise.DeviceState;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that a test method should run on a device which has a secondary user that is not the
 * current user.
 *
 * <p>Your test configuration may be configured so that this test is only runs on a device which
 * has a secondary user that is not the current user. Otherwise, you can use {@link DeviceState}
 * to ensure that the device enters the correct state for the method. If there is not already a
 * secondary user on the device, and the device does not support creating additional users, then
 * the test will be skipped.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnsureHasSecondaryUser {
    /** Should the test app be installed in the secondary user. */
    boolean installTestApp() default true;
}
