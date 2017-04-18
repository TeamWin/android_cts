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

import static com.google.common.truth.Truth.assertThat;

import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

@RunWith(AndroidJUnit4.class)
public class RetryRuleTest {

    private final Description mDescription = Description.createSuiteDescription("Whatever");

    private static final RetryableException sRetryableException =
            new RetryableException("Y U NO RETRY?");

    private static class RetryableStatement extends Statement {
        private final int mNumberFailures;
        private int mNumberCalls;

        RetryableStatement(int numberFailures) {
            mNumberFailures = numberFailures;
        }

        @Override
        public void evaluate() throws Throwable {
            mNumberCalls ++;
            if (mNumberCalls <= mNumberFailures) {
                throw sRetryableException;
            }
        }

        @Override
        public String toString() {
            return "RetryableStatement: failures=" + mNumberFailures + ", calls=" + mNumberCalls;
        }
    }

    @Test
    public void testPass() throws Throwable {
        final RetryRule rule = new RetryRule(2);
        rule.apply(new RetryableStatement(1), mDescription).evaluate();
    }

    @Test
    public void testFail() throws Throwable {
        final RetryRule rule = new RetryRule(2);
        try {
            rule.apply(new RetryableStatement(2), mDescription).evaluate();
            throw new AssertionError("2ND CALL, Y U NO FAIL?");
        } catch (RetryableException e) {
            assertThat(e).isSameAs(sRetryableException);
        }
    }
}
