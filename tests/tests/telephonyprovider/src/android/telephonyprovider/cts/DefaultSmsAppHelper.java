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

package android.telephonyprovider.cts;

import android.support.test.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

class DefaultSmsAppHelper {
    static void setDefaultSmsApp(boolean setToSmsApp) {
        String packageName =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageName();
        String command = String.format(
                "appops set %s WRITE_SMS %s", packageName, setToSmsApp ? "allow" : "default");
        SystemUtil.runShellCommand(command);
    }
}
