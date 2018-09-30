/*
 * Copyright 2018 The Android Open Source Project
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

package android.app.stubs;

import android.annotation.Nullable;
import android.app.Activity;
import android.os.Bundle;
import android.util.Pair;

import java.util.ArrayList;

public class ActivityCallbacksTestActivity extends Activity {

    public enum Event {
        ON_PRE_CREATE,
        ON_CREATE,
        ON_POST_CREATE,

        ON_PRE_START,
        ON_START,
        ON_POST_START,

        ON_PRE_RESUME,
        ON_RESUME,
        ON_POST_RESUME,

        ON_PRE_PAUSE,
        ON_PAUSE,
        ON_POST_PAUSE,

        ON_PRE_STOP,
        ON_STOP,
        ON_POST_STOP,

        ON_PRE_DESTROY,
        ON_DESTROY,
        ON_POST_DESTROY,
    }

    public enum Source {
        ACTIVITY,
        ACTIVITY_CALLBACK
    }

    private ArrayList<Pair<Source, Event>> mCollectedEvents = new ArrayList<>();

    public void collectEvent(Source source, Event event) {
        mCollectedEvents.add(new Pair<>(source, event));
    }

    public ArrayList<Pair<Source, Event>> getCollectedEvents() {
        return mCollectedEvents;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        collectEvent(Source.ACTIVITY, Event.ON_PRE_CREATE);
        super.onCreate(savedInstanceState);
        collectEvent(Source.ACTIVITY, Event.ON_POST_CREATE);
    }

    @Override
    protected void onStart() {
        collectEvent(Source.ACTIVITY, Event.ON_PRE_START);
        super.onStart();
        collectEvent(Source.ACTIVITY, Event.ON_POST_START);
    }

    @Override
    protected void onResume() {
        collectEvent(Source.ACTIVITY, Event.ON_PRE_RESUME);
        super.onResume();
        collectEvent(Source.ACTIVITY, Event.ON_POST_RESUME);
    }

    @Override
    protected void onPause() {
        collectEvent(Source.ACTIVITY, Event.ON_PRE_PAUSE);
        super.onPause();
        collectEvent(Source.ACTIVITY, Event.ON_POST_PAUSE);
    }

    @Override
    protected void onStop() {
        collectEvent(Source.ACTIVITY, Event.ON_PRE_STOP);
        super.onStop();
        collectEvent(Source.ACTIVITY, Event.ON_POST_STOP);
    }

    @Override
    protected void onDestroy() {
        collectEvent(Source.ACTIVITY, Event.ON_PRE_DESTROY);
        super.onDestroy();
        collectEvent(Source.ACTIVITY, Event.ON_POST_DESTROY);
    }
}
