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

package android.server.wm.overlay;

import android.content.ComponentName;
import android.os.Binder;
import android.server.wm.component.ComponentsBase;

public class Components extends ComponentsBase {
    public interface ActionReceiver {
        ComponentName COMPONENT = component("ActionReceiver");
        String ACTION_OVERLAY = "overlay";
        String ACTION_PING = "ping";
        String EXTRA_NAME = "name";
        String EXTRA_OPACITY = "opacity";
        String EXTRA_CALLBACK = "callback";
        int CALLBACK_PONG = Binder.FIRST_CALL_TRANSACTION;
    }

    public interface OverlayActivity {
        ComponentName COMPONENT = component("OverlayActivity");
        String EXTRA_NAME = "name";
        String EXTRA_OPACITY = "opacity";
    }

    public interface ToastActivity {
        ComponentName COMPONENT = component("ToastActivity");
        String EXTRA_CUSTOM = "custom";
    }

    private static ComponentName component(String className) {
        return component(Components.class, className);
    }
}
