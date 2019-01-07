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

import android.content.ComponentName;
import android.server.am.ActivityManagerTestBase;
import android.server.am.UiDeviceUtils;

import org.junit.After;

import java.util.List;

public abstract class IntentTestBase extends ActivityManagerTestBase {

    /**
     * This get's called from {@link IntentGenerationTests} because {@link IntentGenerationTests}
     * run multiple {@link android.server.am.intent.Persistence.TestCase}-s in a single junit test.
     *
     * Normal test classes can just extend this class with manual cleanUp.
     *
     * @param activitiesInUsedInTest activities that should be gone after tearDown
     */
    public void cleanUp(List<ComponentName> activitiesInUsedInTest) throws Exception {
        super.tearDown();
        UiDeviceUtils.pressHomeButton();
        removeStacksWithActivityTypes(ALL_ACTIVITY_TYPE_BUT_HOME);

        this.getAmWmState().waitForWithAmState(
                state -> state.containsNoneOf(activitiesInUsedInTest),
                "Waiting for activity to be removed");
    }

    @After
    @Override
    public void tearDown() throws Exception {
        cleanUp(activitiesUsedInTest());
    }

    /**
     * @return The activities that are launched in this Test.
     */
    abstract List<ComponentName> activitiesUsedInTest();
}
