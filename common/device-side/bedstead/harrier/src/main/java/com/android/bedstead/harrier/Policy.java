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

package com.android.bedstead.harrier;

import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_AFFILIATED_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_DEVICE_OWNER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_IN_BACKGROUND;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_AFFILIATED_CHILD_PROFILES;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_AFFILIATED_OTHER_USERS;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_COPE_PARENT;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_OTHER_USERS;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_OWN_USER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_PARENT;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_UNAFFILIATED_CHILD_PROFILES;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_UNAFFILIATED_OTHER_USERS;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.DOES_NOT_APPLY_IN_BACKGROUND;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.NO;

import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy;
import com.android.bedstead.harrier.annotations.parameterized.IncludeNone;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnAffiliatedDeviceOwnerSecondaryUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnAffiliatedProfileOwnerSecondaryUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnBackgroundDeviceOwnerUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnDeviceOwnerUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnNonAffiliatedDeviceOwnerSecondaryUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnParentOfCorporateOwnedProfileOwner;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnParentOfProfileOwnerWithNoDeviceOwner;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnProfileOwnerPrimaryUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnProfileOwnerProfileWithNoDeviceOwner;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile;

import com.google.auto.value.AutoAnnotation;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class for enterprise policy tests.
 */
public final class Policy {

    private Policy() {

    }

    @AutoAnnotation
    private static IncludeNone includeNone() {
        return new AutoAnnotation_Policy_includeNone();
    }

    @AutoAnnotation
    private static IncludeRunOnDeviceOwnerUser includeRunOnDeviceOwnerUser() {
        return new AutoAnnotation_Policy_includeRunOnDeviceOwnerUser();
    }

    @AutoAnnotation
    private static IncludeRunOnNonAffiliatedDeviceOwnerSecondaryUser includeRunOnNonAffiliatedDeviceOwnerSecondaryUser() {
        return new AutoAnnotation_Policy_includeRunOnNonAffiliatedDeviceOwnerSecondaryUser();
    }

    @AutoAnnotation
    private static IncludeRunOnAffiliatedDeviceOwnerSecondaryUser includeRunOnAffiliatedDeviceOwnerSecondaryUser() {
        return new AutoAnnotation_Policy_includeRunOnAffiliatedDeviceOwnerSecondaryUser();
    }

    @AutoAnnotation
    private static IncludeRunOnAffiliatedProfileOwnerSecondaryUser includeRunOnAffiliatedProfileOwnerSecondaryUser() {
        return new AutoAnnotation_Policy_includeRunOnAffiliatedProfileOwnerSecondaryUser();
    }

    @AutoAnnotation
    private static IncludeRunOnProfileOwnerProfileWithNoDeviceOwner includeRunOnProfileOwnerProfileWithNoDeviceOwner() {
        return new AutoAnnotation_Policy_includeRunOnProfileOwnerProfileWithNoDeviceOwner();
    }

    @AutoAnnotation
    private static IncludeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile() {
        return new AutoAnnotation_Policy_includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile();
    }

    @AutoAnnotation
    private static IncludeRunOnParentOfProfileOwnerWithNoDeviceOwner includeRunOnParentOfProfileOwnerWithNoDeviceOwner() {
        return new AutoAnnotation_Policy_includeRunOnParentOfProfileOwnerWithNoDeviceOwner();
    }

    @AutoAnnotation
    private static IncludeRunOnParentOfCorporateOwnedProfileOwner includeRunOnParentOfCorporateOwnedProfileOwner() {
        return new AutoAnnotation_Policy_includeRunOnParentOfCorporateOwnedProfileOwner();
    }

    @AutoAnnotation
    private static IncludeRunOnProfileOwnerPrimaryUser includeRunOnProfileOwnerPrimaryUser() {
        return new AutoAnnotation_Policy_includeRunOnProfileOwnerPrimaryUser();
    }

    @AutoAnnotation
    private static IncludeRunOnBackgroundDeviceOwnerUser includeRunOnBackgroundDeviceOwnerUser() {
        return new AutoAnnotation_Policy_includeRunOnBackgroundDeviceOwnerUser();
    }

    private static final int VALID_DEVICE_OWNER_FLAGS =
            APPLIES_TO_OWN_USER | APPLIES_IN_BACKGROUND
                    | DOES_NOT_APPLY_IN_BACKGROUND | APPLIES_TO_UNAFFILIATED_OTHER_USERS
                    | APPLIES_TO_AFFILIATED_OTHER_USERS | APPLIES_TO_UNAFFILIATED_CHILD_PROFILES
                    | APPLIES_TO_AFFILIATED_CHILD_PROFILES | APPLIED_BY_DEVICE_OWNER;
    private static final int VALID_PROFILE_OWNER_FLAGS =
            APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE
            | APPLIED_BY_AFFILIATED_PROFILE_OWNER_PROFILE
                    | APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER
                    | APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER
                    | APPLIES_TO_AFFILIATED_OTHER_USERS
                    | APPLIES_TO_OWN_USER
                    | APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO
                    | APPLIES_TO_PARENT
                    | APPLIES_TO_OTHER_USERS
                    | APPLIES_TO_COPE_PARENT
                    | APPLIES_IN_BACKGROUND
                    | DOES_NOT_APPLY_IN_BACKGROUND;

