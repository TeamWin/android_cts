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

package android.service.games;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.GameManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.service.games.cts.app.IGameServiceTestService;
import android.support.test.uiautomator.By;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.UiAutomatorUtils;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * CTS tests for {@link android.service.games.GameService}.
 */
@RunWith(AndroidJUnit4.class)
public final class GameServiceTest {
    private static final String TEST_APP_PACKAGE_NAME = "android.service.games.cts.app";
    private static final String GAME_PACKAGE_NAME = "android.service.games.cts.game";
    private static final String FALSE_POSITIVE_GAME_PACKAGE_NAME =
            "android.service.games.cts.falsepositive";
    private static final String NOT_GAME_PACKAGE_NAME = "android.service.games.cts.notgame";

    private ServiceConnection mServiceConnection;

    @Before
    public void setUp() throws Exception {
        GameManager gameManager =
                getInstrumentation().getContext().getSystemService(GameManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(gameManager,
                manager -> manager.setGameServiceProvider(TEST_APP_PACKAGE_NAME));

        mServiceConnection = new ServiceConnection();
        assertThat(
                getInstrumentation().getContext().bindService(
                        new Intent("android.service.games.action.TEST_SERVICE").setPackage(
                                TEST_APP_PACKAGE_NAME),
                        mServiceConnection,
                        Context.BIND_AUTO_CREATE)).isTrue();
        mServiceConnection.waitForConnection(10, TimeUnit.SECONDS);

        getTestService().setGamePackageNames(ImmutableList.of(GAME_PACKAGE_NAME));
    }

    @After
    public void tearDown() {
        forceStop(GAME_PACKAGE_NAME);
        forceStop(NOT_GAME_PACKAGE_NAME);
        forceStop(FALSE_POSITIVE_GAME_PACKAGE_NAME);

        GameManager gameManager =
                getInstrumentation().getContext().getSystemService(GameManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(gameManager,
                manager -> manager.setGameServiceProvider(""));
    }

    @Test
    public void gameService_connectsOnStartup() throws Exception {
        assumeGameServiceFeaturePresent();

        assertThat(getTestService().isGameServiceConnected()).isTrue();
    }

    @Test
    public void gameService_startsGameSessionsForGames() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(NOT_GAME_PACKAGE_NAME);
        launchAndWaitForPackage(GAME_PACKAGE_NAME);
        launchAndWaitForPackage(FALSE_POSITIVE_GAME_PACKAGE_NAME);

        assertThat(getTestService().getActiveSessions()).containsExactly(
                GAME_PACKAGE_NAME);
    }

    private IGameServiceTestService getTestService() {
        return mServiceConnection.mService;
    }

    private static void assumeGameServiceFeaturePresent() {
        assumeTrue(getInstrumentation().getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_GAME_SERVICE));
    }

    private static void launchAndWaitForPackage(String packageName) throws Exception {
        PackageManager packageManager = getInstrumentation().getContext().getPackageManager();
        getInstrumentation().getContext().startActivity(
                packageManager.getLaunchIntentForPackage(packageName));
        UiAutomatorUtils.waitFindObject(By.pkg(packageName).depth(0));
    }

    private static void forceStop(String packageName) {
        ShellUtils.runShellCommand("am force-stop %s", packageName);
    }

    private static final class ServiceConnection implements android.content.ServiceConnection {
        private final Semaphore mSemaphore = new Semaphore(0);
        private IGameServiceTestService mService;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IGameServiceTestService.Stub.asInterface(service);
            mSemaphore.release();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        public void waitForConnection(int timeout, TimeUnit timeUnit) throws Exception {
            assertThat(mSemaphore.tryAcquire(timeout, timeUnit)).isTrue();
        }
    }
}
