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

import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_AFFILIATED_PROFILE_OWNER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_AFFILIATED_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_DEVICE_OWNER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_IN_BACKGROUND;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_AFFILIATED_OTHER_USERS;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_OWN_USER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_PARENT;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_UNAFFILIATED_OTHER_USERS;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.DO_NOT_APPLY_TO_NEGATIVE_TESTS;
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
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnUnaffiliatedProfileOwnerSecondaryUser;

import com.google.auto.value.AutoAnnotation;
import com.google.common.collect.ImmutableMap;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private static IncludeRunOnUnaffiliatedProfileOwnerSecondaryUser includeRunOnUnaffiliatedProfileOwnerSecondaryUser() {
        return new AutoAnnotation_Policy_includeRunOnUnaffiliatedProfileOwnerSecondaryUser();
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

    // This is a map containing all Include* annotations and the flags which lead to them
    // This is not validated - every state must have a single APPLIED_BY annotation
    private static final ImmutableMap<Integer, Annotation> STATE_ANNOTATIONS = ImmutableMap.<Integer, Annotation>builder()
            .put(APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER, includeRunOnDeviceOwnerUser())
            .put(APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER | APPLIES_IN_BACKGROUND, includeRunOnBackgroundDeviceOwnerUser())

            .put(APPLIED_BY_DEVICE_OWNER | APPLIES_TO_UNAFFILIATED_OTHER_USERS, includeRunOnNonAffiliatedDeviceOwnerSecondaryUser())
            .put(APPLIED_BY_DEVICE_OWNER | APPLIES_TO_AFFILIATED_OTHER_USERS, includeRunOnAffiliatedDeviceOwnerSecondaryUser())

            .put(APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER | APPLIES_TO_OWN_USER, includeRunOnAffiliatedProfileOwnerSecondaryUser())
            .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER | APPLIES_TO_OWN_USER, includeRunOnUnaffiliatedProfileOwnerSecondaryUser())
            .put(APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO | APPLIES_TO_OWN_USER, includeRunOnProfileOwnerPrimaryUser())

            .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE | APPLIES_TO_OWN_USER, includeRunOnProfileOwnerProfileWithNoDeviceOwner())
            .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE | APPLIES_TO_PARENT, includeRunOnParentOfProfileOwnerWithNoDeviceOwner())

            .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE | APPLIES_TO_UNAFFILIATED_OTHER_USERS, includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile())
            .build();

    // This must contain one key for every APPLIED_BY that is being used, and maps to the "default" for testing that DPC type
    // in general this will be a state which runs on the same user as the dpc.
    private static final ImmutableMap<Integer, Annotation> DPC_STATE_ANNOTATIONS = ImmutableMap.<Integer, Annotation>builder()
            .put(APPLIED_BY_DEVICE_OWNER, includeRunOnDeviceOwnerUser())
            .put(APPLIED_BY_AFFILIATED_PROFILE_OWNER, includeRunOnAffiliatedProfileOwnerSecondaryUser())
            .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER, includeRunOnProfileOwnerPrimaryUser())
            .put(APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO, includeRunOnProfileOwnerPrimaryUser())
            .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE, includeRunOnProfileOwnerProfileWithNoDeviceOwner())
            .build();

    private static final int APPLIED_BY_FLAGS =
            APPLIED_BY_DEVICE_OWNER | APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE
                    | APPLIED_BY_AFFILIATED_PROFILE_OWNER_PROFILE
                    | APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER
                    | APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER;


    private static final Map<Annotation, Set<Integer>> ANNOTATIONS_MAP = calculateAnnotationsMap(STATE_ANNOTATIONS);

    private static Map<Annotation, Set<Integer>> calculateAnnotationsMap(
            Map<Integer, Annotation> annotations) {
        Map<Annotation, Set<Integer>> b = new HashMap<>();

        for (Map.Entry<Integer, Annotation> i : annotations.entrySet()) {
            if (!b.containsKey(i.getValue())) {
                b.put(i.getValue(), new HashSet<>());
            }

            b.get(i.getValue()).add(i.getKey());
        }

        return b;
    }


    /**
     * Get positive state annotations for the given policy.
     *
     * <p>These are states which should be run where the policy is able to be applied.
     */
    public static List<Annotation> positiveStates(String policyName, EnterprisePolicy enterprisePolicy) {
        Set<Annotation> annotations = new HashSet<>();

        validateFlags(policyName, enterprisePolicy.dpc());

        for (Map.Entry<Annotation, Set<Integer>> annotation : ANNOTATIONS_MAP.entrySet()) {
            if (isPositive(enterprisePolicy.dpc(), annotation.getValue())) {
                annotations.add(annotation.getKey());
            }
        }

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        return new ArrayList<>(annotations);
    }

    private static boolean isPositive(int[] policyFlags, Set<Integer> annotationFlags) {
        for (int annotationFlag : annotationFlags) {
            if (hasFlag(policyFlags, annotationFlag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNegative(int[] policyFlags, Set<Integer> annotationFlags) {
        for (int annotationFlag : annotationFlags) {
            if (hasFlag(annotationFlag, DO_NOT_APPLY_TO_NEGATIVE_TESTS, /* nonMatchingFlag= */ NO)) {
                return false; // We don't support using this annotation for negative tests
            }

            int appliedByFlag = APPLIED_BY_FLAGS & annotationFlag;
            int otherFlags = annotationFlag ^ appliedByFlag; // remove the appliedByFlag
            if (hasFlag(policyFlags, /* matchingFlag= */ appliedByFlag, /* nonMatchingFlag= */ otherFlags)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get negative state annotations for the given policy.
     *
     * <p>These are states which should be run where the policy is not able to be applied.
     */
    public static List<Annotation> negativeStates(String policyName, EnterprisePolicy enterprisePolicy) {
        Set<Annotation> annotations = new HashSet<>();

        validateFlags(policyName, enterprisePolicy.dpc());

        for (Map.Entry<Annotation, Set<Integer>> annotation : ANNOTATIONS_MAP.entrySet()) {
            if (isNegative(enterprisePolicy.dpc(), annotation.getValue())) {
                annotations.add(annotation.getKey());
            }
        }

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        return new ArrayList<>(annotations);
    }

    /**
     * Get state annotations where the policy cannot be set for the given policy.
     */
    public static List<Annotation> cannotSetPolicyStates(String policyName, EnterprisePolicy enterprisePolicy) {
        Set<Annotation> annotations = new HashSet<>();

        validateFlags(policyName, enterprisePolicy.dpc());

        // TODO(scottjonathan): Always include a state without a dpc

        int allFlags = 0;
        for (int p : enterprisePolicy.dpc()) {
            allFlags = allFlags | p;
        }

        for (Map.Entry<Integer, Annotation> appliedByFlag : DPC_STATE_ANNOTATIONS.entrySet()) {
            if ((appliedByFlag.getKey() & allFlags) == 0) {
                annotations.add(appliedByFlag.getValue());
            }
        }

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        return new ArrayList<>(annotations);
    }

    /**
     * Get state annotations where the policy can be set for the given policy.
     */
    public static List<Annotation> canSetPolicyStates(
            String policyName, EnterprisePolicy enterprisePolicy, boolean singleTestOnly) {
        Set<Annotation> annotations = new HashSet<>();

        validateFlags(policyName, enterprisePolicy.dpc());

        int allFlags = 0;
        for (int p : enterprisePolicy.dpc()) {
            allFlags = allFlags | p;
        }

        for (Map.Entry<Integer, Annotation> appliedByFlag : DPC_STATE_ANNOTATIONS.entrySet()) {
            if ((appliedByFlag.getKey() & allFlags) == appliedByFlag.getKey()) {
                annotations.add(appliedByFlag.getValue());
            }
        }

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        List<Annotation> annotationList = new ArrayList<>(annotations);

        if (singleTestOnly) {
            // We select one annotation in an arbitrary but deterministic way
            annotationList.sort(Comparator.comparing(a -> a.annotationType().getName()));
            Annotation firstAnnotation = annotationList.get(0);
            annotationList.clear();
            annotationList.add(firstAnnotation);
        }

        return annotationList;
    }

    private static void validateFlags(String policyName, int[] values) {
        int usedAppliedByFlags = 0;

        for (int value : values) {
            validateFlags(policyName, value);
            int newUsedAppliedByFlags = usedAppliedByFlags | (value & APPLIED_BY_FLAGS);
            if (newUsedAppliedByFlags == usedAppliedByFlags) {
                throw new IllegalStateException(
                        "Cannot have more than one policy flag APPLIED by the same component. "
                                + "Error in policy " + policyName);
            }
            usedAppliedByFlags = newUsedAppliedByFlags;
        }
    }

    private static void validateFlags(String policyName, int value) {
        int matchingAppliedByFlags = APPLIED_BY_FLAGS & value;

        if (matchingAppliedByFlags == 0) {
            throw new IllegalStateException(
                    "All policy flags must specify 1 APPLIED_BY flag. Policy " + policyName
                            + " did not.");
        }
    }

    private static boolean hasFlag(int[] values, int matchingFlag) {
        return hasFlag(values, matchingFlag, /* nonMatchingFlag= */ NO);
    }

    private static boolean hasFlag(int[] values, int matchingFlag, int nonMatchingFlag) {
        for (int value : values) {
            if (hasFlag(value, matchingFlag, nonMatchingFlag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFlag(int value, int matchingFlag, int nonMatchingFlag) {
        if (!((value & matchingFlag) == matchingFlag)) {
            return false;
        }

        if (nonMatchingFlag != NO) {
            return (value & nonMatchingFlag) != nonMatchingFlag;
        }

        return true;
    }
}
