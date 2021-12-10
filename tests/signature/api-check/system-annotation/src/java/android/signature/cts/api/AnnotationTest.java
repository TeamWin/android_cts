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

import android.os.Build;
import android.os.Bundle;
import android.signature.cts.AnnotationChecker;
import android.signature.cts.ApiDocumentParser;

import android.signature.cts.LogHelper;
import android.util.Log;
import androidx.test.InstrumentationRegistry;
import com.android.compatibility.common.util.DynamicConfigDeviceSide;
import com.android.compatibility.common.util.PropertyUtil;
import java.util.List;

/**
 * Checks that parts of the device's API that are annotated (e.g. with android.annotation.SystemApi)
 * match the API definition.
 */
public class AnnotationTest extends AbstractApiTest {

    private static final String TAG = AnnotationTest.class.getSimpleName();
    private static final String MODULE_NAME = "CtsSystemApiAnnotationTestCases";

    private String[] expectedApiFiles;
    private String annotationForExactMatch;

    @Override
    protected void initializeFromArgs(Bundle instrumentationArgs) throws Exception {
        expectedApiFiles = getCommaSeparatedList(instrumentationArgs, "expected-api-files");
        annotationForExactMatch = instrumentationArgs.getString("annotation-for-exact-match");

        // Make sure that the Instrumentation provided to this test is registered so it can be
        // retrieved by the DynamicConfigDeviceSide below.
        InstrumentationRegistry.registerInstance(getInstrumentation(), new Bundle());

        // Get the DynamicConfig.xml contents and extract the expected failures list.
        DynamicConfigDeviceSide dcds = new DynamicConfigDeviceSide(MODULE_NAME);
        List<String> expectedFailures = dcds.getValues("expected_failures");
        initExpectedFailures(expectedFailures);
    }

    /**
     * Tests that the parts of the device's API that are annotated (e.g. with
     * android.annotation.SystemApi) match the API definition.
     */
    public void testAnnotation() {
        if ("true".equals(PropertyUtil.getProperty("ro.treble.enabled")) &&
                PropertyUtil.getFirstApiLevel() > Build.VERSION_CODES.O_MR1) {
            runWithTestResultObserver(resultObserver -> {
                AnnotationChecker complianceChecker = new AnnotationChecker(resultObserver,
                        classProvider, annotationForExactMatch);

                ApiDocumentParser apiDocumentParser = new ApiDocumentParser(TAG);

                parseApiResourcesAsStream(apiDocumentParser, expectedApiFiles)
                        .forEach(complianceChecker::checkSignatureCompliance);

                // After done parsing all expected API files, perform any deferred checks.
                complianceChecker.checkDeferred();
            });
        }
    }
}
