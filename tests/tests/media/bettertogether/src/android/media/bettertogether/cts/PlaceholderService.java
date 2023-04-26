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

package android.media.bettertogether.cts;

import android.app.Service;
import android.content.Intent;
import android.media.MediaRouter2;
import android.media.RouteListingPreference;
import android.os.IBinder;

import androidx.annotation.Nullable;

/**
 * Service to test checks in {@link MediaRouter2#setRouteListingPreference} when it comes to {@link
 * RouteListingPreference#getLinkedItemComponentName()}.
 */
public class PlaceholderService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
