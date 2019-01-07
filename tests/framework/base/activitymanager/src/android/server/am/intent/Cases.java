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

package android.server.am.intent;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP;
import static android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT;
import static android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.server.am.intent.Persistence.flag;

import android.server.am.intent.Persistence.IntentFlag;

import com.google.common.collect.Lists;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains all information to create and reuse intent test launches.
 * It enumerates all the flags so they can easily be used.
 *
 * It also stores commonly used {@link LaunchSequence} objects for reuse.
 */
public class Cases {

    public static final IntentFlag CLEAR_TASK = flag(FLAG_ACTIVITY_CLEAR_TASK,
            "FLAG_ACTIVITY_CLEAR_TASK");
    public static final IntentFlag CLEAR_TOP = flag(FLAG_ACTIVITY_CLEAR_TOP,
            "FLAG_ACTIVITY_CLEAR_TOP");
    private static final IntentFlag SINGLE_TOP = flag(FLAG_ACTIVITY_SINGLE_TOP,
            "FLAG_ACTIVITY_SINGLE_TOP");
    public static final IntentFlag NEW_TASK = flag(FLAG_ACTIVITY_NEW_TASK,
            "FLAG_ACTIVITY_NEW_TASK");
    public static final IntentFlag NEW_DOCUMENT = flag(FLAG_ACTIVITY_NEW_DOCUMENT,
            "FLAG_ACTIVITY_NEW_DOCUMENT");
    private static final IntentFlag MULTIPLE_TASK = flag(FLAG_ACTIVITY_MULTIPLE_TASK,
            "FLAG_ACTIVITY_MULTIPLE_TASK");
    public static final IntentFlag RESET_TASK_IF_NEEDED = flag(
            FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            "FLAG_ACTIVITY_RESET_TASK_IF_NEEDED");
    public static final IntentFlag PREVIOUS_IS_TOP = flag(FLAG_ACTIVITY_PREVIOUS_IS_TOP,
            "FLAG_ACTIVITY_PREVIOUS_IS_TOP");
    public static final IntentFlag REORDER_TO_FRONT = flag(FLAG_ACTIVITY_REORDER_TO_FRONT,
            "FLAG_ACTIVITY_REORDER_TO_FRONT");

    // Flag only used for parsing intents that contain no flags.
    private static final IntentFlag none = flag(0, "");

    public List<IntentFlag> flags = Lists.newArrayList(
            CLEAR_TASK,
            CLEAR_TOP,
            SINGLE_TOP,
            NEW_TASK,
            NEW_DOCUMENT,
            MULTIPLE_TASK,
            RESET_TASK_IF_NEEDED,
            PREVIOUS_IS_TOP,
            REORDER_TO_FRONT
    );

    /**
     * The human readable flags in the JSON files need to be converted back to the corresponding
     * IntentFlag object when reading the file. This creates a map from the flags to their
     * corresponding object.
     *
     * @return lookup table for the parsing of intent flags in the json files.
     */
    public Map<String, IntentFlag> createFlagParsingTable() {
        HashMap<String, IntentFlag> flags = new HashMap<>();
        for (IntentFlag flag : this.flags) {
            flags.put(flag.name, flag);
        }

        flags.put(none.getName(), none);
        return flags;
    }
}

