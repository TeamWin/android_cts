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
import android.safetycenter.SafetySourceStatus
import android.safetycenter.SafetySourceStatus.STATUS_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceStatus.STATUS_LEVEL_NO_ISSUES
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetySourceStatus]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetySourceStatusTest {
    private val context: Context = getApplicationContext()

    private val statusPendingIntent: PendingIntent = PendingIntent.getActivity(context,
            0 /* requestCode= */, Intent("Status PendingIntent"), FLAG_IMMUTABLE)

    @Test
    fun getTitle_returnsTitle() {
        val safetySourceStatus = SafetySourceStatus.Builder(
                "Status title",
                "Status summary",
                STATUS_LEVEL_NO_ISSUES,
                statusPendingIntent)
                .build()

        assertThat(safetySourceStatus.title).isEqualTo("Status title")
    }

    @Test
    fun getSummary_returnsSummary() {
        val safetySourceStatus = SafetySourceStatus.Builder(
                "Status title",
                "Status summary",
                STATUS_LEVEL_NO_ISSUES,
                statusPendingIntent)
                .build()

        assertThat(safetySourceStatus.summary).isEqualTo("Status summary")
    }

    @Test
    fun getStatusLevel_returnsStatusLevel() {
        val safetySourceStatus = SafetySourceStatus.Builder(
                "Status title",
                "Status summary",
                STATUS_LEVEL_NO_ISSUES,
                statusPendingIntent)
                .build()

        assertThat(safetySourceStatus.statusLevel).isEqualTo(STATUS_LEVEL_NO_ISSUES)
    }

    @Test
    fun getPendingIntent_returnsPendingIntent() {
        val safetySourceStatus = SafetySourceStatus.Builder(
                "Status title",
                "Status summary",
                STATUS_LEVEL_NO_ISSUES,
                statusPendingIntent)
                .build()

        assertThat(safetySourceStatus.pendingIntent).isEqualTo(statusPendingIntent)
    }

    @Test
    fun describeContents_returns0() {
        val safetySourceStatus = SafetySourceStatus.Builder(
                "Status title",
                "Status summary",
                STATUS_LEVEL_NO_ISSUES,
                statusPendingIntent)
                .build()

        assertThat(safetySourceStatus.describeContents()).isEqualTo(0)
    }

    @Test
    fun createFromParcel_withWriteToParcel_returnsOriginalSafetySourceStatus() {
        val safetySourceStatus = SafetySourceStatus.Builder(
                "Status title",
                "Status summary",
                STATUS_LEVEL_NO_ISSUES,
                statusPendingIntent)
                .build()

        val parcel: Parcel = Parcel.obtain()
        safetySourceStatus.writeToParcel(parcel, 0 /* flags */)
        parcel.setDataPosition(0)
        val safetySourceStatusFromParcel: SafetySourceStatus =
                SafetySourceStatus.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(safetySourceStatusFromParcel).isEqualTo(safetySourceStatus)
    }

    // TODO(b/208473675): Use `EqualsTester` for testing `hashcode` and `equals`.
    @Test
    fun hashCode_equals_toString_withEqualByReferenceSafetySourceStatuses_areEqual() {
        val safetySourceStatus = SafetySourceStatus.Builder(
                "Status title",
                "Status summary",
                STATUS_LEVEL_NO_ISSUES,
                statusPendingIntent)
                .build()
        val otherSafetySourceStatus = safetySourceStatus

        assertThat(safetySourceStatus.hashCode()).isEqualTo(otherSafetySourceStatus.hashCode())
        assertThat(safetySourceStatus).isEqualTo(otherSafetySourceStatus)
        assertThat(safetySourceStatus.toString()).isEqualTo(otherSafetySourceStatus.toString())
    }

    @Test
    fun hashCode_equals_toString_withAllFieldsEqual_areEqual() {
        val safetySourceStatus = SafetySourceStatus.Builder(
                "Status title",
                "Status summary",
                STATUS_LEVEL_NO_ISSUES,
                statusPendingIntent)
                .build()
        val otherSafetySourceStatus = SafetySourceStatus.Builder(
                "Status title",
                "Status summary",
                STATUS_LEVEL_NO_ISSUES,
                statusPendingIntent)
                .build()

        assertThat(safetySourceStatus.hashCode()).isEqualTo(otherSafetySourceStatus.hashCode())
        assertThat(safetySourceStatus).isEqualTo(otherSafetySourceStatus)
        assertThat(safetySourceStatus.toString()).isEqualTo(otherSafetySourceStatus.toString())
    }

    @Test
    fun hashCode_equals_toString_withDifferentTitles_areNotEqual() {
        val safetySourceStatus = SafetySourceStatus.Builder(
                "Status title",
                "Status summary",
                STATUS_LEVEL_NO_ISSUES,
                statusPendingIntent)
                .build()
        val otherSafetySourceStatus = SafetySourceStatus.Builder(
                "Other status title",
                "Status summary",
                STATUS_LEVEL_NO_ISSUES,
                statusPendingIntent)
                .build()

        assertThat(safetySourceStatus.hashCode()).isNotEqualTo(otherSafetySourceStatus.hashCode())
        assertThat(safetySourceStatus).isNotEqualTo(otherSafetySourceStatus)
        assertThat(safetySourceStatus.toString()).isNotEqualTo(otherSafetySourceStatus.toString())
    }

    @Test
    fun hashCode_equals_toString_withDifferentSummaries_areNotEqual() {
        val safetySourceStatus = SafetySourceStatus.Builder(
                "Status title",
                "Status summary",
                STATUS_LEVEL_NO_ISSUES,
                statusPendingIntent)
                .build()
        val otherSafetySourceStatus = SafetySourceStatus.Builder(
                "Status title",
                "Other status summary",
                STATUS_LEVEL_NO_ISSUES,
                statusPendingIntent)
                .build()

        assertThat(safetySourceStatus.hashCode()).isNotEqualTo(otherSafetySourceStatus.hashCode())
        assertThat(safetySourceStatus).isNotEqualTo(otherSafetySourceStatus)
        assertThat(safetySourceStatus.toString()).isNotEqualTo(otherSafetySourceStatus.toString())
    }

    @Test
    fun hashCode_equals_toString_withDifferentStatusLevels_areNotEqual() {
        val safetySourceStatus = SafetySourceStatus.Builder(
                "Status title",
                "Status summary",
                STATUS_LEVEL_NO_ISSUES,
                statusPendingIntent)
                .build()
        val otherSafetySourceStatus = SafetySourceStatus.Builder(
                "Status title",
                "Status summary",
                STATUS_LEVEL_CRITICAL_WARNING,
                statusPendingIntent)
                .build()

        assertThat(safetySourceStatus.hashCode()).isNotEqualTo(otherSafetySourceStatus.hashCode())
        assertThat(safetySourceStatus).isNotEqualTo(otherSafetySourceStatus)
        assertThat(safetySourceStatus.toString()).isNotEqualTo(otherSafetySourceStatus.toString())
    }

    @Test
    fun hashCode_equals_toString_withDifferentPendingIntents_areNotEqual() {
        val safetySourceStatus = SafetySourceStatus.Builder(
                "Status title",
                "Status summary",
                STATUS_LEVEL_NO_ISSUES,
                PendingIntent.getActivity(context, 0 /* requestCode= */,
                        Intent("Status PendingIntent"), FLAG_IMMUTABLE))
                .build()
        val otherSafetySourceStatus = SafetySourceStatus.Builder(
                "Status title",
                "Status summary",
                STATUS_LEVEL_CRITICAL_WARNING,
                PendingIntent.getActivity(context, 0 /* requestCode= */,
                        Intent("Other status PendingIntent"), FLAG_IMMUTABLE))
                .build()

        assertThat(safetySourceStatus.hashCode()).isNotEqualTo(otherSafetySourceStatus.hashCode())
        assertThat(safetySourceStatus).isNotEqualTo(otherSafetySourceStatus)
        assertThat(safetySourceStatus.toString()).isNotEqualTo(otherSafetySourceStatus.toString())
    }
}