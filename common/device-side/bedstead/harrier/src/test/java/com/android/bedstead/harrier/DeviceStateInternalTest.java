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

package com.android.bedstead.harrier;

import static com.android.bedstead.harrier.annotations.EnsureHasWorkProfileKt.ensureHasWorkProfile;

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.nene.users.UserReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Use this class to create tests that validate the behavior of {@code DeviceState} components.
 *
 * <p>The tests would comprise multiple steps where each step would execute the implementation
 * of one or more {@code DeviceState} component(s). Device state changes can be asserted after
 * each step.
 *
 * <p>Below is an example of a test that tests the case when an annotation is used twice in a
 * test class, the test asserts that the state created by the first test is reused by the second
 * one.
 *
 * <pre>
 *  {@code
 *  @Test
 *  public void ensureHasWorkProfile_reusesUser() {
 *      AtomicReference<UserReference> user = new AtomicReference<>();
 *
 *      mDeviceState.stepName("createWorkProfile")
 *                 .apply(List.of(ensureHasProfileOwner()), () -> {
 *          user.set(mDeviceState.profileOwner().user());
 *      });
 *
 *      mDeviceState.stepName("reuseWorkProfile")
 *                 .apply(List.of(ensureHasProfileOwner()), () -> {
 *          assertThat(mDeviceState.profileOwner().user()).isEqualTo(user.get());
 *      });
 *  }}
 * </pre>
 */
@RunWith(JUnit4.class)
public final class DeviceStateInternalTest {

    private DeviceStateTester mDeviceState;

    @Before
    public void setUp() {
        mDeviceState = new DeviceStateTester();
    }

    @After
    public void tearDown() {
        mDeviceState.tearDown();
    }

    @Test
    public void ensureHasWorkProfile_reusesUser() {
        AtomicReference<UserReference> user = new AtomicReference<>();

        mDeviceState.stepName("createWorkProfile")
                .apply(List.of(ensureHasWorkProfile()), () -> {
            user.set(mDeviceState.workProfile());
        });

        mDeviceState.stepName("reuseWorkProfile")
                .apply(List.of(ensureHasWorkProfile()), () -> {
            assertThat(mDeviceState.workProfile()).isEqualTo(user.get());
        });
    }
}
