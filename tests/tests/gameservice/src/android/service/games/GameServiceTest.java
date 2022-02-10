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
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.IBinder;
import android.service.games.testing.ActivityResult;
import android.service.games.testing.IGameServiceTestService;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Until;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CTS tests for {@link android.service.games.GameService}.
 */
@RunWith(AndroidJUnit4.class)
public final class GameServiceTest {
    private static final String GAME_PACKAGE_NAME = "android.service.games.cts.game";
    private static final String FALSE_POSITIVE_GAME_PACKAGE_NAME =
            "android.service.games.cts.falsepositive";
    private static final String NOT_GAME_PACKAGE_NAME = "android.service.games.cts.notgame";
    private static final String RESTART_GAME_VERIFIER_PACKAGE_NAME =
            "android.service.games.cts.restartgameverifier";
    private static final String START_ACTIVITY_VERIFIER_PACKAGE_NAME =
            "android.service.games.cts.startactivityverifier";
    private static final String TOUCH_VERIFIER_PACKAGE_NAME =
            "android.service.games.cts.touchverifier";

    private ServiceConnection mServiceConnection;

    @Before
    public void setUp() throws Exception {
        GameManager gameManager =
                getInstrumentation().getContext().getSystemService(GameManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(gameManager,
                manager -> manager.setGameServiceProvider(
                        getInstrumentation().getContext().getPackageName()));

        mServiceConnection = new ServiceConnection();
        assertThat(
                getInstrumentation().getContext().bindService(
                        new Intent("android.service.games.action.TEST_SERVICE").setPackage(
                                getInstrumentation().getContext().getPackageName()),
                        mServiceConnection,
                        Context.BIND_AUTO_CREATE)).isTrue();
        mServiceConnection.waitForConnection(10, TimeUnit.SECONDS);

        getTestService().setGamePackageNames(
                ImmutableList.of(
                        GAME_PACKAGE_NAME,
                        RESTART_GAME_VERIFIER_PACKAGE_NAME,
                        TOUCH_VERIFIER_PACKAGE_NAME));
    }

    @After
    public void tearDown() throws Exception {
        forceStop(GAME_PACKAGE_NAME);
        forceStop(NOT_GAME_PACKAGE_NAME);
        forceStop(FALSE_POSITIVE_GAME_PACKAGE_NAME);
        forceStop(RESTART_GAME_VERIFIER_PACKAGE_NAME);
        forceStop(START_ACTIVITY_VERIFIER_PACKAGE_NAME);
        forceStop(TOUCH_VERIFIER_PACKAGE_NAME);

        getTestService().resetState();

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

    @Test
    public void getTaskId_returnsTaskIdOfGame() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(GAME_PACKAGE_NAME);

        int taskId = getTestService().getFocusedTaskId();

        assertThat(taskId).isEqualTo(
                getActivityTaskId(GAME_PACKAGE_NAME, GAME_PACKAGE_NAME + ".MainActivity"));
    }

    @Test
    public void setTaskOverlayView_addsViewsToOverlay() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(GAME_PACKAGE_NAME);

        Rect touchableBounds = getTestService().getTouchableOverlayBounds();

        Bitmap overlayScreenshot = Bitmap.createBitmap(
                getInstrumentation().getUiAutomation().takeScreenshot(),
                touchableBounds.left,
                touchableBounds.top,
                touchableBounds.width(),
                touchableBounds.height());

        // TODO(b/215706114): UI automator does not appear to have visibility into the overlay;
        //                    therefore, it cannot be used to validate the overlay's contents.
        //                    We should try and see if we can expose the overlay to UI automator in
        //                    order to have a more foolproof validation check. We will use this
        //                    magenta background check in the meantime.
        // The overlay background is set to magenta. Verify that it has been rendered by checking
        // the corners.
        assertThat(overlayScreenshot.getPixel(0, 0)).isEqualTo(Color.MAGENTA);
        assertThat(overlayScreenshot.getPixel(0, overlayScreenshot.getHeight() - 1)).isEqualTo(
                Color.MAGENTA);
        assertThat(overlayScreenshot.getPixel(overlayScreenshot.getWidth() - 1, 0)).isEqualTo(
                Color.MAGENTA);
        assertThat(overlayScreenshot.getPixel(overlayScreenshot.getWidth() - 1,
                overlayScreenshot.getHeight() - 1)).isEqualTo(Color.MAGENTA);
    }

    @Test
    public void setTaskOverlayView_passesTouchesOutsideOverlayToGame() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(TOUCH_VERIFIER_PACKAGE_NAME);

        Rect touchableBounds = getTestService().getTouchableOverlayBounds();
        UiAutomatorUtils.getUiDevice().click(touchableBounds.centerX(), touchableBounds.centerY());

        UiAutomatorUtils.waitFindObject(
                By.res(TOUCH_VERIFIER_PACKAGE_NAME, "times_clicked").text("0"));

        UiAutomatorUtils.getUiDevice().click(touchableBounds.centerX(), touchableBounds.top - 30);
        UiAutomatorUtils.waitFindObject(
                By.res(TOUCH_VERIFIER_PACKAGE_NAME, "times_clicked").text("1"));

