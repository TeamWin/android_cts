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

import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy;
import com.android.bedstead.harrier.annotations.parameterized.IncludeNone;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnAffiliatedDeviceOwnerSecondaryUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnAffiliatedProfileOwnerSecondaryUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnDeviceOwnerUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnNonAffiliatedDeviceOwnerSecondaryUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnParentOfCorporateOwnedProfileOwner;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnParentOfProfileOwner;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnProfileOwnerPrimaryUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnProfileOwnerProfile;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnSecondaryUserInDifferentProfileGroupToProfileOwner;

import com.google.auto.value.AutoAnnotation;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for enterprise policy tests.
 */
public final class Policy {

    private Policy() {

    }

    @AutoAnnotation
    public static IncludeNone includeNone() {
        return new AutoAnnotation_Policy_includeNone();
    }

    @AutoAnnotation
    public static IncludeRunOnDeviceOwnerUser includeRunOnDeviceOwnerUser() {
        return new AutoAnnotation_Policy_includeRunOnDeviceOwnerUser();
    }

    @AutoAnnotation
    public static IncludeRunOnNonAffiliatedDeviceOwnerSecondaryUser includeRunOnNonAffiliatedDeviceOwnerSecondaryUser() {
        return new AutoAnnotation_Policy_includeRunOnNonAffiliatedDeviceOwnerSecondaryUser();
    }

    @AutoAnnotation
    public static IncludeRunOnAffiliatedDeviceOwnerSecondaryUser includeRunOnAffiliatedDeviceOwnerSecondaryUser() {
        return new AutoAnnotation_Policy_includeRunOnAffiliatedDeviceOwnerSecondaryUser();
    }

    @AutoAnnotation
    public static IncludeRunOnAffiliatedProfileOwnerSecondaryUser includeRunOnAffiliatedProfileOwnerSecondaryUser() {
        return new AutoAnnotation_Policy_includeRunOnAffiliatedProfileOwnerSecondaryUser();
    }

    @AutoAnnotation
    public static IncludeRunOnProfileOwnerProfile includeRunOnProfileOwnerProfile() {
        return new AutoAnnotation_Policy_includeRunOnProfileOwnerProfile();
    }

    @AutoAnnotation
    public static IncludeRunOnSecondaryUserInDifferentProfileGroupToProfileOwner includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwner() {
        return new AutoAnnotation_Policy_includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwner();
    }

    @AutoAnnotation
    public static IncludeRunOnParentOfProfileOwner includeRunOnParentOfProfileOwner() {
        return new AutoAnnotation_Policy_includeRunOnParentOfProfileOwner();
    }

    @AutoAnnotation
    public static IncludeRunOnParentOfCorporateOwnedProfileOwner includeRunOnParentOfCorporateOwnedProfileOwner() {
        return new AutoAnnotation_Policy_includeRunOnParentOfCorporateOwnedProfileOwner();
    }

    @AutoAnnotation
    public static IncludeRunOnProfileOwnerPrimaryUser includeRunOnProfileOwnerPrimaryUser() {
        return new AutoAnnotation_Policy_includeRunOnProfileOwnerPrimaryUser();
    }

