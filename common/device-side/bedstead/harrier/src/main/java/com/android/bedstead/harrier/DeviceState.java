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

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.annotations.FailureMode;
import com.android.bedstead.harrier.annotations.RequireFeatures;
import com.android.bedstead.harrier.annotations.RequireUserSupported;
import com.android.bedstead.harrier.annotations.meta.EnsureHasNoProfileAnnotation;
import com.android.bedstead.harrier.annotations.meta.EnsureHasNoUserAnnotation;
import com.android.bedstead.harrier.annotations.meta.EnsureHasProfileAnnotation;
import com.android.bedstead.harrier.annotations.meta.EnsureHasUserAnnotation;
import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation;
import com.android.bedstead.harrier.annotations.meta.RequireRunOnUserAnnotation;
import com.android.bedstead.harrier.annotations.meta.RequiresBedsteadJUnit4;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.User;
import com.android.bedstead.nene.users.UserBuilder;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import junit.framework.AssertionFailedError;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final TestApis sTestApis = new TestApis();
    private static final String SKIP_TEST_TEARDOWN_KEY = "skip-test-teardown";
    private static final String SKIP_CLASS_TEARDOWN_KEY = "skip-class-teardown";
    private static final String SKIP_TESTS_REASON_KEY = "skip-tests-reason";
    private boolean mSkipTestTeardown;
    private boolean mSkipClassTeardown;
    private boolean mSkipTests;
    private boolean mUsingBedsteadJUnit4 = false;
    private String mSkipTestsReason;

    private static final String TV_PROFILE_TYPE_NAME = "com.android.tv.profile";

    public DeviceState() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        mSkipTestTeardown = Boolean.parseBoolean(
                arguments.getString(SKIP_TEST_TEARDOWN_KEY, "false"));
        mSkipClassTeardown = Boolean.parseBoolean(
                arguments.getString(SKIP_CLASS_TEARDOWN_KEY, "false"));
        mSkipTestsReason = arguments.getString(SKIP_TESTS_REASON_KEY, "");
        mSkipTests = !mSkipTestsReason.isEmpty();
    }

    void setSkipTestTeardown(boolean skipTestTeardown) {
        mSkipTestTeardown = skipTestTeardown;
    }

    void setUsingBedsteadJUnit4(boolean usingBedsteadJUnit4) {
        mUsingBedsteadJUnit4 = usingBedsteadJUnit4;
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

                for (Annotation annotation : getAnnotations(description)) {
                    Class<? extends Annotation> annotationType = annotation.annotationType();

                    EnsureHasNoProfileAnnotation ensureHasNoProfileAnnotation =
                            annotationType.getAnnotation(EnsureHasNoProfileAnnotation.class);
                    if (ensureHasNoProfileAnnotation != null) {
                        UserType userType = (UserType) annotation.annotationType()
                                .getMethod("forUser").invoke(annotation);
                        ensureHasNoProfile(ensureHasNoProfileAnnotation.value(), userType);
                    }

                    EnsureHasProfileAnnotation ensureHasProfileAnnotation =
                            annotationType.getAnnotation(EnsureHasProfileAnnotation.class);
                    if (ensureHasProfileAnnotation != null) {
                        UserType forUser = (UserType) annotation.annotationType()
                                .getMethod("forUser").invoke(annotation);
                        boolean installTestApp = (boolean) annotation.annotationType()
                                .getMethod("installTestApp").invoke(annotation);
                            ensureHasProfile(
                                    ensureHasProfileAnnotation.value(), installTestApp, forUser);
                    }


                    EnsureHasNoUserAnnotation ensureHasNoUserAnnotation =
                            annotationType.getAnnotation(EnsureHasNoUserAnnotation.class);
                    if (ensureHasNoUserAnnotation != null) {
                        ensureHasNoUser(ensureHasNoUserAnnotation.value());
                    }

                    EnsureHasUserAnnotation ensureHasUserAnnotation =
                            annotationType.getAnnotation(EnsureHasUserAnnotation.class);
                    if (ensureHasUserAnnotation != null) {
                        boolean installTestApp = (boolean) annotation.getClass()
                                .getMethod("installTestApp").invoke(annotation);
                        ensureHasUser(ensureHasUserAnnotation.value(), installTestApp);
                    }

                    RequireRunOnUserAnnotation requireRunOnUserAnnotation =
                            annotationType.getAnnotation(RequireRunOnUserAnnotation.class);
                    if (requireRunOnUserAnnotation != null) {
                        requireRunOnUser(requireRunOnUserAnnotation.value());
                    }

                    if (annotation instanceof RequireFeatures) {
                        RequireFeatures requireFeaturesAnnotation = (RequireFeatures) annotation;
                        for (String feature: requireFeaturesAnnotation.value()) {
                            requireFeature(feature, requireFeaturesAnnotation.failureMode());
                        }
                    }

                    if (annotation instanceof RequireUserSupported) {
                        RequireUserSupported requireUserSupportedAnnotation =
                                (RequireUserSupported) annotation;
                        for (String userType: requireUserSupportedAnnotation.value()) {
                            requireUserSupported(
                                    userType, requireUserSupportedAnnotation.failureMode());
                        }
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

    private Collection<Annotation> getAnnotations(Description description) {
        if (mUsingBedsteadJUnit4) {
            // The annotations are already exploded
            return description.getAnnotations();
        }

        // Otherwise we should build a new collection by recursively gathering annotations
        // if we find any which don't work without the runner we should error and fail the test
        List<Annotation> annotations = new ArrayList<>(description.getAnnotations());
        checkAnnotations(annotations);

        BedsteadJUnit4.resolveRecursiveAnnotations(annotations,
                /* parameterizedAnnotation= */ null);

        checkAnnotations(annotations);

        return annotations;
    }

    private void checkAnnotations(Collection<Annotation> annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getAnnotation(RequiresBedsteadJUnit4.class) != null
                    || annotation.annotationType().getAnnotation(
                            ParameterizedAnnotation.class) != null) {
                throw new AssertionFailedError("Test is annotated "
                        + annotation.annotationType().getSimpleName()
                        + " which requires using the BedsteadJUnit4 test runner");
            }
        }
    }

    private Statement applySuite(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                base.evaluate();

                if (!mSkipClassTeardown) {
                    teardownShareableState();
                }
            }
        };
    }

    private void requireRunOnUser(String userType) {
        assumeTrue("This test only runs on users of type " + userType,
                isRunningOnUser(userType));
    }

    private void requireFeature(String feature, FailureMode failureMode) {
        checkFailOrSkip("Device must have feature " + feature,
                sTestApis.packages().features().contains(feature), failureMode);
    }

    private com.android.bedstead.nene.users.UserType requireUserSupported(
            String userType, FailureMode failureMode) {
        com.android.bedstead.nene.users.UserType resolvedUserType =
                sTestApis.users().supportedType(userType);

        checkFailOrSkip(
                "Device must support user type " + userType
                + " only supports: " + sTestApis.users().supportedTypes(),
                resolvedUserType != null, failureMode);

        return resolvedUserType;
    }

    private void checkFailOrSkip(String message, boolean value, FailureMode failureMode) {
        if (failureMode.equals(FailureMode.FAIL)) {
            assertWithMessage(message).that(value).isTrue();
        } else if (failureMode.equals(FailureMode.SKIP)) {
            assumeTrue(message, value);
        } else {
            throw new IllegalStateException("Unknown failure mode: " + failureMode);
        }
    }

    public enum UserType {
        CURRENT_USER,
        PRIMARY_USER,
        SECONDARY_USER,
        WORK_PROFILE,
        TV_PROFILE,
    }

    private static final String LOG_TAG = "DeviceState";

    private static final Context sContext = sTestApis.context().instrumentedContext();

    private final Map<com.android.bedstead.nene.users.UserType, UserReference> mUsers =
            new HashMap<>();
    private final Map<com.android.bedstead.nene.users.UserType, Map<UserReference, UserReference>>
            mProfiles = new HashMap<>();

    private final List<UserReference> mCreatedUsers = new ArrayList<>();
    private final List<UserBuilder> mRemovedUsers = new ArrayList<>();
    private final List<BlockingBroadcastReceiver> mRegisteredBroadcastReceivers = new ArrayList<>();

    /**
     * Get the {@link UserReference} of the work profile for the current user
     *
     * <p>This should only be used to get work profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed work profile
     */
    public UserReference workProfile() {
        return workProfile(/* forUser= */ UserType.CURRENT_USER);
    }

    /**
     * Get the {@link UserReference} of the work profile.
     *
     * <p>This should only be used to get work profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed work profile for the given user
     */
    public UserReference workProfile(UserType forUser) {
        return workProfile(resolveUserTypeToUser(forUser));
    }

    /**
     * Get the {@link UserReference} of the work profile.
     *
     * <p>This should only be used to get work profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed work profile for the given user
     */
    public UserReference workProfile(UserReference forUser) {
        return profile(MANAGED_PROFILE_TYPE_NAME, forUser);
    }

    private UserReference profile(String profileType, UserReference forUser) {
        com.android.bedstead.nene.users.UserType resolvedUserType =
                sTestApis.users().supportedType(profileType);

        if (resolvedUserType == null) {
            throw new IllegalStateException("Can not have a profile of type " + profileType
                    + " as they are not supported on this device");
        }

        return profile(resolvedUserType, forUser);
    }

    private UserReference profile(
            com.android.bedstead.nene.users.UserType userType, UserReference forUser) {
        if (userType == null || forUser == null) {
            throw new NullPointerException();
        }

        if (!mProfiles.containsKey(userType) || !mProfiles.get(userType).containsKey(forUser)) {
            throw new IllegalStateException(
                    "No harrier-managed profile of type " + userType + ". This method should only"
                            + " be used when Harrier has been used to create the profile.");
        }

        return mProfiles.get(userType).get(forUser);
    }

    private boolean isRunningOnUser(String userType) {
        return sTestApis.users().instrumented()
                .resolve().type().name().equals(userType);
    }

    /**
     * Get the {@link UserReference} of the tv profile for the current user
     *
     * <p>This should only be used to get tv profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed tv profile
     */
    public UserReference tvProfile() {
        return tvProfile(/* forUser= */ UserType.CURRENT_USER);
    }

    /**
     * Get the {@link UserReference} of the tv profile.
     *
     * <p>This should only be used to get tv profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed tv profile
     */
    public UserReference tvProfile(UserType forUser) {
        return tvProfile(resolveUserTypeToUser(forUser));
    }

    /**
     * Get the {@link UserReference} of the tv profile.
     *
     * <p>This should only be used to get tv profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed tv profile
     */
    public UserReference tvProfile(UserReference forUser) {
        return profile(TV_PROFILE_TYPE_NAME, forUser);
    }

    /**
     * Get the user ID of the first human user on the device.
     *
     * <p>Returns {@code null} if there is none present.
     */
    @Nullable
    public UserReference primaryUser() {
        return sTestApis.users().all()
                .stream().filter(User::isPrimary).findFirst().orElse(null);
    }

    /**
     * Get a secondary user.
     *
     * <p>This should only be used to get secondary users managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed secondary user
     */
    @Nullable
    public UserReference secondaryUser() {
        return user(SECONDARY_USER_TYPE_NAME);
    }

    private UserReference user(String userType) {
        com.android.bedstead.nene.users.UserType resolvedUserType =
                sTestApis.users().supportedType(userType);

        if (resolvedUserType == null) {
            throw new IllegalStateException("Can not have a user of type " + userType
                    + " as they are not supported on this device");
        }

        return user(resolvedUserType);
    }

    private UserReference user(com.android.bedstead.nene.users.UserType userType) {
        if (userType == null) {
            throw new NullPointerException();
        }

        if (!mUsers.containsKey(userType)) {
            throw new IllegalStateException(
                    "No harrier-managed secondary user. This method should only be used when "
                            + "Harrier has been used to create the secondary user.");
        }

        return mUsers.get(userType);
    }

    private UserReference ensureHasProfile(
            String profileType, boolean installTestApp, UserType forUser) {
        requireFeature("android.software.managed_users", FailureMode.SKIP);
        com.android.bedstead.nene.users.UserType resolvedUserType =
                requireUserSupported(profileType, FailureMode.SKIP);

        UserReference forUserReference = resolveUserTypeToUser(forUser);

        UserReference profile =
                sTestApis.users().findProfileOfType(resolvedUserType, forUserReference);
        if (profile == null) {
            profile = createProfile(resolvedUserType, forUserReference);
        }

        profile.start();

        if (installTestApp) {
            sTestApis.packages().find(sContext.getPackageName()).install(profile);
        } else {
            sTestApis.packages().find(sContext.getPackageName()).uninstall(profile);
        }

        if (!mProfiles.containsKey(resolvedUserType)) {
            mProfiles.put(resolvedUserType, new HashMap<>());
        }

        mProfiles.get(resolvedUserType).put(forUserReference, profile);

        return profile;
    }

    private void ensureHasNoProfile(String profileType, UserType forUser) {
        requireFeature("android.software.managed_users", FailureMode.SKIP);

        UserReference forUserReference = resolveUserTypeToUser(forUser);
        com.android.bedstead.nene.users.UserType resolvedProfileType =
                sTestApis.users().supportedType(profileType);

        if (resolvedProfileType == null) {
            // These profile types don't exist so there can't be any
            return;
        }

        UserReference profile =
                sTestApis.users().findProfileOfType(
                        resolvedProfileType,
                        forUserReference);
        if (profile != null) {
            removeAndRecordUser(profile.resolve());
        }
    }

    private void ensureHasUser(String userType, boolean installTestApp) {
        com.android.bedstead.nene.users.UserType resolvedUserType =
                requireUserSupported(userType, FailureMode.SKIP);

        Collection<UserReference> users = sTestApis.users().findUsersOfType(resolvedUserType);

        UserReference user = users.isEmpty() ? createUser(resolvedUserType)
                : users.iterator().next();

        user.start();

        if (installTestApp) {
            sTestApis.packages().find(sContext.getPackageName()).install(user);
        } else {
            sTestApis.packages().find(sContext.getPackageName()).uninstall(user);
        }

        mUsers.put(resolvedUserType, user);
    }

    /**
     * Ensure that there is no user of the given type.
     */
    private void ensureHasNoUser(String userType) {
        com.android.bedstead.nene.users.UserType resolvedUserType =
                sTestApis.users().supportedType(userType);

        if (resolvedUserType == null) {
            // These user types don't exist so there can't be any
            return;
        }

        for (UserReference secondaryUser : sTestApis.users().findUsersOfType(resolvedUserType)) {
            removeAndRecordUser(secondaryUser.resolve());
        }
    }

    private void removeAndRecordUser(User user) {
        if (user == null) {
            return; // Nothing to remove
        }

        mRemovedUsers.add(sTestApis.users().createUser()
                .name(user.name())
                .type(user.type())
                .parent(user.parent()));

        user.remove();
    }

    public void requireCanSupportAdditionalUser() {
        int maxUsers = getMaxNumberOfUsersSupported();
        int currentUsers = sTestApis.users().all().size();

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
                return sTestApis.users().instrumented();
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
        mProfiles.clear();
        mUsers.clear();

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

        for (UserBuilder userBuilder : mRemovedUsers) {
            userBuilder.create();
        }

        mRemovedUsers.clear();
    }

    private UserReference createProfile(
            com.android.bedstead.nene.users.UserType profileType, UserReference parent) {
        requireCanSupportAdditionalUser();
        try {
            UserReference user = sTestApis.users().createUser()
                    .parent(parent)
                    .type(profileType)
                    .createAndStart();
            mCreatedUsers.add(user);
            return user;
        } catch (NeneException e) {
            throw new IllegalStateException("Error creating profile of type " + profileType, e);
        }
    }

    private UserReference createUser(com.android.bedstead.nene.users.UserType userType) {
        requireCanSupportAdditionalUser();
        try {
            UserReference user = sTestApis.users().createUser()
                    .type(userType)
                    .createAndStart();
            mCreatedUsers.add(user);
            return user;
        } catch (NeneException e) {
            throw new IllegalStateException("Error creating user of type " + userType, e);
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
