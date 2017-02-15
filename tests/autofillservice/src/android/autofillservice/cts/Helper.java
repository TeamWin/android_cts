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

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure;
import android.app.assist.AssistStructure.ViewNode;
import android.app.assist.AssistStructure.WindowNode;
import android.support.test.InstrumentationRegistry;
import android.text.TextUtils;
import android.util.Log;
import android.view.autofill.AutoFillValue;

import com.android.compatibility.common.util.SystemUtil;

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
     * Asserts the contents of a text-based node.
     */
    static void assertText(ViewNode node, String expectedValue) {
        assertWithMessage("wrong text on %s", node).that(node.getText().toString())
                .isEqualTo(expectedValue);
        assertWithMessage("wrong auto-fill value on %s", node).that(node.getAutoFillValue())
                .isEqualTo(AutoFillValue.forText(expectedValue));
    }

    /**
     * Asserts a text-base node exists and is sanitized.
     */
    static void assertTextIsSanitized(AssistStructure structure, String resourceId) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertWithMessage("no ViewNode with id %s", resourceId).that(node).isNotNull();
        assertTextIsSanitized(node);
    }

    private Helper() {
    }
}
