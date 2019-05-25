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
 * limitations under the License
 */

package android.accessibility.cts.common;

import android.util.Log;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Custom {@code TestRule} that dump UI data upon test failures.
 *
 * <p>Note: when using other {@code TestRule}s, make sure to use a {@code RuleChain} to ensure it
 * is applied outside of other rules that can fail a test (otherwise this rule may not know that the
 * test failed).
 *
 * <p>To capture the output of this rule, add the following to AndroidTest.xml:
 * <pre>
 *  <!-- Collect output of AccessibilityDumpOnFailureRule. -->
 *  <metrics_collector class="com.android.tradefed.device.metric.FilePullerLogCollector">
 *    <option name="directory-keys" value="/sdcard/<test.package.name>" />
 *    <option name="collect-on-run-ended-only" value="true" />
 *  </metrics_collector>
 * </pre>
 * <p>And disable external storage isolation:
 * <pre>
 *  <application ... android:requestLegacyExternalStorage="true" ... >
 * </pre>
 */
public class AccessibilityDumpOnFailureRule implements TestRule {

    private static final String LOG_TAG = AccessibilityDumpOnFailureRule.class.getSimpleName();

    private AccessibilityDumper mDumper = new AccessibilityDumper();

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    base.evaluate();
                } catch (Throwable t) {
                    // Ignore AssumptionViolatedException. It's not a test fail.
                    if (!(t instanceof AssumptionViolatedException)) {
                        onTestFailure(description, t);
                        throw t;
                    }
                }
            }
        };
    }

    public void dump(int flag) {
        mDumper.dump(flag);
    }

    protected void onTestFailure(Description description, Throwable t) {
        try {
            mDumper.setName(getTestNameFrom(description));
            mDumper.dump();
        } catch (Throwable throwable) {
            Log.e(LOG_TAG, "Dump fail", throwable);
        }
    }

    private String getTestNameFrom(Description description) {
        return description.getTestClass().getSimpleName()
                + "_" + description.getMethodName();
    }
}
