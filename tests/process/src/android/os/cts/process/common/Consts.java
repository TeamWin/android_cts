/*
 * Copyright 2021 The Android Open Source Project
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
package android.os.cts.process.common;

import android.content.ComponentName;
import android.os.cts.process.helper.MyReceiver;
import android.os.cts.process.helper.MyReceiver2;

public class Consts {
    private Consts() {
    }

    public static final String TAG = "CtsProcessTest";

    public static final String PACKAGE_NAME_HELPER_1 = "android.os.cts.process.helper1";
    public static final ComponentName RECEIVER_HELPER_1 = new ComponentName(
            PACKAGE_NAME_HELPER_1, MyReceiver.class.getName());
    public static final ComponentName RECEIVER2_HELPER_1 = new ComponentName(
            PACKAGE_NAME_HELPER_1, MyReceiver2.class.getName());

    public static final String ACTION_SEND_BACK_START_TIME = "ACTION_SEND_BACK_START_TIME";
}
