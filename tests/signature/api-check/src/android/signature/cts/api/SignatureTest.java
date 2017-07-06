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

import static android.signature.cts.CurrentApi.API_FILE_DIRECTORY;
import static android.signature.cts.CurrentApi.TAG_ROOT;
import static android.signature.cts.CurrentApi.TAG_PACKAGE;
import static android.signature.cts.CurrentApi.TAG_CLASS;
import static android.signature.cts.CurrentApi.TAG_INTERFACE;
import static android.signature.cts.CurrentApi.TAG_IMPLEMENTS;
import static android.signature.cts.CurrentApi.TAG_CONSTRUCTOR;
import static android.signature.cts.CurrentApi.TAG_METHOD;
import static android.signature.cts.CurrentApi.TAG_PARAM;
import static android.signature.cts.CurrentApi.TAG_EXCEPTION;
import static android.signature.cts.CurrentApi.TAG_FIELD;

import static android.signature.cts.CurrentApi.ATTRIBUTE_NAME;
import static android.signature.cts.CurrentApi.ATTRIBUTE_TYPE;

import android.os.Bundle;
import android.signature.cts.CurrentApi;
import android.signature.cts.FailureType;
import android.signature.cts.JDiffClassDescription;
import android.signature.cts.JDiffClassDescription.JDiffConstructor;
import android.signature.cts.JDiffClassDescription.JDiffField;
import android.signature.cts.JDiffClassDescription.JDiffMethod;
import android.signature.cts.ResultObserver;
import android.util.Log;

import repackaged.android.test.InstrumentationTestCase;
import repackaged.android.test.InstrumentationTestRunner;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Performs the signature check via a JUnit test.
 */
public class SignatureTest extends InstrumentationTestCase {

    private static final String TAG = SignatureTest.class.getSimpleName();

    private HashSet<String> mKeyTagSet;
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
        mKeyTagSet = new HashSet<>();
        mKeyTagSet.addAll(Arrays.asList(TAG_PACKAGE, TAG_CLASS, TAG_INTERFACE, TAG_IMPLEMENTS,
                TAG_CONSTRUCTOR, TAG_METHOD, TAG_PARAM, TAG_EXCEPTION, TAG_FIELD));
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
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            for (String expectedApiFile : expectedApiFiles) {
                File file = new File(API_FILE_DIRECTORY + "/" + expectedApiFile);
                parser.setInput(new FileInputStream(file), null);
                start(parser);
            }
        } catch (Exception e) {
            mResultObserver.notifyFailure(FailureType.CAUGHT_EXCEPTION, e.getMessage(),
                    e.getMessage());
        }
        if (mResultObserver.mDidFail) {
            fail(mResultObserver.mErrorString.toString());
        }
    }

    private void beginDocument(XmlPullParser parser, String firstElementName)
            throws XmlPullParserException, IOException {
        int type;
        do {
            type = parser.next();
        } while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT);

        if (type != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }

        if (!parser.getName().equals(firstElementName)) {
            throw new XmlPullParserException("Unexpected start tag: found " + parser.getName() +
                    ", expected " + firstElementName);
        }
    }

    /**
     * Signature test entry point.
     */
    private void start(XmlPullParser parser) throws XmlPullParserException, IOException {
        logd(String.format("Name: %s", parser.getName()));
        logd(String.format("Text: %s", parser.getText()));
        logd(String.format("Namespace: %s", parser.getNamespace()));
        logd(String.format("Line Number: %s", parser.getLineNumber()));
        logd(String.format("Column Number: %s", parser.getColumnNumber()));
        logd(String.format("Position Description: %s", parser.getPositionDescription()));
        JDiffClassDescription currentClass = null;
        String currentPackage = "";
        JDiffMethod currentMethod = null;

        beginDocument(parser, TAG_ROOT);
        int type;
        while (true) {
            do {
                type = parser.next();
            } while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT
                    && type != XmlPullParser.END_TAG);

            if (type == XmlPullParser.END_TAG) {
                if (TAG_CLASS.equals(parser.getName())
                        || TAG_INTERFACE.equals(parser.getName())) {
                    currentClass.checkSignatureCompliance();
                } else if (TAG_PACKAGE.equals(parser.getName())) {
                    currentPackage = "";
                }
                continue;
            }

            if (type == XmlPullParser.END_DOCUMENT) {
                break;
            }

            String tagname = parser.getName();
            if (!mKeyTagSet.contains(tagname)) {
                continue;
            }

            if (type == XmlPullParser.START_TAG && tagname.equals(TAG_PACKAGE)) {
                currentPackage = parser.getAttributeValue(null, ATTRIBUTE_NAME);
            } else if (tagname.equals(TAG_CLASS)) {
                currentClass = CurrentApi.loadClassInfo(
                            parser, false, currentPackage, mResultObserver);
            } else if (tagname.equals(TAG_INTERFACE)) {
                currentClass = CurrentApi.loadClassInfo(
                            parser, true, currentPackage, mResultObserver);
            } else if (tagname.equals(TAG_IMPLEMENTS)) {
                currentClass.addImplInterface(parser.getAttributeValue(null, ATTRIBUTE_NAME));
            } else if (tagname.equals(TAG_CONSTRUCTOR)) {
                JDiffConstructor constructor =
                        CurrentApi.loadConstructorInfo(parser, currentClass);
                currentClass.addConstructor(constructor);
                currentMethod = constructor;
            } else if (tagname.equals(TAG_METHOD)) {
                currentMethod = CurrentApi.loadMethodInfo(currentClass.getClassName(), parser);
                currentClass.addMethod(currentMethod);
            } else if (tagname.equals(TAG_PARAM)) {
                currentMethod.addParam(parser.getAttributeValue(null, ATTRIBUTE_TYPE));
            } else if (tagname.equals(TAG_EXCEPTION)) {
                currentMethod.addException(parser.getAttributeValue(null, ATTRIBUTE_TYPE));
            } else if (tagname.equals(TAG_FIELD)) {
                JDiffField field = CurrentApi.loadFieldInfo(currentClass.getClassName(), parser);
                currentClass.addField(field);
            } else {
                throw new RuntimeException(
                        "unknown tag exception:" + tagname);
            }
            if (currentPackage != null) {
                logd(String.format("currentPackage: %s", currentPackage));
            }
            if (currentClass != null) {
                logd(String.format("currentClass: %s", currentClass.toSignatureString()));
            }
            if (currentMethod != null) {
                logd(String.format("currentMethod: %s", currentMethod.toSignatureString()));
            }
        }
    }

    private static void logd(String msg) {
        Log.d(TAG, msg);
    }
}
