/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.view.inputmethod.cts.installtests.common;


/**
 * Utility class for preparing "adb shell" command.
 */
public final class ShellCommandUtils {

    // This is utility class, can't instantiate.
    private ShellCommandUtils() {
    }
    private static final String SETTING_DEFAULT_IME = "secure default_input_method";

    /** Command to get ID of current IME. */
    public static String removeUser(int userId) {
        return "cmd package remove-user " + userId;
    }

    /** Command to get ID of current IME. */
    public static String getCurrentIme(int userId) {
        return String.format("settings --user %d get %s", userId, SETTING_DEFAULT_IME);
    }

    /** Command to get ID of current IME. */
    public static String setStylusHandwritingEnabled(int userId, boolean enabled) {
        return String.format("settings put --user %d secure stylus_handwriting_enabled %d", userId,
                enabled ? 1 : 0);
    }

    /** Command to set current IME to {@code imeId} synchronously for the specified {@code user} */
    public static String setCurrentImeSync(String imeId, int userId) {
        return String.format("ime set --user %d %s", userId, imeId);
    }

    public static String getAvailableImes(int userId) {
        return String.format("ime list -s -a --user %d", userId);
    }

    /** Command to enable IME of {@code imeId} for the specified {@code userId}. */
    public static String enableIme(String imeId, int userId) {
        return String.format("ime enable --user %d %s", userId, imeId);
    }

    /** Command to reset currently selected/enabled IMEs to the default ones. */
    public static String resetImes() {
        return "ime reset";
    }

    /** Command to reset currently selected/enabled IMEs to the default ones for all the users. */
    public static String resetImesForAllUsers() {
        return "ime reset --user all";
    }

    public static String installPackageAsUser(String apkPath, int userId) {
        return "pm install -r --user " + userId + " " + apkPath;
    }

    public static String installExistingPackageAsUser(String packageName, int userId,
            boolean instant) {
        return "pm install-existing --wait --user " + userId + (instant ? " --instant "
                : " --full ") + packageName;
    }

    public static String uninstallPackage(String packageName) {
        return "pm uninstall " + packageName;
    }

    /**
     * Command to uninstall {@code packageName} only for {@code userId}.
     *
     * @param packageName package name of the package to be uninstalled.
     * @param userId      user ID to specify the user.
     */
    public static String uninstallPackage(String packageName, int userId) {
        return "pm uninstall --user " + userId + " " + packageName;
    }

    /**
     * Command to get the last user ID that is specified to
     * InputMethodManagerService.Lifecycle#onUserSwitching().
     *
     * @return the command to be passed to shell command.
     */
    public static String getLastSwitchUserId() {
        return "cmd input_method get-last-switch-user-id";
    }

    /**
     * Command to start the user
     *
     * @return the command to be passed to shell command.
     */
    public static String startUser(int userId) {
        return "am start-user -w " + userId;
    }

    /** Command to switch the user */
    public static String switchToUserId(int userId) {
        return "am switch-user -w " + userId;
    }

    /** Command to turn on the display (if it's sleeping). */
    public static String wakeUp() {
        return "input keyevent KEYCODE_WAKEUP";
    }

    /** Command to dismiss Keyguard (if it's shown) */
    public static String dismissKeyguard() {
        return "wm dismiss-keyguard";
    }

    /** Command to close system dialogs (if shown) */
    public static String closeSystemDialog() {
        return "am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS";
    }

    /** Command to wait until all broadcast queues have passed barrier. */
    public static String waitForBroadcastBarrier() {
        return "am wait-for-broadcast-barrier";
    }

}
