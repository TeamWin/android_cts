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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.GameManager;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.service.games.testing.ActivityResult;
import android.service.games.testing.GetResultActivity;
import android.service.games.testing.IGameServiceTestService;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.util.Log;
import android.util.Size;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.UiAutomatorUtils;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CTS tests for {@link android.service.games.GameService}.
 */
@RunWith(AndroidJUnit4.class)
public final class GameServiceTest {
    static final String TAG = "GameServiceTest";

    private static final String GAME_PACKAGE_NAME = "android.service.games.cts.game";
    private static final String FALSE_POSITIVE_GAME_PACKAGE_NAME =
            "android.service.games.cts.falsepositive";
    private static final String NOT_GAME_PACKAGE_NAME = "android.service.games.cts.notgame";
    private static final String RESTART_GAME_VERIFIER_PACKAGE_NAME =
            "android.service.games.cts.restartgameverifier";
    private static final String START_ACTIVITY_VERIFIER_PACKAGE_NAME =
            "android.service.games.cts.startactivityverifier";
    private static final String SYSTEM_BAR_VERIFIER_PACKAGE_NAME =
            "android.service.games.cts.systembarverifier";
    private static final String TAKE_SCREENSHOT_VERIFIER_PACKAGE_NAME =
            "android.service.games.cts.takescreenshotverifier";
    private static final String TOUCH_VERIFIER_PACKAGE_NAME =
            "android.service.games.cts.touchverifier";

    @Parameter(0)
    public String mVolumeName;

    private ServiceConnection mServiceConnection;
    private ContentResolver mContentResolver;