        UiAutomatorUtils.getUiDevice()
                .click(touchableBounds.centerX(), touchableBounds.bottom + 30);
        UiAutomatorUtils.waitFindObject(
                By.res(TOUCH_VERIFIER_PACKAGE_NAME, "times_clicked").text("2"));

        UiAutomatorUtils.getUiDevice().click(touchableBounds.left - 30, touchableBounds.centerY());
        UiAutomatorUtils.waitFindObject(
                By.res(TOUCH_VERIFIER_PACKAGE_NAME, "times_clicked").text("3"));

        UiAutomatorUtils.getUiDevice().click(touchableBounds.right + 30, touchableBounds.centerY());
        UiAutomatorUtils.waitFindObject(
                By.res(TOUCH_VERIFIER_PACKAGE_NAME, "times_clicked").text("4"));
    }

    @Test
    public void startActivityForResult_startsActivityAndReceivesResultWithData() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(GAME_PACKAGE_NAME);

        getTestService().startGameSessionActivity(
                new Intent("android.service.games.cts.startactivityverifier.START"), null);

        setText(START_ACTIVITY_VERIFIER_PACKAGE_NAME, "result_code_edit_text", "10");
        setText(START_ACTIVITY_VERIFIER_PACKAGE_NAME, "result_data_edit_text", "foobar");
        click(START_ACTIVITY_VERIFIER_PACKAGE_NAME, "send_result_button");

        ActivityResult result = getTestService().getLastActivityResult();

        assertThat(result.getGameSessionPackageName()).isEqualTo(GAME_PACKAGE_NAME);
        assertThat(result.getSuccess().getResultCode()).isEqualTo(10);
        assertThat(result.getSuccess().getData().getStringExtra("data")).isEqualTo("foobar");
    }

    @Test
    public void startActivityForResult_startsActivityAndReceivesResultWithNoData()
            throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(GAME_PACKAGE_NAME);

        getTestService().startGameSessionActivity(
                new Intent("android.service.games.cts.startactivityverifier.START"), null);

        setText(START_ACTIVITY_VERIFIER_PACKAGE_NAME, "result_code_edit_text", "10");
        click(START_ACTIVITY_VERIFIER_PACKAGE_NAME, "send_result_button");

        ActivityResult result = getTestService().getLastActivityResult();

        assertThat(result.getGameSessionPackageName()).isEqualTo(GAME_PACKAGE_NAME);
        assertThat(result.getSuccess().getResultCode()).isEqualTo(10);
        assertThat(result.getSuccess().getData()).isNull();
    }

    @Test
    public void startActivityForResult_cannotStartBlockedActivities() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(GAME_PACKAGE_NAME);

        getTestService().startGameSessionActivity(
                new Intent("android.service.games.cts.startactivityverifier.START_BLOCKED"), null);

        ActivityResult result = getTestService().getLastActivityResult();

        assertThat(result.getGameSessionPackageName()).isEqualTo(GAME_PACKAGE_NAME);
        assertThat(result.getFailure().getClazz()).isEqualTo(SecurityException.class);
    }

    @Test
    public void startActivityForResult_propagatesActivityNotFoundException() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(GAME_PACKAGE_NAME);

        getTestService().startGameSessionActivity(new Intent("NO_ACTION"), null);

        ActivityResult result = getTestService().getLastActivityResult();

        assertThat(result.getGameSessionPackageName()).isEqualTo(GAME_PACKAGE_NAME);
        assertThat(result.getFailure().getClazz()).isEqualTo(ActivityNotFoundException.class);
    }

    @Test
    public void restartGame_gameAppIsRestarted() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(RESTART_GAME_VERIFIER_PACKAGE_NAME);

        UiAutomatorUtils.waitFindObject(
                By.res(RESTART_GAME_VERIFIER_PACKAGE_NAME, "times_started").text("1"));

        getTestService().restartFocusedGameSession();

        UiAutomatorUtils.waitFindObject(
                By.res(RESTART_GAME_VERIFIER_PACKAGE_NAME, "times_started").text("2"));

        getTestService().restartFocusedGameSession();

        UiAutomatorUtils.waitFindObject(
                By.res(RESTART_GAME_VERIFIER_PACKAGE_NAME, "times_started").text("3"));
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

    private static void setText(String resourcePackage, String resourceId, String text)
            throws Exception {
        UiAutomatorUtils.waitFindObject(By.res(resourcePackage, resourceId))
                .setText(text);
    }

    private static void click(String resourcePackage, String resourceId) throws Exception {
        UiAutomatorUtils.waitFindObject(By.res(resourcePackage, resourceId))
                .click();
    }

    private static void forceStop(String packageName) {
        ShellUtils.runShellCommand("am force-stop %s", packageName);
        UiAutomatorUtils.getUiDevice().wait(Until.gone(By.pkg(packageName).depth(0)), 20_000L);
    }

    private static int getActivityTaskId(String packageName, String componentName) {
        final String output = ShellUtils.runShellCommand("am stack list");
        final Pattern pattern = Pattern.compile(
                String.format(".*taskId=([0-9]+): %s/%s.*", packageName, componentName));

        for (String line : output.split("\\n")) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                String taskId = matcher.group(1);
                return Integer.parseInt(taskId);
            }
        }

        return -1;
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
