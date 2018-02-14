/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.server.wm.frametestapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;

public class DialogTestActivity extends Activity implements View.OnApplyWindowInsetsListener{

    private static final String DIALOG_WINDOW_NAME = "TestDialog";

    /**
     * Extra key for test case name.
     * @see android.server.wm.ParentChildTestBase#EXTRA_TEST_CASE
     */
    private static final String EXTRA_TEST_CASE = "test-case";

    private AlertDialog mDialog;

    private Rect mOutsets = new Rect();

    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        View content = new View(this);
        content.setOnApplyWindowInsetsListener(this);
        setContentView(content);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mDialog.dismiss();
    }

    public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
        if (in.isRound()) {
            mOutsets = new Rect(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(),
                    in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom());
        }
        setupTest(getIntent());
        return in;
    }

    private void setupTest(Intent intent) {
        final String testCase = intent.getStringExtra(EXTRA_TEST_CASE);
        switch (testCase) {
            case "MatchParent":
                testMatchParent();
                break;
            case "MatchParentLayoutInOverscan":
                testMatchParentLayoutInOverscan();
                break;
            case "ExplicitSize":
                testExplicitSize();
                break;
            case "ExplicitSizeTopLeftGravity":
                testExplicitSizeTopLeftGravity();
                break;
            case "ExplicitSizeBottomRightGravity":
                testExplicitSizeBottomRightGravity();
                break;
            case "OversizedDimensions":
                testOversizedDimensions();
                break;
            case "OversizedDimensionsNoLimits":
                testOversizedDimensionsNoLimits();
                break;
            case "ExplicitPositionMatchParent":
                testExplicitPositionMatchParent();
                break;
            case "ExplicitPositionMatchParentNoLimits":
                testExplicitPositionMatchParentNoLimits();
                break;
            case "NoFocus":
                testNoFocus();
                break;
            case "WithMargins":
                testWithMargins();
                break;
            default:
                break;
        }
    }

    interface DialogLayoutParamsTest {
        void doSetup(WindowManager.LayoutParams p);
    }

    private void doLayoutParamTest(DialogLayoutParamsTest t) {
        mDialog = new AlertDialog.Builder(this).create();

        mDialog.setMessage("Testing is fun!");
        mDialog.setTitle(DIALOG_WINDOW_NAME);
        mDialog.create();

        Window w = mDialog.getWindow();
        final WindowManager.LayoutParams params = w.getAttributes();
        t.doSetup(params);
        w.setAttributes(params);

        mDialog.show();
    }

    private void testMatchParent() {
        doLayoutParamTest((WindowManager.LayoutParams params) -> {
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
        });
    }

    private void testMatchParentLayoutInOverscan() {
        doLayoutParamTest((WindowManager.LayoutParams params) -> {
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN;
        });
    }

    private void testExplicitSize() {
        doLayoutParamTest((WindowManager.LayoutParams params) -> {
            params.width = 200 - mOutsets.left - mOutsets.right;
            params.height = 200 - mOutsets.bottom - mOutsets.top;
        });
    }

    private void testExplicitSizeTopLeftGravity() {
        doLayoutParamTest((WindowManager.LayoutParams params) -> {
            params.width = 200 - mOutsets.left - mOutsets.right;
            params.height = 200 - mOutsets.bottom - mOutsets.top;
            params.gravity = Gravity.TOP | Gravity.LEFT;
        });
    }

    private void testExplicitSizeBottomRightGravity() {
        doLayoutParamTest((WindowManager.LayoutParams params) -> {
            params.width = 200 - mOutsets.left - mOutsets.right;
            params.height = 200 - mOutsets.bottom - mOutsets.top;
            params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        });
    }

    private void testOversizedDimensions() {
        doLayoutParamTest((WindowManager.LayoutParams params) -> {
            params.width = 100000;
            params.height = 100000;
        });
    }

    private void testOversizedDimensionsNoLimits() {
        doLayoutParamTest((WindowManager.LayoutParams params) -> {
            params.width = 5000;
            params.height = 5000;
            params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            params.gravity = Gravity.LEFT | Gravity.TOP;
        });
    }

    private void testExplicitPositionMatchParent() {
        doLayoutParamTest((WindowManager.LayoutParams params) -> {
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
        });
    }

    private void testExplicitPositionMatchParentNoLimits() {
        doLayoutParamTest((WindowManager.LayoutParams params) -> {
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.LEFT | Gravity.TOP;
            params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        });
    }

    private void testNoFocus() {
        doLayoutParamTest((WindowManager.LayoutParams params) -> {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        });
    }

    private void testWithMargins() {
        doLayoutParamTest((WindowManager.LayoutParams params) -> {
            params.gravity = Gravity.LEFT | Gravity.TOP;
            params.horizontalMargin = .25f;
            params.verticalMargin = .25f;
            params.width = 200 - mOutsets.left - mOutsets.right;
            params.height = 200 - mOutsets.bottom - mOutsets.top;
            params.x = 0;
            params.y = 0;
        });
    }
}
