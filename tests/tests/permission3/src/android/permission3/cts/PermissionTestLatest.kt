/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.permission3.cts

import android.os.Build
import org.junit.Assert
import org.junit.Test

class PermissionTestLatest : BaseUsePermissionTest() {
    /**
     * Not exactly a cts type test but it needs to be run continuously. This test is supposed to
     * start failing once the sdk integer is decided for T. This is important to have because it was
     * assumed that the sdk int will be 33 in frameworks/base/data/etc/platform.xml. This test
     * should be removed once the sdk is finalized.
     */
    @Test
    fun testTApiVersionCodeIsNotSet() {
        Assert.assertEquals(Build.VERSION_CODES.S_V2, Build.VERSION.SDK_INT)
    }
}