    /**
     * Get positive state annotations for the given policy.
     *
     * <p>These are states which should be run where the policy is able to be applied.
     */
    public static List<Annotation> positiveStates(EnterprisePolicy enterprisePolicy) {
        Set<Annotation> annotations = new HashSet<>();

        validateDeviceOwnerFlags(enterprisePolicy.deviceOwner());
        validateProfileOwnerFlags(enterprisePolicy.profileOwner());

        deviceOwnerPositiveStates(enterprisePolicy.deviceOwner(), annotations);
        profileOwnerPositiveStates(enterprisePolicy.profileOwner(), annotations);

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        return new ArrayList<>(annotations);
    }

    private static void deviceOwnerPositiveStates(int flags, Set<Annotation> annotations) {
        if (!hasFlag(flags, APPLIED_BY_DEVICE_OWNER)) {
            return;
        }

        if (hasFlag(flags, APPLIES_TO_OWN_USER)) {
            annotations.add(includeRunOnDeviceOwnerUser());

            if (hasFlag(flags, APPLIES_IN_BACKGROUND)) {
                annotations.add(includeRunOnBackgroundDeviceOwnerUser());
            }
        }

        if (hasFlag(flags, APPLIES_TO_UNAFFILIATED_OTHER_USERS)) {
            annotations.add(includeRunOnNonAffiliatedDeviceOwnerSecondaryUser());
        }

        if (hasFlag(flags, APPLIES_TO_AFFILIATED_OTHER_USERS)) {
            annotations.add(includeRunOnAffiliatedDeviceOwnerSecondaryUser());
        }
    }

    private static void profileOwnerPositiveStates(int flags, Set<Annotation> annotations) {
        if (hasFlag(flags, APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER)) {
            if (hasFlag(flags, APPLIES_TO_OWN_USER)) {
                annotations.add(includeRunOnAffiliatedProfileOwnerSecondaryUser());
            }
        }

        if (hasFlag(flags, APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER)) {
            if (hasFlag(flags, APPLIES_TO_OWN_USER)) {
                // TODO(scottjonathan): This might be more appropriate as includeRunOnUnaffiliatedProfileOwnerSecondaryUser
                annotations.add(includeRunOnProfileOwnerPrimaryUser());
            }
        }

        // APPLIED_BY_AFFILIATED_PROFILE_OWNER_PROFILE

        if (hasFlag(flags, APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE)) {
            if (hasFlag(flags, APPLIES_TO_OWN_USER)) {
                annotations.add(includeRunOnProfileOwnerProfileWithNoDeviceOwner());
            }

            if (hasFlag(flags, APPLIES_TO_PARENT)) {
                annotations.add(includeRunOnParentOfProfileOwnerWithNoDeviceOwner());
            }
        }

        if (hasFlag(flags, APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO)) {
            if (hasFlag(flags, APPLIES_TO_OWN_USER)) {
                annotations.add(includeRunOnProfileOwnerPrimaryUser());
            }
        }
    }

    /**
     * Get negative state annotations for the given policy.
     *
     * <p>These are states which should be run where the policy is not able to be applied.
     */
    public static List<Annotation> negativeStates(EnterprisePolicy enterprisePolicy) {
        Set<Annotation> annotations = new HashSet<>();

        validateDeviceOwnerFlags(enterprisePolicy.deviceOwner());
        validateProfileOwnerFlags(enterprisePolicy.profileOwner());

        deviceOwnerNegativeStates(enterprisePolicy.deviceOwner(), annotations);
        profileOwnerNegativeStates(enterprisePolicy.profileOwner(), annotations);

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        return new ArrayList<>(annotations);
    }

    private static void deviceOwnerNegativeStates(int flags, Set<Annotation> annotations) {
        if (!hasFlag(flags, APPLIED_BY_DEVICE_OWNER)) {
            return;
        }

        if (!hasFlag(flags, APPLIES_TO_OWN_USER)) {
            // Seems like it'd never happen
            annotations.add(includeRunOnDeviceOwnerUser());
        }

        if (!hasFlag(flags, APPLIES_TO_AFFILIATED_OTHER_USERS)) {
            annotations.add(includeRunOnAffiliatedDeviceOwnerSecondaryUser());
        }

        if (!hasFlag(flags, APPLIES_TO_UNAFFILIATED_OTHER_USERS)) {
            annotations.add(includeRunOnNonAffiliatedDeviceOwnerSecondaryUser());
        }
    }

