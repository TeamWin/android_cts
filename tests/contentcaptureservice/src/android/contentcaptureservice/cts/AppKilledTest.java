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

import static android.contentcaptureservice.cts.Assertions.assertNoEvents;
import static android.contentcaptureservice.cts.Helper.eventually;
import static android.contentcaptureservice.cts.Helper.sContext;
import static android.contentcaptureservice.cts.OutOfProcessActivity.getStartedMarker;
import static android.contentcaptureservice.cts.OutOfProcessActivity.getStoppedMarker;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import android.content.Intent;
import android.contentcaptureservice.cts.CtsContentCaptureService.Session;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import org.junit.Test;

@AppModeFull(reason = "BlankWithTitleActivityTest is enough")
public class AppKilledTest extends AbstractContentCaptureIntegrationActivityLessTest {

    @Test
    public void testDoIt() throws Exception {
        final CtsContentCaptureService service = enableService();

        startAndWaitOutOfProcessActivity();

        killOutOfProcessActivity();

        final Session session = service.getOnlyFinishedSession();
        Log.v(mTag, "session id: " + session.id);

        assertNoEvents(session, OutOfProcessActivity.COMPONENT_NAME);
    }

    private void startAndWaitOutOfProcessActivity() throws Exception {
        final Intent startIntent = new Intent(sContext,
                OutOfProcessActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getStartedMarker(sContext).delete();
        sContext.startActivity(startIntent);
        eventually("getStartedMarker()", () -> {
            return getStartedMarker(sContext).exists();
        });
        getStartedMarker(sContext).delete();
    }

    private void killOutOfProcessActivity() throws Exception {
        // Waiting for activity to stop (stop marker appears)
        eventually("getStoppedMarker()", () -> {
            return getStoppedMarker(sContext).exists();
        });

        // Kill it!
        runShellCommand("am broadcast --receiver-foreground "
                + "-n android.contentcaptureservice.cts/.SelfDestructReceiver");
    }

}
