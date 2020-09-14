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

package android.cts.tagging.support;

import android.app.Activity;
import android.cts.tagging.Utils;
import android.os.Bundle;
import android.util.Log;

public class DeviceKernelHelpers extends Activity {
    private static final String TAG = DeviceKernelHelpers.class.getSimpleName();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (Utils.kernelSupportsTaggedPointers()) {
            Log.i(TAG, "Kernel supports tagged pointers: true");
        } else {
            Log.i(TAG, "Kernel supports tagged pointers: false");
        }
    }
}
