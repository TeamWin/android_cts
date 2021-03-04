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

package android.contentcaptureservice.cts;

import static android.contentcaptureservice.cts.Helper.MY_PACKAGE;
import static android.contentcaptureservice.cts.Helper.MY_SECOND_PACKAGE;
import static android.contentcaptureservice.cts.Helper.NO_ACTIVITIES;
import static android.contentcaptureservice.cts.Helper.NO_PACKAGES;
import static android.contentcaptureservice.cts.Helper.read;
import static android.contentcaptureservice.cts.Helper.sContext;
import static android.contentcaptureservice.cts.Helper.toSet;
import static android.contentcaptureservice.cts.OutOfProcessActivity.ACTION_CHECK_MANAGER_AND_FINISH;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.platform.test.annotations.AppModeFull;
import android.service.contentcapture.ContentCaptureService;
import android.support.test.uiautomator.UiDevice;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;

import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.Set;

/**
 * Tests for {@link ContentCaptureService#setContentCaptureWhitelist(Set, Set)}.
 *
 * <p><b>NOTE</b>: not all cases are supported because in some of them the test package is
 * whitelisted in 'lite' mode as it's the same as the service's.
 */
@AppModeFull(reason = "BlankWithTitleActivityTest is enough")
public class WhitelistTest extends AbstractContentCaptureIntegrationActivityLessTest {
    public final ServiceTestRule mServiceRule = new ServiceTestRule();
    private UiDevice mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    @Ignore("will be whitelisted 'lite'")
    @Test
    public void testNothingWhitelisted() throws Exception {
        final CtsContentCaptureService service = enableService(NO_PACKAGES, NO_ACTIVITIES);
        assertNotWhitelisted(service);
    }

    @Ignore("will be whitelisted 'lite'")
    @Test
    public void testNotWhitelisted_byPackage() throws Exception {
        final CtsContentCaptureService service = enableService(toSet("not.me"), NO_ACTIVITIES);
        assertNotWhitelisted(service);
    }

    @Test
    public void testNotWhitelisted_byActivity() throws Exception {
        final CtsContentCaptureService service = enableService(NO_PACKAGES,
                toSet(ComponentName.createRelative(MY_PACKAGE, ".who.cares")));
        assertNotWhitelisted(service);
    }

    @Test
    public void testWhitelisted_byPackage() throws Exception {
        final CtsContentCaptureService service = enableService(toSet(MY_PACKAGE), NO_ACTIVITIES);
        assertWhitelisted(service);
    }

    @Test
    public void testWhitelisted_byActivity() throws Exception {
        final CtsContentCaptureService service = enableService(NO_PACKAGES,
                toSet(OutOfProcessActivity.COMPONENT_NAME));
        assertWhitelisted(service);
    }

    @Test
    public void testNotWhitelisted_byService_alreadyRunning() throws Exception {
        IOutOfPackageDataSharingService service = getDataShareService();

        enableService(NO_PACKAGES, NO_ACTIVITIES);

        assertContentCaptureManagerAvailable(service, false);
    }

    @Test
    public void testWhitelisted_byService_alreadyRunning() throws Exception {
        IOutOfPackageDataSharingService service = getDataShareService();

        enableService(toSet(MY_SECOND_PACKAGE), NO_ACTIVITIES);

        // Wait for update to propagate
        mUiDevice.waitForIdle();

        assertContentCaptureManagerAvailable(service, true);
    }

    @Test
    public void testRinseAndRepeat_alreadyRunning() throws Exception {
        IOutOfPackageDataSharingService service = getDataShareService();

        // No Package
        final CtsContentCaptureService ccService = enableService(NO_PACKAGES, NO_ACTIVITIES);
        assertThat(service.isContentCaptureManagerAvailable()).isEqualTo(false);

        // Right package
        ccService.setContentCaptureWhitelist(toSet(MY_SECOND_PACKAGE), NO_ACTIVITIES);
        mUiDevice.waitForIdle();
        assertThat(service.isContentCaptureManagerAvailable()).isEqualTo(true);

        // Make sure no-package updates after killing (SystemServiceRegistry caches)
        killService();
        service = getDataShareService();
        ccService.setContentCaptureWhitelist(NO_PACKAGES, NO_ACTIVITIES);

        assertContentCaptureManagerAvailable(service, true);
    }

    @Test
    public void testRinseAndRepeat() throws Exception {

        // Right package
        final CtsContentCaptureService service = enableService(toSet(MY_PACKAGE), NO_ACTIVITIES);
        assertWhitelisted(service);

        // Wrong activity
        service.setContentCaptureWhitelist(NO_PACKAGES,
                toSet(ComponentName.createRelative(MY_PACKAGE, ".who.cares")));
        assertNotWhitelisted(service);

        // Right activity
        service.setContentCaptureWhitelist(NO_PACKAGES, toSet(OutOfProcessActivity.COMPONENT_NAME));
        assertWhitelisted(service);
    }

    private void assertWhitelisted(@NonNull CtsContentCaptureService service) throws Exception {
        launchActivityAndAssert(service, /* expectHasManager= */ true);
    }

    private void assertNotWhitelisted(@NonNull CtsContentCaptureService service) throws Exception {
        launchActivityAndAssert(service, /* expectHasManager= */ false);
    }

    private void assertContentCaptureManagerAvailable(IOutOfPackageDataSharingService service,
            boolean isAvailable) throws Exception {
        try {
            assertThat(service.isContentCaptureManagerAvailable()).isEqualTo(isAvailable);
        } finally {
            killService();
        }
    }

    private void launchActivityAndAssert(@NonNull CtsContentCaptureService service,
            boolean expectHasManager) throws Exception {
        OutOfProcessActivity.startActivity(sContext, ACTION_CHECK_MANAGER_AND_FINISH);
        final File startedMarker = OutOfProcessActivity.waitActivityStarted(sContext);
        try {
            final String result = read(startedMarker);
            if (expectHasManager) {
                assertThat(result).startsWith(OutOfProcessActivity.HAS_MANAGER);
            } else {
                assertThat(result).isEqualTo(OutOfProcessActivity.NO_MANAGER);
            }
        } finally {
            // Must kill process, otherwise next call might fail because of the cached
            // CaptureOptions state in the application context
            service.setIgnoreOrphanSessionEvents(true); //
            OutOfProcessActivity.killOutOfProcessActivity();
        }
    }

    private IOutOfPackageDataSharingService getDataShareService() throws Exception {
        Intent outsideService = new Intent();
        outsideService.setComponent(new ComponentName(
                "android.contentcaptureservice.cts2",
                "android.contentcaptureservice.cts2.OutOfPackageDataSharingService"
        ));
         IBinder service = mServiceRule.bindService(outsideService);
        return IOutOfPackageDataSharingService.Stub.asInterface(service);
    }

    private void killService() {
        runShellCommand("am broadcast --receiver-foreground "
                + "-n android.contentcaptureservice.cts2/.SelfDestructReceiver");
    }
}
