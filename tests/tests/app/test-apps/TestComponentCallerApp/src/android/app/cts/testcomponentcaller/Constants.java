/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app.cts.testcomponentcaller;

import android.content.ComponentName;

/** Constants shared with {@link android.app.cts.ComponentCallerTest} */
public class Constants {
    public static final String HELPER_APP_PACKAGE = "android.app.cts.testcomponentcaller";
    public static final ComponentName HELPER_APP_INITIAL_CALLER_ACTIVITY = new ComponentName(
            HELPER_APP_PACKAGE, HELPER_APP_PACKAGE + ".TestInitialCallerActivity");

    private static final String TEST_PACKAGE = "android.app.cts";
    public static final ComponentName TEST_RECEIVER = new ComponentName(TEST_PACKAGE,
            TEST_PACKAGE + ".ComponentCallerTest$TestReceiver");
    public static final ComponentName TEST_INITIAL_CALLER_ACTIVITY = new ComponentName(TEST_PACKAGE,
            TEST_PACKAGE + ".ComponentCallerTest$InitialCallerTestActivity");

    public static final String TEST_RECEIVER_ACTION = "android.app.cts.ACTIVITY_CALLER_ACTION";

    public static final String URI_LOCATION_ID = "uriLocationId";
    public static final int NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID = 0;
    public static final int URI_IN_DATA_LOCATION_ID = 1;
    public static final int URI_IN_CLIP_DATA_LOCATION_ID = 2;

    public static final String ACTION_ID = "actionId";
    public static final int START_TEST_ACTIVITY_ACTION_ID = 0;
    public static final int SEND_TEST_BROADCAST_ACTION_ID = 1;

    public static final String MODE_FLAGS_TO_CHECK = "modeFlags";

    public static final String EXTRA_SECURITY_EXCEPTION_CAUGHT = "securityExceptionCaught";
    public static final String EXTRA_ILLEGAL_ARG_EXCEPTION_CAUGHT =
            "illegalArgumentExceptionCaught";
    public static final String EXTRA_CHECK_CONTENT_URI_PERMISSION_RESULT = "permissionResult";
    public static final int INVALID_PERMISSION_RESULT = -2;
}
