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

package android.gamemanager.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.app.GameManager;
import android.app.GameModeInfo;
import android.app.GameState;
import android.app.Instrumentation;
import android.content.Context;
import android.support.test.uiautomator.UiDevice;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class GameManagerTest {
    private static final String TAG = "GameManagerTest";
    private static final String GAME_OVERLAY_FEATURE_NAME =
            "com.google.android.feature.GAME_OVERLAY";
    private static final String POWER_DUMPSYS_CMD = "dumpsys android.hardware.power.IPower/default";
    private static final Pattern GAME_LOADING_REGEX =
            Pattern.compile("^GAME_LOADING\\t(\\d*)\\t\\d*$", Pattern.MULTILINE);

    private GameManagerCtsActivity mActivity;
    private Context mContext;
    private GameManager mGameManager;
    private UiDevice mUiDevice;

    @Rule
    public ActivityScenarioRule<GameManagerCtsActivity> mActivityRule =
            new ActivityScenarioRule<>(GameManagerCtsActivity.class);

    @Before
    public void setUp() {
        mActivityRule.getScenario().onActivity(activity -> {
            mActivity = activity;
        });

        final Instrumentation instrumentation = getInstrumentation();
        mContext = instrumentation.getContext();
        mGameManager = mContext.getSystemService(GameManager.class);
        mUiDevice = UiDevice.getInstance(instrumentation);
    }

    /**
     * Test that GameManager::getGameMode() returns the correct value when forcing the Game Mode to
     * GAME_MODE_UNSUPPORTED.
     */
    @Test
    public void testGetGameModeUnsupported() {
        assumeTrue(InstrumentationRegistry.getContext().getPackageManager()
                .hasSystemFeature(GAME_OVERLAY_FEATURE_NAME));

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(mActivity.getPackageName(),
                        GameManager.GAME_MODE_UNSUPPORTED));

        int gameMode = mActivity.getGameMode();

        assertEquals("Game Manager returned incorrect value.",
                GameManager.GAME_MODE_UNSUPPORTED, gameMode);
    }

    /**
     * Test that GameManager::getGameMode() returns the correct value when forcing the Game Mode to
     * GAME_MODE_STANDARD.
     */
    @Test
    public void testGetGameModeStandard() {
        assumeTrue(InstrumentationRegistry.getContext().getPackageManager()
                .hasSystemFeature(GAME_OVERLAY_FEATURE_NAME));

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(mActivity.getPackageName(),
                        GameManager.GAME_MODE_STANDARD));

        int gameMode = mActivity.getGameMode();

        assertEquals("Game Manager returned incorrect value.",
                GameManager.GAME_MODE_STANDARD, gameMode);
    }

    /**
     * Test that GameManager::getGameMode() returns the correct value when forcing the Game Mode to
     * GAME_MODE_PERFORMANCE.
     */
    @Test
    public void testGetGameModePerformance() {
        assumeTrue(InstrumentationRegistry.getContext().getPackageManager()
                .hasSystemFeature(GAME_OVERLAY_FEATURE_NAME));

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(mActivity.getPackageName(),
                        GameManager.GAME_MODE_PERFORMANCE));

        int gameMode = mActivity.getGameMode();

        assertEquals("Game Manager returned incorrect value.",
                GameManager.GAME_MODE_PERFORMANCE, gameMode);
    }

    /**
     * Test that GameManager::getGameMode() returns the correct value when forcing the Game Mode to
     * GAME_MODE_BATTERY.
     */
    @Test
    public void testGetGameModeBattery() {
        assumeTrue(InstrumentationRegistry.getContext().getPackageManager()
                .hasSystemFeature(GAME_OVERLAY_FEATURE_NAME));

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(mActivity.getPackageName(),
                        GameManager.GAME_MODE_BATTERY));

        int gameMode = mActivity.getGameMode();

        assertEquals("Game Manager returned incorrect value.",
                GameManager.GAME_MODE_BATTERY, gameMode);
    }

    private int getGameLoadingCount() throws IOException {
        final Matcher matcher =
                GAME_LOADING_REGEX.matcher(mUiDevice.executeShellCommand(POWER_DUMPSYS_CMD));
        assumeTrue(matcher.find());
        return Integer.parseInt(matcher.group(1));
    }

    /**
     * Test that GameManager::setGameContext() with an 'isLoading' context does not invokes the mode
     * on the PowerHAL when performance mode is not invoked.
     */
    @Test
    public void testSetGameContextStandardMode() throws IOException, InterruptedException {
        final int gameLoadingCountBefore = getGameLoadingCount();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(mActivity.getPackageName(),
                GameManager.GAME_MODE_STANDARD));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager, (gameManager) ->
                gameManager.setGameState(new GameState(true, GameState.MODE_NONE)));
        Thread.sleep(500);  // Wait for change to take effect.
        assertEquals(gameLoadingCountBefore, getGameLoadingCount());
    }

    /**
     * Test that GameManager::setGameContext() with an 'isLoading' context actually invokes the mode
     * on the PowerHAL when performance mode is invoked.
     */
    @Test
    public void testSetGameContextPerformanceMode() throws IOException, InterruptedException {
        final int gameLoadingCountBefore = getGameLoadingCount();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(mActivity.getPackageName(),
                GameManager.GAME_MODE_PERFORMANCE));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager, (gameManager) ->
                gameManager.setGameState(new GameState(true, GameState.MODE_NONE)));
        Thread.sleep(500);  // Wait for change to take effect.
        assertEquals(gameLoadingCountBefore + 1, getGameLoadingCount());
    }

    /**
     * Test that GameManager::getGameModeInfo() returns correct values for a game.
     */
    @Test
    public void testGetGameModeInfo() {
        assumeTrue(InstrumentationRegistry.getContext().getPackageManager()
                .hasSystemFeature(GAME_OVERLAY_FEATURE_NAME));

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(mActivity.getPackageName(),
                        GameManager.GAME_MODE_BATTERY));

        GameModeInfo gameModeInfo = mGameManager.getGameModeInfo(mActivity.getPackageName());
        assertEquals("GameManager#getGameModeInfo returned incorrect available game modes.",
                3, gameModeInfo.getAvailableGameModes().length);
        assertEquals("GameManager#getGameModeInfo returned incorrect active game mode.",
                GameManager.GAME_MODE_BATTERY, gameModeInfo.getActiveGameMode());
    }
}
