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

package android.dnd.cts.dragsource;

import android.app.Activity;
import android.content.ClipData;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

public class DragSource extends Activity{
    private static final String URI_PREFIX =
            "content://" + DragSourceContentProvider.AUTHORITY + "/data/";

    private static final String MAGIC_VALUE = "42";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = getLayoutInflater().inflate(R.layout.main_activity, null);
        setContentView(view);

        setUpDragSource(R.id.dont_grant, false);
        setUpDragSource(R.id.do_grant, true);
    }

    private void setUpDragSource(final int resourceId, final boolean grantPermissions) {
        findViewById(resourceId).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                int flags = View.DRAG_FLAG_GLOBAL;
                if (grantPermissions) {
                    flags |= View.DRAG_FLAG_GLOBAL_URI_READ | View.DRAG_FLAG_GLOBAL_URI_WRITE;
                }
                final Uri uri = Uri.parse(URI_PREFIX + MAGIC_VALUE);
                v.startDragAndDrop(
                        ClipData.newUri(getContentResolver(), "", uri),
                        new View.DragShadowBuilder(v),
                        null,
                        flags);
                return false;
            }
        });
    }
}
