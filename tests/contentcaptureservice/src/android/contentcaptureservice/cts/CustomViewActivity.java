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
package android.contentcaptureservice.cts;

import static android.contentcaptureservice.cts.Assertions.assertRightActivity;
import static android.contentcaptureservice.cts.Assertions.assertViewWithUnknownParentAppeared;

import static com.google.common.truth.Truth.assertThat;

import android.contentcaptureservice.cts.CtsContentCaptureService.Session;
import android.contentcaptureservice.cts.common.DoubleVisitor;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewStructure;
import android.view.contentcapture.ContentCaptureEvent;

import androidx.annotation.NonNull;

import java.util.List;

public class CustomViewActivity extends AbstractContentCaptureActivity {

    private static final String TAG = CustomViewActivity.class.getSimpleName();

    private static DoubleVisitor<CustomView, ViewStructure> sCustomViewDelegate;

    CustomView mCustomView;

    /**
     * Sets a delegate that provides the behavior of
     * {@link CustomView#onProvideContentCaptureStructure(ViewStructure, int)}.
     */
    static void setCustomViewDelegate(@NonNull DoubleVisitor<CustomView, ViewStructure> delegate) {
        sCustomViewDelegate = delegate;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.custom_view_activity);
        mCustomView = findViewById(R.id.custom_view);
        Log.d(TAG, "onCreate(): custom view id is " + mCustomView.getAutofillId());
        if (sCustomViewDelegate != null) {
            Log.d(TAG, "Setting delegate on " + mCustomView);
            mCustomView.setContentCaptureDelegate(
                    (structure) -> sCustomViewDelegate.visit(mCustomView, structure));
        }
    }

    @Override
    public void assertDefaultEvents(@NonNull Session session) {
        assertRightActivity(session, session.id, this);

        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(TAG, "events: " + events);
        // TODO(b/119638528): check right number once we get rid of grandparent
        assertThat(events.size()).isAtLeast(1);

        // Assert just the relevant events
        assertViewWithUnknownParentAppeared(events, 0, session.id, mCustomView);

        // TODO(b/122315042): assert views disappeared
    }
}
