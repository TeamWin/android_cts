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

package android.dnd.cts;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;
import android.view.Display;
import android.view.WindowManager;

import java.util.concurrent.TimeoutException;

import static android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT;

public class DragAndDropTest extends InstrumentationTestCase {

    private static final String DRAG_SOURCE_PKG = "android.dnd.cts.dragsource";
    private static final String DROP_TARGET_PKG = "android.dnd.cts.droptarget";

    private static final int TIMEOUT = 5000;

    private UiDevice mDevice;
    private Context mContext;
    private int mDisplayWidth;
    private int mDisplayHeight;

    public void setUp() throws TimeoutException {
        mDevice = UiDevice.getInstance(getInstrumentation());
        mContext = getInstrumentation().getContext();
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        mDisplayWidth = size.x;
        mDisplayHeight = size.y;
    }

    private void startAppOnHalfScreen(String packageName, boolean rightHalf) {
        Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);

        int left = (int)(mDisplayWidth * (rightHalf ? 0.55 : 0.1));
        int right = left + (int) (mDisplayWidth * 0.35);
        int top = (int) (mDisplayHeight * 0.1) ;
        int bottom = mDisplayHeight - top;

        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchBounds( new Rect(left, top, right, bottom));
        mContext.startActivity(intent, options.toBundle());

        // Wait for the app to appear
        mDevice.wait(Until.hasObject(By.pkg(packageName).depth(0)), TIMEOUT);
    }

    private Point getVisibleCenter(String packageName, String sourceViewId) {
        return findObject(packageName, sourceViewId).getVisibleCenter();
    }

    private UiObject2 findObject(String packageName, String id) {
        return mDevice.findObject(By.res(packageName, id));
    }

    private void drag(Point srcPosition, Point tgtPosition) {
        mDevice.drag(srcPosition.x, srcPosition.y, tgtPosition.x, tgtPosition.y, 50);
    }

    private void doCrossAppDrag(String sourceViewId, String targetViewId, String expectedResult) {
        if (!mContext.getPackageManager().hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT)) {
            return;
        }

        startAppOnHalfScreen(DRAG_SOURCE_PKG, false);
        Point srcPosition = getVisibleCenter(DRAG_SOURCE_PKG, sourceViewId);

        startAppOnHalfScreen(DROP_TARGET_PKG, true);
        Point tgtPosition = getVisibleCenter(DROP_TARGET_PKG, targetViewId);

        drag(srcPosition, tgtPosition);

        // If we don't do that the next 'findObject' often fails.
        mDevice.click(tgtPosition.x, tgtPosition.y);

        UiObject2 result = findObject(DROP_TARGET_PKG, "result");
        assertEquals(expectedResult, result.getText());
    }

    public void testDontGrantDontRequest() {
        doCrossAppDrag("dont_grant", "dont_request", "Exception");
    }

    public void testDoGrantDontRequest() {
        doCrossAppDrag("do_grant", "dont_request", "Exception");
    }

    public void testDontGrantDoRequest() {
        doCrossAppDrag("dont_grant", "do_request", "Null DropPermissions");
    }

    public void testDoGrantDoRequest() {
        doCrossAppDrag("do_grant", "do_request", "OK");
    }
}