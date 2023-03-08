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

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.content.pm.PackageManager.FEATURE_MANAGED_USERS;
import static android.os.Build.VERSION.SDK_INT;

import static com.android.bedstead.harrier.AnnotationExecutorUtil.checkFailOrSkip;
import static com.android.bedstead.harrier.AnnotationExecutorUtil.failOrSkip;
import static com.android.bedstead.harrier.Defaults.DEFAULT_PASSWORD;
import static com.android.bedstead.harrier.annotations.EnsureHasAccount.DEFAULT_ACCOUNT_KEY;
import static com.android.bedstead.harrier.annotations.EnsureTestAppInstalled.DEFAULT_TEST_APP_KEY;
import static com.android.bedstead.nene.flags.CommonFlags.DevicePolicyManager.ENABLE_DEVICE_POLICY_ENGINE_FLAG;
import static com.android.bedstead.nene.flags.CommonFlags.DevicePolicyManager.PERMISSION_BASED_ACCESS_EXPERIMENT_FLAG;
import static com.android.bedstead.nene.flags.CommonFlags.NAMESPACE_DEVICE_POLICY_MANAGER;
import static com.android.bedstead.nene.users.UserType.MANAGED_PROFILE_TYPE_NAME;
import static com.android.bedstead.nene.users.UserType.SECONDARY_USER_TYPE_NAME;
import static com.android.bedstead.nene.utils.Versions.meetsSdkVersionRequirements;
import static com.android.bedstead.remoteaccountauthenticator.RemoteAccountAuthenticator.REMOTE_ACCOUNT_AUTHENTICATOR_TEST_APP;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.BeforeClass;
import com.android.bedstead.harrier.annotations.EnsureBluetoothDisabled;
import com.android.bedstead.harrier.annotations.EnsureBluetoothEnabled;
import com.android.bedstead.harrier.annotations.EnsureCanAddUser;
import com.android.bedstead.harrier.annotations.EnsureCanGetPermission;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveAppOp;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureFeatureFlagEnabled;
import com.android.bedstead.harrier.annotations.EnsureFeatureFlagNotEnabled;
import com.android.bedstead.harrier.annotations.EnsureFeatureFlagValue;
import com.android.bedstead.harrier.annotations.EnsureGlobalSettingSet;
import com.android.bedstead.harrier.annotations.EnsureHasAccount;
import com.android.bedstead.harrier.annotations.EnsureHasAccountAuthenticator;
import com.android.bedstead.harrier.annotations.EnsureHasAccounts;
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.harrier.annotations.EnsureHasAppOp;
import com.android.bedstead.harrier.annotations.EnsureHasNoAccounts;
import com.android.bedstead.harrier.annotations.EnsureHasNoAdditionalUser;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.EnsurePackageNotInstalled;
import com.android.bedstead.harrier.annotations.EnsurePasswordNotSet;
import com.android.bedstead.harrier.annotations.EnsurePasswordSet;
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn;
import com.android.bedstead.harrier.annotations.EnsureTestAppHasAppOp;
import com.android.bedstead.harrier.annotations.EnsureTestAppHasPermission;
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled;
import com.android.bedstead.harrier.annotations.EnsureUnlocked;
import com.android.bedstead.harrier.annotations.EnsureWifiDisabled;
import com.android.bedstead.harrier.annotations.EnsureWifiEnabled;
import com.android.bedstead.harrier.annotations.FailureMode;
import com.android.bedstead.harrier.annotations.OtherUser;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireFeatureFlagEnabled;
import com.android.bedstead.harrier.annotations.RequireFeatureFlagNotEnabled;
import com.android.bedstead.harrier.annotations.RequireFeatureFlagValue;
import com.android.bedstead.harrier.annotations.RequireHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireInstantApp;
import com.android.bedstead.harrier.annotations.RequireLowRamDevice;
import com.android.bedstead.harrier.annotations.RequireMultiUserSupport;
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireNotInstantApp;
import com.android.bedstead.harrier.annotations.RequireNotLowRamDevice;
import com.android.bedstead.harrier.annotations.RequireNotVisibleBackgroundUsers;
import com.android.bedstead.harrier.annotations.RequireNotVisibleBackgroundUsersOnDefaultDisplay;
import com.android.bedstead.harrier.annotations.RequirePackageInstalled;
import com.android.bedstead.harrier.annotations.RequirePackageNotInstalled;
import com.android.bedstead.harrier.annotations.RequireRunOnAdditionalUser;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.harrier.annotations.RequireSystemServiceAvailable;
import com.android.bedstead.harrier.annotations.RequireTargetSdkVersion;
import com.android.bedstead.harrier.annotations.RequireUserSupported;
import com.android.bedstead.harrier.annotations.RequireVisibleBackgroundUsers;
import com.android.bedstead.harrier.annotations.RequireVisibleBackgroundUsersOnDefaultDisplay;
import com.android.bedstead.harrier.annotations.TestTag;
import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDelegate;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDevicePolicyManagerRoleHolder;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDelegate;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoProfileOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasProfileOwner;
import com.android.bedstead.harrier.annotations.enterprise.RequireHasPolicyExemptApps;
import com.android.bedstead.harrier.annotations.meta.EnsureHasNoProfileAnnotation;
import com.android.bedstead.harrier.annotations.meta.EnsureHasNoUserAnnotation;
import com.android.bedstead.harrier.annotations.meta.EnsureHasProfileAnnotation;
import com.android.bedstead.harrier.annotations.meta.EnsureHasUserAnnotation;
import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation;
import com.android.bedstead.harrier.annotations.meta.RequireRunOnProfileAnnotation;
import com.android.bedstead.harrier.annotations.meta.RequireRunOnUserAnnotation;
import com.android.bedstead.harrier.annotations.meta.RequiresBedsteadJUnit4;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.accounts.AccountReference;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.devicepolicy.DeviceOwnerType;
import com.android.bedstead.nene.devicepolicy.DevicePolicyController;
import com.android.bedstead.nene.devicepolicy.ProfileOwner;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.flags.Flags;
import com.android.bedstead.nene.logcat.SystemServerException;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.permissions.PermissionContextImpl;
import com.android.bedstead.nene.types.OptionalBoolean;
import com.android.bedstead.nene.users.UserBuilder;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.Tags;
import com.android.bedstead.nene.utils.Versions;
import com.android.bedstead.remoteaccountauthenticator.RemoteAccountAuthenticator;
import com.android.bedstead.remotedpc.RemoteDelegate;
import com.android.bedstead.remotedpc.RemoteDevicePolicyManagerRoleHolder;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.bedstead.remotedpc.RemoteDpcUsingParentInstance;
import com.android.bedstead.remotedpc.RemotePolicyManager;
import com.android.bedstead.remotedpc.RemoteTestApp;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.bedstead.testapp.TestAppProvider;
import com.android.bedstead.testapp.TestAppQueryBuilder;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.eventlib.EventLogs;
import com.android.queryable.annotations.Query;

import com.google.common.base.Objects;

import junit.framework.AssertionFailedError;

import org.junit.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
public final class DeviceState extends HarrierRule {
    private static final String SWITCHED_TO_USER = "switchedToUser";
    private static final String SWITCHED_TO_PARENT_USER = "switchedToParentUser";
    public static final String INSTALL_INSTRUMENTED_APP = "installInstrumentedApp";
    private static final String IS_QUIET_MODE_ENABLED = "isQuietModeEnabled";
    public static final String FOR_USER = "forUser";
    public static final String DPC_IS_PRIMARY = "dpcIsPrimary";
    public static final String AFFILIATION_IDS = "affiliationIds";
    private static final String USE_PARENT_INSTANCE_OF_DPC = "useParentInstanceOfDpc";

    private final Context mContext = TestApis.context().instrumentedContext();
    private static final String SKIP_TEST_TEARDOWN_KEY = "skip-test-teardown";
    private static final String SKIP_CLASS_TEARDOWN_KEY = "skip-class-teardown";
    private static final String SKIP_TESTS_REASON_KEY = "skip-tests-reason";
    private static final String MIN_SDK_VERSION_KEY = "min-sdk-version";
    private static final String PERMISSIONS_INSTRUMENTATION_PACKAGE_KEY =
            "permission-instrumentation-package";
    private static final String ADDITIONAL_FEATURES_KEY = "additional-features";
    private boolean mSkipTestTeardown;
    private boolean mSkipClassTeardown;
    private boolean mSkipTests;
    private boolean mFailTests;
    private boolean mUsingBedsteadJUnit4 = false;
    private String mSkipTestsReason;
    private String mFailTestsReason;
    private List<String> mAdditionalFeatures;
    // The minimum version supported by tests, defaults to current version
    private int mMinSdkVersion;
    private int mMinSdkVersionCurrentTest;
    private @Nullable String mPermissionsInstrumentationPackage;
    private final Set<String> mPermissionsInstrumentationPackagePermissions = new HashSet<>();

    // Marks if the conditions for requiring running under permission instrumentation have been set
    // if not - we assume the test should never run under permission instrumentation
    // This is only used if a permission instrumentation package is set
    private boolean mHasRequirePermissionInstrumentation = false;

    private static final String TV_PROFILE_TYPE_NAME = "com.android.tv.profile";
    private static final String CLONE_PROFILE_TYPE_NAME = "android.os.usertype.profile.CLONE";


    // We timeout 10 seconds before the infra would timeout
    private static final Duration MAX_TEST_DURATION =
            Duration.ofMillis(
                    Long.parseLong(TestApis.instrumentation().arguments().getString(
                            "timeout_msec", "600000")) - 2000);
    private final ExecutorService mTestExecutor = Executors.newSingleThreadExecutor();
    private Thread mTestThread;

    public DeviceState() {
        Future<Thread> testThreadFuture = mTestExecutor.submit(Thread::currentThread);

        mSkipTestTeardown = TestApis.instrumentation().arguments().getBoolean(
                SKIP_TEST_TEARDOWN_KEY, false);
        mSkipClassTeardown = TestApis.instrumentation().arguments().getBoolean(
                SKIP_CLASS_TEARDOWN_KEY, false);

        mSkipTestsReason = TestApis.instrumentation().arguments().getString(SKIP_TESTS_REASON_KEY,
                "");
        mSkipTests = !mSkipTestsReason.isEmpty();
        mMinSdkVersion = TestApis.instrumentation().arguments().getInt(MIN_SDK_VERSION_KEY,
                SDK_INT);
        mPermissionsInstrumentationPackage = TestApis.instrumentation().arguments().getString(
                PERMISSIONS_INSTRUMENTATION_PACKAGE_KEY);
        mAdditionalFeatures = Arrays.asList(TestApis.instrumentation().arguments().getString(
                ADDITIONAL_FEATURES_KEY, "").split(","));
        if (mPermissionsInstrumentationPackage != null) {
            mPermissionsInstrumentationPackagePermissions.addAll(
                    TestApis.packages().find(mPermissionsInstrumentationPackage)
                            .requestedPermissions());
        }

        try {
            mTestThread = testThreadFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new AssertionError(
                    "Error setting up DeviceState. Interrupted getting test thread", e);
        }
    }

    @Override
    void setSkipTestTeardown(boolean skipTestTeardown) {
        mSkipTestTeardown = skipTestTeardown;
    }

    @Override
    void setUsingBedsteadJUnit4(boolean usingBedsteadJUnit4) {
        mUsingBedsteadJUnit4 = usingBedsteadJUnit4;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        if (description.isTest()) {
            return applyTest(base, description);
        } else if (description.isSuite()) {
            return applySuite(base, description);
        }
        throw new IllegalStateException("Unknown description type: " + description);
    }

