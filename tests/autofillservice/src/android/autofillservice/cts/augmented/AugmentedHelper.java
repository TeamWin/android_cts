/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.autofillservice.cts.augmented;

import static android.view.autofill.AutofillManager.MAX_TEMP_AUGMENTED_SERVICE_DURATION_MS;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.autofillservice.cts.Helper;
import android.autofillservice.cts.augmented.CtsAugmentedAutofillService.AugmentedFillRequest;
import android.content.ComponentName;
import android.service.autofill.augmented.FillRequest;
import android.util.Log;
import android.util.Pair;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Preconditions;

import java.util.List;

/**
 * Helper for common funcionalities.
 */
public final class AugmentedHelper {

    private static final String TAG = AugmentedHelper.class.getSimpleName();

    @NonNull
    public static String getActivityName(@Nullable FillRequest request) {
        if (request == null) return "N/A (null request)";

        final ComponentName componentName = request.getActivityComponent();
        if (componentName == null) return "N/A (no component name)";

        return componentName.flattenToShortString();
    }

    /**
     * Sets the augmented capture service.
     */
    public static void setAugmentedService(@NonNull String service) {
        Log.d(TAG, "Setting service to " + service);
        runShellCommand("cmd autofill set temporary-augmented-service 0 %s %d", service,
                MAX_TEMP_AUGMENTED_SERVICE_DURATION_MS);
    }

    /**
     * Resets the content capture service.
     */
    public static void resetAugmentedService() {
        Log.d(TAG, "Resetting back to default service");
        runShellCommand("cmd autofill set temporary-augmented-service 0");
    }

    public static void assertBasicRequestInfo(@NonNull AugmentedFillRequest request,
            @NonNull Activity activity, @NonNull AutofillId expectedFocusedId,
            @NonNull AutofillValue expectedFocusedValue) {
        Preconditions.checkNotNull(activity);
        Preconditions.checkNotNull(expectedFocusedId);
        assertWithMessage("no AugmentedFillRequest").that(request).isNotNull();
        assertWithMessage("no FillRequest on %s", request).that(request.request).isNotNull();
        assertWithMessage("no FillController on %s", request).that(request.controller).isNotNull();
        assertWithMessage("no FillCallback on %s", request).that(request.callback).isNotNull();
        // TODO(b/122728762): re-add when set
//        assertWithMessage("no CancellationSignal on %s", request).that(request.cancellationSignal)
//                .isNotNull();
        // NOTE: task id can change, we might need to set it in the activity's onCreate()
        assertWithMessage("wrong task id on %s", request).that(request.request.getTaskId())
                .isEqualTo(activity.getTaskId());

        final ComponentName actualComponentName = request.request.getActivityComponent();
        assertWithMessage("no activity name on %s", request).that(actualComponentName).isNotNull();
        assertWithMessage("wrong activity name on %s", request).that(actualComponentName)
                .isEqualTo(activity.getComponentName());
        final AutofillId actualFocusedId = request.request.getFocusedId();
        assertWithMessage("no focused id on %s", request).that(actualFocusedId).isNotNull();
        assertWithMessage("wrong focused id on %s", request).that(actualFocusedId)
                .isEqualTo(expectedFocusedId);
        final AutofillValue actualFocusedValue = request.request.getFocusedValue();
        assertWithMessage("no focused value on %s", request).that(actualFocusedValue).isNotNull();
        assertAutofillValue(expectedFocusedValue, actualFocusedValue);
    }

    public static void assertAutofillValue(final AutofillValue expectedValue,
            final AutofillValue actualValue) {
        // It only supports text values for now...
        assertWithMessage("expected value is not text: %s", expectedValue)
                .that(expectedValue.isText()).isTrue();
        assertWithMessage("actual value is not text: %s", actualValue)
                .that(actualValue.isText()).isTrue();

        assertWithMessage("wrong autofill value").that(actualValue.getTextValue())
                .isEqualTo(expectedValue.getTextValue());
    }

    @NonNull
    public static String toString(@Nullable List<Pair<AutofillId, AutofillValue>> values) {
        if (values == null) return "null";
        final StringBuilder string = new StringBuilder("[");
        final int size = values.size();
        for (int i = 0; i < size; i++) {
            final Pair<AutofillId, AutofillValue> value = values.get(i);
            string.append(i).append(':').append(value.first).append('=')
                   .append(Helper.toString(value.second));
            if (i < size - 1) {
                string.append(", ");
            }

        }
        return string.append(']').toString();
    }

    @NonNull
    public static String toString(@Nullable FillRequest request) {
        if (request == null) return "(null request)";

        final StringBuilder string =
                new StringBuilder("FillRequest[act=").append(getActivityName(request))
                .append(", taskId=").append(request.getTaskId());

        final AutofillId focusedId = request.getFocusedId();
        if (focusedId != null) {
            string.append(", focusedId=").append(focusedId);
        }
        final AutofillValue focusedValue = request.getFocusedValue();
        if (focusedValue != null) {
            string.append(", focusedValue=").append(focusedValue);
        }

        return string.append(']').toString();
    }

    // Used internally by UiBot to assert the UI
    static String getContentDescriptionForUi(@NonNull AutofillId focusedId) {
        return "ui_for_" + focusedId;
    }

    private AugmentedHelper() {
        throw new UnsupportedOperationException("contain static methods only");
    }
}
