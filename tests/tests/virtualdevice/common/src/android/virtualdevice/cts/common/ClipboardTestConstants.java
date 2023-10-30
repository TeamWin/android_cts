/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.virtualdevice.cts.common;

public class ClipboardTestConstants {
    // The test activity should attempt to write a ClipData to the clipboard.
    public static final String ACTION_WRITE = "writeClipboard";

    // The test activity should attempt to read the clipboard contents and send them back.
    public static final String ACTION_READ = "readClipboard";

    // A parecelable containing a ResultReceiver used to send a result back to the caller.
    public static final String EXTRA_RESULT_RECEIVER = "resultReceiver";

    // A boolean used to return the result of hasPrimaryClip()
    public static final String EXTRA_HAS_CLIP_DATA = "hasClipData";

    // A parcelable containing ClipData that was written or read from the clipboard.
    public static final String EXTRA_CLIP_DATA = "clipData";

    // The id of the device whose clipboard to access.
    public static final String EXTRA_DEVICE_ID = "deviceId";
}