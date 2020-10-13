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

import static com.android.compatibility.common.util.enterprise.DeviceState.UserType.CURRENT_USER;

import com.android.compatibility.common.util.enterprise.DeviceState;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that a test method should run on a user which has a work profile.
 *
 * <p>Use of this annotation implies {@code RequireFeatures("android.software.managed_users")}.
 *
 * <p>Your test configuration may be configured so that this test is only runs on a user which has
 * a work profile. Otherwise, you can use {@link DeviceState} to ensure that the device enters
 * the correct state for the method.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnsureHasWorkProfile {
    /** Which user type should the work profile be attached to. */
    DeviceState.UserType forUser() default CURRENT_USER;

    /** Should the test app be installed in the work profile. */
    boolean installTestApp() default true;
}