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

package android.dnd.cts.droptarget;

import android.app.Activity;
import android.content.ClipData;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.DropPermissions;
import android.view.View;
import android.widget.TextView;

public class DropTarget extends Activity {

    private static final String MAGIC_VALUE = "42";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = getLayoutInflater().inflate(R.layout.main_activity, null);
        setContentView(view);

        setUpDropTarget(R.id.dont_request, false);
        setUpDropTarget(R.id.do_request, true);
    }

    private void setUpDropTarget(final int targetResourceId,
                                 final boolean requestPermissions) {
        findViewById(targetResourceId).setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent event) {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return true;

                    case DragEvent.ACTION_DRAG_ENTERED:
                        return true;

                    case DragEvent.ACTION_DRAG_LOCATION:
                        return true;

                    case DragEvent.ACTION_DRAG_EXITED:
                        return true;

                    case DragEvent.ACTION_DROP:
                        String result;
                        try {
                            result = processDrop(event, requestPermissions);
                        } catch (Exception e) {
                            result = "Exception";
                            ((TextView) findViewById(R.id.details)).setText(e.getMessage());
                        }
                        ((TextView) findViewById(R.id.result)).setText(result);
                        return true;

                    case DragEvent.ACTION_DRAG_ENDED:
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    private String processDrop(DragEvent event, boolean requestPermissions) {
        final ClipData clipData = event.getClipData();
        if (clipData == null) {
           return "Null ClipData";
        }
        if (clipData.getItemCount() == 0) {
           return "Empty ClipData";
        }
        ClipData.Item item = clipData.getItemAt(0);
        if (item == null) {
            return "Null ClipData.Item";
        }
        Uri uri = item.getUri();
        if (uri == null) {
            return "Null Uri";
        }

        DropPermissions dp = null;
        if (requestPermissions) {
            dp = requestDropPermissions(event);
            if (dp == null) {
                return "Null DropPermissions";
            }
        }

        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor == null) {
                return "Null Cursor";
            }
            cursor.moveToPosition(0);
            String value = cursor.getString(0);
            if (!MAGIC_VALUE.equals(value)) {
                return "Wrong value: " + value;
            }
            return "OK";
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (dp != null) {
                dp.release();
            }
        }
    }
}
