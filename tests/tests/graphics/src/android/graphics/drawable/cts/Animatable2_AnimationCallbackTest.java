/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.graphics.drawable.cts;

import junit.framework.TestCase;

import android.graphics.drawable.Animatable2.AnimationCallback;
import android.support.test.filters.SmallTest;

@SmallTest
public class Animatable2_AnimationCallbackTest extends TestCase {

    public void testCallback() {
        // These are no-op methods. Just make sure they don't crash.
        AnimationCallback callback = new AnimationCallback() {};
        callback.onAnimationStart(null);
        callback.onAnimationEnd(null);
    }
}