    /**
     * Get positive state annotations for the given policy.
     *
     * <p>These are states which should be run where the policy is able to be applied.
     */
    public static List<Annotation> positiveStates(EnterprisePolicy enterprisePolicy) {
        List<Annotation> annotations = new ArrayList<>();

        switch (enterprisePolicy.deviceOwner()) {
            case NO:
                break;
            case GLOBAL:
                annotations.add(includeRunOnDeviceOwnerUser());
                annotations.add(includeRunOnNonAffiliatedDeviceOwnerSecondaryUser());
                break;
            case AFFILIATED:
                annotations.add(includeRunOnDeviceOwnerUser());
                annotations.add(includeRunOnAffiliatedDeviceOwnerSecondaryUser());
                break;
            case USER:
                annotations.add(includeRunOnDeviceOwnerUser());
                break;
            default:
                throw new IllegalStateException(
                        "Unknown policy control: " + enterprisePolicy.deviceOwner());
        }

        switch (enterprisePolicy.profileOwner()) {
            case NO:
                break;
            case AFFILIATED:
                annotations.add(includeRunOnAffiliatedProfileOwnerSecondaryUser());
                break;
            case AFFILIATED_OR_NO_DO:
                annotations.add(includeRunOnProfileOwnerPrimaryUser());
                annotations.add(includeRunOnAffiliatedProfileOwnerSecondaryUser());
                break;
            case PARENT:
                annotations.add(includeRunOnProfileOwnerProfile());
                annotations.add(includeRunOnParentOfProfileOwner());
                break;
            case COPE_PARENT:
                annotations.add(includeRunOnProfileOwnerProfile());
                //                TODO(scottjonathan): Re-add when we can setup this state
//                annotations.add(INCLUDE_RUN_ON_PARENT_OF_CORPORATE_OWNED_PROFILE_OWNER);
                break;
            case PROFILE:
                annotations.add(includeRunOnProfileOwnerProfile());
                break;
            default:
                throw new IllegalStateException(
                        "Unknown policy control: " + enterprisePolicy.profileOwner());
        }

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        return annotations;
    }

    /**
     * Get negative state annotations for the given policy.
     *
     * <p>These are states which should be run where the policy is not able to be applied.
     */
    public static List<Annotation> negativeStates(EnterprisePolicy enterprisePolicy) {
        List<Annotation> annotations = new ArrayList<>();

        switch (enterprisePolicy.deviceOwner()) {
            case NO:
                break;
            case GLOBAL:
                break;
            case AFFILIATED:
                annotations.add(includeRunOnNonAffiliatedDeviceOwnerSecondaryUser());
                break;
            case USER:
                annotations.add(includeRunOnAffiliatedDeviceOwnerSecondaryUser());
                break;
            default:
                throw new IllegalStateException(
                        "Unknown policy control: " + enterprisePolicy.deviceOwner());
        }

        switch (enterprisePolicy.profileOwner()) {
            case NO:
                break;
            case AFFILIATED:
                // TODO(scottjonathan): Define negative states
                break;
            case AFFILIATED_OR_NO_DO:
                // TODO(scottjonathan): Define negative states
                break;
            case PARENT:
                annotations.add(
                        includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwner());
                break;
            case COPE_PARENT:
                annotations.add(
                        includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwner());
                annotations.add(
                        includeRunOnParentOfProfileOwner());
                break;
            case PROFILE:
                annotations.add(
                        includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwner());
                //                TODO(scottjonathan): Re-add when we can setup this state
//                annotations.add(
//                        INCLUDE_RUN_ON_PARENT_OF_CORPORATE_OWNED_PROFILE_OWNER);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown policy control: " + enterprisePolicy.profileOwner());
        }

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        return annotations;
    }

    /**
     * Get state annotations where the policy cannot be set for the given policy.
     */
    public static List<Annotation> cannotSetPolicyStates(EnterprisePolicy enterprisePolicy) {
        List<Annotation> annotations = new ArrayList<>();

        // TODO(scottjonathan): Always include a state without a dpc

        if (enterprisePolicy.deviceOwner() == EnterprisePolicy.DeviceOwnerControl.NO) {
            annotations.add(includeRunOnDeviceOwnerUser());
        }

        if (enterprisePolicy.profileOwner() == EnterprisePolicy.ProfileOwnerControl.NO) {
            annotations.add(includeRunOnProfileOwnerProfile());
        } else if (enterprisePolicy.profileOwner() == EnterprisePolicy.ProfileOwnerControl.AFFILIATED) {
            annotations.add(includeRunOnProfileOwnerProfile());
        } else if (enterprisePolicy.profileOwner() == EnterprisePolicy.ProfileOwnerControl.AFFILIATED_OR_NO_DO) {
            annotations.add(includeRunOnProfileOwnerProfile());
        }

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        return annotations;
    }
}
