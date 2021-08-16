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

    /** A policy that cannot be applied. */
    int NO = 0;

    // Applies to

    // Note that profiles are a subset of users
    int APPLIES_TO_OWN_USER = 1;
    int APPLIES_TO_UNAFFILIATED_OTHER_USERS = 1 << 1;
    int APPLIES_TO_AFFILIATED_OTHER_USERS = 1 << 2;
    int APPLIES_TO_UNAFFILIATED_CHILD_PROFILES = 1 << 3;
    int APPLIES_TO_AFFILIATED_CHILD_PROFILES = 1 << 4;

    /** A policy that can be applied by a Profile Owner to the parent of the profile owner. */
    int APPLIES_TO_PARENT = 1 << 5;

    /**
     * A policy that can be applied by a Profile Owner to the parent of the profile owner if the
     * profile is a COPE profile.
     */
    int APPLIES_TO_COPE_PARENT = 1 << 6;

    int APPLIES_TO_CHILD_PROFILES =
            APPLIES_TO_UNAFFILIATED_CHILD_PROFILES | APPLIES_TO_AFFILIATED_CHILD_PROFILES;
    int APPLIES_TO_OTHER_USERS =
            APPLIES_TO_UNAFFILIATED_OTHER_USERS | APPLIES_TO_AFFILIATED_OTHER_USERS;

    /** A policy that can be applied to all users on the device. */
    int APPLIES_GLOBALLY = APPLIES_TO_OWN_USER | APPLIES_TO_OTHER_USERS | APPLIES_TO_CHILD_PROFILES;


    // Applied by

    int APPLIED_BY_DEVICE_OWNER = 1 << 7;

    /** A policy that can be applied by a profile owner of an unaffiliated profile. */
    int APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE = 1 << 8;

    /** A policy that can be applied by a profile owner of an affiliated profile */
    int APPLIED_BY_AFFILIATED_PROFILE_OWNER_PROFILE = 1 << 9;

    int APPLIED_BY_PROFILE_OWNER_PROFILE =
            APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE
                    | APPLIED_BY_AFFILIATED_PROFILE_OWNER_PROFILE;

    /**
     * A policy that can be applied by a Profile Owner for a User (not Profile) with no Device
     * Owner.
     */
    int APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO = 1 << 10;

    /**
     * A policy that can be applied by an unaffiliated Profile Owner for a User (not Profile) with
     * a Device Owner.
     */
    int APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER_WITH_DO = 1 << 11;

    /** A policy that can be applied by a profile owner of an unaffiliated user. */
    int APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER =
            APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO
                    | APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER_WITH_DO;

    /** A policy that can be applied by a profile owner of an affiliated user. */
    int APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER = 1 << 12;

    int APPLIED_BY_PROFILE_OWNER_USER =
            APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER | APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER;

    int APPLIED_BY_PROFILE_OWNER =
            APPLIED_BY_PROFILE_OWNER_PROFILE
            | APPLIED_BY_PROFILE_OWNER_USER;


    // Modifiers

    int APPLIES_IN_BACKGROUND = 1 << 13;
    int DOES_NOT_APPLY_IN_BACKGROUND = 1 << 14;

    int deviceOwner();
    int profileOwner();
}
