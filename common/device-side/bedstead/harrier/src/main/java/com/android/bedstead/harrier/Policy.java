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
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnDeviceOwnerUser;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for enterprise policy tests.
 */
@IncludeNone
@IncludeRunOnDeviceOwnerUser
public final class Policy {

    private Policy() {

    }

    private static final IncludeNone INCLUDE_NONE_ANNOTATION =
            Policy.class.getAnnotation(IncludeNone.class);
    private static final IncludeRunOnDeviceOwnerUser INCLUDE_RUN_ON_DEVICE_OWNER_USER =
            Policy.class.getAnnotation(IncludeRunOnDeviceOwnerUser.class);

    /**
     * Get positive state annotations for the given policy.
     *
     * <p>These are states which should be run where the policy is able to be applied.
     */
    public static List<Annotation> positiveStates(EnterprisePolicy enterprisePolicy) {
        List<Annotation> annotations = new ArrayList<>();

        annotations.add(INCLUDE_NONE_ANNOTATION);

        return annotations;
    }

    /**
     * Get negative state annotations for the given policy.
     *
     * <p>These are states which should be run where the policy is not able to be applied.
     */
    public static List<Annotation> negativeStates(EnterprisePolicy enterprisePolicy) {
        List<Annotation> annotations = new ArrayList<>();

        annotations.add(INCLUDE_NONE_ANNOTATION);

        return annotations;
    }
}