    private Statement applyTest(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Future<Throwable> future = mTestExecutor.submit(() -> {
                    try {
                        executeTest(base, description);
                        return null;
                    } catch (Throwable e) {
                        return e;
                    }
                });

                try {
                    Throwable t = future.get(MAX_TEST_DURATION.getSeconds(), TimeUnit.SECONDS);
                    if (t != null) {
                        if (t.getStackTrace().length > 0) {
                            if (t.getStackTrace()[0].getMethodName().equals("createExceptionOrNull")) {
                                SystemServerException s = TestApis.logcat().findSystemServerException(t);
                                if (s != null) {
                                    throw s;
                                }
                            }
                        }

                        throw t;
                    }
                } catch (TimeoutException e) {
                    StackTraceElement[] stack = mTestThread.getStackTrace();
                    future.cancel(true);

                    AssertionError assertionError = new AssertionError(
                            "Timed out executing test " + description.getDisplayName()
                                    + " after " + MAX_TEST_DURATION);
                    assertionError.setStackTrace(stack);
                    throw assertionError;
                }
            }
        };
    }

    private void executeTest(Statement base, Description description) throws Throwable {
        String testName = description.getMethodName();

        try {
            Log.d(LOG_TAG, "Preparing state for test " + testName);

            if (mOriginalSwitchedUser == null) {
                mOriginalSwitchedUser = TestApis.users().current();
            }
            testApps().snapshot();
            Tags.clearTags();
            Tags.addTag(Tags.USES_DEVICESTATE);
            assumeFalse(mSkipTestsReason, mSkipTests);
            assertFalse(mFailTestsReason, mFailTests);
            TestApis.packages().features().addAll(mAdditionalFeatures);

            // Ensure that tests only see events from the current test
            EventLogs.resetLogs();

            // Avoid cached activities on screen
            TestApis.activities().clearAllActivities();

            mMinSdkVersionCurrentTest = mMinSdkVersion;
            List<Annotation> annotations = getAnnotations(description);
            applyAnnotations(annotations, /* isTest= */ true);
            String coexistenceOption = TestApis.instrumentation().arguments().getString("COEXISTENCE", "?");
            if (coexistenceOption.equals("true")) {
                ensureFeatureFlagEnabled(NAMESPACE_DEVICE_POLICY_MANAGER, PERMISSION_BASED_ACCESS_EXPERIMENT_FLAG);
                ensureFeatureFlagEnabled(NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG);
            } else if (coexistenceOption.equals("false")) {
                ensureFeatureFlagNotEnabled(NAMESPACE_DEVICE_POLICY_MANAGER, PERMISSION_BASED_ACCESS_EXPERIMENT_FLAG);
                ensureFeatureFlagEnabled(NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG);
            }

            Log.d(LOG_TAG, "Finished preparing state for test " + testName);

            base.evaluate();
        } finally {
            Log.d(LOG_TAG, "Tearing down state for test " + testName);
            teardownNonShareableState();
            if (!mSkipTestTeardown) {
                teardownShareableState();
            }
            Log.d(LOG_TAG, "Finished tearing down state for test " + testName);
        }
    }

    private void applyAnnotations(List<Annotation> annotations, boolean isTest)
            throws Throwable {
        Log.d(LOG_TAG, "Applying annotations: " + annotations);
        for (Annotation annotation : annotations) {
            Log.v(LOG_TAG, "Applying annotation " + annotation);

            Class<? extends Annotation> annotationType = annotation.annotationType();

            EnsureHasNoProfileAnnotation ensureHasNoProfileAnnotation =
                    annotationType.getAnnotation(EnsureHasNoProfileAnnotation.class);
            if (ensureHasNoProfileAnnotation != null) {
                UserType userType = (UserType) annotation.annotationType()
                        .getMethod(FOR_USER).invoke(annotation);
                ensureHasNoProfile(ensureHasNoProfileAnnotation.value(), userType);
                continue;
            }

            EnsureHasProfileAnnotation ensureHasProfileAnnotation =
                    annotationType.getAnnotation(EnsureHasProfileAnnotation.class);
            if (ensureHasProfileAnnotation != null) {
                UserType forUser = (UserType) annotation.annotationType()
                        .getMethod(FOR_USER).invoke(annotation);
                OptionalBoolean installInstrumentedApp = (OptionalBoolean)
                        annotation.annotationType()
                                .getMethod(INSTALL_INSTRUMENTED_APP).invoke(annotation);

                OptionalBoolean isQuietModeEnabled = OptionalBoolean.ANY;

                try {
                    isQuietModeEnabled = (OptionalBoolean)
                            annotation.annotationType().getMethod(
                                    IS_QUIET_MODE_ENABLED).invoke(annotation);
                } catch (NoSuchMethodException e) {
                    // Expected, we default to ANY
                }

                boolean dpcIsPrimary = false;
                boolean useParentInstance = false;
                TestAppQueryBuilder dpcQuery = null;
                if (ensureHasProfileAnnotation.hasProfileOwner()) {
                    // TODO(b/206441366): Add instant app support
                    requireNotInstantApp(
                            "Instant Apps cannot run Enterprise Tests", FailureMode.SKIP);

                    dpcIsPrimary = (boolean)
                            annotation.annotationType()
                                    .getMethod(DPC_IS_PRIMARY).invoke(annotation);

                    if (dpcIsPrimary) {
                        useParentInstance = (boolean)
                                annotation.annotationType()
                                        .getMethod(USE_PARENT_INSTANCE_OF_DPC).invoke(
                                                annotation);

                    }

                    dpcQuery = getDpcQueryFromAnnotation(annotation);
                }

                OptionalBoolean switchedToParentUser = (OptionalBoolean)
                        annotation.annotationType()
                                .getMethod(SWITCHED_TO_PARENT_USER).invoke(annotation);

                ensureHasProfile(
                        ensureHasProfileAnnotation.value(), installInstrumentedApp,
                        forUser, ensureHasProfileAnnotation.hasProfileOwner(),
                        dpcIsPrimary, useParentInstance, switchedToParentUser, isQuietModeEnabled,
                        dpcQuery);

                if (ensureHasProfileAnnotation.hasProfileOwner()) {
                    ((ProfileOwner) profileOwner(
                            workProfile(forUser)).devicePolicyController()).setIsOrganizationOwned(
                            isOrganizationOwned(annotation));
                }

                continue;
            }

            EnsureHasNoUserAnnotation ensureHasNoUserAnnotation =
                    annotationType.getAnnotation(EnsureHasNoUserAnnotation.class);
            if (ensureHasNoUserAnnotation != null) {
                ensureHasNoUser(ensureHasNoUserAnnotation.value());
                continue;
            }

            EnsureHasUserAnnotation ensureHasUserAnnotation =
                    annotationType.getAnnotation(EnsureHasUserAnnotation.class);
            if (ensureHasUserAnnotation != null) {
                OptionalBoolean installInstrumentedApp = (OptionalBoolean)
                        annotation.annotationType()
                                .getMethod(INSTALL_INSTRUMENTED_APP).invoke(annotation);
                OptionalBoolean switchedToUser = (OptionalBoolean)
                        annotation.annotationType()
                                .getMethod(SWITCHED_TO_USER).invoke(annotation);
                ensureHasUser(
                        ensureHasUserAnnotation.value(), installInstrumentedApp,
                        switchedToUser);
                continue;
            }

            if (annotation instanceof EnsureHasAdditionalUser) {
                EnsureHasAdditionalUser ensureHasAdditionalUserAnnotation =
                        (EnsureHasAdditionalUser) annotation;
                ensureHasAdditionalUser(
                        ensureHasAdditionalUserAnnotation.installInstrumentedApp(),
                        ensureHasAdditionalUserAnnotation.switchedToUser());
                continue;
            }

            if (annotation instanceof EnsureHasNoAdditionalUser) {
                ensureHasNoAdditionalUser();
                continue;
            }

            if (annotation instanceof RequireRunOnAdditionalUser) {
                RequireRunOnAdditionalUser requireRunOnAdditionalUserAnnotation =
                        (RequireRunOnAdditionalUser) annotation;
                requireRunOnAdditionalUser(
                        requireRunOnAdditionalUserAnnotation.switchedToUser());
                continue;
            }

            RequireRunOnUserAnnotation requireRunOnUserAnnotation =
                    annotationType.getAnnotation(RequireRunOnUserAnnotation.class);
            if (requireRunOnUserAnnotation != null) {
                OptionalBoolean switchedToUser = (OptionalBoolean)
                        annotation.annotationType()
                                .getMethod(SWITCHED_TO_USER).invoke(annotation);
                requireRunOnUser(requireRunOnUserAnnotation.value(), switchedToUser);
                continue;
            }

            if (annotation instanceof TestTag) {
                TestTag testTagAnnotation = (TestTag) annotation;
                Tags.addTag(testTagAnnotation.value());
            }

            RequireRunOnProfileAnnotation requireRunOnProfileAnnotation =
                    annotationType.getAnnotation(RequireRunOnProfileAnnotation.class);
            if (requireRunOnProfileAnnotation != null) {
                OptionalBoolean installInstrumentedAppInParent = (OptionalBoolean)
                        annotation.annotationType()
                                .getMethod("installInstrumentedAppInParent")
                                .invoke(annotation);

                OptionalBoolean switchedToParentUser = (OptionalBoolean)
                        annotation.annotationType()
                                .getMethod(SWITCHED_TO_PARENT_USER).invoke(annotation);


                boolean dpcIsPrimary = false;
                Set<String> affiliationIds = null;
                TestAppQueryBuilder dpcQuery = null;
                if (requireRunOnProfileAnnotation.hasProfileOwner()) {
                    dpcIsPrimary = (boolean)
                            annotation.annotationType()
                                    .getMethod(DPC_IS_PRIMARY).invoke(annotation);
                    affiliationIds = new HashSet<>(Arrays.asList((String[])
                            annotation.annotationType()
                                    .getMethod(AFFILIATION_IDS).invoke(annotation)));
                    dpcQuery = getDpcQueryFromAnnotation(annotation);
                }

                requireRunOnProfile(requireRunOnProfileAnnotation.value(),
                        installInstrumentedAppInParent,
                        requireRunOnProfileAnnotation.hasProfileOwner(),
                        dpcIsPrimary, /* useParentInstance= */ false,
                        switchedToParentUser, affiliationIds, dpcQuery);

                if (requireRunOnProfileAnnotation.hasProfileOwner()) {
                    ((ProfileOwner) profileOwner(
                            workProfile()).devicePolicyController()).setIsOrganizationOwned(
                            isOrganizationOwned(annotation));
                }

                continue;
            }

            if (annotation instanceof EnsureTestAppInstalled) {
                EnsureTestAppInstalled ensureTestAppInstalledAnnotation =
                        (EnsureTestAppInstalled) annotation;
                ensureTestAppInstalled(
                        ensureTestAppInstalledAnnotation.key(),
                        ensureTestAppInstalledAnnotation.packageName(),
                        ensureTestAppInstalledAnnotation.onUser(),
                        ensureTestAppInstalledAnnotation.isPrimary()
                );
                continue;
            }

            if (annotation instanceof EnsureTestAppHasPermission) {
                EnsureTestAppHasPermission ensureTestAppHasPermissionAnnotation =
                        (EnsureTestAppHasPermission) annotation;
                ensureTestAppHasPermission(
                        ensureTestAppHasPermissionAnnotation.testAppKey(),
                        ensureTestAppHasPermissionAnnotation.value(),
                        ensureTestAppHasPermissionAnnotation.minVersion(),
                        ensureTestAppHasPermissionAnnotation.maxVersion(),
                        ensureTestAppHasPermissionAnnotation.failureMode()
                );
                continue;
            }

            if (annotation instanceof EnsureTestAppHasAppOp) {
                EnsureTestAppHasAppOp ensureTestAppHasAppOpAnnotation =
                        (EnsureTestAppHasAppOp) annotation;
                ensureTestAppHasAppOp(
                        ensureTestAppHasAppOpAnnotation.testAppKey(),
                        ensureTestAppHasAppOpAnnotation.value(),
                        ensureTestAppHasAppOpAnnotation.minVersion(),
                        ensureTestAppHasAppOpAnnotation.maxVersion()
                );
                continue;
            }

            if (annotation instanceof EnsureHasDelegate) {
                EnsureHasDelegate ensureHasDelegateAnnotation =
                        (EnsureHasDelegate) annotation;
                ensureHasDelegate(
                        ensureHasDelegateAnnotation.admin(),
                        Arrays.asList(ensureHasDelegateAnnotation.scopes()),
                        ensureHasDelegateAnnotation.isPrimary());
                continue;
            }

            if (annotation instanceof EnsureHasDevicePolicyManagerRoleHolder) {
                EnsureHasDevicePolicyManagerRoleHolder ensureHasDevicePolicyManagerRoleHolder =
                        (EnsureHasDevicePolicyManagerRoleHolder) annotation;
                ensureHasDevicePolicyManagerRoleHolder(
                        ensureHasDevicePolicyManagerRoleHolder.onUser(),
                        ensureHasDevicePolicyManagerRoleHolder.isPrimary());
            }


            if (annotation instanceof EnsureHasDeviceOwner) {
                EnsureHasDeviceOwner ensureHasDeviceOwnerAnnotation =
                        (EnsureHasDeviceOwner) annotation;

                ensureHasDeviceOwner(ensureHasDeviceOwnerAnnotation.failureMode(),
                        ensureHasDeviceOwnerAnnotation.isPrimary(),
                        new HashSet<>(
                                Arrays.asList(
                                        ensureHasDeviceOwnerAnnotation.affiliationIds())),
                        ensureHasDeviceOwnerAnnotation.type(),
                        getDpcQueryFromAnnotation(annotation));
                continue;
            }

            if (annotation instanceof EnsureHasNoDelegate) {
                EnsureHasNoDelegate ensureHasNoDelegateAnnotation =
                        (EnsureHasNoDelegate) annotation;
                ensureHasNoDelegate(ensureHasNoDelegateAnnotation.admin());
                continue;
            }

            if (annotation instanceof EnsureHasNoDeviceOwner) {
                ensureHasNoDeviceOwner();
                continue;
            }

            if (annotation instanceof RequireFeature) {
                RequireFeature requireFeatureAnnotation = (RequireFeature) annotation;
                requireFeature(
                        requireFeatureAnnotation.value(),
                        requireFeatureAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof RequireDoesNotHaveFeature) {
                RequireDoesNotHaveFeature requireDoesNotHaveFeatureAnnotation =
                        (RequireDoesNotHaveFeature) annotation;
                requireDoesNotHaveFeature(
                        requireDoesNotHaveFeatureAnnotation.value(),
                        requireDoesNotHaveFeatureAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof EnsureHasProfileOwner) {
                EnsureHasProfileOwner ensureHasProfileOwnerAnnotation =
                        (EnsureHasProfileOwner) annotation;
                ensureHasProfileOwner(ensureHasProfileOwnerAnnotation.onUser(),
                        ensureHasProfileOwnerAnnotation.isPrimary(),
                        ensureHasProfileOwnerAnnotation.useParentInstance(),
                        new HashSet<>(Arrays.asList(
                                ensureHasProfileOwnerAnnotation.affiliationIds())),
                        getDpcQueryFromAnnotation(annotation));
                continue;
            }

            if (annotationType.equals(EnsureHasNoProfileOwner.class)) {
                EnsureHasNoProfileOwner ensureHasNoProfileOwnerAnnotation =
                        (EnsureHasNoProfileOwner) annotation;
                ensureHasNoProfileOwner(ensureHasNoProfileOwnerAnnotation.onUser());
                continue;
            }

            if (annotation instanceof RequireUserSupported) {
                RequireUserSupported requireUserSupportedAnnotation =
                        (RequireUserSupported) annotation;
                requireUserSupported(
                        requireUserSupportedAnnotation.value(),
                        requireUserSupportedAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof RequireLowRamDevice) {
                RequireLowRamDevice requireLowRamDeviceAnnotation =
                        (RequireLowRamDevice) annotation;
                requireLowRamDevice(requireLowRamDeviceAnnotation.reason(),
                        requireLowRamDeviceAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof RequireNotLowRamDevice) {
                RequireNotLowRamDevice requireNotLowRamDeviceAnnotation =
                        (RequireNotLowRamDevice) annotation;
                requireNotLowRamDevice(requireNotLowRamDeviceAnnotation.reason(),
                        requireNotLowRamDeviceAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof RequireVisibleBackgroundUsers) {
                RequireVisibleBackgroundUsers castedAnnotation =
                        (RequireVisibleBackgroundUsers) annotation;
                requireVisibleBackgroundUsersSupported(castedAnnotation.reason(),
                        castedAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof RequireNotVisibleBackgroundUsers) {
                RequireNotVisibleBackgroundUsers castedAnnotation =
                        (RequireNotVisibleBackgroundUsers) annotation;
                requireVisibleBackgroundUsersNotSupported(castedAnnotation.reason(),
                        castedAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof RequireVisibleBackgroundUsersOnDefaultDisplay) {
                RequireVisibleBackgroundUsersOnDefaultDisplay castedAnnotation =
                        (RequireVisibleBackgroundUsersOnDefaultDisplay) annotation;
                requireVisibleBackgroundUsersOnDefaultDisplaySupported(castedAnnotation.reason(),
                        castedAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof RequireNotVisibleBackgroundUsersOnDefaultDisplay) {
                RequireNotVisibleBackgroundUsersOnDefaultDisplay castedAnnotation =
                        (RequireNotVisibleBackgroundUsersOnDefaultDisplay) annotation;
                requireVisibleBackgroundUsersOnDefaultDisplayNotSupported(castedAnnotation.reason(),
                        castedAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof RequireSystemServiceAvailable) {
                RequireSystemServiceAvailable requireSystemServiceAvailableAnnotation =
                        (RequireSystemServiceAvailable) annotation;
                requireSystemServiceAvailable(requireSystemServiceAvailableAnnotation.value(),
                        requireSystemServiceAvailableAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof RequireTargetSdkVersion) {
                RequireTargetSdkVersion requireTargetSdkVersionAnnotation =
                        (RequireTargetSdkVersion) annotation;

                requireTargetSdkVersion(
                        requireTargetSdkVersionAnnotation.min(),
                        requireTargetSdkVersionAnnotation.max(),
                        requireTargetSdkVersionAnnotation.failureMode());

                continue;
            }

            if (annotation instanceof RequireSdkVersion) {
                RequireSdkVersion requireSdkVersionAnnotation =
                        (RequireSdkVersion) annotation;

                if (requireSdkVersionAnnotation.reason().isEmpty()) {
                    requireSdkVersion(
                            requireSdkVersionAnnotation.min(),
                            requireSdkVersionAnnotation.max(),
                            requireSdkVersionAnnotation.failureMode());
                } else {
                    requireSdkVersion(
                            requireSdkVersionAnnotation.min(),
                            requireSdkVersionAnnotation.max(),
                            requireSdkVersionAnnotation.failureMode(),
                            requireSdkVersionAnnotation.reason());
                }

                continue;
            }

            if (annotation instanceof RequirePackageInstalled) {
                RequirePackageInstalled requirePackageInstalledAnnotation =
                        (RequirePackageInstalled) annotation;
                requirePackageInstalled(
                        requirePackageInstalledAnnotation.value(),
                        requirePackageInstalledAnnotation.onUser(),
                        requirePackageInstalledAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof RequirePackageNotInstalled) {
                RequirePackageNotInstalled requirePackageNotInstalledAnnotation =
                        (RequirePackageNotInstalled) annotation;
                requirePackageNotInstalled(
                        requirePackageNotInstalledAnnotation.value(),
                        requirePackageNotInstalledAnnotation.onUser(),
                        requirePackageNotInstalledAnnotation.failureMode()
                );
                continue;
            }

            if (annotation instanceof EnsurePackageNotInstalled) {
                EnsurePackageNotInstalled ensurePackageNotInstalledAnnotation =
                        (EnsurePackageNotInstalled) annotation;
                ensurePackageNotInstalled(
                        ensurePackageNotInstalledAnnotation.value(),
                        ensurePackageNotInstalledAnnotation.onUser()
                );
                continue;
            }

            if (annotation instanceof RequireNotHeadlessSystemUserMode) {
                RequireNotHeadlessSystemUserMode requireNotHeadlessSystemUserModeAnnotation =
                        (RequireNotHeadlessSystemUserMode) annotation;
                requireNotHeadlessSystemUserMode(
                        requireNotHeadlessSystemUserModeAnnotation.reason());
                continue;
            }

            if (annotation instanceof RequireHeadlessSystemUserMode) {
                RequireHeadlessSystemUserMode requireHeadlessSystemUserModeAnnotation =
                        (RequireHeadlessSystemUserMode) annotation;
                requireHeadlessSystemUserMode(requireHeadlessSystemUserModeAnnotation.reason());
                continue;
            }

            if (annotation instanceof EnsureCanGetPermission) {
                EnsureCanGetPermission ensureCanGetPermissionAnnotation =
                        (EnsureCanGetPermission) annotation;

                if (!meetsSdkVersionRequirements(
                        ensureCanGetPermissionAnnotation.minVersion(),
                        ensureCanGetPermissionAnnotation.maxVersion())) {
                    Log.d(LOG_TAG,
                            "Version " + SDK_INT + " does not need to get permissions "
                                    + Arrays.toString(
                                    ensureCanGetPermissionAnnotation.value()));
                    continue;
                }

                for (String permission : ensureCanGetPermissionAnnotation.value()) {
                    ensureCanGetPermission(permission);
                }
                continue;
            }

            if (annotation instanceof EnsureHasAppOp) {
                EnsureHasAppOp ensureHasAppOpAnnotation = (EnsureHasAppOp) annotation;

                if (!meetsSdkVersionRequirements(
                        ensureHasAppOpAnnotation.minVersion(),
                        ensureHasAppOpAnnotation.maxVersion())) {
                    Log.d(LOG_TAG,
                            "Version " + SDK_INT + " does not need to get appOp "
                                    + ensureHasAppOpAnnotation.value());
                    continue;
                }

                try {
                    withAppOp(ensureHasAppOpAnnotation.value());
                } catch (NeneException e) {
                    failOrSkip("Error getting appOp: " + e,
                            ensureHasAppOpAnnotation.failureMode());
                }
                continue;
            }

            if (annotation instanceof EnsureDoesNotHaveAppOp) {
                EnsureDoesNotHaveAppOp ensureDoesNotHaveAppOpAnnotation =
                        (EnsureDoesNotHaveAppOp) annotation;

                try {
                    withoutAppOp(ensureDoesNotHaveAppOpAnnotation.value());
                } catch (NeneException e) {
                    failOrSkip("Error denying appOp: " + e,
                            ensureDoesNotHaveAppOpAnnotation.failureMode());
                }
                continue;
            }

            if (annotation instanceof EnsureHasPermission) {
                EnsureHasPermission ensureHasPermissionAnnotation =
                        (EnsureHasPermission) annotation;

                if (!meetsSdkVersionRequirements(
                        ensureHasPermissionAnnotation.minVersion(),
                        ensureHasPermissionAnnotation.maxVersion())) {
                    Log.d(LOG_TAG,
                            "Version " + SDK_INT + " does not need to get permission "
                                    + Arrays.toString(ensureHasPermissionAnnotation.value()));
                    continue;
                }

                for (String permission : ensureHasPermissionAnnotation.value()) {
                    ensureCanGetPermission(permission);
                }

                try {
                    withPermission(ensureHasPermissionAnnotation.value());
                } catch (NeneException e) {
                    failOrSkip("Error getting permission: " + e,
                            ensureHasPermissionAnnotation.failureMode());
                }
                continue;
            }

            if (annotation instanceof EnsureDoesNotHavePermission) {
                EnsureDoesNotHavePermission ensureDoesNotHavePermission =
                        (EnsureDoesNotHavePermission) annotation;

                try {
                    withoutPermission(ensureDoesNotHavePermission.value());
                } catch (NeneException e) {
                    failOrSkip("Error denying permission: " + e,
                            ensureDoesNotHavePermission.failureMode());
                }
                continue;
            }

            if (annotation instanceof EnsureScreenIsOn) {
                ensureScreenIsOn();
                continue;
            }

            if (annotation instanceof EnsureUnlocked) {
                ensureUnlocked();
                continue;
            }

            if (annotation instanceof EnsurePasswordSet) {
                EnsurePasswordSet ensurePasswordSetAnnotation =
                        (EnsurePasswordSet) annotation;
                ensurePasswordSet(
                        ensurePasswordSetAnnotation.forUser(),
                        ensurePasswordSetAnnotation.password());
                continue;
            }

            if (annotation instanceof EnsurePasswordNotSet) {
                EnsurePasswordNotSet ensurePasswordNotSetAnnotation =
                        (EnsurePasswordNotSet) annotation;
                ensurePasswordNotSet(ensurePasswordNotSetAnnotation.forUser());
                continue;
            }

            if (annotation instanceof OtherUser) {
                OtherUser otherUserAnnotation = (OtherUser) annotation;
                mOtherUserType = otherUserAnnotation.value();
                continue;
            }

            if (annotation instanceof EnsureBluetoothEnabled) {
                ensureBluetoothEnabled();
                continue;
            }

            if (annotation instanceof EnsureBluetoothDisabled) {
                ensureBluetoothDisabled();
                continue;
            }

            if (annotation instanceof EnsureWifiEnabled) {
                ensureWifiEnabled();
                continue;
            }

            if (annotation instanceof EnsureWifiDisabled) {
                ensureWifiDisabled();
                continue;
            }

            if (annotation instanceof EnsureGlobalSettingSet) {
                EnsureGlobalSettingSet ensureGlobalSettingSetAnnotation =
                        (EnsureGlobalSettingSet) annotation;
                ensureGlobalSettingSet(
                        ensureGlobalSettingSetAnnotation.key(),
                        ensureGlobalSettingSetAnnotation.value());
                continue;
            }

            if (annotation instanceof RequireMultiUserSupport) {
                RequireMultiUserSupport requireMultiUserSupportAnnotation =
                        (RequireMultiUserSupport) annotation;
                requireMultiUserSupport(requireMultiUserSupportAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof RequireHasPolicyExemptApps) {
                RequireHasPolicyExemptApps requireHasPolicyExemptAppsAnnotation =
                        (RequireHasPolicyExemptApps) annotation;
                requireHasPolicyExemptApps(requireHasPolicyExemptAppsAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof RequireInstantApp) {
                RequireInstantApp requireInstantAppAnnotation =
                        (RequireInstantApp) annotation;
                requireInstantApp(requireInstantAppAnnotation.reason(),
                        requireInstantAppAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof RequireNotInstantApp) {
                RequireNotInstantApp requireNotInstantAppAnnotation =
                        (RequireNotInstantApp) annotation;
                requireNotInstantApp(requireNotInstantAppAnnotation.reason(),
                        requireNotInstantAppAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof EnsureCanAddUser) {
                EnsureCanAddUser ensureCanAddUserAnnotation = (EnsureCanAddUser) annotation;
                ensureCanAddUser(
                        ensureCanAddUserAnnotation.number(),
                        ensureCanAddUserAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof RequireFeatureFlagEnabled) {
                RequireFeatureFlagEnabled requireFeatureFlagEnabledAnnotation =
                        (RequireFeatureFlagEnabled) annotation;
                requireFeatureFlagEnabled(
                        requireFeatureFlagEnabledAnnotation.namespace(),
                        requireFeatureFlagEnabledAnnotation.key(),
                        requireFeatureFlagEnabledAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof EnsureFeatureFlagEnabled) {
                EnsureFeatureFlagEnabled ensureFeatureFlagEnabledAnnotation =
                        (EnsureFeatureFlagEnabled) annotation;
                ensureFeatureFlagEnabled(
                        ensureFeatureFlagEnabledAnnotation.namespace(),
                        ensureFeatureFlagEnabledAnnotation.key());
                continue;
            }

            if (annotation instanceof RequireFeatureFlagNotEnabled) {
                RequireFeatureFlagNotEnabled requireFeatureFlagNotEnabledAnnotation =
                        (RequireFeatureFlagNotEnabled) annotation;
                requireFeatureFlagNotEnabled(
                        requireFeatureFlagNotEnabledAnnotation.namespace(),
                        requireFeatureFlagNotEnabledAnnotation.key(),
                        requireFeatureFlagNotEnabledAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof EnsureFeatureFlagNotEnabled) {
                EnsureFeatureFlagNotEnabled ensureFeatureFlagNotEnabledAnnotation =
                        (EnsureFeatureFlagNotEnabled) annotation;
                ensureFeatureFlagNotEnabled(
                        ensureFeatureFlagNotEnabledAnnotation.namespace(),
                        ensureFeatureFlagNotEnabledAnnotation.key());
                continue;
            }

            if (annotation instanceof RequireFeatureFlagValue) {
                RequireFeatureFlagValue requireFeatureFlagValueAnnotation =
                        (RequireFeatureFlagValue) annotation;
                requireFeatureFlagValue(
                        requireFeatureFlagValueAnnotation.namespace(),
                        requireFeatureFlagValueAnnotation.key(),
                        requireFeatureFlagValueAnnotation.value(),
                        requireFeatureFlagValueAnnotation.failureMode());
                continue;
            }

            if (annotation instanceof EnsureFeatureFlagValue) {
                EnsureFeatureFlagValue ensureFeatureFlagValueAnnotation =
                        (EnsureFeatureFlagValue) annotation;
                ensureFeatureFlagValue(
                        ensureFeatureFlagValueAnnotation.namespace(),
                        ensureFeatureFlagValueAnnotation.key(),
                        ensureFeatureFlagValueAnnotation.value());
                continue;
            }

            UsesAnnotationExecutor usesAnnotationExecutorAnnotation =
                    annotationType.getAnnotation(UsesAnnotationExecutor.class);
            if (usesAnnotationExecutorAnnotation != null) {
                AnnotationExecutor executor =
                        getAnnotationExecutor(usesAnnotationExecutorAnnotation.value());
                executor.applyAnnotation(annotation);
                continue;
            }


            if (annotation instanceof EnsureHasAccountAuthenticator) {
                EnsureHasAccountAuthenticator ensureHasAccountAuthenticatorAnnotation =
                        (EnsureHasAccountAuthenticator) annotation;
                ensureHasAccountAuthenticator(ensureHasAccountAuthenticatorAnnotation.onUser());
                continue;
            }

            if (annotation instanceof EnsureHasAccount) {
                EnsureHasAccount ensureHasAccountAnnotation =
                        (EnsureHasAccount) annotation;
                ensureHasAccount(
                        ensureHasAccountAnnotation.onUser(),
                        ensureHasAccountAnnotation.key(),
                        ensureHasAccountAnnotation.features());
                continue;
            }

            if (annotation instanceof EnsureHasAccounts) {
                EnsureHasAccounts ensureHasAccountsAnnotation =
                        (EnsureHasAccounts) annotation;
                ensureHasAccounts(ensureHasAccountsAnnotation.value());
                continue;
            }

            if (annotation instanceof EnsureHasNoAccounts) {
                EnsureHasNoAccounts ensureHasNoAccountsAnnotation =
                        (EnsureHasNoAccounts) annotation;
                ensureHasNoAccounts(ensureHasNoAccountsAnnotation.onUser());
                continue;
            }

            if (annotation instanceof EnsureHasUserRestriction) {
                EnsureHasUserRestriction ensureHasUserRestrictionAnnotation =
                        (EnsureHasUserRestriction) annotation;
                ensureHasUserRestriction(
                        ensureHasUserRestrictionAnnotation.value(),
                        ensureHasUserRestrictionAnnotation.onUser());
                continue;
            }

            if (annotation instanceof EnsureDoesNotHaveUserRestriction) {
                EnsureDoesNotHaveUserRestriction ensureDoesNotHaveUserRestrictionAnnotation =
                        (EnsureDoesNotHaveUserRestriction) annotation;
                ensureDoesNotHaveUserRestriction(
                        ensureDoesNotHaveUserRestrictionAnnotation.value(),
                        ensureDoesNotHaveUserRestrictionAnnotation.onUser());
                continue;
            }
        }

        requireSdkVersion(/* min= */ mMinSdkVersionCurrentTest,
                /* max= */ Integer.MAX_VALUE, FailureMode.SKIP);

        if (isTest && mPermissionsInstrumentationPackage != null
                && !mHasRequirePermissionInstrumentation) {
            requireNoPermissionsInstrumentation("No reason to use instrumentation");
        }
    }

    private static TestAppQueryBuilder getDpcQueryFromAnnotation(Annotation annotation) {
        try {
            Method queryMethod = annotation.annotationType().getMethod("dpc");
            Query query = (Query) queryMethod.invoke(annotation);
            return new TestAppProvider().query(query);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            Log.i(LOG_TAG, "Unable to get dpc query value for "
                    + annotation.annotationType().getName(), e);
        }
        return new TestAppProvider().query(); // No dpc specified - use any
    }

    private List<Annotation> getAnnotations(Description description) {
        if (mUsingBedsteadJUnit4 && description.isTest()) {
            // The annotations are already exploded for tests
            return new ArrayList<>(description.getAnnotations());
        }

        // Otherwise we should build a new collection by recursively gathering annotations
        // if we find any which don't work without the runner we should error and fail the test
        List<Annotation> annotations = new ArrayList<>();

        if (description.isTest()) {
            annotations =
                    new ArrayList<>(Arrays.asList(description.getTestClass().getAnnotations()));
        }

        annotations.addAll(description.getAnnotations());

        checkAnnotations(annotations);

        BedsteadJUnit4.resolveRecursiveAnnotations(this, annotations,
                /* parameterizedAnnotation= */ null);

        checkAnnotations(annotations);

        return annotations;
    }

    private void checkAnnotations(Collection<Annotation> annotations) {
        if (mUsingBedsteadJUnit4) {
            return;
        }
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

    private Statement applySuite(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                checkValidAnnotations(description);

                TestClass testClass = new TestClass(description.getTestClass());

                PermissionContextImpl permissionContext = null;

                if (mSkipTests || mFailTests) {
                    Log.d(
                            LOG_TAG,
                            "Skipping suite setup and teardown due to skipTests: "
                                    + mSkipTests
                                    + ", failTests: "
                                    + mFailTests);
                    base.evaluate();
                    return;
                }

                Log.d(LOG_TAG, "Preparing state for suite " + description.getClassName());

                Tags.clearTags();
                Tags.addTag(Tags.USES_DEVICESTATE);
                if (TestApis.packages().instrumented().isInstantApp()) {
                    Tags.addTag(Tags.INSTANT_APP);
                }

                boolean originalFlagSyncEnabled = TestApis.flags().getFlagSyncEnabled();

                try {
                    TestApis.device().keepScreenOn(true);
                    TestApis.flags().setFlagSyncEnabled(false);

                    if (!Tags.hasTag(Tags.INSTANT_APP)) {
                        TestApis.device().setKeyguardEnabled(false);
                    }
                    TestApis.users().setStopBgUsersOnSwitch(OptionalBoolean.FALSE);

                    try {
                        List<Annotation> annotations = new ArrayList<>(getAnnotations(description));
                        applyAnnotations(annotations, /* isTest= */ false);
                    } catch (AssumptionViolatedException e) {
                        Log.i(LOG_TAG, "Assumption failed during class setup", e);
                        mSkipTests = true;
                        mSkipTestsReason = e.getMessage();
                    } catch (AssertionError e) {
                        Log.i(LOG_TAG, "Assertion failed during class setup", e);
                        mFailTests = true;
                        mFailTestsReason = e.getMessage();
                    }

                    Log.d(
                            LOG_TAG,
                            "Finished preparing state for suite " + description.getClassName());

                    if (!mSkipTests && !mFailTests) {
                        // Tests may be skipped during the class setup
                        runAnnotatedMethods(testClass, BeforeClass.class);
                    }

                    base.evaluate();
                } finally {
                    runAnnotatedMethods(testClass, AfterClass.class);

                    if (permissionContext != null) {
                        permissionContext.close();
                    }

                    if (!mSkipClassTeardown) {
                        teardownShareableState();
                    }

                    if (!Tags.hasTag(Tags.INSTANT_APP)) {
                        TestApis.device().setKeyguardEnabled(true);
                    }
                    // TODO(b/249710985): Reset to the default for the device or the previous value
                    // TestApis.device().keepScreenOn(false);
                    TestApis.users().setStopBgUsersOnSwitch(OptionalBoolean.ANY);
                    TestApis.flags().setFlagSyncEnabled(originalFlagSyncEnabled);
                }
            }
        };
    }

    private static final Map<Class<? extends Annotation>, Class<? extends Annotation>>
            BANNED_ANNOTATIONS_TO_REPLACEMENTS = getBannedAnnotationsToReplacements();

    private static Map<
            Class<? extends Annotation>,
            Class<? extends Annotation>> getBannedAnnotationsToReplacements() {
        Map<
                Class<? extends Annotation>,
                Class<? extends Annotation>> bannedAnnotationsToReplacements = new HashMap<>();
        bannedAnnotationsToReplacements.put(org.junit.BeforeClass.class, BeforeClass.class);
        bannedAnnotationsToReplacements.put(org.junit.AfterClass.class, AfterClass.class);
        return bannedAnnotationsToReplacements;
    }

    private void checkValidAnnotations(Description classDescription) {
        for (Method method : classDescription.getTestClass().getMethods()) {
            for (Map.Entry<
                    Class<? extends Annotation>,
                    Class<? extends Annotation>> bannedAnnotation
                    : BANNED_ANNOTATIONS_TO_REPLACEMENTS.entrySet()) {
                if (method.isAnnotationPresent(bannedAnnotation.getKey())) {
                    throw new IllegalStateException("Do not use "
                            + bannedAnnotation.getKey().getCanonicalName()
                            + " when using DeviceState, replace with "
                            + bannedAnnotation.getValue().getCanonicalName());
                }
            }

            if (method.getAnnotation(BeforeClass.class) != null
                    || method.getAnnotation(AfterClass.class) != null) {
                checkPublicStaticVoidNoArgs(method);
            }
        }
    }

    private void checkPublicStaticVoidNoArgs(Method method) {
        if (method.getParameterTypes().length > 0) {
            throw new IllegalStateException(
                    "Method " + method.getName() + " should have no parameters");
        }
        if (method.getReturnType() != Void.TYPE) {
            throw new IllegalStateException("Method " + method.getName() + "() should be void");
        }
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalStateException(
                    "Method " + method.getName() + "() should be static");
        }
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalStateException(
                    "Method " + method.getName() + "() should be public");
        }
    }

    private void runAnnotatedMethods(
            TestClass testClass, Class<? extends Annotation> annotation) throws Throwable {
        List<FrameworkMethod> methods = new ArrayList<>(
                testClass.getAnnotatedMethods(annotation));
        Collections.reverse(methods);
        for (FrameworkMethod method : methods) {
            try {
                method.invokeExplosively(testClass.getJavaClass());
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private void requireRunOnAdditionalUser(OptionalBoolean switchedToUser) {
        requireRunOnUser(new String[]{SECONDARY_USER_TYPE_NAME}, switchedToUser);

        if (TestApis.users().isHeadlessSystemUserMode()) {
            if (TestApis.users().instrumented().equals(TestApis.users().initial())) {
                throw new AssumptionViolatedException(
                        "This test requires running on an additional secondary user");
            }
        }
    }

    private void requireRunOnUser(String[] userTypes, OptionalBoolean switchedToUser) {
        UserReference instrumentedUser = TestApis.users().instrumented();

        assumeTrue("This test only runs on users of type " + Arrays.toString(userTypes),
                Arrays.stream(userTypes).anyMatch(
                        i -> i.equals(instrumentedUser.type().name())));

        mUsers.put(instrumentedUser.type(), instrumentedUser);

        if (switchedToUser == OptionalBoolean.ANY) {
            if (!mAnnotationHasSwitchedUser) {
                switchedToUser = OptionalBoolean.TRUE;
            }
        }
        ensureSwitchedToUser(switchedToUser, instrumentedUser);
    }

    private void requireRunOnProfile(String userType,
            OptionalBoolean installInstrumentedAppInParent,
            boolean hasProfileOwner, boolean dpcIsPrimary, boolean useParentInstance,
            OptionalBoolean switchedToParentUser, Set<String> affiliationIds,
            TestAppQueryBuilder dpcQuery) {
        UserReference instrumentedUser = TestApis.users().instrumented();

        assumeTrue("This test only runs on users of type " + userType,
                instrumentedUser.type().name().equals(userType));

        if (!mProfiles.containsKey(instrumentedUser.type())) {
            mProfiles.put(instrumentedUser.type(), new HashMap<>());
        }

        mProfiles.get(instrumentedUser.type()).put(instrumentedUser.parent(),
                instrumentedUser);

        if (installInstrumentedAppInParent.equals(OptionalBoolean.TRUE)) {
            TestApis.packages().find(sContext.getPackageName()).installExisting(
                    instrumentedUser.parent());
        } else if (installInstrumentedAppInParent.equals(OptionalBoolean.FALSE)) {
            TestApis.packages().find(sContext.getPackageName()).uninstall(
                    instrumentedUser.parent());
        }

        if (hasProfileOwner) {
            ensureHasProfileOwner(
                    instrumentedUser, dpcIsPrimary, useParentInstance, affiliationIds, dpcQuery);
        } else {
            ensureHasNoProfileOwner(instrumentedUser);
        }

        ensureSwitchedToUser(switchedToParentUser, instrumentedUser.parent());
    }

    private void ensureSwitchedToUser(OptionalBoolean switchedtoUser, UserReference user) {
        if (switchedtoUser.equals(OptionalBoolean.TRUE)) {
            mAnnotationHasSwitchedUser = true;
            switchToUser(user);
        } else if (switchedtoUser.equals(OptionalBoolean.FALSE)) {
            mAnnotationHasSwitchedUser = true;
            switchFromUser(user);
        }
    }

    private void requireFeature(String feature, FailureMode failureMode) {
        checkFailOrSkip("Device must have feature " + feature,
                TestApis.packages().features().contains(feature), failureMode);
    }

    private void requireDoesNotHaveFeature(String feature, FailureMode failureMode) {
        checkFailOrSkip("Device must not have feature " + feature,
                !TestApis.packages().features().contains(feature), failureMode);
    }

    private void requireNoPermissionsInstrumentation(String reason) {
        boolean instrumentingPermissions =
                TestApis.context()
                        .instrumentedContext().getPackageName()
                        .equals(mPermissionsInstrumentationPackage);

        checkFailOrSkip(
                "This test never runs using permissions instrumentation on this version"
                        + " of Android: " + reason,
                !instrumentingPermissions,
                FailureMode.SKIP
        );
    }

    private void requirePermissionsInstrumentation(String reason) {
        mHasRequirePermissionInstrumentation = true;
        boolean instrumentingPermissions =
                TestApis.context()
                        .instrumentedContext().getPackageName()
                        .equals(mPermissionsInstrumentationPackage);

        checkFailOrSkip(
                "This test only runs when using permissions instrumentation on this"
                        + " version of Android: " + reason,
                instrumentingPermissions,
                FailureMode.SKIP
        );
    }

    private void requireTargetSdkVersion(
            int min, int max, FailureMode failureMode) {
        int targetSdkVersion = TestApis.packages().instrumented().targetSdkVersion();

        checkFailOrSkip(
                "TargetSdkVersion must be between " + min + " and " + max
                        + " (inclusive) (version is " + targetSdkVersion + ")",
                min <= targetSdkVersion && max >= targetSdkVersion,
                failureMode
        );
    }

    private void requireSdkVersion(int min, int max, FailureMode failureMode) {
        requireSdkVersion(min, max, failureMode,
                "Sdk version must be between " + min + " and " + max + " (inclusive)");
    }

    private void requireSdkVersion(
            int min, int max, FailureMode failureMode, String failureMessage) {
        mMinSdkVersionCurrentTest = min;
        checkFailOrSkip(
                failureMessage + " (version is " + SDK_INT + ")",
                meetsSdkVersionRequirements(min, max),
                failureMode
        );
    }

    private com.android.bedstead.nene.users.UserType requireUserSupported(
            String userType, FailureMode failureMode) {
        com.android.bedstead.nene.users.UserType resolvedUserType =
                TestApis.users().supportedType(userType);

        checkFailOrSkip(
                "Device must support user type " + userType
                        + " only supports: " + TestApis.users().supportedTypes(),
                resolvedUserType != null, failureMode);

        return resolvedUserType;
    }

    private static final String LOG_TAG = "DeviceState";

    private static final Context sContext = TestApis.context().instrumentedContext();

    private final Map<com.android.bedstead.nene.users.UserType, UserReference> mUsers =
            new HashMap<>();
    private final Map<com.android.bedstead.nene.users.UserType, Map<UserReference, UserReference>>
            mProfiles = new HashMap<>();
    private DevicePolicyController mDeviceOwner;
    private Map<UserReference, DevicePolicyController> mProfileOwners = new HashMap<>();
    private RemotePolicyManager mDelegateDpc;
    private RemotePolicyManager mPrimaryPolicyManager;
    private RemoteDevicePolicyManagerRoleHolder mDevicePolicyManagerRoleHolder;
    private UserType mOtherUserType;

    private PermissionContextImpl mPermissionContext = null;
    private Map<UserReference, Set<String>> mAddedUserRestrictions = new HashMap<>();
    private Map<UserReference, Set<String>> mRemovedUserRestrictions = new HashMap<>();
    private final List<UserReference> mCreatedUsers = new ArrayList<>();
    private final List<RemovedUser> mRemovedUsers = new ArrayList<>();
    private final List<UserReference> mUsersSetPasswords = new ArrayList<>();
    private final List<BlockingBroadcastReceiver> mRegisteredBroadcastReceivers = new ArrayList<>();
    private boolean mHasChangedDeviceOwner = false;
    private DevicePolicyController mOriginalDeviceOwner;
    private Integer mOriginalDeviceOwnerType;
    private boolean mHasChangedDeviceOwnerType;
    private Map<UserReference, DevicePolicyController> mChangedProfileOwners = new HashMap<>();
    private UserReference mOriginalSwitchedUser;
    private Boolean mOriginalBluetoothEnabled;
    private Boolean mOriginalWifiEnabled;
    private Map<String, Map<String, String>> mOriginalFlagValues = new HashMap<>();
    private TestAppProvider mTestAppProvider = new TestAppProvider();
    private Map<String, TestAppInstance> mTestApps = new HashMap<>();
    private final Map<String, String> mOriginalGlobalSettings = new HashMap<>();
    private boolean mAnnotationHasSwitchedUser = false;
    private final Set<AccountReference> mCreatedAccounts = new HashSet<>();
    private Map<String, AccountReference> mAccounts = new HashMap<>();
    private final Map<UserReference, RemoteAccountAuthenticator> mAccountAuthenticators =
            new HashMap<>();

    private static final class RemovedUser {
        // Store the user builder so we can recreate the user later
        public final UserBuilder userBuilder;
        public final boolean isRunning;
        public final boolean isOriginalSwitchedToUser;

        RemovedUser(UserBuilder userBuilder,
                boolean isRunning, boolean isOriginalSwitchedToUser) {
            this.userBuilder = userBuilder;
            this.isRunning = isRunning;
            this.isOriginalSwitchedToUser = isOriginalSwitchedToUser;
        }
    }

    /**
     * Get the {@link UserReference} of the work profile for the initial user.
     *
     * <p>If the current user is a work profile, then the current user will be returned.
     *
     * <p>This should only be used to get work profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed work profile
     */
    public UserReference workProfile() {
        return workProfile(/* forUser= */ UserType.INITIAL_USER);
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

    /**
     * Get the {@link UserReference} of the profile of the given type for the given user.
     *
     * <p>This should only be used to get profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed profile for the given user
     */
    public UserReference profile(String profileType, UserType forUser) {
        return profile(profileType, resolveUserTypeToUser(forUser));
    }

    /**
     * Get the {@link UserReference} of the profile for the current user.
     *
     * <p>If the current user is a profile of the correct type, then the current user will be
     * returned.
     *
     * <p>This should only be used to get profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed profile
     */
    public UserReference profile(String profileType) {
        return profile(profileType, /* forUser= */ UserType.INSTRUMENTED_USER);
    }

    /**
     * Get the {@link UserReference} of the profile of the given type for the given user.
     *
     * <p>This should only be used to get profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed profile for the given user
     */
    public UserReference profile(String profileType, UserReference forUser) {
        com.android.bedstead.nene.users.UserType resolvedUserType =
                TestApis.users().supportedType(profileType);

        if (resolvedUserType == null) {
            throw new IllegalStateException("Can not have a profile of type " + profileType
                    + " as they are not supported on this device");
        }

        return profile(resolvedUserType, forUser);
    }

    /**
     * Get the {@link UserReference} of the profile of the given type for the given user.
     *
     * <p>This should only be used to get profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed profile for the given user
     */
    public UserReference profile(
            com.android.bedstead.nene.users.UserType userType, UserReference forUser) {
        if (userType == null || forUser == null) {
            throw new NullPointerException();
        }

        if (!mProfiles.containsKey(userType) || !mProfiles.get(userType).containsKey(forUser)) {
            UserReference parentUser = TestApis.users().instrumented().parent();

            if (parentUser != null) {
                if (mProfiles.containsKey(userType)
                        && mProfiles.get(userType).containsKey(parentUser)) {
                    return mProfiles.get(userType).get(parentUser);
                }
            }

            throw new IllegalStateException(
                    "No harrier-managed profile of type " + userType
                            + ". This method should only"
                            + " be used when Harrier has been used to create the profile.");
        }

        return mProfiles.get(userType).get(forUser);
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
        return tvProfile(/* forUser= */ UserType.INSTRUMENTED_USER);
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
     * Get the {@link UserReference} of the clone profile for the current user
     *
     * <p>This should only be used to get clone profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed clone profile
     */
    public UserReference cloneProfile() {
        return cloneProfile(/* forUser= */ UserType.INITIAL_USER);
    }

    /**
     * Get the {@link UserReference} of the clone profile.
     *
     * <p>This should only be used to get clone profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed clone profile
     */
    public UserReference cloneProfile(UserType forUser) {
        return cloneProfile(resolveUserTypeToUser(forUser));
    }

    /**
     * Get the {@link UserReference} of the clone profile.
     *
     * <p>This should only be used to get clone profiles managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed clone profile
     */
    public UserReference cloneProfile(UserReference forUser) {
        return profile(CLONE_PROFILE_TYPE_NAME, forUser);
    }

    /**
     * Gets the user ID of the initial user.
     */
    // TODO(b/249047658): cache the initial user at the start of the run.
    public UserReference initialUser() {
        return TestApis.users().initial();
    }

    /**
     * Gets the user ID of the first human user on the device.
     */
    public UserReference primaryUser() {
        return TestApis.users().all()
                .stream().filter(UserReference::isPrimary).findFirst()
                .orElseThrow(IllegalStateException::new);
    }

    /**
     * Get a secondary user.
     *
     * <p>This should only be used to get secondary users managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed secondary user
     */
    public UserReference secondaryUser() {
        return user(SECONDARY_USER_TYPE_NAME);
    }

    /**
     * Gets the user marked as "other" by use of the {@code @OtherUser} annotation.
     *
     * @throws IllegalStateException if there is no "other" user
     */
    public UserReference otherUser() {
        if (mOtherUserType == null) {
            throw new IllegalStateException("No other user specified. Use @OtherUser");
        }

        return resolveUserTypeToUser(mOtherUserType);
    }

    /**
     * Get a user of the given type.
     *
     * <p>This should only be used to get users managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed user of the correct type
     */
    public UserReference user(String userType) {
        com.android.bedstead.nene.users.UserType resolvedUserType =
                TestApis.users().supportedType(userType);

        if (resolvedUserType == null) {
            throw new IllegalStateException("Can not have a user of type " + userType
                    + " as they are not supported on this device");
        }

        return user(resolvedUserType);
    }

    /**
     * Get a user of the given type.
     *
     * <p>This should only be used to get users managed by Harrier (using either the
     * annotations or calls to the {@link DeviceState} class.
     *
     * @throws IllegalStateException if there is no harrier-managed user of the correct type
     */
    public UserReference user(com.android.bedstead.nene.users.UserType userType) {
        if (userType == null) {
            throw new NullPointerException();
        }

        if (!mUsers.containsKey(userType)) {
            throw new IllegalStateException(
                    "No harrier-managed user of type " + userType
                            + ". This method should only be"
                            + " used when Harrier has been used to create the user.");
        }

        return mUsers.get(userType);
    }

    private UserReference ensureHasProfile(
            String profileType,
            OptionalBoolean installInstrumentedApp,
            UserType forUser,
            boolean hasProfileOwner,
            boolean profileOwnerIsPrimary,
            boolean useParentInstance,
            OptionalBoolean switchedToParentUser,
            OptionalBoolean isQuietModeEnabled,
            TestAppQueryBuilder dpcQuery) {
        com.android.bedstead.nene.users.UserType resolvedUserType =
                requireUserSupported(profileType, FailureMode.SKIP);

        UserReference forUserReference = resolveUserTypeToUser(forUser);

        UserReference profile =
                TestApis.users().findProfileOfType(resolvedUserType, forUserReference);
        if (profile == null) {
            if (profileType.equals(MANAGED_PROFILE_TYPE_NAME)) {
                // TODO(b/239961027): either remove this check (once tests on UserManagerTest /
                // MultipleUsersOnMultipleDisplaysTest uses non-work profiles) or add a unit test
                // for it on DeviceStateTest
                requireFeature(FEATURE_MANAGED_USERS, FailureMode.SKIP);

                // DO + work profile isn't a valid state
                ensureHasNoDeviceOwner();
            }

            profile = createProfile(resolvedUserType, forUserReference);
        }

        profile.start();

        if (isQuietModeEnabled == OptionalBoolean.TRUE) {
            profile.setQuietMode(true);
        } else if (isQuietModeEnabled == OptionalBoolean.FALSE) {
            profile.setQuietMode(false);
        }

        if (installInstrumentedApp.equals(OptionalBoolean.TRUE)) {
            TestApis.packages().find(sContext.getPackageName()).installExisting(
                    profile);
        } else if (installInstrumentedApp.equals(OptionalBoolean.FALSE)) {
            TestApis.packages().find(sContext.getPackageName()).uninstall(profile);
        }

        if (!mProfiles.containsKey(resolvedUserType)) {
            mProfiles.put(resolvedUserType, new HashMap<>());
        }

        mProfiles.get(resolvedUserType).put(forUserReference, profile);

        if (hasProfileOwner) {
            ensureHasProfileOwner(
                    profile, profileOwnerIsPrimary,
                    useParentInstance,
                    /* affiliationIds= */ null,
                    dpcQuery);
        }

        ensureSwitchedToUser(switchedToParentUser, forUserReference);

        return profile;
    }

    private void ensureHasNoProfile(String profileType, UserType forUser) {
        UserReference forUserReference = resolveUserTypeToUser(forUser);
        com.android.bedstead.nene.users.UserType resolvedProfileType =
                TestApis.users().supportedType(profileType);

        if (resolvedProfileType == null) {
            // These profile types don't exist so there can't be any
            return;
        }

        UserReference profile =
                TestApis.users().findProfileOfType(
                        resolvedProfileType,
                        forUserReference);
        if (profile != null) {
            // We can't remove an organization owned profile
            ProfileOwner profileOwner = TestApis.devicePolicy().getProfileOwner(profile);
            if (profileOwner != null && profileOwner.isOrganizationOwned()) {
                profileOwner.setIsOrganizationOwned(false);
            }

            removeAndRecordUser(profile);
        }
    }

    private void ensureHasUser(
            String userType, OptionalBoolean installInstrumentedApp,
            OptionalBoolean switchedToUser) {
        com.android.bedstead.nene.users.UserType resolvedUserType =
                requireUserSupported(userType, FailureMode.SKIP);

        Collection<UserReference> users = TestApis.users().findUsersOfType(resolvedUserType);

        UserReference user = users.isEmpty() ? createUser(resolvedUserType)
                : users.iterator().next();

        user.start();

        if (installInstrumentedApp.equals(OptionalBoolean.TRUE)) {
            TestApis.packages().find(sContext.getPackageName()).installExisting(user);
        } else if (installInstrumentedApp.equals(OptionalBoolean.FALSE)) {
            TestApis.packages().find(sContext.getPackageName()).uninstall(user);
        }

        ensureSwitchedToUser(switchedToUser, user);

        mUsers.put(resolvedUserType, user);
    }

    private void ensureHasNoAdditionalUser() {
        if (!isHeadlessSystemUserMode()) {
            if (TestApis.users()
                    .instrumented()
                    .type()
                    .equals(TestApis.users().supportedType(SECONDARY_USER_TYPE_NAME))) {
                throw new AssumptionViolatedException(
                        "Tests with @EnsureHasNoAdditionalUser cannot run on a secondary user on"
                                + " a non headless system user device.");
            }
        }

        UserReference additionalUser = additionalUserOrNull();
        while (additionalUser != null) {
            additionalUser.remove();

            additionalUser = additionalUserOrNull();
        }
    }

    private void ensureHasAdditionalUser(
            OptionalBoolean installInstrumentedApp, OptionalBoolean switchedToUser) {
        if (TestApis.users().isHeadlessSystemUserMode()) {
            com.android.bedstead.nene.users.UserType resolvedUserType =
                    requireUserSupported(SECONDARY_USER_TYPE_NAME, FailureMode.SKIP);

            Collection<UserReference> users = TestApis.users().findUsersOfType(resolvedUserType);
            if (users.size() < 2) {
                createUser(resolvedUserType);
            }

            UserReference user = additionalUser();

            if (installInstrumentedApp.equals(OptionalBoolean.TRUE)) {
                TestApis.packages().find(sContext.getPackageName()).installExisting(user);
            } else if (installInstrumentedApp.equals(OptionalBoolean.FALSE)) {
                TestApis.packages().find(sContext.getPackageName()).uninstall(user);
            }

            ensureSwitchedToUser(switchedToUser, user);
        } else {
            ensureHasUser(SECONDARY_USER_TYPE_NAME, installInstrumentedApp, switchedToUser);
        }
    }

    /**
     * Ensure that there is no user of the given type.
     */
    private void ensureHasNoUser(String userType) {
        com.android.bedstead.nene.users.UserType resolvedUserType =
                TestApis.users().supportedType(userType);

        if (resolvedUserType == null) {
            // These user types don't exist so there can't be any
            return;
        }

        for (UserReference secondaryUser : TestApis.users().findUsersOfType(resolvedUserType)) {
            if (secondaryUser.equals(TestApis.users().instrumented())) {
                throw new AssumptionViolatedException(
                        "This test only runs on devices without a "
                                + userType + " user. But the instrumented user is " + userType);
            }
            removeAndRecordUser(secondaryUser);
        }
    }

    private void removeAndRecordUser(UserReference userReference) {
        if (userReference == null) {
            return; // Nothing to remove
        }

        switchFromUser(userReference);

        if (!mCreatedUsers.remove(userReference)) {
            mRemovedUsers.add(
                    new RemovedUser(
                    TestApis.users().createUser()
                    .name(userReference.name())
                    .type(userReference.type())
                    .parent(userReference.parent()),
                            userReference.isRunning(),
                            Objects.equal(mOriginalSwitchedUser, userReference)));
        }

        userReference.remove();
    }

    private void ensureCanAddUser() {
        ensureCanAddUser(1, FailureMode.SKIP);
    }

    private void ensureCanAddUser(int number, FailureMode failureMode) {
        int maxUsers = getMaxNumberOfUsersSupported();
        int currentUsers = TestApis.users().all().size();

        // TODO(scottjonathan): Try to remove users until we have space - this will have to take
        // into account other users which have been added during the setup of this test.

        checkFailOrSkip(
                "The device does not have space for "
                        + number
                        + " additional "
                        + "user(s) ("
                        + currentUsers
                        + " current users, "
                        + maxUsers
                        + " max users)",
                currentUsers + number <= maxUsers,
                failureMode);
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
    public BlockingBroadcastReceiver registerBroadcastReceiver(IntentFilter intentFilter) {
        return registerBroadcastReceiver(intentFilter, /* checker= */ null);
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

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiver(
            IntentFilter intentfilter, Function<Intent, Boolean> checker) {
        BlockingBroadcastReceiver broadcastReceiver =
                new BlockingBroadcastReceiver(mContext, intentfilter, checker);
        broadcastReceiver.register();
        mRegisteredBroadcastReceivers.add(broadcastReceiver);

        return broadcastReceiver;
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiverForUser(
            UserReference user, String action) {
        return registerBroadcastReceiverForUser(user, action, /* checker= */ null);
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiverForUser(
            UserReference user, IntentFilter intentFilter) {
        return registerBroadcastReceiverForUser(user, intentFilter, /* checker= */ null);
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiverForUser(
            UserReference user, String action, Function<Intent, Boolean> checker) {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            BlockingBroadcastReceiver broadcastReceiver =
                    new BlockingBroadcastReceiver(
                            TestApis.context().androidContextAsUser(user), action, checker);
            broadcastReceiver.register();
            mRegisteredBroadcastReceivers.add(broadcastReceiver);

            return broadcastReceiver;
        }
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiverForUser(
            UserReference user, IntentFilter intentFilter, Function<Intent, Boolean> checker) {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            BlockingBroadcastReceiver broadcastReceiver =
                    new BlockingBroadcastReceiver(
                            TestApis.context().androidContextAsUser(user), intentFilter, checker);
            broadcastReceiver.register();
            mRegisteredBroadcastReceivers.add(broadcastReceiver);

            return broadcastReceiver;
        }
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiverForAllUsers(String action) {
        return registerBroadcastReceiverForAllUsers(action, /* checker= */ null);
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiverForAllUsers(IntentFilter intentFilter) {
        return registerBroadcastReceiverForAllUsers(intentFilter, /* checker= */ null);
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiverForAllUsers(
            String action, Function<Intent, Boolean> checker) {
            try (PermissionContext p =
                         TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
                BlockingBroadcastReceiver broadcastReceiver =
                        new BlockingBroadcastReceiver(mContext, action, checker);
                broadcastReceiver.registerForAllUsers();

                mRegisteredBroadcastReceivers.add(broadcastReceiver);

                return broadcastReceiver;
            }
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiverForAllUsers(
            IntentFilter intentFilter, Function<Intent, Boolean> checker) {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            BlockingBroadcastReceiver broadcastReceiver =
                    new BlockingBroadcastReceiver(mContext, intentFilter, checker);
            broadcastReceiver.registerForAllUsers();

            mRegisteredBroadcastReceivers.add(broadcastReceiver);

            return broadcastReceiver;
        }
    }

    private UserReference resolveUserTypeToUser(UserType userType) {
        switch (userType) {
            case SYSTEM_USER:
                return TestApis.users().system();
            case INSTRUMENTED_USER:
                return TestApis.users().instrumented();
            case CURRENT_USER:
                return TestApis.users().current();
            case PRIMARY_USER:
                return primaryUser();
            case SECONDARY_USER:
                return secondaryUser();
            case WORK_PROFILE:
                return workProfile();
            case TV_PROFILE:
                return tvProfile();
            case DPC_USER:
                return dpc().user();
            case INITIAL_USER:
                return TestApis.users().initial();
            case ADDITIONAL_USER:
                return additionalUser();
            case CLONE_PROFILE:
                return cloneProfile();
            case ANY:
                throw new IllegalStateException("ANY UserType can not be used here");
            default:
                throw new IllegalArgumentException("Unknown user type " + userType);
        }
    }

    public UserReference additionalUser() {
        UserReference additionalUser = additionalUserOrNull();

        if (additionalUser == null) {
            throw new IllegalStateException(
                    "No additional user found. Ensure the correct annotations "
                            + "have been used to declare use of additional user.");
        }

        return additionalUser;
    }

    private UserReference additionalUserOrNull() {
        // TODO: Cache additional user at start of test
        return TestApis.users()
                .findUsersOfType(TestApis.users().supportedType(SECONDARY_USER_TYPE_NAME))
                .stream()
                .sorted(Comparator.comparing(UserReference::id))
                .skip(TestApis.users().isHeadlessSystemUserMode() ? 1 : 0)
                .findFirst()
                .orElse(null);
    }

    private void teardownNonShareableState() {
        mProfiles.clear();
        mUsers.clear();
        mAnnotationHasSwitchedUser = false;

        for (BlockingBroadcastReceiver broadcastReceiver : mRegisteredBroadcastReceivers) {
            broadcastReceiver.unregisterQuietly();
        }
        mRegisteredBroadcastReceivers.clear();
        mDelegateDpc = null;
        mPrimaryPolicyManager = null;
        mOtherUserType = null;
        mTestApps.clear();
        mAccounts.clear();
        mAccountAuthenticators.clear();

        mTestAppProvider.restore();
        if (mPermissionContext != null) {
            mPermissionContext.close();
            mPermissionContext = null;
        }

        for (Map.Entry<String, Map<String, String>> namespace : mOriginalFlagValues.entrySet()) {
            for (Map.Entry<String, String> key : namespace.getValue().entrySet()) {
                TestApis.flags().set(namespace.getKey(), key.getKey(), key.getValue());
            }
        }
        mOriginalFlagValues.clear();

        mAnnotationExecutors.values().forEach(AnnotationExecutor::teardownNonShareableState);
    }

    private Set<TestAppInstance> mInstalledTestApps = new HashSet<>();
    private Set<TestAppInstance> mUninstalledTestApps = new HashSet<>();

    private void teardownShareableState() {
        mCreatedAccounts.forEach(AccountReference::remove);

        for (Map.Entry<UserReference, Set<String>> userRestrictions
                : mAddedUserRestrictions.entrySet()) {
            for (String restriction : userRestrictions.getValue()) {
                ensureDoesNotHaveUserRestriction(restriction, userRestrictions.getKey());
            }
        }

        for (Map.Entry<UserReference, Set<String>> userRestrictions
                : mRemovedUserRestrictions.entrySet()) {
            for (String restriction : userRestrictions.getValue()) {
                ensureHasUserRestriction(restriction, userRestrictions.getKey());
            }
        }

        mAddedUserRestrictions.clear();
        mRemovedUserRestrictions.clear();

        if (mHasChangedDeviceOwner) {
            if (mOriginalDeviceOwner == null) {
                if (mDeviceOwner != null) {
                    mDeviceOwner.remove();
                }
            } else if (!mOriginalDeviceOwner.equals(mDeviceOwner)) {
                if (mDeviceOwner != null) {
                    mDeviceOwner.remove();
                }

                ensureHasNoProfileOwner(TestApis.users().system());
                TestApis.devicePolicy().setDeviceOwner(
                        mOriginalDeviceOwner.componentName());
            }

            if (mOriginalDeviceOwner != null && mOriginalDeviceOwnerType != null) {
                ((DeviceOwner) mOriginalDeviceOwner).setType(mOriginalDeviceOwnerType);
            }

            mHasChangedDeviceOwner = false;
            mOriginalDeviceOwner = null;

            mHasChangedDeviceOwnerType = false;
            mOriginalDeviceOwnerType = null;
        } else {
            // Device owner type changed but the device owner is the same.
            if (mHasChangedDeviceOwnerType) {
                ((DeviceOwner) mDeviceOwner).setType(mOriginalDeviceOwnerType);

                mHasChangedDeviceOwnerType = false;
                mOriginalDeviceOwnerType = null;
            }
        }

        for (Map.Entry<UserReference, DevicePolicyController> originalProfileOwner :
                mChangedProfileOwners.entrySet()) {

            ProfileOwner currentProfileOwner =
                    TestApis.devicePolicy().getProfileOwner(originalProfileOwner.getKey());
            if (Objects.equal(currentProfileOwner, originalProfileOwner.getValue())) {
                continue; // No need to restore
            }

            if (currentProfileOwner != null) {
                currentProfileOwner.remove();
            }

            if (originalProfileOwner.getValue() != null) {
                TestApis.devicePolicy().setProfileOwner(originalProfileOwner.getKey(),
                        originalProfileOwner.getValue().componentName());
            }
        }
        mChangedProfileOwners.clear();
        if (mDevicePolicyManagerRoleHolder != null) {
            TestApis.devicePolicy().unsetDevicePolicyManagementRoleHolder(
                    mDevicePolicyManagerRoleHolder.testApp().pkg(),
                    mDevicePolicyManagerRoleHolder.user());
            mDevicePolicyManagerRoleHolder = null;
        }

        for (UserReference user : mUsersSetPasswords) {
            if (mCreatedUsers.contains(user)) {
                continue; // Will be removed anyway
            }
            user.clearPassword();
        }

        mUsersSetPasswords.clear();

        UserReference ephemeralUser = null;
        UserReference currentUser = TestApis.users().current();
        for (UserReference user : mCreatedUsers) {
            try {
                if (user.equals(currentUser)) {
                    // user will be removed after switching to mOriginalSwitchedUser below.
                    user.removeWhenPossible();
                    ephemeralUser = user;
                } else {
                    user.remove();
                }
            } catch (NeneException e) {
                if (user.exists()) {
                    // Otherwise it's probably just already removed
                    throw new NeneException("Could not remove user", e);
                }
            }

        }

        mCreatedUsers.clear();

        for (RemovedUser removedUser : mRemovedUsers) {
            UserReference user = removedUser.userBuilder.create();
            if (removedUser.isRunning) {
                user.start();
            }

            if (removedUser.isOriginalSwitchedToUser) {
                mOriginalSwitchedUser = user;
            }
        }

        mRemovedUsers.clear();
        if (mOriginalSwitchedUser != null) {
            if (!mOriginalSwitchedUser.exists()) {
                Log.d(LOG_TAG, "Could not switch back to original user "
                        + mOriginalSwitchedUser
                        + " as it does not exist. Switching to initial instead.");
                TestApis.users().initial().switchTo();
            } else {
                mOriginalSwitchedUser.switchTo();
            }
            mOriginalSwitchedUser = null;

            // wait for ephemeral user to be removed after being switched away
            if (ephemeralUser != null) {
                Poll.forValue("Ephemeral user exists", ephemeralUser::exists)
                        .toBeEqualTo(false)
                        .timeout(Duration.ofMinutes(1))
                        .errorOnFail()
                        .await();
            }
        }

        for (TestAppInstance installedTestApp : mInstalledTestApps) {
            installedTestApp.uninstall();
        }
        mInstalledTestApps.clear();

        for (TestAppInstance uninstalledTestApp : mUninstalledTestApps) {
            uninstalledTestApp.testApp().install(uninstalledTestApp.user());
        }
        mUninstalledTestApps.clear();

        if (mOriginalBluetoothEnabled != null) {
            TestApis.bluetooth().setEnabled(mOriginalBluetoothEnabled);
            mOriginalBluetoothEnabled = null;
        }

        if (mOriginalWifiEnabled != null) {
            TestApis.wifi().setEnabled(mOriginalWifiEnabled);
            mOriginalWifiEnabled = null;
        }

        for (Map.Entry<String, String> s : mOriginalGlobalSettings.entrySet()) {
            TestApis.settings().global().putString(s.getKey(), s.getValue());
        }
        mOriginalGlobalSettings.clear();

        TestApis.activities().clearAllActivities();
        mAnnotationExecutors.values().forEach(AnnotationExecutor::teardownShareableState);
    }

    private UserReference createProfile(
            com.android.bedstead.nene.users.UserType profileType, UserReference parent) {
        ensureCanAddUser();
        try {
            UserReference user = TestApis.users().createUser()
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
        ensureCanAddUser();
        try {
            UserReference user = TestApis.users().createUser()
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
                            (output) -> Integer.parseInt(output.split(": ", 2)[1]
                                    .trim()));
        } catch (AdbException e) {
            throw new IllegalStateException("Invalid command output", e);
        }
    }

    private void ensureHasDevicePolicyManagerRoleHolder(UserType onUser, boolean isPrimary) {
        UserReference user = resolveUserTypeToUser(onUser);

        if (!user.equals(TestApis.users().instrumented())) {
            // INTERACT_ACROSS_USERS_FULL is required for RemoteDPC
            ensureCanGetPermission(INTERACT_ACROSS_USERS_FULL);
        }

        ensureTestAppInstalled(RemoteDevicePolicyManagerRoleHolder.sTestApp, user);
        TestApis.devicePolicy().setDevicePolicyManagementRoleHolder(
                RemoteDevicePolicyManagerRoleHolder.sTestApp.pkg(), user);

        mDevicePolicyManagerRoleHolder =
                new RemoteDevicePolicyManagerRoleHolder(
                        RemoteDevicePolicyManagerRoleHolder.sTestApp, user);

        if (isPrimary) {
            // We will override the existing primary
            if (mPrimaryPolicyManager != null) {
                Log.i(LOG_TAG, "Overriding primary policy manager "
                        + mPrimaryPolicyManager + " with " + mDevicePolicyManagerRoleHolder);
            }
            mPrimaryPolicyManager = mDevicePolicyManagerRoleHolder;
        }
    }

    private void ensureHasDelegate(
            EnsureHasDelegate.AdminType adminType, List<String> scopes, boolean isPrimary) {
        RemotePolicyManager dpc = getDeviceAdmin(adminType);

        boolean specifiesAdminType = adminType != EnsureHasDelegate.AdminType.PRIMARY;
        boolean currentPrimaryPolicyManagerIsNotDelegator =
                !Objects.equal(mPrimaryPolicyManager, dpc);

        if (isPrimary && mPrimaryPolicyManager != null
                && (specifiesAdminType || currentPrimaryPolicyManagerIsNotDelegator)) {
            throw new IllegalStateException(
                    "Only one DPC can be marked as primary per test (current primary is "
                            + mPrimaryPolicyManager + ")");
        }

        if (!dpc.user().equals(TestApis.users().instrumented())) {
            // INTERACT_ACROSS_USERS_FULL is required for RemoteDPC
            ensureCanGetPermission(INTERACT_ACROSS_USERS_FULL);
        }

        ensureTestAppInstalled(RemoteDelegate.sTestApp, dpc.user());
        RemoteDelegate delegate = new RemoteDelegate(RemoteDelegate.sTestApp, dpc().user());
        dpc.devicePolicyManager().setDelegatedScopes(
                dpc.componentName(), delegate.packageName(), scopes);

        if (isPrimary) {
            mDelegateDpc = dpc;
            mPrimaryPolicyManager = delegate;
        }
    }

    private void ensureHasNoDelegate(EnsureHasNoDelegate.AdminType adminType) {
        if (adminType == EnsureHasNoDelegate.AdminType.ANY) {
            for (UserReference user : TestApis.users().all()) {
                ensureTestAppNotInstalled(RemoteDelegate.sTestApp, user);
            }
            return;
        }
        RemotePolicyManager dpc =
                adminType == EnsureHasNoDelegate.AdminType.PRIMARY ? mPrimaryPolicyManager
                        : adminType == EnsureHasNoDelegate.AdminType.DEVICE_OWNER
                                ? deviceOwner()
                                : adminType == EnsureHasNoDelegate.AdminType.PROFILE_OWNER
                                        ? profileOwner() : null;
        if (dpc == null) {
            throw new IllegalStateException("Unknown Admin Type " + adminType);
        }

        ensureTestAppNotInstalled(RemoteDelegate.sTestApp, dpc.user());
    }

    private void ensureTestAppInstalled(
            String key, String packageName, UserType onUser, boolean isPrimary) {
        TestApp testApp;
        if (packageName.isEmpty()) {
            testApp = mTestAppProvider.any();
        } else {
            testApp = mTestAppProvider.query()
                    .wherePackageName().isEqualTo(packageName)
                    .get();
        }

        TestAppInstance testAppInstance = ensureTestAppInstalled(
                testApp, resolveUserTypeToUser(onUser));

        mTestApps.put(key, testAppInstance);

        if (isPrimary) {
            if (mPrimaryPolicyManager != null) {
                throw new IllegalStateException(
                        "Only one DPC can be marked as primary per test (current primary is "
                                + mPrimaryPolicyManager + ")");
            }

            mPrimaryPolicyManager = new RemoteTestApp(testAppInstance);
        }
    }

    private void ensureTestAppHasPermission(
            String testAppKey, String[] permissions, int minVersion, int maxVersion,
            FailureMode failureMode) {
        checkTestAppExistsWithKey(testAppKey);

        try {
            mTestApps.get(testAppKey).permissions()
                    .withPermissionOnVersionBetween(minVersion, maxVersion, permissions);
        } catch (NeneException e) {
            if (failureMode.equals(FailureMode.SKIP) && e.getMessage().contains("Cannot grant")) {
                failOrSkip(e.getMessage(), FailureMode.SKIP);
            } else {
                throw e;
            }
        }
    }

    private void ensureTestAppHasAppOp(
            String testAppKey, String[] appOps, int minVersion, int maxVersion) {
        checkTestAppExistsWithKey(testAppKey);

        mTestApps.get(testAppKey).permissions()
                .withAppOpOnVersionBetween(minVersion, maxVersion, appOps);
    }

    private void checkTestAppExistsWithKey(String testAppKey) {
        if (!mTestApps.containsKey(testAppKey)) {
            throw new NeneException(
                    "No testapp with key " + testAppKey + ". Use @EnsureTestAppInstalled."
                            + "Valid Test apps: " + mTestApps);
        }
    }

    private RemotePolicyManager getDeviceAdmin(EnsureHasDelegate.AdminType adminType) {
        switch (adminType) {
            case DEVICE_OWNER:
                return deviceOwner();
            case PROFILE_OWNER:
                return profileOwner();
            case PRIMARY:
                return dpc();
            default:
                throw new IllegalStateException("Unknown device admin type " + adminType);
        }
    }

    private TestAppInstance ensureTestAppInstalled(TestApp testApp, UserReference user) {
        Package pkg = TestApis.packages().find(testApp.packageName());
        if (pkg != null && TestApis.packages().find(testApp.packageName()).installedOnUser(
                user)) {
            return testApp.instance(user);
        }

        TestAppInstance testAppInstance = testApp.install(user);
        mInstalledTestApps.add(testAppInstance);
        return testAppInstance;
    }

    private void ensureTestAppNotInstalled(TestApp testApp, UserReference user) {
        Package pkg = TestApis.packages().find(testApp.packageName());
        if (pkg == null || !TestApis.packages().find(testApp.packageName()).installedOnUser(
                user)) {
            return;
        }

        TestAppInstance instance = testApp.instance(user);

        if (mInstalledTestApps.contains(instance)) {
            mInstalledTestApps.remove(instance);
        } else {
            mUninstalledTestApps.add(instance);
        }

        testApp.uninstall(user);
    }

    private void ensureHasDeviceOwner(FailureMode failureMode, boolean isPrimary,
            Set<String> affiliationIds, int type, TestAppQueryBuilder dpcQuery) {
        // TODO(scottjonathan): Should support non-remotedpc device owner (default to remotedpc)

        UserReference userReference = TestApis.users().system();

        if (isPrimary && mPrimaryPolicyManager != null && !userReference.equals(
                mPrimaryPolicyManager.user())) {
            throw new IllegalStateException(
                    "Only one DPC can be marked as primary per test (current primary is "
                            + mPrimaryPolicyManager + ")");
        }
        if (!userReference.equals(TestApis.users().instrumented())) {
            // INTERACT_ACROSS_USERS_FULL is required for RemoteDPC
            ensureCanGetPermission(INTERACT_ACROSS_USERS_FULL);
        }

        DeviceOwner currentDeviceOwner = TestApis.devicePolicy().getDeviceOwner();

        // if current device owner matches query, keep it as it is
        if (RemoteDpc.matchesRemoteDpcQuery(currentDeviceOwner, dpcQuery)) {
            mDeviceOwner = currentDeviceOwner;
        } else {
            // if there is no device owner, or current device owner is not a remote dpc
            UserReference instrumentedUser = TestApis.users().instrumented();

            if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)) {
                // Prior to S we can't set device owner if there are other users on the device

                if (instrumentedUser.id() != 0) {
                    // If we're not on the system user we can't reach the required state
                    throw new AssumptionViolatedException(
                            "Can't set Device Owner when running on non-system-user"
                                    + " on this version of Android");
                }

                for (UserReference u : TestApis.users().all()) {
                    if (u.equals(instrumentedUser)) {
                        // Can't remove the user we're running on
                        continue;
                    }
                    try {
                        removeAndRecordUser(u);
                    } catch (NeneException e) {
                        failOrSkip(
                                "Error removing user to prepare for DeviceOwner: "
                                        + e.toString(),
                                failureMode);
                    }
                }
            }

            // We must remove all non-test users on all devices though
            // (except for the first 1 if headless and always the system user)
            int allowedNonTestUsers = TestApis.users().isHeadlessSystemUserMode() ? 1 : 0;

            for (UserReference u : TestApis.users().all()) {
                if (u.isSystem()) {
                    continue;
                }
                if (u.isForTesting()) {
                    continue;
                }
                if (allowedNonTestUsers > 0) {
                    allowedNonTestUsers--;
                    continue;
                }
                try {
                    removeAndRecordUser(u);
                } catch (NeneException e) {
                    failOrSkip(
                            "Error removing user to prepare for DeviceOwner: "
                                    + e.toString(),
                            failureMode);
                }
            }


            if (Versions.meetsMinimumSdkVersionRequirement(Versions.U)) {
                ensureHasNoAccounts(UserType.ANY);
            } else {
                // Prior to U this only checked the system user
                ensureHasNoAccounts(UserType.SYSTEM_USER);
            }
            ensureHasNoProfileOwner(userReference);

            if (!mHasChangedDeviceOwner) {
                recordDeviceOwner();
                mHasChangedDeviceOwner = true;
                mHasChangedDeviceOwnerType = true;
            }

            mDeviceOwner = RemoteDpc.setAsDeviceOwner(dpcQuery).devicePolicyController();
        }

        if (isPrimary) {
            mPrimaryPolicyManager = RemoteDpc.forDevicePolicyController(mDeviceOwner);
        }

        int deviceOwnerType = ((DeviceOwner) mDeviceOwner).getType();
        if (deviceOwnerType != type) {
            if (!mHasChangedDeviceOwnerType) {
                mOriginalDeviceOwnerType = deviceOwnerType;
                mHasChangedDeviceOwnerType = true;
            }

            ((DeviceOwner) mDeviceOwner).setType(type);
        }

        if (type != DeviceOwnerType.FINANCED) {
            // API is not allowed to be called by a financed device owner.
            RemoteDpc remoteDpcForDeviceOwner = RemoteDpc.forDevicePolicyController(mDeviceOwner);
            remoteDpcForDeviceOwner.devicePolicyManager()
                    .setAffiliationIds(
                            remoteDpcForDeviceOwner.componentName(),
                            affiliationIds);
        }
    }

    private void recordDeviceOwner() {
        mOriginalDeviceOwner = TestApis.devicePolicy().getDeviceOwner();
        mOriginalDeviceOwnerType =
                mOriginalDeviceOwner != null ? ((DeviceOwner) mOriginalDeviceOwner).getType()
                        : null;
    }

    private void ensureHasProfileOwner(UserType onUser, boolean isPrimary,
            boolean useParentInstance, Set<String> affiliationIds, TestAppQueryBuilder dpcQuery) {
        // TODO(scottjonathan): Should support non-remotedpc profile owner
        //  (default to remotedpc)
        UserReference user = resolveUserTypeToUser(onUser);
        ensureHasProfileOwner(user, isPrimary, useParentInstance, affiliationIds, dpcQuery);
    }

    private void ensureHasProfileOwner(
            UserReference user, boolean isPrimary, boolean useParentInstance,
            Set<String> affiliationIds, TestAppQueryBuilder dpcQuery) {
        if (isPrimary && mPrimaryPolicyManager != null
                && !user.equals(mPrimaryPolicyManager.user())) {
            throw new IllegalStateException(
                    "Only one DPC can be marked as primary per test");
        }

        if (!user.equals(TestApis.users().instrumented())) {
            // INTERACT_ACROSS_USERS_FULL is required for RemoteDPC
            ensureCanGetPermission(INTERACT_ACROSS_USERS_FULL);
        }

        ProfileOwner currentProfileOwner = TestApis.devicePolicy().getProfileOwner(user);
        DeviceOwner currentDeviceOwner = TestApis.devicePolicy().getDeviceOwner();

        if (currentDeviceOwner != null && currentDeviceOwner.user().equals(user)) {
            // Can't have DO and PO on the same user
            ensureHasNoDeviceOwner();
        }

        if (Versions.meetsMinimumSdkVersionRequirement(Versions.U)) {
            ensureHasNoAccounts(user);
        } else {
            // Prior to U this incorrectly checked the system user
            ensureHasNoAccounts(UserType.SYSTEM_USER);
        }

        if (RemoteDpc.matchesRemoteDpcQuery(currentProfileOwner, dpcQuery)) {
            mProfileOwners.put(user, currentProfileOwner);
        } else {
            if (!mChangedProfileOwners.containsKey(user)) {
                mChangedProfileOwners.put(user, currentProfileOwner);
            }

            mProfileOwners.put(user,
                    RemoteDpc.setAsProfileOwner(user, dpcQuery).devicePolicyController());
        }

        if (isPrimary) {
            if (useParentInstance) {
                mPrimaryPolicyManager = new RemoteDpcUsingParentInstance(
                        RemoteDpc.forDevicePolicyController(mProfileOwners.get(user)));
            } else {
                mPrimaryPolicyManager =
                        RemoteDpc.forDevicePolicyController(mProfileOwners.get(user));
            }
        }

        if (affiliationIds != null) {
            RemoteDpc profileOwner = profileOwner(user);
            profileOwner.devicePolicyManager()
                    .setAffiliationIds(profileOwner.componentName(), affiliationIds);
        }
    }

    private void ensureHasNoDeviceOwner() {
        DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();

        if (deviceOwner == null) {
            return;
        }

        if (!mHasChangedDeviceOwner) {
            recordDeviceOwner();
            mHasChangedDeviceOwner = true;
            mHasChangedDeviceOwnerType = true;
        }

        mDeviceOwner = null;
        deviceOwner.remove();
    }

    private void ensureHasNoProfileOwner(UserType onUser) {
        UserReference user = resolveUserTypeToUser(onUser);

        ensureHasNoProfileOwner(user);
    }

    private void ensureHasNoProfileOwner(UserReference user) {
        ProfileOwner currentProfileOwner = TestApis.devicePolicy().getProfileOwner(user);

        if (currentProfileOwner == null) {
            return;
        }

        if (!mChangedProfileOwners.containsKey(user)) {
            mChangedProfileOwners.put(user, currentProfileOwner);
        }

        TestApis.devicePolicy().getProfileOwner(user).remove();
        mProfileOwners.remove(user);
    }

    /**
     * Get the {@link RemoteDpc} for the device owner controlled by Harrier.
     *
     * <p>If no Harrier-managed device owner exists, an exception will be thrown.
     *
     * <p>If the device owner is not a RemoteDPC then an exception will be thrown
     */
    public RemoteDpc deviceOwner() {
        if (mDeviceOwner == null) {
            throw new IllegalStateException(
                    "No Harrier-managed device owner. This method should "
                            + "only be used when Harrier was used to set the Device Owner.");
        }
        if (!RemoteDpc.isRemoteDpc(mDeviceOwner)) {
            throw new IllegalStateException("The device owner is not a RemoteDPC."
                    + " You must use Nene to query for this device owner.");
        }

        return RemoteDpc.forDevicePolicyController(mDeviceOwner);
    }

    /**
     * Get the {@link RemoteDpc} for the profile owner on the current user controlled by Harrier.
     *
     * <p>If no Harrier-managed profile owner exists, an exception will be thrown.
     *
     * <p>If the profile owner is not a RemoteDPC then an exception will be thrown.
     */
    public RemoteDpc profileOwner() {
        return profileOwner(UserType.INSTRUMENTED_USER);
    }

    /**
     * Get the {@link RemoteDpc} for the profile owner on the given user controlled by Harrier.
     *
     * <p>If no Harrier-managed profile owner exists, an exception will be thrown.
     *
     * <p>If the profile owner is not a RemoteDPC then an exception will be thrown.
     */
    public RemoteDpc profileOwner(UserType onUser) {
        if (onUser == null) {
            throw new NullPointerException();
        }

        return profileOwner(resolveUserTypeToUser(onUser));
    }

    /**
     * Get the {@link RemoteDpc} for the profile owner on the given user controlled by Harrier.
     *
     * <p>If no Harrier-managed profile owner exists, an exception will be thrown.
     *
     * <p>If the profile owner is not a RemoteDPC then an exception will be thrown.
     */
    public RemoteDpc profileOwner(UserReference onUser) {
        if (onUser == null) {
            throw new NullPointerException();
        }

        if (!mProfileOwners.containsKey(onUser)) {
            throw new IllegalStateException(
                    "No Harrier-managed profile owner. This method should "
                            + "only be used when Harrier was used to set the Profile Owner.");
        }

        DevicePolicyController profileOwner = mProfileOwners.get(onUser);

        if (!RemoteDpc.isRemoteDpc(profileOwner)) {
            throw new IllegalStateException("The profile owner is not a RemoteDPC."
                    + " You must use Nene to query for this profile owner.");
        }

        return RemoteDpc.forDevicePolicyController(profileOwner);
    }

    private void requirePackageInstalled(
            String packageName, UserType forUser, FailureMode failureMode) {
        Package pkg = TestApis.packages().find(packageName);

        if (forUser.equals(UserType.ANY)) {
            checkFailOrSkip(
                    packageName + " is required to be installed",
                    !pkg.installedOnUsers().isEmpty(),
                    failureMode);
        } else {
            checkFailOrSkip(
                    packageName + " is required to be installed for " + forUser,
                    pkg.installedOnUser(resolveUserTypeToUser(forUser)),
                    failureMode);
        }
    }

    private void requirePackageNotInstalled(
            String packageName, UserType forUser, FailureMode failureMode) {
        Package pkg = TestApis.packages().find(packageName);

        if (forUser.equals(UserType.ANY)) {
            checkFailOrSkip(
                    packageName + " is required to be not installed",
                    pkg.installedOnUsers().isEmpty(),
                    failureMode);
        } else {
            checkFailOrSkip(
                    packageName + " is required to be not installed for " + forUser,
                    !pkg.installedOnUser(resolveUserTypeToUser(forUser)),
                    failureMode);
        }
    }

    private void ensurePackageNotInstalled(
            String packageName, UserType forUser) {
        Package pkg = TestApis.packages().find(packageName);

        if (forUser.equals(UserType.ANY)) {
            pkg.uninstallFromAllUsers();
        } else {
            UserReference user = resolveUserTypeToUser(forUser);
            pkg.uninstall(user);
        }
    }

    /**
     * Behaves like {@link #dpc()} except that when running on a delegate, this will return
     * the delegating DPC not the delegate.
     */
    public RemotePolicyManager dpcOnly() {
        if (mPrimaryPolicyManager != null) {
            if (mPrimaryPolicyManager.isDelegate()) {
                return mDelegateDpc;
            }
        }

        return dpc();
    }

    /**
     * Get the most appropriate {@link RemotePolicyManager} instance for the device state.
     *
     * <p>This method should only be used by tests which are annotated with {@link PolicyTest}.
     *
     * <p>This may be a DPC, a delegate, or a normal app with or without given permissions.
     *
     * <p>If no policy manager is set as "primary" for the device state, then this method will first
     * check for a profile owner in the current user, or else check for a device owner.
     *
     * <p>If no Harrier-managed profile owner or device owner exists, an exception will be thrown.
     *
     * <p>If the profile owner or device owner is not a RemoteDPC then an exception will be thrown.
     */
    public RemotePolicyManager dpc() {
        if (mPrimaryPolicyManager != null) {
            return mPrimaryPolicyManager;
        }

        if (mProfileOwners.containsKey(TestApis.users().instrumented())) {
            DevicePolicyController profileOwner =
                    mProfileOwners.get(TestApis.users().instrumented());


            if (RemoteDpc.isRemoteDpc(profileOwner)) {
                return RemoteDpc.forDevicePolicyController(profileOwner);
            }
        }

        if (mDeviceOwner != null) {
            if (RemoteDpc.isRemoteDpc(mDeviceOwner)) {
                return RemoteDpc.forDevicePolicyController(mDeviceOwner);
            }

        }

        throw new IllegalStateException("No Harrier-managed profile owner or device owner. "
                + "Ensure you have set up the DPC using bedstead annotations.");
    }


    /**
     * Get the Device Policy Management Role Holder.
     */
    public RemoteDevicePolicyManagerRoleHolder dpmRoleHolder() {
        if (mDevicePolicyManagerRoleHolder == null) {
            throw new IllegalStateException(
                    "No Harrier-managed device policy manager role holder.");
        }

        return mDevicePolicyManagerRoleHolder;
    }

    /**
     * Get a {@link TestAppProvider} which is cleared between tests.
     *
     * <p>Note that you must still manage the test apps manually. To have the infrastructure
     * automatically remove test apps use the {@link EnsureTestAppInstalled} annotation.
     */
    public TestAppProvider testApps() {
        return mTestAppProvider;
    }

    /**
     * Get a test app installed with @EnsureTestAppInstalled with no key.
     */
    public TestAppInstance testApp() {
        return testApp(DEFAULT_TEST_APP_KEY);
    }

    /**
     * Get a test app installed with `@EnsureTestAppInstalled` with the given key.
     */
    public TestAppInstance testApp(String key) {
        if (!mTestApps.containsKey(key)) {
            throw new NeneException("No testapp with given key. Use @EnsureTestAppInstalled");
        }

        return mTestApps.get(key);
    }

    private void ensureCanGetPermission(String permission) {
        if (mPermissionsInstrumentationPackage == null) {
            // We just need to check if we can get it generally

            if (TestApis.permissions().usablePermissions().contains(permission)) {
                return;
            }

            if (TestApis.packages().instrumented().isInstantApp()) {
                // Instant Apps aren't able to know the permissions of shell so we can't know
                // if we
                // can adopt it - we'll assume we can adopt and log
                Log.i(LOG_TAG,
                        "Assuming we can get permission " + permission
                                + " as running on instant app");
                return;
            }

            TestApis.permissions().throwPermissionException(
                    "Can not get required permission", permission);
        }

        if (TestApis.permissions().adoptablePermissions().contains(permission)) {
            requireNoPermissionsInstrumentation("Requires permission " + permission);
        } else if (mPermissionsInstrumentationPackagePermissions.contains(permission)) {
            requirePermissionsInstrumentation("Requires permission " + permission);
        } else {
            // Can't get permission at all - error (including the permissions for both)
            TestApis.permissions().throwPermissionException(
                    "Can not get permission " + permission + " including by instrumenting "
                            + mPermissionsInstrumentationPackage
                            + "\n " + mPermissionsInstrumentationPackage + " permissions: "
                            + mPermissionsInstrumentationPackagePermissions,
                    permission
            );
        }
    }

    private void switchToUser(UserReference user) {
        UserReference currentUser = TestApis.users().current();
        if (!currentUser.equals(user)) {
            if (mOriginalSwitchedUser == null) {
                mOriginalSwitchedUser = currentUser;
            }
            user.switchTo();
        }
    }

    private void switchFromUser(UserReference user) {
        UserReference currentUser = TestApis.users().current();
        if (!currentUser.equals(user)) {
            return;
        }

        // We need to find a different user to switch to
        // full users only, starting with lowest ID
        List<UserReference> users = new ArrayList<>(TestApis.users().all());
        users.sort(Comparator.comparingInt(UserReference::id));

        for (UserReference otherUser : users) {
            if (otherUser.equals(user)) {
                continue;
            }

            if (otherUser.parent() != null) {
                continue;
            }

            if (!otherUser.isRunning()) {
                continue;
            }

            switchToUser(otherUser);
            return;
        }

        // There are no users to switch to so we'll create one
        ensureHasUser(SECONDARY_USER_TYPE_NAME,
                /* installInstrumentedApp= */ OptionalBoolean.ANY,
                /* switchedToUser= */ OptionalBoolean.TRUE);
    }

    private void requireNotHeadlessSystemUserMode(String reason) {
        assumeFalse(reason, TestApis.users().isHeadlessSystemUserMode());
    }

    private void requireHeadlessSystemUserMode(String reason) {
        assumeTrue(reason, TestApis.users().isHeadlessSystemUserMode());
    }

    private void requireLowRamDevice(String reason, FailureMode failureMode) {
        checkFailOrSkip(reason,
                TestApis.context().instrumentedContext()
                        .getSystemService(ActivityManager.class)
                        .isLowRamDevice(),
                failureMode);
    }

    private void requireNotLowRamDevice(String reason, FailureMode failureMode) {
        checkFailOrSkip(reason,
                !TestApis.context().instrumentedContext()
                        .getSystemService(ActivityManager.class)
                        .isLowRamDevice(),
                failureMode);
    }

    private void requireVisibleBackgroundUsersSupported(String reason, FailureMode failureMode) {
        if (!TestApis.users().isVisibleBackgroundUsersSupported()) {
            String message = "Device does not support visible background users, but test requires "
                    + "it. Reason: " + reason;
            failOrSkip(message, failureMode);
        }
    }

    private void requireVisibleBackgroundUsersNotSupported(String reason, FailureMode failureMode) {
        if (TestApis.users().isVisibleBackgroundUsersSupported()) {
            String message = "Device supports visible background users, but test requires that it "
                    + "doesn't. Reason: " + reason;
            failOrSkip(message, failureMode);
        }
    }

    private void requireVisibleBackgroundUsersOnDefaultDisplaySupported(String reason,
            FailureMode failureMode) {
        if (!TestApis.users().isVisibleBackgroundUsersOnDefaultDisplaySupported()) {
            String message = "Device does not support visible background users on default display, "
                    + "but test requires it. Reason: " + reason;
            failOrSkip(message, failureMode);
        }
    }

    private void requireVisibleBackgroundUsersOnDefaultDisplayNotSupported(String reason,
            FailureMode failureMode) {
        if (TestApis.users().isVisibleBackgroundUsersOnDefaultDisplaySupported()) {
            String message = "Device supports visible background users on default display, but test"
                    + " requires that it doesn't. Reason: " + reason;
            failOrSkip(message, failureMode);
        }
    }

    private void ensureScreenIsOn() {
        TestApis.device().wakeUp();
    }

    private void ensureUnlocked() {
        TestApis.device().unlock();
    }

    private void ensurePasswordSet(UserType forUser, String password) {
        UserReference user = resolveUserTypeToUser(forUser);

        if (user.hasLockCredential()) {
            return;
        }

        try {
            user.setPassword(password);
        } catch (NeneException e) {
            throw new AssertionError("Require password set but error when setting "
                    + "password on user " + user, e);
        }
        mUsersSetPasswords.add(user);
    }

    private void ensurePasswordNotSet(UserType forUser) {
        UserReference user = resolveUserTypeToUser(forUser);

        if (!user.hasLockCredential()) {
            return;
        }

        try {
            user.clearPassword(DEFAULT_PASSWORD);
        } catch (NeneException e
        ) {
            throw new AssertionError(
                    "Test requires user " + user + " does not have a password. "
                            + "Password is set and is not DEFAULT_PASSWORD.");
        }
        mUsersSetPasswords.remove(user);
    }

    private void ensureBluetoothEnabled() {
        if (mOriginalBluetoothEnabled == null) {
            mOriginalBluetoothEnabled = TestApis.bluetooth().isEnabled();
        }
        TestApis.bluetooth().setEnabled(true);
    }

    private void ensureBluetoothDisabled() {
        if (mOriginalBluetoothEnabled == null) {
            mOriginalBluetoothEnabled = TestApis.bluetooth().isEnabled();
        }
        TestApis.bluetooth().setEnabled(false);
    }

    private void ensureWifiEnabled() {
        if (mOriginalWifiEnabled == null) {
            mOriginalWifiEnabled = TestApis.wifi().isEnabled();
        }
        TestApis.wifi().setEnabled(true);
    }

    private void ensureWifiDisabled() {
        if (mOriginalWifiEnabled == null) {
            mOriginalWifiEnabled = TestApis.wifi().isEnabled();
        }
        TestApis.wifi().setEnabled(false);
    }

    private boolean isOrganizationOwned(Annotation annotation)
            throws InvocationTargetException, IllegalAccessException {
        Method isOrganizationOwnedMethod;

        try {
            isOrganizationOwnedMethod = annotation.annotationType().getMethod(
                    "isOrganizationOwned");
        } catch (NoSuchMethodException ignored) {
            return false;
        }

        return (boolean) isOrganizationOwnedMethod.invoke(annotation);
    }

    private void withAppOp(String... appOp) {
        if (mPermissionContext == null) {
            mPermissionContext = TestApis.permissions().withAppOp(appOp);
        } else {
            mPermissionContext = mPermissionContext.withAppOp(appOp);
        }
    }

    private void withoutAppOp(String... appOp) {
        if (mPermissionContext == null) {
            mPermissionContext = TestApis.permissions().withoutAppOp(appOp);
        } else {
            mPermissionContext = mPermissionContext.withoutAppOp(appOp);
        }
    }

    private void withPermission(String... permission) {
        if (mPermissionContext == null) {
            mPermissionContext = TestApis.permissions().withPermission(permission);
        } else {
            mPermissionContext = mPermissionContext.withPermission(permission);
        }
    }

    private void withoutPermission(String... permission) {
        requireNotInstantApp("Uses withoutPermission", FailureMode.SKIP);

        if (mPermissionContext == null) {
            mPermissionContext = TestApis.permissions().withoutPermission(permission);
        } else {
            mPermissionContext = mPermissionContext.withoutPermission(permission);
        }
    }

    private void ensureGlobalSettingSet(String key, String value){
        if (!mOriginalGlobalSettings.containsKey(key)) {
            mOriginalGlobalSettings.put(key, TestApis.settings().global().getString(value));
        }

        TestApis.settings().global().putString(key, value);
    }

    private void requireMultiUserSupport(FailureMode failureMode) {
        checkFailOrSkip("This test is only supported on multi user devices",
                TestApis.users().supportsMultipleUsers(), failureMode);
    }

    private void requireHasPolicyExemptApps(FailureMode failureMode) {
        checkFailOrSkip("OEM does not define any policy-exempt apps",
                !TestApis.devicePolicy().getPolicyExemptApps().isEmpty(), failureMode);
    }

    private void requireInstantApp(String reason, FailureMode failureMode) {
        checkFailOrSkip("Test only runs as an instant-app: " + reason,
                TestApis.packages().instrumented().isInstantApp(), failureMode);
    }

    private void requireNotInstantApp(String reason, FailureMode failureMode) {
        checkFailOrSkip("Test does not run as an instant-app: " + reason,
                !TestApis.packages().instrumented().isInstantApp(), failureMode);
    }

    private void requireFeatureFlagEnabled(String namespace, String key, FailureMode failureMode) {
        checkFailOrSkip("Feature flag " + namespace + ":" + key + " must be enabled",
                TestApis.flags().isEnabled(namespace, key), failureMode);
    }

    private void ensureFeatureFlagEnabled(String namespace, String key) {
        ensureFeatureFlagValue(namespace, key, Flags.ENABLED_VALUE);
    }

    private void requireFeatureFlagNotEnabled(
            String namespace, String key, FailureMode failureMode) {
        checkFailOrSkip("Feature flag " + namespace + ":" + key + " must not be enabled",
                !TestApis.flags().isEnabled(namespace, key), failureMode);
    }

    private void ensureFeatureFlagNotEnabled(String namespace, String key) {
        ensureFeatureFlagValue(namespace, key, Flags.DISABLED_VALUE);
    }

    private void requireFeatureFlagValue(
            String namespace, String key, String value, FailureMode failureMode) {
        checkFailOrSkip("Feature flag " + namespace + ":" + key + " must be enabled",
                Objects.equal(value, TestApis.flags().get(namespace, key)), failureMode);
    }

    private void ensureFeatureFlagValue(String namespace, String key, String value) {
        Map<String, String> originalNamespace =
                mOriginalFlagValues.computeIfAbsent(namespace, k -> new HashMap<>());
        if (!originalNamespace.containsKey(key)) {
            originalNamespace.put(key, TestApis.flags().get(namespace, key));
        }

        TestApis.flags().set(namespace, key, value);
    }

    /**
     * Access harrier-managed accounts on the instrumented user.
     */
    public RemoteAccountAuthenticator accounts() {
        return accounts(TestApis.users().instrumented());
    }

    /**
     * Access harrier-managed accounts on the given user.
     */
    public RemoteAccountAuthenticator accounts(UserType user) {
        return accounts(resolveUserTypeToUser(user));
    }

    /**
     * Access harrier-managed accounts on the given user.
     */
    public RemoteAccountAuthenticator accounts(UserReference user) {
        if (!mAccountAuthenticators.containsKey(user)) {
            throw new IllegalStateException("No Harrier-Managed account authenticator on user "
                    + user + ". Did you use @EnsureHasAccountAuthenticator or @EnsureHasAccount?");
        }

        return mAccountAuthenticators.get(user);
    }

    private void ensureHasAccountAuthenticator(UserType onUser) {
        UserReference user = resolveUserTypeToUser(onUser);
        // We don't use .install() so we can rely on the default testapp sharing/uninstall logic
        ensureTestAppInstalled(REMOTE_ACCOUNT_AUTHENTICATOR_TEST_APP,
                user);

        mAccountAuthenticators.put(user, RemoteAccountAuthenticator.install(user));
    }

    private void ensureHasAccount(UserType onUser, String key, String[] features) {
        ensureHasAccount(onUser, key, features, new HashSet<>());
    }


    private AccountReference ensureHasAccount(UserType onUser, String key, String[] features,
            Set<AccountReference> ignoredAccounts) {
        ensureHasAccountAuthenticator(onUser);

        Optional<AccountReference> account =
                accounts(onUser).allAccounts().stream().filter(i -> !ignoredAccounts.contains(i))
                        .findFirst();

        if (account.isPresent()) {
            accounts(onUser).setFeatures(account.get(), Set.of(features));
            mAccounts.put(key, account.get());
            return account.get();
        }

        AccountReference createdAccount = accounts(onUser).addAccount()
                .features(Set.of(features))
                .add();
        mCreatedAccounts.add(createdAccount);
        mAccounts.put(key, createdAccount);
        return createdAccount;
    }

    private void ensureHasAccounts(EnsureHasAccount[] accounts) {
        Set<AccountReference> ignoredAccounts = new HashSet<>();

        for (EnsureHasAccount account : accounts) {
            ignoredAccounts.add(ensureHasAccount(
                    account.onUser(), account.key(), account.features(), ignoredAccounts));
        }
    }

    private void ensureHasNoAccounts(UserType userType) {
        if (userType == UserType.ANY) {
            TestApis.users().all().forEach(this::ensureHasNoAccounts);
        } else {
            ensureHasNoAccounts(resolveUserTypeToUser(userType));
        }
    }

    private void ensureHasNoAccounts(UserReference user) {
        if (REMOTE_ACCOUNT_AUTHENTICATOR_TEST_APP.pkg().installedOnUser(user)) {
            RemoteAccountAuthenticator.install(user).allAccounts()
                    .forEach(AccountReference::remove);
        }

        if (!TestApis.accounts().all(user).isEmpty()) {
            throw new NeneException("Expected no accounts on user " + user
                    + " but there was some that could not be removed");
        }
    }

    /**
     * Get the default account defined with {@link EnsureHasAccount}.
     */
    public AccountReference account() {
        return account(DEFAULT_ACCOUNT_KEY);
    }

    /**
     * Get the account defined with {@link EnsureHasAccount} with a given key.
     */
    public AccountReference account(String key) {
        if (!mAccounts.containsKey(key)) {
            throw new IllegalStateException("No account for key " + key);
        }

        return mAccounts.get(key);
    }

    @Override
    boolean isHeadlessSystemUserMode() {
        return TestApis.users().isHeadlessSystemUserMode();
    }

    private final Map<Class<? extends AnnotationExecutor>, AnnotationExecutor> mAnnotationExecutors = new HashMap<>();

    private AnnotationExecutor getAnnotationExecutor(Class<? extends AnnotationExecutor> annotationExecutorClass) {
        if (!mAnnotationExecutors.containsKey(annotationExecutorClass)) {
            try {
                mAnnotationExecutors.put(
                        annotationExecutorClass, annotationExecutorClass.newInstance());
            } catch (Exception e) {
                throw new RuntimeException("Error creating annotation executor", e);
            }
        }
        return mAnnotationExecutors.get(annotationExecutorClass);
    }

    private void ensureHasUserRestriction(String restriction, UserType onUser) {
        ensureHasUserRestriction(restriction, resolveUserTypeToUser(onUser));
    }

    private void ensureHasUserRestriction(String restriction, UserReference onUser) {
        if (TestApis.devicePolicy().userRestrictions(onUser).isSet(restriction)) {
            return;
        }

        boolean hasSet = false;

        if (onUser.equals(TestApis.users().system())) {
            hasSet = trySetUserRestrictionWithDeviceOwner(restriction);
        }

        if (!hasSet) {
            hasSet = trySetUserRestrictionWithProfileOwner(onUser, restriction);
        }

        if (!hasSet && !onUser.equals(TestApis.users().system())) {
            hasSet = trySetUserRestrictionWithDeviceOwner(restriction);
        }

        if (!hasSet) {
            throw new AssumptionViolatedException(
                    "Infra cannot set user restriction " + restriction);
        }

        if (mRemovedUserRestrictions.containsKey(onUser)
                && mRemovedUserRestrictions.get(onUser).contains(restriction)) {
            mRemovedUserRestrictions.get(onUser).remove(restriction);
        } else {
            if (!mAddedUserRestrictions.containsKey(onUser)) {
                mAddedUserRestrictions.put(onUser, new HashSet<>());
            }

            mAddedUserRestrictions.get(onUser).add(restriction);
        }

        if (!TestApis.devicePolicy().userRestrictions(onUser).isSet(restriction)) {
            throw new NeneException("Error setting user restriction " + restriction);
        }
    }

    private boolean trySetUserRestrictionWithDeviceOwner(String restriction) {
        ensureHasDeviceOwner(FailureMode.FAIL,
                /* isPrimary= */ false,
                /* affiliationIds= */ Set.of(), /* type= */ DeviceOwnerType.DEFAULT, new TestAppProvider().query());

        RemotePolicyManager dpc = deviceOwner();
        try {
            dpc.devicePolicyManager().addUserRestriction(dpc.componentName(), restriction);
        } catch (SecurityException e) {
            if (e.getMessage().contains("cannot set user restriction")) {
                return false;
            }
            throw e;
        }
        return true;
    }

    private boolean trySetUserRestrictionWithProfileOwner(UserReference onUser, String restriction) {
        ensureHasProfileOwner(onUser,
                /* isPrimary= */ false, /* isParentInstance= */ false,
                /* affiliationIds= */ Set.of(), new TestAppProvider().query());

        RemotePolicyManager dpc = profileOwner(onUser);
        try {
            dpc.devicePolicyManager().addUserRestriction(dpc.componentName(), restriction);
        } catch (SecurityException e) {
            if (e.getMessage().contains("cannot set user restriction")) {
                return false;
            }
            throw e;
        }
        return true;
    }

    private boolean tryClearUserRestrictionWithDeviceOwner(String restriction) {
        ensureHasDeviceOwner(FailureMode.FAIL,
                /* isPrimary= */ false,
                /* affiliationIds= */ Set.of(), /* type= */ DeviceOwnerType.DEFAULT, new TestAppProvider().query());

        RemotePolicyManager dpc = deviceOwner();
        try {
            dpc.devicePolicyManager().clearUserRestriction(dpc.componentName(), restriction);
        } catch (SecurityException e) {
            if (e.getMessage().contains("cannot set user restriction")) {
                return false;
            }
            throw e;
        }
        return true;
    }

    private boolean tryClearUserRestrictionWithProfileOwner(UserReference onUser, String restriction) {
        ensureHasProfileOwner(onUser,
                /* isPrimary= */ false, /* isParentInstance= */ false,
                /* affiliationIds= */ Set.of(), new TestAppProvider().query());

        RemotePolicyManager dpc = profileOwner(onUser);
        try {
            dpc.devicePolicyManager().clearUserRestriction(dpc.componentName(), restriction);
        } catch (SecurityException e) {
            if (e.getMessage().contains("cannot set user restriction")) {
                return false;
            }
            throw e;
        }
        return true;
    }

    private void ensureDoesNotHaveUserRestriction(String restriction, UserType onUser) {
        ensureDoesNotHaveUserRestriction(restriction, resolveUserTypeToUser(onUser));
    }

    private void ensureDoesNotHaveUserRestriction(String restriction, UserReference onUser) {
        if (!TestApis.devicePolicy().userRestrictions(onUser).isSet(restriction)) {
            return;
        }

        boolean hasSet = false;

        if (onUser.equals(TestApis.users().system())) {
            hasSet = tryClearUserRestrictionWithDeviceOwner(restriction);
        }

        if (!hasSet) {
            hasSet = tryClearUserRestrictionWithProfileOwner(onUser, restriction);
        }

        if (!hasSet && !onUser.equals(TestApis.users().system())) {
            hasSet = tryClearUserRestrictionWithDeviceOwner(restriction);
        }

        if (!hasSet) {
            throw new AssumptionViolatedException(
                    "Infra cannot clear user restriction " + restriction);
        }

        if (mAddedUserRestrictions.containsKey(onUser)
                && mAddedUserRestrictions.get(onUser).contains(restriction)) {
            mAddedUserRestrictions.get(onUser).remove(restriction);
        } else {
            if (!mRemovedUserRestrictions.containsKey(onUser)) {
                mRemovedUserRestrictions.put(onUser, new HashSet<>());
            }

            mRemovedUserRestrictions.get(onUser).add(restriction);
        }

        if (TestApis.devicePolicy().userRestrictions(onUser).isSet(restriction)) {
            throw new NeneException("Error removing user restriction " + restriction + ". "
                    + "It's possible this is set by the system and cannot be removed");
        }
    }

    private void requireSystemServiceAvailable(Class<?> serviceClass, FailureMode failureMode) {
        Object service = mContext.getSystemService(serviceClass);

        checkFailOrSkip("Requires " + serviceClass + " to be available",
                service != null, failureMode);
    }
}
