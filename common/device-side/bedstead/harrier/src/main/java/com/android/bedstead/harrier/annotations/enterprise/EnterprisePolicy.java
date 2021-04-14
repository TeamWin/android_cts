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

package com.android.bedstead.harrier.annotations.enterprise;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to annotate an enterprise policy for use with {@link NegativePolicyTest} and
 * {@link PositivePolicyTest}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnterprisePolicy {
    enum DeviceOwnerControl {
        /** A policy that can be applied by a Device Owner to all users on the device. */
        GLOBAL,
        /** A policy that can be applied by a Device Owner to only the Device Owner's user. */
        USER,
        /** A policy that cannot be applied by a Device Owner. */
        NO
    }

    enum ProfileOwnerControl {
        /** A policy that can be applied by a Profile Owner to the profile itself and its parent. */
        PARENT,
        /**
         * A policy that can be applied by a Profile Owner to the profile itself, and to the
         * parent if it is a COPE profile.
         */
        COPE_PARENT,
        /** A policy that can be applied by a Profile Owner to the profile itself. */
        PROFILE,
        /** A policy that cannot be applied by a Profile Owner. */
        NO
    }

    DeviceOwnerControl deviceOwner();
    ProfileOwnerControl profileOwner();
}
