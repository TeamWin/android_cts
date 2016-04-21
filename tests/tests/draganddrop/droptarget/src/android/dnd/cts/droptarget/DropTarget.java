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
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.DragAndDropPermissions;
import android.view.DragEvent;
import android.view.View;
import android.widget.TextView;

public class DropTarget extends Activity {

    private static final String MAGIC_VALUE = "42";
    public static final String RESULT_OK = "OK";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = getLayoutInflater().inflate(R.layout.main_activity, null);
        setContentView(view);

        setUpDropTarget(R.id.dont_request, new OnDragUriReadListener(false));
        setUpDropTarget(R.id.request_read, new OnDragUriReadListener());
        setUpDropTarget(R.id.request_write, new OnDragUriWriteListener());
        setUpDropTarget(R.id.request_read_nested, new OnDragUriReadPrefixListener());
        setUpDropTarget(R.id.request_take_persistable, new OnDragUriTakePersistableListener());
    }

    private void setUpDropTarget(final int targetResourceId, OnDragUriListener listener) {
        findViewById(targetResourceId).setOnDragListener(listener);
    }

    private String checkExtraValue(DragEvent event) {
        PersistableBundle extras = event.getClipDescription().getExtras();
        if (extras == null) {
            return "Null";
        }

        final String value = extras.getString("extraKey");
        if ("extraValue".equals(value)) {
            return RESULT_OK;
        }
        return value;
    }

    private abstract class OnDragUriListener implements View.OnDragListener {
        private final boolean requestPermissions;

        public OnDragUriListener(boolean requestPermissions) {
            this.requestPermissions = requestPermissions;
        }

        @Override
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    ((TextView) findViewById(R.id.drag_started)).setText("DRAG_STARTED");
                    ((TextView) findViewById(R.id.extra_value)).setText(checkExtraValue(event));
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

            DragAndDropPermissions permissions = null;
            if (requestPermissions) {
                permissions = requestDragAndDropPermissions(event);
                if (permissions == null) {
                    return "Null DragAndDropPermissions";
                }
            }

            try {
                return processUri(uri);
            } finally {
                if (permissions != null) {
                    permissions.release();
                }
            }
        }

        abstract protected String processUri(Uri uri);
    }

    private class OnDragUriReadListener extends OnDragUriListener {
        public OnDragUriReadListener(boolean requestPermissions) {
            super(requestPermissions);
        }

        public OnDragUriReadListener() {
            super(true);
        }

        protected String processUri(Uri uri) {
            return checkQueryResult(uri, MAGIC_VALUE);
        }

        protected String checkQueryResult(Uri uri, String expectedValue) {
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor == null) {
                    return "Null Cursor";
                }
                cursor.moveToPosition(0);
                String value = cursor.getString(0);
                if (!expectedValue.equals(value)) {
                    return "Wrong value: " + value;
                }
                return RESULT_OK;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }

    private class OnDragUriWriteListener extends OnDragUriListener {
        public OnDragUriWriteListener() {
            super(true);
        }

        protected String processUri(Uri uri) {
            ContentValues values = new ContentValues();
            values.put("key", 100);
            getContentResolver().update(uri, values, null, null);
            return RESULT_OK;
        }
    }

    private class OnDragUriReadPrefixListener extends OnDragUriReadListener {
        @Override
        protected String processUri(Uri uri) {
            final String result1 = queryPrefixed(uri, "1");
            if (!result1.equals(RESULT_OK)) {
                return result1;
            }
            final String result2 = queryPrefixed(uri, "2");
            if (!result2.equals(RESULT_OK)) {
                return result2;
            }
            return queryPrefixed(uri, "3");
        }

        private String queryPrefixed(Uri uri, String selector) {
            final Uri prefixedUri = Uri.parse(uri.toString() + "/" + selector);
            return checkQueryResult(prefixedUri, selector);
        }
    }

    private class OnDragUriTakePersistableListener extends OnDragUriListener {
        public OnDragUriTakePersistableListener() {
            super(true);
        }

        @Override
        protected String processUri(Uri uri) {
            getContentResolver().takePersistableUriPermission(
                    uri, View.DRAG_FLAG_GLOBAL_URI_READ);
            getContentResolver().releasePersistableUriPermission(
                    uri, View.DRAG_FLAG_GLOBAL_URI_READ);
            return RESULT_OK;
        }
    }
}
