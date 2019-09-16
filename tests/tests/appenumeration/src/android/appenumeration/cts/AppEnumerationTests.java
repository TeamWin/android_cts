/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.appenumeration.cts;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteCallback;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.hamcrest.core.IsNull;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class AppEnumerationTests {

    private static final String PKG_BASE = "android.appenumeration.";

    /** A package with no published API and so isn't queryable by anything but package name */
    private static final String TARGET_NO_API = PKG_BASE + "noapi";
    /** A package that declares itself force queryable, making it visible to all other packages */
    private static final String TARGET_FORCEQUERYABLE = PKG_BASE + "forcequeryable";
    /** A package that exposes itself via various intent filters (activities, services, etc.) */
    private static final String TARGET_FILTERS = PKG_BASE + "filters";

    /** A package that has no queries tag or permission to query any specific packages */
    private static final String QUERIES_NOTHING = PKG_BASE + "queries.nothing";
    /** A package that has no queries tag or permissions but targets Q */
    private static final String QUERIES_NOTHING_Q = PKG_BASE + "queries.nothing.q";
    /** A package that has no queries but gets the QUERY_ALL_PACKAGES permission */
    private static final String QUERIES_NOTHING_PERM = PKG_BASE + "queries.nothing.haspermission";
    /** A package that queries for the action in {@link #TARGET_FILTERS} activity filter */
    private static final String QUERIES_ACTIVITY_ACTION = PKG_BASE + "queries.activity.action";
    /** A package that queries for the action in {@link #TARGET_FILTERS} service filter */
    private static final String QUERIES_SERVICE_ACTION = PKG_BASE + "queries.service.action";
    /** A package that queries for the authority in {@link #TARGET_FILTERS} provider */
    private static final String QUERIES_PROVIDER_AUTH = PKG_BASE + "queries.provider.authority";
    /** A package that queries for {@link #TARGET_NO_API} package */
    private static final String QUERIES_PACKAGE = PKG_BASE + "queries.pkg";

    private static final String[] ALL_QUERIES_PACKAGES = {
            QUERIES_NOTHING,
            QUERIES_NOTHING_Q,
            QUERIES_NOTHING_PERM,
            QUERIES_ACTIVITY_ACTION,
            QUERIES_SERVICE_ACTION,
            QUERIES_PROVIDER_AUTH,
            QUERIES_PACKAGE,
    };

    private static Context sContext;
    private static Handler sResponseHandler;
    private static HandlerThread sResponseThread;

    private static boolean sGlobalFeatureEnabled;

    @Rule
    public TestName name = new TestName();

    @BeforeClass
    public static void setup() {
        final String deviceConfigResponse =
                SystemUtil.runShellCommand(
                        "device_config get package_manager_service "
                                + "package_query_filtering_enabled")
                        .trim();
        sGlobalFeatureEnabled = Boolean.parseBoolean(deviceConfigResponse);
        System.out.println("Feature enabled: " + sGlobalFeatureEnabled);
        if (!sGlobalFeatureEnabled) return;

        sContext = InstrumentationRegistry.getInstrumentation().getContext();
        sResponseThread = new HandlerThread("response");
        sResponseThread.start();
        sResponseHandler = new Handler(sResponseThread.getLooper());
    }

    @Before
    public void setupTest() {
        if (!sGlobalFeatureEnabled) return;

        setFeatureEnabledForAll(true);
    }

    @AfterClass
    public static void tearDown() {
        if (!sGlobalFeatureEnabled) return;
        sResponseThread.quit();
    }

    @Test
    public void all_canSeeForceQueryable() throws Exception {
        assertVisible(QUERIES_NOTHING, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_ACTIVITY_ACTION, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_SERVICE_ACTION, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_PROVIDER_AUTH, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_PACKAGE, TARGET_FORCEQUERYABLE);
    }

    @Test
    public void queriesNothing_cannotSeeNonForceQueryable() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_NO_API);
        assertNotVisible(QUERIES_NOTHING, TARGET_FILTERS);
    }

    @Test
    public void queriesNothing_featureOff_canSeeAll() throws Exception {
        setFeatureEnabledForAll(QUERIES_NOTHING, false);
        assertVisible(QUERIES_NOTHING, TARGET_NO_API);
        assertVisible(QUERIES_NOTHING, TARGET_FILTERS);
    }

    @Test
    public void queriesNothingTargetsQ_canSeeAll() throws Exception {
        assertVisible(QUERIES_NOTHING_Q, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_NOTHING_Q, TARGET_NO_API);
        assertVisible(QUERIES_NOTHING_Q, TARGET_FILTERS);
    }

    @Test
    public void queriesNothingHasPermission_canSeeAll() throws Exception {
        assertVisible(QUERIES_NOTHING_PERM, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_NOTHING_PERM, TARGET_NO_API);
        assertVisible(QUERIES_NOTHING_PERM, TARGET_FILTERS);
    }

    @Test
    public void queriesSomething_cannotSeeNoApi() throws Exception {
        assertNotVisible(QUERIES_ACTIVITY_ACTION, TARGET_NO_API);
        assertNotVisible(QUERIES_SERVICE_ACTION, TARGET_NO_API);
        assertNotVisible(QUERIES_PROVIDER_AUTH, TARGET_NO_API);
    }

    @Test
    public void queriesActivityAction_canSeeTarget() throws Exception {
        assertVisible(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesServiceAction_canSeeTarget() throws Exception {
        assertVisible(QUERIES_SERVICE_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesProviderAuthority_canSeeTarget() throws Exception {
        assertVisible(QUERIES_PROVIDER_AUTH, TARGET_FILTERS);
    }

    @Test
    public void queriesPackage_canSeeTarget() throws Exception {
        assertVisible(QUERIES_PACKAGE, TARGET_NO_API);

    }
    @Test
    public void whenStarted_canSeeCaller() throws Exception {
        // let's first make sure that the target cannot see the caller.
        assertNotVisible(QUERIES_NOTHING, QUERIES_NOTHING_PERM);
        // now let's start the target and make sure that it can see the caller as part of that call
        PackageInfo packageInfo = startForResult(QUERIES_NOTHING_PERM, QUERIES_NOTHING);
        assertThat(packageInfo, IsNull.notNullValue());
        assertThat(packageInfo.packageName, is(QUERIES_NOTHING_PERM));
        // and finally let's re-run the last check to make sure that the target can still see the
        // caller
        assertVisible(QUERIES_NOTHING, QUERIES_NOTHING_PERM);
    }

    private void assertVisible(String sourcePackageName, String targetPackageName)
            throws Exception {
        if (!sGlobalFeatureEnabled) return;
        Assert.assertNotNull(sourcePackageName + " should be able to see " + targetPackageName,
                getPackageInfo(sourcePackageName, targetPackageName));
    }


    private void setFeatureEnabledForAll(Boolean enabled) {
        for (String pkgName : ALL_QUERIES_PACKAGES) {
            setFeatureEnabledForAll(pkgName, enabled);
        }
    }

    private void setFeatureEnabledForAll(String packageName, Boolean enabled) {
        SystemUtil.runShellCommand(
                "am compat " + (enabled == null ? "reset" : enabled ? "enable" : "disable")
                        + " 135549675 " + packageName);
    }

    private void assertNotVisible(String sourcePackageName, String targetPackageName)
            throws Exception {
        if (!sGlobalFeatureEnabled) return;
        try {
            getPackageInfo(sourcePackageName, targetPackageName);
            fail(sourcePackageName + " should not be able to see " + targetPackageName);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    private PackageInfo getPackageInfo(String sourcePackageName, String targetPackageName)
            throws Exception {
        Bundle response = sendCommand(sourcePackageName, targetPackageName,
                PKG_BASE + "cts.action.GET_PACKAGE_INFO");
        return response.getParcelable(Intent.EXTRA_RETURN_RESULT);
    }

    private PackageInfo startForResult(String sourcePackageName, String targetPackageName)
            throws Exception {
        Bundle response = sendCommand(sourcePackageName, targetPackageName,
                PKG_BASE + "cts.action.START_FOR_RESULT");
        return response.getParcelable(Intent.EXTRA_RETURN_RESULT);
    }

    private Bundle sendCommand(String sourcePackageName, String targetPackageName, String action)
            throws Exception {
        Intent intent = new Intent(action)
                .setComponent(new ComponentName(
                        sourcePackageName, PKG_BASE + "cts.query.TestActivity"))
                // data uri unique to each activity start to ensure actual launch and not just
                // redisplay
                .setData(Uri.parse("test://" + name.getMethodName() + targetPackageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, targetPackageName);
        final ConditionVariable latch = new ConditionVariable();
        final AtomicReference<Bundle> resultReference = new AtomicReference<>();
        final RemoteCallback callback = new RemoteCallback(
                bundle -> {
                    resultReference.set(bundle);
                    latch.open();
                },
                sResponseHandler);
        intent.putExtra("remoteCallback", callback);
        sContext.startActivity(intent);
        if (!latch.block(TimeUnit.SECONDS.toMillis(10))) {
            throw new TimeoutException(
                    "Latch timed out while awiating a response from " + targetPackageName);
        }
        final Bundle bundle = resultReference.get();
        if (bundle != null && bundle.containsKey("error")) {
            throw (Exception) Objects.requireNonNull(bundle.getSerializable("error"));
        }
        return bundle;
    }

}
