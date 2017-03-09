/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.autofillservice.cts;

import static android.autofillservice.cts.Helper.dumpStructure;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure;
import android.app.assist.AssistStructure.ViewNode;
import android.app.assist.AssistStructure.WindowNode;
import android.icu.util.Calendar;
import android.os.Bundle;
import android.service.autofill.Dataset;
import android.service.autofill.FillResponse;
import android.support.test.InstrumentationRegistry;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.autofill.AutoFillId;
import android.view.autofill.AutoFillValue;

import com.android.compatibility.common.util.SystemUtil;

import java.util.List;
import java.util.Map;

/**
 * Helper for common funcionalities.
 */
final class Helper {

    private static final String TAG = "AutoFillCtsHelper";

    static final String ID_USERNAME_LABEL = "username_label";
    static final String ID_USERNAME = "username";
    static final String ID_PASSWORD_LABEL = "password_label";
    static final String ID_PASSWORD = "password";
    static final String ID_LOGIN = "login";
    static final String ID_OUTPUT = "output";

    /**
     * Timeout (in milliseconds) until framework binds / unbinds from service.
     */
    static final long CONNECTION_TIMEOUT_MS = 2000;

    /**
     * Timeout (in milliseconds) until framework unbinds from a service.
     */
    static final long IDLE_UNBIND_TIMEOUT_MS = 5 * DateUtils.SECOND_IN_MILLIS;

    /**
     * Timeout (in milliseconds) for expected auto-fill requests.
     */
    static final long FILL_TIMEOUT_MS = 2000;

    /**
     * Timeout (in milliseconds) for expected save requests.
     */
    static final long SAVE_TIMEOUT_MS = 2000;

    /**
     * Timeout (in milliseconds) for UI operations. Typically used by {@link UiBot}.
     */
    static final int UI_TIMEOUT_MS = 2000;

    /**
     * Runs a Shell command, returning a trimmed response.
     */
    static String runShellCommand(String template, Object...args) {
        final String command = String.format(template, args);
        Log.d(TAG, "runShellCommand(): " + command);
        try {
            final String result = SystemUtil
                    .runShellCommand(InstrumentationRegistry.getInstrumentation(), command);
            return TextUtils.isEmpty(result) ? "" : result.trim();
        } catch (Exception e) {
            throw new RuntimeException("Command '" + command + "' failed: ", e);
        }
    }

    /**
     * Dump the assist structure on {@link System#out}.
     */
    static void dumpStructure(String message, AssistStructure structure) {
        final StringBuffer buffer = new StringBuffer(message)
                .append(": component=")
                .append(structure.getActivityComponent());
        final int nodes = structure.getWindowNodeCount();
        for (int i = 0; i < nodes; i++) {
            final WindowNode windowNode = structure.getWindowNodeAt(i);
            dump(buffer, windowNode.getRootViewNode(), " ", 0);
        }
        System.out.println(buffer.toString());
    }

    private static void dump(StringBuffer buffer, ViewNode node, String prefix, int childId) {
        final int childrenSize = node.getChildCount();
        buffer.append("\n").append(prefix)
            .append('#').append(childId).append(':')
            .append("resId=").append(node.getIdEntry())
            .append(" class=").append(node.getClassName())
            .append(" text=").append(node.getText())
            .append(" class=").append(node.getClassName())
            .append(" #children=").append(childrenSize);

        buffer.append("\n").append(prefix)
            .append("   afId=").append(node.getAutoFillId())
            .append(" afType=").append(node.getAutoFillType())
            .append(" afValue=").append(node.getAutoFillValue())
            .append(" checked=").append(node.isChecked());

        prefix += " ";
        if (childrenSize > 0) {
            for (int i = 0; i < childrenSize; i++) {
                dump(buffer, node.getChildAt(i), prefix, i);
            }
        }
    }

