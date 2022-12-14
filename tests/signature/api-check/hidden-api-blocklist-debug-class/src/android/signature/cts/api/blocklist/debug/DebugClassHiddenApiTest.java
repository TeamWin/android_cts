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

package android.signature.cts.api.blocklist.debug;

import android.signature.cts.DexMemberChecker;

import static org.junit.Assert.assertFalse;

public class DebugClassHiddenApiTest extends android.signature.cts.api.HiddenApiTest {
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Try to exempt DexMemberChecker class from hidden API checks.
        // This should fail as this process is not debuggable.
        assertFalse(DexMemberChecker.requestExemptionFromHiddenApiChecks());
    }
}
