/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.safetycenter.cts

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Parcel
import android.safetycenter.SafetySourceIssue.SEVERITY_LEVEL_INFORMATION
import android.safetycenter.SafetySourceIssue.SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceIssue.Action
import android.safetycenter.SafetySourceIssue
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetySourceIssue]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetySourceIssueTest {
    private val context: Context = getApplicationContext()

    private val actionPendingIntent1: PendingIntent = PendingIntent.getActivity(context,
            0 /* requestCode= */, Intent("Action PendingIntent 1"), FLAG_IMMUTABLE)
    private val action1 = Action.Builder("Action label 1", actionPendingIntent1).build()
    private val actionPendingIntent2: PendingIntent = PendingIntent.getActivity(context,
            0 /* requestCode= */, Intent("Action PendingIntent 2"), FLAG_IMMUTABLE)
    private val action2 = Action.Builder("Action label 2", actionPendingIntent2).build()

    @Test
    fun action_getLabel_returnsLabel() {
        val action = Action.Builder("Action label", actionPendingIntent1).build()

        assertThat(action.label).isEqualTo("Action label")
    }

    @Test
    fun action_isResolving_withDefaultBuilder_returnsFalse() {
        val action = Action.Builder("Action label", actionPendingIntent1).build()

        assertThat(action.isResolving).isFalse()
    }

    @Test
    fun action_isResolving_whenSetExplicitly_returnsResolving() {
        val action = Action.Builder("Action label", actionPendingIntent1)
                .setResolving(true)
                .build()

        assertThat(action.isResolving).isTrue()
    }

    @Test
    fun action_getPendingIntent_returnsPendingIntent() {
        val action = Action.Builder("Action label", actionPendingIntent1).build()

        assertThat(action.pendingIntent).isEqualTo(actionPendingIntent1)
    }

    @Test
    fun action_describeContents_returns0() {
        val action = Action.Builder("Action label", actionPendingIntent1).build()

        assertThat(action.describeContents()).isEqualTo(0)
    }

    @Test
    fun action_createFromParcel_withWriteToParcel_returnsOriginalAction() {
        val action = Action.Builder("Action label", actionPendingIntent1).build()

        val parcel: Parcel = Parcel.obtain()
        action.writeToParcel(parcel, 0 /* flags */)
        parcel.setDataPosition(0)
        val actionFromParcel: Action = Action.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(actionFromParcel).isEqualTo(action)
    }

    // TODO(b/208473675): Use `EqualsTester` for testing `hashcode` and `equals`.
    @Test
    fun action_hashCode_equals_toString_withEqualByReferenceActions_areEqual() {
        val action = Action.Builder("Action label", actionPendingIntent1).build()
        val otherAction = action

        assertThat(action.hashCode()).isEqualTo(otherAction.hashCode())
        assertThat(action).isEqualTo(otherAction)
        assertThat(action.toString()).isEqualTo(otherAction.toString())
    }

    @Test
    fun action_hashCode_equals_toString_withAllFieldsEqual_areEqual() {
        val action = Action.Builder("Action label", actionPendingIntent1).build()
        val otherAction = Action.Builder("Action label", actionPendingIntent1).build()

        assertThat(action.hashCode()).isEqualTo(otherAction.hashCode())
        assertThat(action).isEqualTo(otherAction)
        assertThat(action.toString()).isEqualTo(otherAction.toString())
    }

    @Test
    fun action_hashCode_equals_toString_withDifferentLabels_areNotEqual() {
        val action = Action.Builder("Action label", actionPendingIntent1).build()
        val otherAction = Action.Builder("Other action label", actionPendingIntent1).build()

        assertThat(action.hashCode()).isNotEqualTo(otherAction.hashCode())
        assertThat(action).isNotEqualTo(otherAction)
        assertThat(action.toString()).isNotEqualTo(otherAction.toString())
    }

    @Test
    fun action_hashCode_equals_toString_withDifferentResolving_areNotEqual() {
        val action =
                Action.Builder("Action label", actionPendingIntent1).setResolving(false)
                        .build()
        val otherAction =
                Action.Builder("Action label", actionPendingIntent1).setResolving(true).build()

        assertThat(action.hashCode()).isNotEqualTo(otherAction.hashCode())
        assertThat(action).isNotEqualTo(otherAction)
        assertThat(action.toString()).isNotEqualTo(otherAction.toString())
    }

    @Test
    fun action_hashCode_equals_toString_withDifferentPendingIntents_areNotEqual() {
        val action = Action.Builder("Action label",
                PendingIntent.getActivity(context, 0 /* requestCode= */,
                        Intent("Action PendingIntent"), FLAG_IMMUTABLE))
                .build()
        val otherAction = Action.Builder("Action label",
                PendingIntent.getActivity(context, 0 /* requestCode= */,
                        Intent("Other action PendingIntent"), FLAG_IMMUTABLE))
                .build()

        assertThat(action.hashCode()).isNotEqualTo(otherAction.hashCode())
        assertThat(action).isNotEqualTo(otherAction)
        assertThat(action.toString()).isNotEqualTo(otherAction.toString())
    }

    @Test
    fun getTitle_returnsTitle() {
        val safetySourceIssue = SafetySourceIssue.Builder(
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION)
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.title).isEqualTo("Issue title")
    }

    @Test
    fun getSummary_returnsSummary() {
        val safetySourceIssue = SafetySourceIssue.Builder(
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION)
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.summary).isEqualTo("Issue summary")
    }

    @Test
    fun getSeverityLevel_returnsSeverityLevel() {
        val safetySourceIssue = SafetySourceIssue.Builder(
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION)
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.severityLevel).isEqualTo(SEVERITY_LEVEL_INFORMATION)
    }

    @Test
    fun getActions_returnsActions() {
        val safetySourceIssue = SafetySourceIssue.Builder(
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION)
                .addAction(action1)
                .addAction(action2)
                .build()

        assertThat(safetySourceIssue.actions).containsExactly(action1, action2).inOrder()
    }

    @Test
    fun build_withNoActions_throwsIllegalArgumentException() {
        val safetySourceIssueBuilder = SafetySourceIssue.Builder(
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION)

        assertThrows("Safety source issue must contain at least 1 action",
                IllegalArgumentException::class.java) { safetySourceIssueBuilder.build() }
    }

    @Test
    fun build_withMoreThanTwoActions_throwsIllegalArgumentException() {
        val safetySourceIssueBuilder = SafetySourceIssue.Builder(
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION)
                .addAction(action1)
                .addAction(action2)
                .addAction(action1)

        assertThrows("Safety source issue must not contain more than 2 actions",
                IllegalArgumentException::class.java) { safetySourceIssueBuilder.build() }
    }

    @Test
    fun describeContents_returns0() {
        val safetySourceIssue = SafetySourceIssue.Builder(
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION)
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.describeContents()).isEqualTo(0)
    }

    @Test
    fun createFromParcel_withWriteToParcel_returnsOriginalSafetySourceIssue() {
        val safetySourceIssue = SafetySourceIssue.Builder(
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION)
                .addAction(action1)
                .addAction(action2)
                .build()

        val parcel: Parcel = Parcel.obtain()
        safetySourceIssue.writeToParcel(parcel, 0 /* flags */)
        parcel.setDataPosition(0)
        val safetySourceIssueFromParcel: SafetySourceIssue =
                SafetySourceIssue.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(safetySourceIssueFromParcel).isEqualTo(safetySourceIssue)
    }

    // TODO(b/208473675): Use `EqualsTester` for testing `hashcode` and `equals`.
    @Test
    fun hashCode_equals_toString_withEqualByReferenceSafetySourceIssues_areEqual() {
        val safetySourceIssue = SafetySourceIssue.Builder(
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION)
                .addAction(action1)
                .build()
        val otherSafetySourceIssue = safetySourceIssue

        assertThat(safetySourceIssue.hashCode()).isEqualTo(otherSafetySourceIssue.hashCode())
        assertThat(safetySourceIssue).isEqualTo(otherSafetySourceIssue)
        assertThat(safetySourceIssue.toString()).isEqualTo(otherSafetySourceIssue.toString())
    }

    @Test
    fun hashCode_equals_toString_withAllFieldsEqual_areEqual() {
        val safetySourceIssue = SafetySourceIssue.Builder(
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION)
                .addAction(Action.Builder("Action label 1", actionPendingIntent1)
                        .setResolving(false)
                        .build())
                .build()
        val otherSafetySourceIssue = SafetySourceIssue.Builder(
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION)
                .addAction(Action.Builder("Action label 1", actionPendingIntent1)
                        .setResolving(false)
                        .build())
                .build()

        assertThat(safetySourceIssue.hashCode()).isEqualTo(otherSafetySourceIssue.hashCode())
        assertThat(safetySourceIssue).isEqualTo(otherSafetySourceIssue)
        assertThat(safetySourceIssue.toString()).isEqualTo(otherSafetySourceIssue.toString())
    }

    @Test
    fun hashCode_equals_toString_withDifferentTitles_areNotEqual() {
        val safetySourceIssue = SafetySourceIssue.Builder(
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION)
                .addAction(action1)
                .build()
        val otherSafetySourceIssue = SafetySourceIssue.Builder(
                "Other issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION)
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.hashCode()).isNotEqualTo(otherSafetySourceIssue.hashCode())
        assertThat(safetySourceIssue).isNotEqualTo(otherSafetySourceIssue)
        assertThat(safetySourceIssue.toString()).isNotEqualTo(otherSafetySourceIssue.toString())
    }

    @Test
    fun hashCode_equals_toString_withDifferentSummaries_areNotEqual() {
        val safetySourceIssue = SafetySourceIssue.Builder(
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION)
                .addAction(action1)
                .build()
        val otherSafetySourceIssue = SafetySourceIssue.Builder(
                "Issue title",
                "Other issue summary",
                SEVERITY_LEVEL_INFORMATION)
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.hashCode()).isNotEqualTo(otherSafetySourceIssue.hashCode())
        assertThat(safetySourceIssue).isNotEqualTo(otherSafetySourceIssue)
        assertThat(safetySourceIssue.toString()).isNotEqualTo(otherSafetySourceIssue.toString())
    }

    @Test
    fun hashCode_equals_toString_withDifferentSeverityLevels_areNotEqual() {
        val safetySourceIssue = SafetySourceIssue.Builder(
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION)
                .addAction(action1)
                .build()
        val otherSafetySourceIssue = SafetySourceIssue.Builder(
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_CRITICAL_WARNING)
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.hashCode()).isNotEqualTo(otherSafetySourceIssue.hashCode())
        assertThat(safetySourceIssue).isNotEqualTo(otherSafetySourceIssue)
        assertThat(safetySourceIssue.toString()).isNotEqualTo(otherSafetySourceIssue.toString())
    }

    @Test
    fun hashCode_equals_toString_withDifferentActions_areNotEqual() {
        val safetySourceIssue = SafetySourceIssue.Builder(
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION)
                .addAction(action1)
                .addAction(action2)
                .build()
        val otherSafetySourceIssue = SafetySourceIssue.Builder(
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION)
                .addAction(action2)
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.hashCode()).isNotEqualTo(otherSafetySourceIssue.hashCode())
        assertThat(safetySourceIssue).isNotEqualTo(otherSafetySourceIssue)
        assertThat(safetySourceIssue.toString()).isNotEqualTo(otherSafetySourceIssue.toString())
    }
}