    /**
     * Gets a node given its Android resource id, or {@code null} if not found.
     */
    static ViewNode findNodeByResourceId(AssistStructure structure, String resourceId) {
        Log.v(TAG, "Parsing request for activity " + structure.getActivityComponent());
        final int nodes = structure.getWindowNodeCount();
        for (int i = 0; i < nodes; i++) {
            final WindowNode windowNode = structure.getWindowNodeAt(i);
            final ViewNode rootNode = windowNode.getRootViewNode();
            final ViewNode node = findNodeByResourceId(rootNode, resourceId);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    /**
     * Gets a node given its Android resource id, or {@code null} if not found.
     */
    static ViewNode findNodeByResourceId(ViewNode node, String resourceId) {
        if (resourceId.equals(node.getIdEntry())) {
            return node;
        }
        final int childrenSize = node.getChildCount();
        if (childrenSize > 0) {
            for (int i = 0; i < childrenSize; i++) {
                final ViewNode found = findNodeByResourceId(node.getChildAt(i), resourceId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Gets a node given its expected text, or {@code null} if not found.
     */
    static ViewNode findNodeByText(AssistStructure structure, String text) {
        Log.v(TAG, "Parsing request for activity " + structure.getActivityComponent());
        final int nodes = structure.getWindowNodeCount();
        for (int i = 0; i < nodes; i++) {
            final WindowNode windowNode = structure.getWindowNodeAt(i);
            final ViewNode rootNode = windowNode.getRootViewNode();
            final ViewNode node = findNodeByText(rootNode, text);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    /**
     * Gets a node given its expected text, or {@code null} if not found.
     */
    static ViewNode findNodeByText(ViewNode node, String text) {
        if (text.equals(node.getText())) {
            return node;
        }
        final int childrenSize = node.getChildCount();
        if (childrenSize > 0) {
            for (int i = 0; i < childrenSize; i++) {
                final ViewNode found = findNodeByText(node.getChildAt(i), text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Asserts a text-base node is sanitized.
     */
    static void assertTextIsSanitized(ViewNode node) {
      final CharSequence text = node.getText();
      final String resourceId = node.getIdEntry();
      if (!TextUtils.isEmpty(text)) {
        throw new AssertionError("text on sanitized field " + resourceId + ": " + text);
      }
      assertNodeHasNoAutoFillValue(node);
    }

    static void assertNodeHasNoAutoFillValue(ViewNode node) {
        final AutoFillValue value = node.getAutoFillValue();
        assertWithMessage("node.getAutoFillValue()").that(value).isNull();
    }

    /**
     * Asserts the contents of a text-based node that is also auto-fillable.
     *
     */
    static void assertTextOnly(ViewNode node, String expectedValue) {
        assertText(node, expectedValue, false);
    }

    /**
     * Asserts the contents of a text-based node that is also auto-fillable.
     *
     */
    static void assertTextAndValue(ViewNode node, String expectedValue) {
        assertText(node, expectedValue, true);
    }

    private static void assertText(ViewNode node, String expectedValue, boolean isAutofillable) {
        assertWithMessage("wrong text on %s", node).that(node.getText().toString())
                .isEqualTo(expectedValue);
        final AutoFillValue value = node.getAutoFillValue();
        if (isAutofillable) {
            assertWithMessage("null auto-fill value on %s", node).that(value).isNotNull();
            assertWithMessage("wrong auto-fill value on %s", node)
                    .that(value.getTextValue().toString()).isEqualTo(expectedValue);
        } else {
            assertWithMessage("node %s should not have AutoFillValue", node).that(value).isNull();
        }
    }

    /**
     * Asserts the auto-fill value of a list-based node.
     */
    static ViewNode assertListValue(ViewNode node, int expectedIndex) {
        final AutoFillValue value = node.getAutoFillValue();
        assertWithMessage("null auto-fill value on %s", node).that(value).isNotNull();
        assertWithMessage("wrong auto-fill value on %s", node).that(value.getListValue())
                .isEqualTo(expectedIndex);
        return node;
    }

    /**
     * Asserts the auto-fill value of a toggle-based node.
     *
     */
    static void assertToggleValue(ViewNode node, boolean expectedToggle) {
        final AutoFillValue value = node.getAutoFillValue();
        assertWithMessage("null auto-fill value on %s", node).that(value).isNotNull();
        assertWithMessage("wrong auto-fill value on %s", node).that(value.getToggleValue())
                .isEqualTo(expectedToggle);
    }

    /**
     * Asserts the auto-fill value of a date-based node.
     */
    static void assertDateValue(ViewNode node, int year, int month, int day) {
        final AutoFillValue value = node.getAutoFillValue();
        assertWithMessage("null auto-fill value on %s", node).that(value).isNotNull();

        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(value.getDateValue());

        assertWithMessage("Wrong year on AutoFillValue %s", value)
            .that(cal.get(Calendar.YEAR)).isEqualTo(year);
        assertWithMessage("Wrong month on AutoFillValue %s", value)
            .that(cal.get(Calendar.MONTH)).isEqualTo(month);
        assertWithMessage("Wrong day on AutoFillValue %s", value)
             .that(cal.get(Calendar.DAY_OF_MONTH)).isEqualTo(day);
    }

    /**
     * Asserts the auto-fill value of a time-based node.
     */
    static void assertTimeValue(ViewNode node, int hour, int minute) {
        final AutoFillValue value = node.getAutoFillValue();
        assertWithMessage("null auto-fill value on %s", node).that(value).isNotNull();

        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(value.getDateValue());

        assertWithMessage("Wrong hour on AutoFillValue %s", value)
            .that(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(hour);
        assertWithMessage("Wrong minute on AutoFillValue %s", value)
            .that(cal.get(Calendar.MINUTE)).isEqualTo(minute);
    }

    /**
     * Asserts a text-base node exists and is sanitized.
     */
    static ViewNode assertTextIsSanitized(AssistStructure structure, String resourceId) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertWithMessage("no ViewNode with id %s", resourceId).that(node).isNotNull();
        assertTextIsSanitized(node);
        return node;
    }

    /**
     * Asserts a list-based node exists and is sanitized.
     */
    static void assertListValueIsSanitized(AssistStructure structure, String resourceId) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertWithMessage("no ViewNode with id %s", resourceId).that(node).isNotNull();
        assertTextIsSanitized(node);
    }

    /**
     * Asserts a toggle node exists and is sanitized.
     */
    static void assertToggleIsSanitized(AssistStructure structure, String resourceId) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertNodeHasNoAutoFillValue(node);
        assertWithMessage("ViewNode %s should not be checked", resourceId).that(node.isChecked())
                .isFalse();
    }

    /**
     * Asserts a node exists and has the {@code expected} number of children.
     */
    static void assertNumberOfChildren(AssistStructure structure, String resourceId, int expected) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        final int actual = node.getChildCount();
        if (actual != expected) {
            dumpStructure("assertNumberOfChildren()", structure);
            throw new AssertionError("assertNumberOfChildren() for " + resourceId
                    + " failed: expected " + expected + ", got " + actual);
        }
    }

    /**
     * Asserts the number of children in the Assist structure.
     */
    static void assertNumberOfChildren(AssistStructure structure, int expected) {
        assertWithMessage("wrong number of nodes").that(structure.getWindowNodeCount())
                .isEqualTo(1);
        final int actual = getNumberNodes(structure);
        if (actual != expected) {
            dumpStructure("assertNumberOfChildren()", structure);
            throw new AssertionError("assertNumberOfChildren() for structure failed: expected "
                    + expected + ", got " + actual);
        }
    }

    /**
     * Gets the total number of nodes in an structure, including all descendants.
     */
    static int getNumberNodes(AssistStructure structure) {
        int count = 0;
        final int nodes = structure.getWindowNodeCount();
        for (int i = 0; i < nodes; i++) {
            final WindowNode windowNode = structure.getWindowNodeAt(i);
            final ViewNode rootNode = windowNode.getRootViewNode();
            count += getNumberNodes(rootNode);
        }
        return count;
    }

    /**
     * Gets the total number of nodes in an node, including all descendants and the node itself.
     */
    private static int getNumberNodes(ViewNode node) {
        int count = 1;
        final int childrenSize = node.getChildCount();
        if (childrenSize > 0) {
            for (int i = 0; i < childrenSize; i++) {
                count += getNumberNodes(node.getChildAt(i));
            }
        }
        return count;
    }

    private Helper() {
    }
}
