/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.signature.cts.api;

import android.os.Bundle;
import android.signature.cts.ApiDocumentParser;
import android.signature.cts.ApiComplianceChecker;
import android.signature.cts.FailureType;
import android.signature.cts.JDiffClassDescription;
import android.signature.cts.ResultObserver;
import java.io.File;
import java.io.FileInputStream;
import repackaged.android.test.InstrumentationTestCase;
import repackaged.android.test.InstrumentationTestRunner;

import static android.signature.cts.CurrentApi.API_FILE_DIRECTORY;

/**
 * Performs the signature check via a JUnit test.
 */
public class SignatureTest extends InstrumentationTestCase {

    private static final String TAG = SignatureTest.class.getSimpleName();

    private TestResultObserver mResultObserver;

    private String[] expectedApiFiles;

    private class TestResultObserver implements ResultObserver {

        boolean mDidFail = false;

        StringBuilder mErrorString = new StringBuilder();

        @Override
        public void notifyFailure(FailureType type, String name, String errorMessage) {
            mDidFail = true;
            mErrorString.append("\n");
            mErrorString.append(type.toString().toLowerCase());
            mErrorString.append(":\t");
            mErrorString.append(name);
            mErrorString.append("\tError: ");
            mErrorString.append(errorMessage);
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResultObserver = new TestResultObserver();

        // Get the arguments passed to the instrumentation.
        Bundle instrumentationArgs =
                ((InstrumentationTestRunner) getInstrumentation()).getArguments();

        String argument = instrumentationArgs.getString("expected-api-files");
        expectedApiFiles = argument.split(",");
    }

    /**
     * Tests that the device's API matches the expected set defined in xml.
     * <p/>
     * Will check the entire API, and then report the complete list of failures
     */
    public void testSignature() {
        try {
            ApiComplianceChecker complianceChecker = new ApiComplianceChecker(mResultObserver);
            ApiDocumentParser apiDocumentParser = new ApiDocumentParser(
                    TAG, new ApiDocumentParser.Listener() {
                @Override
                public void completedClass(JDiffClassDescription classDescription) {
                    complianceChecker.checkSignatureCompliance(classDescription);
                }
            });

            for (String expectedApiFile : expectedApiFiles) {
                File file = new File(API_FILE_DIRECTORY + "/" + expectedApiFile);
                apiDocumentParser.parse(new FileInputStream(file));
            }
        } catch (Exception e) {
            mResultObserver.notifyFailure(FailureType.CAUGHT_EXCEPTION, e.getMessage(),
                    e.getMessage());
        }
        if (mResultObserver.mDidFail) {
            fail(mResultObserver.mErrorString.toString());
        }
    }
}
