/*
 * Copyright 2022 The Android Open Source Project
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
package android.media.codec.cts;

import android.app.Activity;
import android.content.Intent;
import android.os.Process;

public class MediaCodecResourceTestHighPriorityActivity extends Activity {
    public static final String ACTION_HIGH_PRIORITY_ACTIVITY_READY =
            "android.media.codec.cts.HIGH_PRIORITY_TEST_ACTIVITY_READY";

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent();
        intent.setAction(ACTION_HIGH_PRIORITY_ACTIVITY_READY);
        intent.putExtra("pid", Process.myPid());
        intent.putExtra("uid", Process.myUid());
        sendBroadcast(intent);
    }
}
