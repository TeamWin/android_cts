/*
 * Copyright (C) 2009 The Android Open Source Project
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
package android.webkit.cts;

import android.webkit.ConsoleMessage;
import android.webkit.cts.WebViewSyncLoader.WaitForProgressClient;

import com.google.common.util.concurrent.SettableFuture;

// A chrome client for listening webview chrome events.
class ChromeClient extends WaitForProgressClient {
    private final SettableFuture<ConsoleMessage.MessageLevel> mMessageLevel =
            SettableFuture.create();

    public ChromeClient(WebViewOnUiThread onUiThread) {
        super(onUiThread);
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage message) {
        mMessageLevel.set(message.messageLevel());
        // return false for default handling; i.e. printing the message.
        return false;
    }

    public ConsoleMessage.MessageLevel getMessageLevel() {
        return WebkitUtils.waitForFuture(mMessageLevel);
    }
}
