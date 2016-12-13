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
package android.transition.cts;

import static org.mockito.Mockito.mock;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.transition.Transition.TransitionListener;

import com.android.compatibility.common.util.transition.TrackingTransition;
import com.android.compatibility.common.util.transition.TrackingVisibility;

public class TargetActivity extends Activity {
    public static final String EXTRA_LAYOUT_ID = "layoutId";

    final TrackingVisibility enterTransition = new TrackingVisibility();
    final TrackingVisibility returnTransition = new TrackingVisibility();
    final TrackingTransition sharedElementEnterTransition = new TrackingTransition();
    final TrackingTransition sharedElementReturnTransition = new TrackingTransition();

    final TransitionListener enterListener = mock(TransitionListener.class);
    final TransitionListener returnListener = mock(TransitionListener.class);

    public static TargetActivity sLastCreated;

    @Override
    public void onCreate(Bundle bundle){
        super.onCreate(bundle);
        Intent intent = getIntent();
        int layoutId = R.layout.transition_main;
        if (intent != null) {
            layoutId = intent.getIntExtra(EXTRA_LAYOUT_ID, layoutId);
        }
        setContentView(layoutId);
        getWindow().setEnterTransition(enterTransition);
        getWindow().setReturnTransition(returnTransition);
        getWindow().setSharedElementEnterTransition(sharedElementEnterTransition);
        getWindow().setSharedElementReturnTransition(sharedElementReturnTransition);
        enterTransition.addListener(enterListener);
        returnTransition.addListener(returnListener);

        sLastCreated = this;
    }
}
