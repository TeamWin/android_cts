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
import android.signature.cts.ApiComplianceChecker;
import android.signature.cts.ApiDocumentParser;
import android.signature.cts.ClassProvider;
import android.signature.cts.FailureType;
import android.signature.cts.JDiffClassDescription;
import android.signature.cts.ReflectionHelper;
import com.google.common.base.Suppliers;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * Performs the signature check via a JUnit test.
 */
public class SignatureTest extends AbstractApiTest {

    private static final String TAG = SignatureTest.class.getSimpleName();

    protected static final Supplier<String[]> EXPECTED_API_FILES =
            getSupplierOfAnOptionalCommaSeparatedListArgument("expected-api-files");
    protected static final Supplier<String[]> BASE_API_FILES =
            getSupplierOfAnOptionalCommaSeparatedListArgument("base-api-files");
    protected static final Supplier<String[]> UNEXPECTED_API_FILES =
            getSupplierOfAnOptionalCommaSeparatedListArgument("unexpected-api-files");
    protected static final Supplier<String[]> PREVIOUS_API_FILES =
            getSupplierOfAnOptionalCommaSeparatedListArgument("previous-api-files");

    protected static final Supplier<Set<JDiffClassDescription>> UNEXPECTED_CLASSES =
            Suppliers.memoize(SignatureTest::loadUnexpectedClasses)::get;

    @Override
    protected void initializeFromArgs(Bundle instrumentationArgs) throws Exception {
        String[] expectedApiFiles = EXPECTED_API_FILES.get();
        String[] unexpectedApiFiles = UNEXPECTED_API_FILES.get();

        if (expectedApiFiles.length + unexpectedApiFiles.length == 0) {
            throw new IllegalStateException(
                    "Expected at least one file to be specified in"
                            + " 'expected-api-files' or 'unexpected-api-files'");
        }
    }

    /**
     * Tests that the device's API matches the expected set defined in xml.
     * <p/>
     * Will check the entire API, and then report the complete list of failures
     */
    @Test
    public void testSignature() {
        runWithTestResultObserver(mResultObserver -> {
            Set<JDiffClassDescription> unexpectedClasses = UNEXPECTED_CLASSES.get();
            for (JDiffClassDescription classDescription : unexpectedClasses) {
                Class<?> unexpectedClass = findUnexpectedClass(classDescription, mClassProvider);
                if (unexpectedClass != null) {
                    mResultObserver.notifyFailure(
                            FailureType.UNEXPECTED_CLASS,
                            classDescription.getAbsoluteClassName(),
                            "Class should not be accessible to this APK");
                }
            }

            ApiComplianceChecker complianceChecker =
                    new ApiComplianceChecker(mResultObserver, mClassProvider);

            // Load classes from any API files that form the base which the expected APIs extend.
            loadBaseClasses(complianceChecker);
            // Load classes from system API files and check for signature compliance.
            String[] expectedApiFiles = EXPECTED_API_FILES.get();
            checkClassesSignatureCompliance(complianceChecker, expectedApiFiles, unexpectedClasses,
                    false /* isPreviousApi */);
            // Load classes from previous API files and check for signature compliance.
            String[] previousApiFiles = PREVIOUS_API_FILES.get();
            checkClassesSignatureCompliance(complianceChecker, previousApiFiles, unexpectedClasses,
                    true /* isPreviousApi */);

            // After done parsing all expected API files, perform any deferred checks.
            complianceChecker.checkDeferred();
        });
    }

    private static <T> Predicate<T> not(Predicate<T> predicate) {
        return predicate.negate();
    }

    private Class<?> findUnexpectedClass(JDiffClassDescription classDescription,
            ClassProvider classProvider) {
        try {
            return ReflectionHelper.findMatchingClass(classDescription, classProvider);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Set<JDiffClassDescription> loadUnexpectedClasses() {
        ApiDocumentParser apiDocumentParser = new ApiDocumentParser(TAG);
        return retrieveApiResourcesAsStream(SignatureTest.class.getClassLoader(), UNEXPECTED_API_FILES.get())
                .flatMap(apiDocumentParser::parseAsStream)
                .collect(Collectors.toCollection(SignatureTest::newSetOfClassDescriptions));
    }

    private static TreeSet<JDiffClassDescription> newSetOfClassDescriptions() {
        return new TreeSet<>(Comparator.comparing(JDiffClassDescription::getAbsoluteClassName));
    }

    private void loadBaseClasses(ApiComplianceChecker complianceChecker) {
        ApiDocumentParser apiDocumentParser = new ApiDocumentParser(TAG);
        parseApiResourcesAsStream(apiDocumentParser, BASE_API_FILES.get())
                .forEach(complianceChecker::addBaseClass);
    }

    private void checkClassesSignatureCompliance(ApiComplianceChecker complianceChecker,
            String[] classes, Set<JDiffClassDescription> unexpectedClasses, boolean isPreviousApi) {
        ApiDocumentParser apiDocumentParser = new ApiDocumentParser(TAG);
        parseApiResourcesAsStream(apiDocumentParser, classes)
                .filter(not(unexpectedClasses::contains))
                .map(clazz -> clazz.setPreviousApiFlag(isPreviousApi))
                .forEach(complianceChecker::checkSignatureCompliance);
    }

}