    @Before
    public void setUp() throws Exception {
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
                        SYSTEM_BAR_VERIFIER_PACKAGE_NAME,
                        TAKE_SCREENSHOT_VERIFIER_PACKAGE_NAME,
                        TOUCH_VERIFIER_PACKAGE_NAME));

        GameManager gameManager =
                getInstrumentation().getContext().getSystemService(GameManager.class);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(gameManager,
                manager -> manager.setGameServiceProvider(
                        getInstrumentation().getContext().getPackageName()));
        mContentResolver = getInstrumentation().getContext().getContentResolver();
    }

    @After
    public void tearDown() throws Exception {
        forceStop(GAME_PACKAGE_NAME);
        forceStop(NOT_GAME_PACKAGE_NAME);
        forceStop(FALSE_POSITIVE_GAME_PACKAGE_NAME);
        forceStop(RESTART_GAME_VERIFIER_PACKAGE_NAME);
        forceStop(START_ACTIVITY_VERIFIER_PACKAGE_NAME);
        forceStop(SYSTEM_BAR_VERIFIER_PACKAGE_NAME);
        forceStop(TAKE_SCREENSHOT_VERIFIER_PACKAGE_NAME);
        forceStop(TOUCH_VERIFIER_PACKAGE_NAME);

        GameManager gameManager =
                getInstrumentation().getContext().getSystemService(GameManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(gameManager,
                manager -> manager.setGameServiceProvider(""));

        getTestService().resetState();
    }

    @Test
    public void gameService_connectsOnStartup() throws Exception {
        assumeGameServiceFeaturePresent();

        waitForGameServiceConnected();
        assertThat(isGameServiceConnected()).isTrue();
    }

    @Test
    public void gameService_connectsWhenGameServiceComponentIsEnabled() throws Exception {
        assumeGameServiceFeaturePresent();

        waitForGameServiceConnected();

        getTestService().setGameServiceComponentEnabled(false);
        waitForGameServiceDisconnected();

        getTestService().setGameServiceComponentEnabled(true);
        waitForGameServiceConnected();
    }

    @Test
    public void gameService_connectsWhenGameSessionServiceComponentIsEnabled() throws Exception {
        assumeGameServiceFeaturePresent();

        waitForGameServiceConnected();

        getTestService().setGameSessionServiceComponentEnabled(false);
        waitForGameServiceDisconnected();

        getTestService().setGameSessionServiceComponentEnabled(true);
        waitForGameServiceConnected();
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
        swipeFromTopEdgeToShowSystemBars();

        Rect touchableBounds = waitForTouchableOverlayBounds();

        Bitmap overlayScreenshot = Bitmap.createBitmap(
                getInstrumentation().getUiAutomation().takeScreenshot(),
                touchableBounds.left,
                touchableBounds.top,
                touchableBounds.width(),
                touchableBounds.height());

        // TODO(b/218901969): UI automator does not appear to have visibility into the overlay;
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
        swipeFromTopEdgeToShowSystemBars();

        Rect touchableBounds = waitForTouchableOverlayBounds();
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
    public void onTransientSystemBarVisibilityChanged_nonTransient_doesNotDispatchShow()
            throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(SYSTEM_BAR_VERIFIER_PACKAGE_NAME);

        UiAutomatorUtils.getUiDevice().findObject(
                        By.text("Show system bars permanently")
                                .pkg(SYSTEM_BAR_VERIFIER_PACKAGE_NAME))
                .click();

        assertOverlayDoesNotAppear();
        assertThat(
                getTestService().getOnSystemBarVisibilityChangedInfo().getTimesShown())
                .isEqualTo(0);
    }

    @Test
    public void onTransientSystemBarVisibilityFromRevealGestureChanged_dispatchesHideEvent()
            throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(GAME_PACKAGE_NAME);
        swipeFromTopEdgeToShowSystemBars();

        assertThat(waitForTouchableOverlayBounds().isEmpty()).isFalse();
        assertThat(
                getTestService().getOnSystemBarVisibilityChangedInfo().getTimesShown())
                .isEqualTo(1);

        assertThat(waitForOverlayToDisappear().isEmpty()).isTrue();
        assertThat(
                getTestService().getOnSystemBarVisibilityChangedInfo().getTimesHidden())
                .isEqualTo(1);
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

    @Test
    public void takeScreenshot_expectedScreenshotSaved() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(TAKE_SCREENSHOT_VERIFIER_PACKAGE_NAME);

        // Make sure that the overlay is shown so that assertions can be made to check that
        // the overlay is excluded from the game screenshot.
        getTestService().showOverlayForFocusedGameSession();
        final Rect overlayBounds = waitForTouchableOverlayBounds();

        long startTimeSecs = Instant.now().getEpochSecond();
        final boolean ret = getTestService().takeScreenshotForFocusedGameSession();

        // Make sure a screenshot was taken, saved in media, and has the same dimensions as the
        // device screen.
        assertTrue(ret);
        final Uri contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        final List<Uri> list = new ArrayList<>();
        try (Cursor cursor = mContentResolver.query(contentUri,
                new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.DATE_ADDED}, null, null,
                MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
            while (cursor.moveToNext()) {
                final long addedTimeSecs = cursor.getLong(2);
                // try to find the latest screenshot file created within 5s
                if (addedTimeSecs >= startTimeSecs && addedTimeSecs - startTimeSecs < 5) {
                    final long id = cursor.getLong(0);
                    final Uri screenshotUri = ContentUris.withAppendedId(contentUri, id);
                    final String name = cursor.getString(1);
                    Log.d(TAG, "Found screenshot with name " + name);
                    list.add(screenshotUri);
                    final ImageDecoder.Source source = ImageDecoder.createSource(mContentResolver,
                            screenshotUri);
                    // convert the hardware bitmap to a mutable 4-byte bitmap to get/compare pixel
                    final Bitmap gameScreenshot = ImageDecoder.decodeBitmap(source).copy(
                            Bitmap.Config.ARGB_8888, true);

                    final Size screenSize = getScreenSize();
                    assertThat(gameScreenshot.getWidth()).isEqualTo(screenSize.getWidth());
                    assertThat(gameScreenshot.getHeight()).isEqualTo(screenSize.getHeight());

                    // The test game is always fullscreen red. It is too expensive to verify that
                    // the entire bitmap is red, so spot check certain areas.

                    // 1. Make sure that the overlay is excluded from the screenshot by checking
                    // pixels within the overlay bounds:

                    // top-left of overlay bounds:
                    assertThat(
                            gameScreenshot.getPixel(overlayBounds.left + 1,
                                    overlayBounds.top + 1)).isEqualTo(
                            Color.RED);
                    // bottom-left corner of overlay bounds:
                    assertThat(gameScreenshot.getPixel(overlayBounds.left + 1,
                            overlayBounds.bottom - 1)).isEqualTo(Color.RED);
                    // top-right corner of overlay bounds:
                    assertThat(
                            gameScreenshot.getPixel(overlayBounds.right - 1,
                                    overlayBounds.top + 1)).isEqualTo(
                            Color.RED);
                    // bottom-right corner of overlay bounds:
                    assertThat(gameScreenshot.getPixel(overlayBounds.right - 1,
                            overlayBounds.bottom - 1)).isEqualTo(Color.RED);
                    // middle corner of overlay bounds:
                    assertThat(
                            gameScreenshot.getPixel((overlayBounds.left + overlayBounds.right) / 2,
                                    (overlayBounds.top + overlayBounds.bottom) / 2)).isEqualTo(
                            Color.RED);

                    // 2. Also check some pixels between the edge of the screen and the overlay
                    // bounds:

                    // above and to the left of the overlay
                    assertThat(
                            gameScreenshot.getPixel(overlayBounds.left / 2,
                                    overlayBounds.top / 2)).isEqualTo(
                            Color.RED);
                    // below and to the left of the overlay
                    assertThat(gameScreenshot.getPixel(overlayBounds.left / 2,
                            (overlayBounds.bottom + gameScreenshot.getHeight()) / 2)).isEqualTo(
                            Color.RED);
                    // above and to the right of the overlay
                    assertThat(gameScreenshot.getPixel(
                            (overlayBounds.left + gameScreenshot.getWidth()) / 2,
                            overlayBounds.top / 2)).isEqualTo(Color.RED);
                    // below and to the right of the overlay
                    assertThat(gameScreenshot.getPixel(
                            (overlayBounds.left + gameScreenshot.getWidth()) / 2,
                            (overlayBounds.bottom + gameScreenshot.getHeight()) / 2)).isEqualTo(
                            Color.RED);

                    // 3. Finally check some pixels at the corners of the screen:

                    // top-left corner of screen
                    assertThat(gameScreenshot.getPixel(0, 0)).isEqualTo(Color.RED);
                    // bottom-left corner of screen
                    assertThat(
                            gameScreenshot.getPixel(0, gameScreenshot.getHeight() - 1)).isEqualTo(
                            Color.RED);
                    // top-right corner of screen
                    assertThat(gameScreenshot.getPixel(gameScreenshot.getWidth() - 1, 0)).isEqualTo(
                            Color.RED);
                    // bottom-right corner of screen
                    assertThat(gameScreenshot.getPixel(gameScreenshot.getWidth() - 1,
                            gameScreenshot.getHeight() - 1)).isEqualTo(Color.RED);
                    final PendingIntent pi = MediaStore.createDeleteRequest(mContentResolver,
                            ImmutableList.of(screenshotUri));
                    final GetResultActivity.Result result = startIntentWithGrant(pi);
                    assertEquals(Activity.RESULT_OK, result.resultCode);
                }
            }
        }
        assertThat(list.size()).isGreaterThan(0);
    }

    private GetResultActivity.Result startIntentWithGrant(PendingIntent pi) throws Exception {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        final Intent intent = new Intent(inst.getContext(), GetResultActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        final UiDevice device = UiDevice.getInstance(inst);
        final GetResultActivity activity = (GetResultActivity) inst.startActivitySync(intent);
        inst.waitForIdleSync();
        activity.mResult.clear();
        device.waitForIdle();
        activity.startIntentSenderForResult(pi.getIntentSender(), 42, null, 0, 0, 0);
        device.waitForIdle();
        final UiSelector grant = new UiSelector().textMatches("(?i)Allow");
        final boolean grantExists = new UiObject(grant).waitForExists(5000);
        if (grantExists) {
            device.findObject(grant).click();
        }
        return activity.getResult();
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

    private static void swipeFromTopEdgeToShowSystemBars() {
        UiDevice uiDevice = UiAutomatorUtils.getUiDevice();
        uiDevice.swipe(
                uiDevice.getDisplayWidth() / 2, 20,
                uiDevice.getDisplayWidth() / 2, uiDevice.getDisplayHeight() / 2,
                10);
    }

    private void waitForGameServiceConnected() {
        PollingCheck.waitFor(() -> isGameServiceConnected(),
                "Timed out waiting for game service to connect");
    }

    private void waitForGameServiceDisconnected() {
        PollingCheck.waitFor(() -> !isGameServiceConnected(),
                "Timed out waiting for game service to disconnect");
    }

    private boolean isGameServiceConnected() {
        try {
            return getTestService().isGameServiceConnected();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    private Rect waitForTouchableOverlayBounds() {
        return PollingCheck.waitFor(
                5_000L,
                () -> {
                    try {
                        return getTestService().getTouchableOverlayBounds();
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    }
                },
                bounds -> !bounds.isEmpty());
    }

    private void assertOverlayDoesNotAppear() {
        assertThat(waitForTouchableOverlayBounds().isEmpty()).isTrue();
    }

    private Rect waitForOverlayToDisappear() {
        return PollingCheck.waitFor(
                20_000L,
                () -> {
                    try {
                        return getTestService().getTouchableOverlayBounds();
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    }
                },
                Rect::isEmpty);
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

    private static Size getScreenSize() {
        WindowManager wm =
                (WindowManager)
                        InstrumentationRegistry.getInstrumentation()
                                .getContext()
                                .getSystemService(Context.WINDOW_SERVICE);
        WindowMetrics windowMetrics = wm.getCurrentWindowMetrics();
        Rect windowBounds = windowMetrics.getBounds();
        return new Size(windowBounds.width(), windowBounds.height());
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
