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

package com.android.bedstead.harrier;

import static com.android.bedstead.nene.users.UserType.MANAGED_PROFILE_TYPE_NAME;
import static com.android.bedstead.nene.users.UserType.SECONDARY_USER_TYPE_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasTvProfile;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.FailureMode;
import com.android.bedstead.harrier.annotations.RequireFeatures;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnTvProfile;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.User;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;


/**
 * A Junit rule which exposes methods for efficiently changing and querying device state.
 *
 * <p>States set by the methods on this class will by default be cleaned up after the test.
 *
 *
 * <p>Using this rule also enforces preconditions in annotations from the
 * {@code com.android.comaptibility.common.util.enterprise.annotations} package.
 *
 * {@code assumeTrue} will be used, so tests which do not meet preconditions will be skipped.
 */
public final class DeviceState implements TestRule {

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final TestApis mTestApis = new TestApis();
    private static final String SKIP_TEST_TEARDOWN_KEY = "skip-test-teardown";
    private static final String SKIP_TESTS_REASON_KEY = "skip-tests-reason";
    private final boolean mSkipTestTeardown;
    private boolean mSkipTests;
    private String mSkipTestsReason;

    private static final String TV_PROFILE_TYPE_NAME = "com.android.tv.profile";

    public DeviceState() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        mSkipTestTeardown = Boolean.parseBoolean(
                arguments.getString(SKIP_TEST_TEARDOWN_KEY, "false"));
        mSkipTestsReason = arguments.getString(SKIP_TESTS_REASON_KEY, "");
        mSkipTests = !mSkipTestsReason.isEmpty();
    }

    @Override public Statement apply(final Statement base,
            final Description description) {

        if (description.isTest()) {
            return applyTest(base, description);
        } else if (description.isSuite()) {
            return applySuite(base, description);
        }
        throw new IllegalStateException("Unknown description type: " + description);
    }

    private Statement applyTest(final Statement base, final Description description) {
        return new Statement() {
            @Override public void evaluate() throws Throwable {
                Log.d(LOG_TAG, "Preparing state for test " + description.getMethodName());

                assumeFalse(mSkipTestsReason, mSkipTests);

                if (description.getAnnotation(RequireRunOnPrimaryUser.class) != null) {
                    assumeTrue("@RequireRunOnPrimaryUser tests only run on primary user",
                            isRunningOnPrimaryUser());
                }
                if (description.getAnnotation(RequireRunOnWorkProfile.class) != null) {
                    assumeTrue("@RequireRunOnWorkProfile tests only run on work profile",
                            isRunningOnWorkProfile());
                }
                if (description.getAnnotation(RequireRunOnSecondaryUser.class) != null) {
                    assumeTrue("@RequireRunOnSecondaryUser tests only run on secondary user",
                            isRunningOnSecondaryUser());
                }
                if (description.getAnnotation(RequireRunOnTvProfile.class) != null) {
                    assumeTrue("@RequireRunOnTvProfile tests only run on TV profile",
                            isRunningOnTvProfile());
                }
                EnsureHasWorkProfile ensureHasWorkAnnotation =
                        description.getAnnotation(EnsureHasWorkProfile.class);
                if (ensureHasWorkAnnotation != null) {
                    ensureHasWorkProfile(
                            /* installTestApp= */ ensureHasWorkAnnotation.installTestApp(),
                            /* forUser= */ ensureHasWorkAnnotation.forUser()
                    );
                }
                EnsureHasTvProfile ensureHasTvProfileAnnotation =
                        description.getAnnotation(EnsureHasTvProfile.class);
                if (ensureHasTvProfileAnnotation != null) {
                    ensureHasTvProfile(
                            /* installTestApp= */ ensureHasTvProfileAnnotation.installTestApp(),
                            /* forUser= */ ensureHasTvProfileAnnotation.forUser()
                    );
                }
                EnsureHasSecondaryUser ensureHasSecondaryUserAnnotation =
                        description.getAnnotation(EnsureHasSecondaryUser.class);
                if (ensureHasSecondaryUserAnnotation != null) {
                    ensureHasSecondaryUser(
                            /* installTestApp= */ ensureHasSecondaryUserAnnotation.installTestApp()
                    );
                }
                RequireFeatures requireFeaturesAnnotation =
                        description.getAnnotation(RequireFeatures.class);
                if (requireFeaturesAnnotation != null) {
                    for (String feature: requireFeaturesAnnotation.value()) {
                        requireFeature(feature, requireFeaturesAnnotation.failureMode());
                    }
                }

                Log.d(LOG_TAG,
                        "Finished preparing state for test " + description.getMethodName());

                try {
                    base.evaluate();
                } finally {
                    Log.d(LOG_TAG,
                            "Tearing down state for test " + description.getMethodName());
                    teardownNonShareableState();
                    if (!mSkipTestTeardown) {
                        teardownShareableState();
                    }
                    Log.d(LOG_TAG,
                            "Finished tearing down state for test " + description.getMethodName());
                }
            }};
    }

    private Statement applySuite(final Statement base, final Description description) {
        return base;
    }

    private void requireFeature(String feature, FailureMode failureMode) {
        if (failureMode.equals(FailureMode.FAIL)) {
            assertThat(mTestApis.packages().features().contains(feature)).isTrue();
        } else if (failureMode.equals(FailureMode.SKIP)) {
            assumeTrue("Device must have feature " + feature,
                    mTestApis.packages().features().contains(feature));
        } else {
            throw new IllegalStateException("Unknown failure mode: " + failureMode);
        }
    }

    private void requireUserSupported(String userType) {
        assumeTrue("Device must support user type " + userType
                + " only supports: " + mTestApis.users().supportedTypes(),
                mTestApis.users().supportedType(userType) != null);
    }

    public enum UserType {
        CURRENT_USER,
        PRIMARY_USER,
        SECONDARY_USER,
        WORK_PROFILE,
        TV_PROFILE,
    }

    private static final String LOG_TAG = "DeviceState";

    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();

    private List<UserReference> mCreatedUsers = new ArrayList<>();
    private List<BlockingBroadcastReceiver> mRegisteredBroadcastReceivers = new ArrayList<>();

    @Nullable
    public UserReference workProfile() {
        return workProfile(/* forUser= */ UserType.CURRENT_USER);
    }

    @Nullable
    public UserReference workProfile(UserType forUser) {
        return workProfile(resolveUserTypeToUser(forUser));
    }

    @Nullable
    public UserReference workProfile(UserReference forUser) {
        return mTestApis.users().all().stream()
                .filter(u -> forUser.equals(u.parent())
                        && u.type().name().equals(MANAGED_PROFILE_TYPE_NAME))
                .findFirst().orElse(null);
    }

    public boolean isRunningOnWorkProfile() {
        return mTestApis.users().instrumented()
                .resolve().type().name().equals(MANAGED_PROFILE_TYPE_NAME);
    }

    @Nullable
    public UserReference tvProfile() {
        return tvProfile(/* forUser= */ UserType.CURRENT_USER);
    }

    @Nullable
    public UserReference tvProfile(UserType forUser) {
        return tvProfile(resolveUserTypeToUser(forUser));
    }

    @Nullable
    public UserReference tvProfile(UserReference forUser) {
        return mTestApis.users().all().stream()
                .filter(u -> forUser.equals(u.parent()) && u.type().equals(TV_PROFILE_TYPE_NAME))
                .findFirst().orElse(null);
    }

    public boolean isRunningOnTvProfile() {
        return mTestApis.users().instrumented().resolve()
                .type().name().equals(TV_PROFILE_TYPE_NAME);
    }

    public boolean isRunningOnPrimaryUser() {
        return mTestApis.users().instrumented().resolve().isPrimary();
    }

    public boolean isRunningOnSecondaryUser() {
        return mTestApis.users().instrumented().resolve()
                .type().name().equals(SECONDARY_USER_TYPE_NAME);
    }

    /**
     * Get the user ID of the first human user on the device.
     *
     * <p>Returns {@code null} if there is none present.
     */
    @Nullable
    public UserReference primaryUser() {
        return mTestApis.users().all()
                .stream().filter(User::isPrimary).findFirst().orElse(null);
    }

    /**
     * Get the user ID of a human user on the device other than the primary user.
     *
     * <p>Returns {@code null} if there is none present.
     */
    @Nullable
    public UserReference secondaryUser() {
        return mTestApis.users().all()
                .stream().filter(u -> u.type().name().equals(SECONDARY_USER_TYPE_NAME))
                .findFirst().orElse(null);
    }

    public void ensureHasWorkProfile(boolean installTestApp, UserType forUser) {
        requireFeature("android.software.managed_users", FailureMode.SKIP);
        requireUserSupported(MANAGED_PROFILE_TYPE_NAME);

        UserReference forUserReference = resolveUserTypeToUser(forUser);

        UserReference workProfile = workProfile(forUserReference);
        if (workProfile == null) {
            workProfile = createWorkProfile(forUserReference);
        }

        workProfile.start();

        if (installTestApp) {
            mTestApis.packages().find(sInstrumentation.getContext().getPackageName())
                    .install(workProfile);
        } else {
            mTestApis.packages().find(sInstrumentation.getContext().getPackageName())
                    .uninstall(workProfile);
        }
    }

    public void ensureHasTvProfile(boolean installTestApp, UserType forUser) {
        requireUserSupported(TV_PROFILE_TYPE_NAME);

        UserReference forUserReference = resolveUserTypeToUser(forUser);

        UserReference tvProfile = tvProfile(forUserReference);
        if (tvProfile == null) {
            tvProfile = createTvProfile(forUserReference);
        }

        tvProfile.start();

        if (installTestApp) {
            mTestApis.packages().find(sInstrumentation.getContext().getPackageName())
                    .install(tvProfile);
        } else {
            mTestApis.packages().find(sInstrumentation.getContext().getPackageName())
                    .uninstall(tvProfile);
        }
    }

    public void ensureHasSecondaryUser(boolean installTestApp) {
        requireUserSupported(SECONDARY_USER_TYPE_NAME);

        UserReference secondaryUser = secondaryUser();
        if (secondaryUser == null) {
            secondaryUser = createSecondaryUser();
        }

        secondaryUser.start();

        if (installTestApp) {
            mTestApis.packages().find(sInstrumentation.getContext().getPackageName())
                    .install(secondaryUser);
        } else {
            mTestApis.packages().find(sInstrumentation.getContext().getPackageName())
                    .uninstall(secondaryUser);
        }
    }

    public void requireCanSupportAdditionalUser() {
        int maxUsers = getMaxNumberOfUsersSupported();
        int currentUsers = mTestApis.users().all().size();

        assumeTrue("The device does not have space for an additional user (" + currentUsers +
                " current users, " + maxUsers + " max users)", currentUsers + 1 <= maxUsers);
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiver(String action) {
        return registerBroadcastReceiver(action, /* checker= */ null);
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiver(
            String action, Function<Intent, Boolean> checker) {
        BlockingBroadcastReceiver broadcastReceiver =
                new BlockingBroadcastReceiver(mContext, action, checker);
        broadcastReceiver.register();
        mRegisteredBroadcastReceivers.add(broadcastReceiver);

        return broadcastReceiver;
    }

    private UserReference resolveUserTypeToUser(UserType userType) {
        switch (userType) {
            case CURRENT_USER:
                return mTestApis.users().instrumented();
            case PRIMARY_USER:
                return primaryUser();
            case SECONDARY_USER:
                return secondaryUser();
            case WORK_PROFILE:
                return workProfile();
            case TV_PROFILE:
                return tvProfile();
            default:
                throw new IllegalArgumentException("Unknown user type " + userType);
        }
    }

    private void teardownNonShareableState() {
        for (BlockingBroadcastReceiver broadcastReceiver : mRegisteredBroadcastReceivers) {
            broadcastReceiver.unregisterQuietly();
        }
        mRegisteredBroadcastReceivers.clear();
    }

    private void teardownShareableState() {
        for (UserReference user : mCreatedUsers) {
            user.remove();
        }

        mCreatedUsers.clear();
    }

    private UserReference createWorkProfile(UserReference parent) {
        requireCanSupportAdditionalUser();
        try {
            UserReference user = mTestApis.users().createUser()
                    .parent(parent)
                    .type(mTestApis.users().supportedType(MANAGED_PROFILE_TYPE_NAME))
                    .createAndStart();
            mCreatedUsers.add(user);
            return user;
        } catch (NeneException e) {
            throw new IllegalStateException("Error creating work profile", e);
        }
    }

    private UserReference createTvProfile(UserReference parent) {
        requireCanSupportAdditionalUser();
        try {
            UserReference user = mTestApis.users().createUser()
                    .parent(parent)
                    .type(mTestApis.users().supportedType(TV_PROFILE_TYPE_NAME))
                    .createAndStart();
            mCreatedUsers.add(user);
            return user;
        } catch (NeneException e) {
            throw new IllegalStateException("Error creating tv profile", e);
        }
    }

    private UserReference createSecondaryUser() {
        requireCanSupportAdditionalUser();
        try {
            UserReference user = mTestApis.users().createUser()
                    .createAndStart();
            mCreatedUsers.add(user);
            return user;
        } catch (NeneException e) {
            throw new IllegalStateException("Error creating secondary user", e);
        }
    }

    private int getMaxNumberOfUsersSupported() {
        try {
            return ShellCommand.builder("pm get-max-users")
                    .validate((output) -> output.startsWith("Maximum supported users:"))
                    .executeAndParseOutput(
                            (output) -> Integer.parseInt(output.split(": ", 2)[1].trim()));
        } catch (AdbException e) {
            throw new IllegalStateException("Invalid command output", e);
        }
    }
}
