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
import android.os.Bundle;
import android.service.autofill.Dataset;
import android.service.autofill.FillResponse;
import android.support.test.InstrumentationRegistry;
import android.text.TextUtils;
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

    /**
     * Timeout (in milliseconds) until framework binds / unbinds from service.
     */
    static final long CONNECTION_TIMEOUT_MS = 2000;

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

    // TODO(b/33197203 , b/35395043): temporary guard to skip assertions known to fail
    static final boolean IGNORE_DANGLING_SESSIONS = true;

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
            .append(" text=").append(node.getText())
            .append(" #children=").append(childrenSize);

        buffer.append("\n").append(prefix)
            .append("   afId=").append(node.getAutoFillId())
            .append(" afType=").append(node.getAutoFillType())
            .append(" afValue=").append(node.getAutoFillValue());

        prefix += " ";
        if (childrenSize > 0) {
            for (int i = 0; i < childrenSize; i++) {
                dump(buffer, node.getChildAt(i), prefix, i);
            }
        }
    }

    /**
     * Gets a node give its Android resource id, or {@code null} if not found.
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
     * Gets a node give its Android resource id, or {@code null} if not found.
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
     * Asserts a text-base node is sanitized.
     */
    static void assertTextIsSanitized(ViewNode node) {
      final CharSequence text = node.getText();
      final String resourceId = node.getIdEntry();
      if (!TextUtils.isEmpty(text)) {
        throw new AssertionError("text on sanitized field " + resourceId + ": " + text);
      }
      final AutoFillValue initialValue = node.getAutoFillValue();
      assertWithMessage("auto-fill value on sanitized field %s: %s", resourceId,
              initialValue).that(initialValue).isNull();
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
     * Asserts a text-base node exists and is sanitized.
     * @return
     */
    static ViewNode assertTextIsSanitized(AssistStructure structure, String resourceId) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertWithMessage("no ViewNode with id %s", resourceId).that(node).isNotNull();
        assertTextIsSanitized(node);
        return node;
    }

    /**
     * Asserts a list-base node exists and is sanitized.
     */
    static void assertListValueIsSanitized(AssistStructure structure, String resourceId) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertWithMessage("no ViewNode with id %s", resourceId).that(node).isNotNull();
        assertTextIsSanitized(node);
    }

    // TODO(b/33197203, b/33802548): move to CannedFillResponse
    static FillResponse createFromCannedResponse(AssistStructure structure,
            CannedFillResponse response) {
        final FillResponse.Builder builder = new FillResponse.Builder();
        if (response.datasets != null) {
            for (CannedFillResponse.CannedDataset cannedDataset : response.datasets) {
                final Dataset dataset = createFromCannedDataset(structure, cannedDataset);
                assertWithMessage("Cannot create datase").that(dataset).isNotNull();
                builder.addDataset(dataset);
            }
        }
        if (response.savableIds != null) {
            for (String resourceId : response.savableIds) {
                final ViewNode node = findNodeByResourceId(structure, resourceId);
                if (node == null) {
                    dumpStructure("onFillRequest()", structure);
                    throw new AssertionError("No node with savable resourceId " + resourceId);
                }
                final AutoFillId id = node.getAutoFillId();
                builder.addSavableFields(id);
            }
        }
        builder.setExtras(response.extras);
        builder.setAuthentication(response.authentication,
                response.presentation);
        return builder.build();
    }

    // TODO(b/33197203, b/33802548): move to CannedFillResponse
    static Dataset createFromCannedDataset(AssistStructure structure,
            CannedFillResponse.CannedDataset dataset) {
        final Dataset.Builder builder = new Dataset.Builder(dataset.presentation);
        if (dataset.fields != null) {
            for (Map.Entry<String, AutoFillValue> entry : dataset.fields.entrySet()) {
                final String resourceId = entry.getKey();
                final ViewNode node = findNodeByResourceId(structure, resourceId);
                assertWithMessage("Cannot find node:" + resourceId).that(node).isNotNull();
                final AutoFillId id = node.getAutoFillId();
                final AutoFillValue value = entry.getValue();
                builder.setValue(id, value);
            }
        }
        builder.setAuthentication(dataset.authentication);
        return builder.build();
    }

    private Helper() {
    }
}
