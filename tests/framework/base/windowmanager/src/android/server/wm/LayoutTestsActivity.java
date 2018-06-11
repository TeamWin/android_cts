/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.wm;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.view.WindowManager.LayoutParams;

import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;

public class LayoutTestsActivity extends Activity {
    private View mChild;

    public void addWindowHidingStatusBar() {
        addWindow(View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    public void addWindowHidingNavigationBar() {
        addWindow(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    public void addWindowHidingBothSystemBars() {
        addWindow(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    private void addWindow(int systemUiVisibility) {
        mChild = new View(this);
        mChild.setSystemUiVisibility(systemUiVisibility);
        getWindowManager().addView(mChild, new LayoutParams(TYPE_APPLICATION_PANEL));
    }

    public void removeWindow() {
        getWindowManager().removeViewImmediate(mChild);
    }

    public void getWindowVisibleDisplayFrame(Rect outRect) {
        getWindow().getDecorView().getWindowVisibleDisplayFrame(outRect);
    }

    public void addWindow(View view) {
        getWindowManager().addView(view, new LayoutParams(TYPE_APPLICATION_PANEL));
    }
}
