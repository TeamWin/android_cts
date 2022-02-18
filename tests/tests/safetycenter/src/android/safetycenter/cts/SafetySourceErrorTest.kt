/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.os.Parcel
import android.safetycenter.SafetySourceError
import android.safetycenter.SafetySourceError.SOURCE_ERROR_TYPE_ACTION_ERROR
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafetySourceErrorTest {

    val actionError1 = SafetySourceError.Builder(SOURCE_ERROR_TYPE_ACTION_ERROR)
            .setIssueId("issue_id_1")
            .setActionId("action_id_1")
            .build()
    val actionError2 = SafetySourceError.Builder(SOURCE_ERROR_TYPE_ACTION_ERROR)
            .setIssueId("issue_id_2")
            .setActionId("action_id_2")
            .build()

    @Test
    fun getType_returnsType() {
        assertThat(actionError1.type).isEqualTo(SOURCE_ERROR_TYPE_ACTION_ERROR)
        assertThat(actionError2.type).isEqualTo(SOURCE_ERROR_TYPE_ACTION_ERROR)
    }

    @Test
    fun getIssueId_returnsIssueId() {
        assertThat(actionError1.issueId).isEqualTo("issue_id_1")
        assertThat(actionError2.issueId).isEqualTo("issue_id_2")
    }

    @Test
    fun getActionId_returnsActionId() {
        assertThat(actionError1.actionId).isEqualTo("action_id_1")
        assertThat(actionError2.actionId).isEqualTo("action_id_2")
    }

    @Test
    fun createFromParcel_withWriteToParcel_returnsEquivalentObject() {
        val parcel = Parcel.obtain()

        actionError1.writeToParcel(parcel, /* flags= */ 0)
        parcel.setDataPosition(0)

        val fromParcel = SafetySourceError.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(fromParcel).isEqualTo(actionError1)
    }

    @Test
    fun equals_hashCode_toString_equalByReference_areEqual() {
        assertThat(actionError1).isEqualTo(actionError1)
        assertThat(actionError1.hashCode()).isEqualTo(actionError1.hashCode())
        assertThat(actionError1.toString()).isEqualTo(actionError1.toString())
    }

    @Test
    fun equals_hashCode_toString_actionErrors_equalByValue_areEqual() {
        val actionError = SafetySourceError.Builder(SOURCE_ERROR_TYPE_ACTION_ERROR)
                .setIssueId("issue_id_1")
                .setActionId("action_id_1")
                .build()
        val equivalentActionError = SafetySourceError.Builder(SOURCE_ERROR_TYPE_ACTION_ERROR)
                .setIssueId("issue_id_1")
                .setActionId("action_id_1")
                .build()

        assertThat(actionError).isEqualTo(equivalentActionError)
        assertThat(actionError.hashCode()).isEqualTo(equivalentActionError.hashCode())
        assertThat(actionError.toString()).isEqualTo(equivalentActionError.toString())
    }

    @Test
    fun equals_toString_withDifferentIssueIds_areNotEqual() {
        val actionError = SafetySourceError.Builder(SOURCE_ERROR_TYPE_ACTION_ERROR)
                .setIssueId("issue_id_1")
                .setActionId("action_id_1")
                .build()
        val differentActionError = SafetySourceError.Builder(SOURCE_ERROR_TYPE_ACTION_ERROR)
                .setIssueId("issue_id_2")
                .setActionId("action_id_1")
                .build()

        assertThat(actionError).isNotEqualTo(differentActionError)
        assertThat(actionError.toString()).isNotEqualTo(differentActionError.toString())
    }

    @Test
    fun equals_toString_withDifferentActionIds_areNotEqual() {
        val actionError = SafetySourceError.Builder(SOURCE_ERROR_TYPE_ACTION_ERROR)
                .setIssueId("issue_id_1")
                .setActionId("action_id_1")
                .build()
        val differentActionError = SafetySourceError.Builder(SOURCE_ERROR_TYPE_ACTION_ERROR)
                .setIssueId("issue_id_1")
                .setActionId("action_id_2")
                .build()

        assertThat(actionError).isNotEqualTo(differentActionError)
        assertThat(actionError.toString()).isNotEqualTo(differentActionError.toString())
    }
}