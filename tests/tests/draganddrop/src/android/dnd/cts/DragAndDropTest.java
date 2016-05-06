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
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;

import java.util.concurrent.TimeoutException;

public class DragAndDropTest extends InstrumentationTestCase {

    private static final String DRAG_SOURCE_PKG = "android.dnd.cts.dragsource";
    private static final String DROP_TARGET_PKG = "android.dnd.cts.droptarget";

    private static final int TIMEOUT = 5000;

    // Constants copied from ActivityManager.StackId. If they are changed there, these must be
    // updated.
    public static final int FREEFORM_WORKSPACE_STACK_ID = 1;
    public static final int DOCKED_STACK_ID = 3;

    private UiDevice mDevice;

    public void setUp() throws TimeoutException {
        mDevice = UiDevice.getInstance(getInstrumentation());
    }

    private void startAppInStack(String packageName, int stackId, String mode) {
        Context context = getInstrumentation().getContext();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("mode", mode);

        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchStackId(stackId);
        context.startActivity(intent, options.toBundle());

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
        mDevice.drag(srcPosition.x, srcPosition.y, tgtPosition.x, tgtPosition.y, 100);
    }

    private void doCrossAppDrag(String sourceMode, String targetMode, String expectedResult) {
        startAppInStack(DRAG_SOURCE_PKG, DOCKED_STACK_ID, sourceMode);
        Point srcPosition = getVisibleCenter(DRAG_SOURCE_PKG, "source");

        startAppInStack(DROP_TARGET_PKG, FREEFORM_WORKSPACE_STACK_ID, targetMode);
        Point tgtPosition = getVisibleCenter(DROP_TARGET_PKG, "target");

        drag(srcPosition, tgtPosition);

        // If we don't do that the next 'findObject' often fails.
        mDevice.click(tgtPosition.x, tgtPosition.y);

        UiObject2 result = findObject(DROP_TARGET_PKG, "result");
        assertNotNull("Result widget not found", result);
        assertEquals(expectedResult, result.getText());
    }

    private void assertDragStarted(String expectedResult) {
        UiObject2 drag_started = findObject(DROP_TARGET_PKG, "drag_started");
        assertEquals(expectedResult, drag_started.getText());
    }

    private void assertExtraValue(String expectedResult) {
        UiObject2 extra_value = findObject(DROP_TARGET_PKG, "extra_value");
        assertEquals(expectedResult, extra_value.getText());
    }

    public void testLocal() {
        doCrossAppDrag("disallow_global", "dont_request", "N/A");
        assertDragStarted("N/A");
        assertExtraValue("N/A");
    }

    public void testCancel() {
        doCrossAppDrag("cancel_soon", "dont_request", "N/A");
        assertDragStarted("DRAG_STARTED");
        assertExtraValue("OK");
    }

    public void testDontGrantDontRequest() {
        doCrossAppDrag("dont_grant", "dont_request", "Exception");
        assertDragStarted("DRAG_STARTED");
        assertExtraValue("OK");
    }

    public void testDontGrantRequestRead() {
        doCrossAppDrag("dont_grant", "request_read", "Null DragAndDropPermissions");
    }

    public void testDontGrantRequestWrite() {
        doCrossAppDrag("dont_grant", "request_write", "Null DragAndDropPermissions");
    }

    public void testGrantReadDontRequest() {
        doCrossAppDrag("grant_read", "dont_request", "Exception");
    }

    public void testGrantReadRequestRead() {
        doCrossAppDrag("grant_read", "request_read", "OK");
    }

    public void testGrantReadRequestWrite() {
        doCrossAppDrag("grant_read", "request_write", "Exception");
    }

    public void testGrantWriteDontRequest() {
        doCrossAppDrag("grant_write", "dont_request", "Exception");
    }

    public void testGrantWriteRequestRead() {
        doCrossAppDrag("grant_write", "request_read", "Exception");
    }

    public void testGrantWriteRequestWrite() {
        doCrossAppDrag("grant_write", "request_write", "OK");
    }

    public void testGrantReadPrefixRequestReadNested() {
        doCrossAppDrag("grant_read_prefix", "request_read_nested", "OK");
    }

    public void testGrantReadNoPrefixRequestReadNested() {
        doCrossAppDrag("grant_read_noprefix", "request_read_nested", "Exception");
    }

    public void testGrantPersistableRequestTakePersistable() {
        doCrossAppDrag("grant_read_persistable", "request_take_persistable", "OK");
    }

    public void testGrantReadRequestTakePersistable() {
        doCrossAppDrag("grant_read", "request_take_persistable", "Exception");
    }
}
