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
package android.trust.cts

import android.Manifest.permission.ACCESS_KEYGUARD_SECURE_STORAGE
import android.Manifest.permission.PROVIDE_TRUST_AGENT
import android.app.trust.TrustManager
import android.trust.TrustTestActivity
import android.trust.UserUnlockRequestTrustAgent
import android.trust.cts.lib.TrustAgentRule
import androidx.test.InstrumentationRegistry
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Test for testing the user unlock trigger.
 *
 * atest TrustTestCases:UserUnlockRequestTest
 */
@RunWith(AndroidJUnit4::class)
class UserUnlockRequestTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiAutomation = instrumentation.uiAutomation
    private val context = instrumentation.context
    private val trustManager = context.getSystemService(TrustManager::class.java) as TrustManager
    private val userId = context.userId

    @get:Rule
    val rule: RuleChain = RuleChain
        .outerRule(ActivityScenarioRule(TrustTestActivity::class.java))
        .around(AdoptShellPermissionsRule(
            uiAutomation, PROVIDE_TRUST_AGENT, ACCESS_KEYGUARD_SECURE_STORAGE))
        .around(TrustAgentRule(context, UserUnlockRequestTrustAgent::class.java))

    @Test
    fun reportUserRequestedUnlock_propagatesToAgent() {
        val oldCount = UserUnlockRequestTrustAgent.instance().onUserRequestedUnlockCallCount
        trustManager.reportUserRequestedUnlock(userId)
        await()

        assertThat(UserUnlockRequestTrustAgent.instance().onUserRequestedUnlockCallCount)
            .isEqualTo(oldCount + 1)
    }

    companion object {
        private const val TAG = "UserUnlockRequestTest"
        private fun await() = Thread.sleep(250)
    }
}
