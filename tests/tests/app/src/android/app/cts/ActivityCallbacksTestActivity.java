/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package android.app.cts;

import android.annotation.Nullable;
import android.app.Activity;
import android.os.Bundle;
import android.util.Pair;

import java.util.ArrayList;

public class ActivityCallbacksTestActivity extends Activity {

    enum Event {
        ON_PRE_CREATE,
        ON_CREATE,
        ON_START,
        ON_RESUME,
        ON_PAUSE,
        ON_STOP,
        ON_DESTROY,
    }

    enum Source {
        ACTIVITY,
        ACTIVITY_CALLBACK
    }

    private ArrayList<Pair<Source, Event>> mCollectedEvents = new ArrayList<>();

    void collectEvent(Source source, Event event) {
        mCollectedEvents.add(new Pair<>(source, event));
    }

    ArrayList<Pair<Source, Event>> getCollectedEvents() {
        return mCollectedEvents;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        collectEvent(Source.ACTIVITY, Event.ON_CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        collectEvent(Source.ACTIVITY, Event.ON_START);
    }

    @Override
    protected void onResume() {
        super.onResume();
        collectEvent(Source.ACTIVITY, Event.ON_RESUME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        collectEvent(Source.ACTIVITY, Event.ON_PAUSE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        collectEvent(Source.ACTIVITY, Event.ON_STOP);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        collectEvent(Source.ACTIVITY, Event.ON_DESTROY);
    }
}
