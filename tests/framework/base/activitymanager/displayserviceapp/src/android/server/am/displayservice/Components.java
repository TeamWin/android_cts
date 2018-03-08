/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.server.am.displayservice;

import android.content.ComponentName;
import android.server.am.component.ComponentsBase;

public class Components extends ComponentsBase {

    public static final ComponentName VIRTUAL_DISPLAY_SERVICE = component(
            Components.class, "VirtualDisplayService");

    /**
     * Constants for {@link android.server.am.displayservice.VirtualDisplayService}.
     */
    public static final class VirtualDisplayService {
        public static final String VIRTUAL_DISPLAY_NAME = "CtsVirtualDisplay";
        // String extra key for command. Value should be one of COMMAND_* below.
        public static final String EXTRA_COMMAND = "command";
        // Boolean extra key to show keyguard on the display.
        public static final String EXTRA_SHOW_CONTENT_WHEN_LOCKED = "show_content_when_locked";
        // Extra values for {@link #EXTRA_COMMAND}.
        public static final String COMMAND_CREATE = "create";
        public static final String COMMAND_DESTROY = "destroy";
        public static final String COMMAND_OFF = "off";
        public static final String COMMAND_ON = "on";
    }
}