    private static void profileOwnerNegativeStates(int flags, Set<Annotation> annotations) {
        if (hasFlag(flags, APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER)) {
            if (!hasFlag(flags, APPLIES_TO_OWN_USER)) {
                annotations.add(includeRunOnAffiliatedProfileOwnerSecondaryUser());
            }
        }

        if (hasFlag(flags, APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER)) {
            if (!hasFlag(flags, APPLIES_TO_OWN_USER)) {
                // TODO(scottjonathan): This might be more appropriate as includeRunOnUnaffiliatedProfileOwnerSecondaryUser
                annotations.add(includeRunOnProfileOwnerPrimaryUser());
            }
        }

        // APPLIED_BY_AFFILIATED_PROFILE_OWNER_PROFILE

        if (hasFlag(flags, APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE)) {
            if (!hasFlag(flags, APPLIES_TO_OWN_USER)) {
                annotations.add(includeRunOnProfileOwnerProfileWithNoDeviceOwner());
            }

            if (!hasFlag(flags, APPLIES_TO_PARENT)) {
                annotations.add(includeRunOnParentOfProfileOwnerWithNoDeviceOwner());
            }

            if (!hasFlag(flags, APPLIES_TO_OTHER_USERS)) {
                annotations.add(
                        includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile());
            }
        }

        if (hasFlag(flags, APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO)) {
            if (!hasFlag(flags, APPLIES_TO_OWN_USER)) {
                annotations.add(includeRunOnProfileOwnerPrimaryUser());
            }
        }
    }

    /**
     * Get state annotations where the policy cannot be set for the given policy.
     */
    public static List<Annotation> cannotSetPolicyStates(EnterprisePolicy enterprisePolicy) {
        Set<Annotation> annotations = new HashSet<>();

        validateDeviceOwnerFlags(enterprisePolicy.deviceOwner());
        validateProfileOwnerFlags(enterprisePolicy.profileOwner());

        // TODO(scottjonathan): Always include a state without a dpc

        deviceOwnerCannotSetPolicyStates(enterprisePolicy.deviceOwner(), annotations);
        profileOwnerCannotSetPolicyStates(enterprisePolicy.profileOwner(), annotations);

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        return new ArrayList<>(annotations);
    }

    private static void deviceOwnerCannotSetPolicyStates(int flags, Set<Annotation> annotations) {
        if (flags == NO) { // Can't be set by DO
            annotations.add(includeRunOnDeviceOwnerUser());
        }
    }

    private static void profileOwnerCannotSetPolicyStates(int flags, Set<Annotation> annotations) {
        if (!hasFlag(flags, APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER)) {
            annotations.add(includeRunOnAffiliatedProfileOwnerSecondaryUser());
        }

        if (!hasFlag(flags, APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER)) {
            annotations.add(includeRunOnProfileOwnerPrimaryUser());
        }

        // APPLIED_BY_AFFILIATED_PROFILE_OWNER_PROFILE

        if (!hasFlag(flags, APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE)) {
            annotations.add(includeRunOnProfileOwnerProfileWithNoDeviceOwner());
        }

        if (!hasFlag(flags, APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO)) {
            annotations.add(includeRunOnProfileOwnerPrimaryUser());
        }
    }

    private static void validateDeviceOwnerFlags(int value) {
        if (value == NO) {
            return;
        }

        int invalidFlags = (value | VALID_DEVICE_OWNER_FLAGS) ^ VALID_DEVICE_OWNER_FLAGS;
        if (invalidFlags > 0) {
            throw new IllegalStateException(
                    "Invalid state passed for device owner: " + extractFlags(invalidFlags));
        }
    }

    private static void validateProfileOwnerFlags(int value) {
        if (value == NO) {
            return;
        }
        int invalidFlags = (value | VALID_PROFILE_OWNER_FLAGS) ^ VALID_PROFILE_OWNER_FLAGS;
        if (invalidFlags > 0) {
            throw new IllegalStateException(
                    "Invalid state passed for profile owner: " + extractFlags(invalidFlags));
        }
    }

    private static boolean hasFlag(int value, int flag) {
        return (value & flag) > 0;
    }

    private static Set<Integer> extractFlags(int flags) {
        Set<Integer> extracted = new HashSet<>();
        int nextValue = 1;
        while (flags > 0) {
            if ((flags & 1) > 0) {
                extracted.add(nextValue);
            }
            nextValue = nextValue << 1;
            flags = flags >> 1;
        }

        return extracted;
    }
}
