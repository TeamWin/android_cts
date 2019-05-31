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

package android.server.wm;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.Presubmit;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class StartActivityAsUserTests {
    static final String EXTRA_CALLBACK = "callback";
    static final String KEY_USER_ID = "user id";

    private static final String PACKAGE = "android.server.wm.cts";
    private static final String CLASS = "android.server.wm.StartActivityAsUserActivity";
    private static final Context sContext = InstrumentationRegistry.getInstrumentation()
            .getContext();
    private static final ActivityManager sAm = sContext.getSystemService(ActivityManager.class);
    private static final int INVALID_STACK = -1;

    private int mSecondUserId;
    private ActivityAndWindowManagersState mAmWmState = new ActivityAndWindowManagersState();

    @Before
    public void createSecondUser() {
        String output = runShellCommand("pm create-user --profileOf " + sContext.getUserId()
                + " user2");
        mSecondUserId = Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
        assertThat(mSecondUserId).isNotEqualTo(0);
        runShellCommand("pm install-existing --user " + mSecondUserId + " android.server.wm.cts");
    }

    @After
    public void removeSecondUser() {
        runShellCommand("pm remove-user " + mSecondUserId);
    }

    @BeforeClass
    public static void assumeMultiUser() {
        assumeTrue(UserManager.supportsMultipleUsers());
    }

    @Test
    public void startActivityValidUser() throws Throwable {
        int[] secondUser= {-1};
        CountDownLatch latch = new CountDownLatch(1);
        RemoteCallback cb = new RemoteCallback((Bundle result) -> {
            secondUser[0] = result.getInt(KEY_USER_ID);
            latch.countDown();
        });

        Intent intent = new Intent(sContext, StartActivityAsUserActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_CALLBACK, cb);

        UserHandle secondUserHandle = UserHandle.of(mSecondUserId);

        try {
            runWithShellPermissionIdentity(() -> {
                sContext.startActivityAsUser(intent, secondUserHandle);
                sAm.switchUser(secondUserHandle);
                try {
                    latch.await(5, TimeUnit.SECONDS);
                } finally {
                    sAm.switchUser(sContext.getUser());
                }
            });
        } catch (RuntimeException e) {
            throw e.getCause();
        }

        assertThat(secondUser[0]).isEqualTo(mSecondUserId);
    }

    @Test
    public void startActivityInvalidUser() {
        UserHandle secondUserHandle = UserHandle.of(mSecondUserId * 100);
        int[] stackId = {-1};

        Intent intent = new Intent(sContext, StartActivityAsUserActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        runWithShellPermissionIdentity(() -> {
            sContext.startActivityAsUser(intent, secondUserHandle);
            ActivityManagerState amState = mAmWmState.getAmState();
            amState.computeState();
            ComponentName componentName = ComponentName.createRelative(PACKAGE, CLASS);
            stackId[0] = amState.getStackIdByActivity(componentName);
        });

        assertThat(stackId[0]).isEqualTo(INVALID_STACK);
    }
}
