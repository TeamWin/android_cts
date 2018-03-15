/*
 * Copyright 2018 The Android Open Source Project
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

package android.media.cts;

import android.content.Context;
import android.media.MediaPlaylistAgent;

/**
 * A mock implementation of {@link MediaPlaylistAgent} for testing.
 * <p>
 * Ideally Mockito should work without this class, but it hass following issues.
 *   - Creating mock instance with Mockito.mock() fails because it doesn't initialize provider
 *     and null provider will cause NPE.
 *   - Creating spy instance with Mockito.spy() fails because spy() doesn't work for inner class.
 */
public class MockPlaylistAgent extends MediaPlaylistAgent {
    public MockPlaylistAgent(Context context) {
        super(context);
    }
}