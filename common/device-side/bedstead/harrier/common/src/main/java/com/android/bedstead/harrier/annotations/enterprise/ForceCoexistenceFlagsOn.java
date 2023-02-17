/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.bedstead.nene.flags.CommonFlags.DevicePolicyManager.ENABLE_COEXISTENCE_FLAG;
import static com.android.bedstead.nene.flags.CommonFlags.DevicePolicyManager.PERMISSION_BASED_ACCESS_EXPERIMENT_FLAG;
import static com.android.bedstead.nene.flags.CommonFlags.NAMESPACE_DEVICE_POLICY_MANAGER;

import com.android.bedstead.harrier.annotations.AnnotationRunPrecedence;
import com.android.bedstead.harrier.annotations.EnsureFeatureFlagEnabled;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@EnsureFeatureFlagEnabled(
        namespace = NAMESPACE_DEVICE_POLICY_MANAGER,
        key = ENABLE_COEXISTENCE_FLAG,
        weight = AnnotationRunPrecedence.LATE
)
@EnsureFeatureFlagEnabled(
        namespace = NAMESPACE_DEVICE_POLICY_MANAGER,
        key = PERMISSION_BASED_ACCESS_EXPERIMENT_FLAG,
        weight = AnnotationRunPrecedence.LATE
)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ForceCoexistenceFlagsOn {
}
