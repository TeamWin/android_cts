/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.signature.cts.tests;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import android.signature.cts.ApiComplianceChecker;
import android.signature.cts.ClassProvider;
import android.signature.cts.ExcludingClassProvider;
import android.signature.cts.FailureType;
import android.signature.cts.JDiffClassDescription;
import android.signature.cts.ResultObserver;
import android.signature.cts.tests.data.ApiAnnotation;
import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * Test class for JDiffClassDescription.
 */
@SuppressWarnings("deprecation")
public class ApiComplianceCheckerTest extends TestCase {

    private static final String VALUE = "VALUE";

    private class NoFailures implements ResultObserver {
        @Override
        public void notifyFailure(FailureType type, String name, String errmsg) {
            Assert.fail("Saw unexpected test failure: " + name + " failure type: " + type
                    + " error message: " + errmsg);
        }
    }

    private class ExpectFailure implements ResultObserver {
        private FailureType expectedType;
        private boolean failureSeen;

        ExpectFailure(FailureType expectedType) {
            this.expectedType = expectedType;
        }

        @Override
        public void notifyFailure(FailureType type, String name, String errMsg) {
            if (type == expectedType) {
                if (failureSeen) {
                    Assert.fail("Saw second test failure: " + name + " failure type: " + type);
                } else {
                    // We've seen the error, mark it and keep going
                    failureSeen = true;
                }
            } else {
                Assert.fail("Saw unexpected test failure: " + name + " failure type: " + type);
            }
        }

        void validate() {
            Assert.assertTrue(failureSeen);
        }
    }

    private void checkSignatureCompliance(JDiffClassDescription classDescription) {
        checkSignatureCompliance(classDescription, false);
    }

    private void checkSignatureCompliance(JDiffClassDescription classDescription,
            boolean doExactMatch, String... excludedRuntimeClassNames) {
        ResultObserver resultObserver = new NoFailures();
        checkSignatureCompliance(classDescription, resultObserver, doExactMatch,
                excludedRuntimeClassNames);
    }

    private void checkSignatureCompliance(JDiffClassDescription classDescription,
            ResultObserver resultObserver) {
        checkSignatureCompliance(classDescription, resultObserver, false);
    }

    private void checkSignatureCompliance(JDiffClassDescription classDescription,
            ResultObserver resultObserver, boolean doExactMatch, String... excludedRuntimeClasses) {
        ClassProvider provider = new TestClassesProvider();
        if (excludedRuntimeClasses.length != 0) {
            provider = new ExcludingClassProvider(provider,
                    name -> Arrays.stream(excludedRuntimeClasses)
                            .anyMatch(myname -> myname.equals(name)));
        }
        ApiComplianceChecker complianceChecker = new ApiComplianceChecker(resultObserver,
                provider);
        if (doExactMatch) {
            complianceChecker.setAnnotationForExactMatch(ApiAnnotation.class.getName());
        }
        complianceChecker.checkSignatureCompliance(classDescription);
        if (doExactMatch) {
            complianceChecker.checkExactMatch();
        }
    }

    /**
     * Create the JDiffClassDescription for "NormalClass".
     *
     * @return the new JDiffClassDescription
     */
    private JDiffClassDescription createNormalClass() {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "NormalClass");
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC);
        return clz;
    }

    public void testNormalClassCompliance() {
        JDiffClassDescription clz = createNormalClass();
        checkSignatureCompliance(clz);
        assertEquals(clz.toSignatureString(), "public class NormalClass");
    }

    public void testMissingClass() {
        ExpectFailure observer = new ExpectFailure(FailureType.MISSING_CLASS);
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "NoSuchClass");
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        checkSignatureCompliance(clz, observer);
        observer.validate();
    }

    public void testSimpleConstructor() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffConstructor constructor =
                new JDiffClassDescription.JDiffConstructor("NormalClass", Modifier.PUBLIC);
        clz.addConstructor(constructor);
        checkSignatureCompliance(clz);
        assertEquals(constructor.toSignatureString(), "public NormalClass()");
    }

    public void testOneArgConstructor() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffConstructor constructor =
                new JDiffClassDescription.JDiffConstructor("NormalClass", Modifier.PRIVATE);
        constructor.addParam("java.lang.String");
        clz.addConstructor(constructor);
        checkSignatureCompliance(clz);
        assertEquals(constructor.toSignatureString(), "private NormalClass(java.lang.String)");
    }

    public void testConstructorThrowsException() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffConstructor constructor =
                new JDiffClassDescription.JDiffConstructor("NormalClass", Modifier.PROTECTED);
        constructor.addParam("java.lang.String");
        constructor.addParam("java.lang.String");
        constructor.addException("android.signature.cts.tests.data.NormalException");
        clz.addConstructor(constructor);
        checkSignatureCompliance(clz);
        assertEquals(constructor.toSignatureString(),
                "protected NormalClass(java.lang.String, java.lang.String) " +
                "throws android.signature.cts.tests.data.NormalException");
    }

    public void testPackageProtectedConstructor() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffConstructor constructor =
                new JDiffClassDescription.JDiffConstructor("NormalClass", 0);
        constructor.addParam("java.lang.String");
        constructor.addParam("java.lang.String");
        constructor.addParam("java.lang.String");
        clz.addConstructor(constructor);
        checkSignatureCompliance(clz);
        assertEquals(constructor.toSignatureString(),
                "NormalClass(java.lang.String, java.lang.String, java.lang.String)");
    }

    public void testStaticMethod() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffMethod method = new JDiffClassDescription.JDiffMethod(
                "staticMethod", Modifier.STATIC | Modifier.PUBLIC, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
        assertEquals(method.toSignatureString(), "public static void staticMethod()");
    }

    public void testSyncMethod() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffMethod method = new JDiffClassDescription.JDiffMethod(
                "syncMethod", Modifier.SYNCHRONIZED | Modifier.PUBLIC, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
        assertEquals(method.toSignatureString(), "public synchronized void syncMethod()");
    }

    public void testPackageProtectMethod() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffMethod method = new JDiffClassDescription.JDiffMethod(
                "packageProtectedMethod", 0, "boolean");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
        assertEquals(method.toSignatureString(), "boolean packageProtectedMethod()");
    }

    public void testPrivateMethod() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffMethod method = new JDiffClassDescription.JDiffMethod(
                "privateMethod", Modifier.PRIVATE, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
        assertEquals(method.toSignatureString(), "private void privateMethod()");
    }

    public void testProtectedMethod() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffMethod method = new JDiffClassDescription.JDiffMethod(
                "protectedMethod", Modifier.PROTECTED, "java.lang.String");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
        assertEquals(method.toSignatureString(), "protected java.lang.String protectedMethod()");
    }

    public void testThrowsMethod() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffMethod method = new JDiffClassDescription.JDiffMethod(
                "throwsMethod", Modifier.PUBLIC, "void");
        method.addException("android.signature.cts.tests.data.NormalException");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
        assertEquals(method.toSignatureString(), "public void throwsMethod() " +
                "throws android.signature.cts.tests.data.NormalException");
    }

    public void testNativeMethod() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffMethod method = new JDiffClassDescription.JDiffMethod(
                "nativeMethod", Modifier.PUBLIC | Modifier.NATIVE, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
        assertEquals(method.toSignatureString(), "public native void nativeMethod()");
    }

    public void testFinalField() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "FINAL_FIELD", "java.lang.String", Modifier.PUBLIC | Modifier.FINAL, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals(field.toSignatureString(), "public final java.lang.String FINAL_FIELD");
    }

    public void testStaticField() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "STATIC_FIELD", "java.lang.String", Modifier.PUBLIC | Modifier.STATIC, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals(field.toSignatureString(), "public static java.lang.String STATIC_FIELD");
    }

    public void testVolatileFiled() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "VOLATILE_FIELD", "java.lang.String", Modifier.PUBLIC | Modifier.VOLATILE, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals(field.toSignatureString(), "public volatile java.lang.String VOLATILE_FIELD");
    }

    public void testTransientField() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "TRANSIENT_FIELD", "java.lang.String",
                Modifier.PUBLIC | Modifier.TRANSIENT, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals(field.toSignatureString(),
                "public transient java.lang.String TRANSIENT_FIELD");
    }

    public void testPackageField() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "PACAKGE_FIELD", "java.lang.String", 0, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals(field.toSignatureString(), "java.lang.String PACAKGE_FIELD");
    }

    public void testPrivateField() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "PRIVATE_FIELD", "java.lang.String", Modifier.PRIVATE, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals(field.toSignatureString(), "private java.lang.String PRIVATE_FIELD");
    }

    public void testProtectedField() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "PROTECTED_FIELD", "java.lang.String", Modifier.PROTECTED, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals(field.toSignatureString(), "protected java.lang.String PROTECTED_FIELD");
    }

    public void testFieldValue() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "VALUE_FIELD", "java.lang.String",
                Modifier.PUBLIC | Modifier.FINAL | Modifier.STATIC , "\"\\u2708\"");
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals(field.toSignatureString(),
                "public static final java.lang.String VALUE_FIELD");
    }

    public void testFieldValueChanged() {
        ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_FIELD);
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "VALUE_FIELD", "java.lang.String",
                Modifier.PUBLIC | Modifier.FINAL | Modifier.STATIC , "\"&#9992;\"");
        clz.addField(field);
        checkSignatureCompliance(clz, observer);
        assertEquals(field.toSignatureString(),
                "public static final java.lang.String VALUE_FIELD");
        observer.validate();
    }

    public void testInnerClass() {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "NormalClass.InnerClass");
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC);
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "innerClassData", "java.lang.String", Modifier.PRIVATE, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals(clz.toSignatureString(), "public class NormalClass.InnerClass");
    }

    public void testInnerInnerClass() {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "NormalClass.InnerClass.InnerInnerClass"
        );
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC);
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "innerInnerClassData", "java.lang.String", Modifier.PRIVATE, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals(clz.toSignatureString(),
                "public class NormalClass.InnerClass.InnerInnerClass");
    }

    public void testInnerInterface() {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "NormalClass.InnerInterface");
        clz.setType(JDiffClassDescription.JDiffType.INTERFACE);
        clz.setModifier(Modifier.PUBLIC | Modifier.STATIC | Modifier.ABSTRACT);
        clz.addMethod(
                new JDiffClassDescription.JDiffMethod("doSomething",
                    Modifier.PUBLIC | Modifier.ABSTRACT, "void"));
        checkSignatureCompliance(clz);
        assertEquals(clz.toSignatureString(), "public interface NormalClass.InnerInterface");
    }

    public void testInterface() {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "NormalInterface");
        clz.setType(JDiffClassDescription.JDiffType.INTERFACE);
        clz.setModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
        clz.addMethod(
                new JDiffClassDescription.JDiffMethod("doSomething",
                    Modifier.ABSTRACT| Modifier.PUBLIC, "void"));
        checkSignatureCompliance(clz);
        assertEquals(clz.toSignatureString(), "public interface NormalInterface");
    }

    public void testFinalClass() {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "FinalClass");
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC | Modifier.FINAL);
        checkSignatureCompliance(clz);
        assertEquals(clz.toSignatureString(), "public final class FinalClass");
    }

    /**
     * Test the case where the API declares the method not synchronized, but it
     * actually is.
     */
    public void testAddingSync() {
        ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_METHOD);
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffMethod method = new JDiffClassDescription.JDiffMethod(
                "syncMethod", Modifier.PUBLIC, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz, observer);
        observer.validate();
    }

    /**
     * Test the case where the API declares the method is synchronized, but it
     * actually is not.
     */
    public void testRemovingSync() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffMethod method = new JDiffClassDescription.JDiffMethod(
                "notSyncMethod", Modifier.SYNCHRONIZED | Modifier.PUBLIC, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
    }

    /**
     * API says method is not native, but it actually is. http://b/1839558
     */
    public void testAddingNative() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffMethod method = new JDiffClassDescription.JDiffMethod(
                "nativeMethod", Modifier.PUBLIC, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
    }

    /**
     * API says method is native, but actually isn't. http://b/1839558
     */
    public void testRemovingNative() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffMethod method = new JDiffClassDescription.JDiffMethod(
                "notNativeMethod", Modifier.NATIVE | Modifier.PUBLIC, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
    }

    public void testAbstractClass() {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "AbstractClass");
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
        checkSignatureCompliance(clz);
        assertEquals(clz.toSignatureString(), "public abstract class AbstractClass");
    }

    /**
     * API lists class as abstract, reflection does not. http://b/1839622
     */
    public void testRemovingAbstractFromAClass() {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "NormalClass");
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
        checkSignatureCompliance(clz);
    }

    /**
     * reflection lists class as abstract, api does not. http://b/1839622
     */
    public void testAddingAbstractToAClass() {
        ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_CLASS);
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "AbstractClass");
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC);
        checkSignatureCompliance(clz, observer);
        observer.validate();
    }

    public void testFinalMethod() {
        JDiffClassDescription clz = createNormalClass();
        JDiffClassDescription.JDiffMethod method = new JDiffClassDescription.JDiffMethod(
                "finalMethod", Modifier.PUBLIC | Modifier.FINAL, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
        assertEquals(method.toSignatureString(), "public final void finalMethod()");
    }

    /**
     * Final Class, API lists methods as non-final, reflection has it as final.
     * http://b/1839589
     */
    public void testAddingFinalToAMethodInAFinalClass() {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "FinalClass");
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC | Modifier.FINAL);
        JDiffClassDescription.JDiffMethod method = new JDiffClassDescription.JDiffMethod(
                "finalMethod", Modifier.PUBLIC, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
    }

    /**
     * Final Class, API lists methods as final, reflection has it as non-final.
     * http://b/1839589
     */
    public void testRemovingFinalToAMethodInAFinalClass() {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "FinalClass");
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC | Modifier.FINAL);
        JDiffClassDescription.JDiffMethod method = new JDiffClassDescription.JDiffMethod(
                "nonFinalMethod", Modifier.PUBLIC | Modifier.FINAL, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
    }

    /**
     * non-final Class, API lists methods as non-final, reflection has it as
     * final. http://b/1839589
     */
    public void testAddingFinalToAMethodInANonFinalClass() {
        ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_METHOD);
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "NormalClass");
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC);
        JDiffClassDescription.JDiffMethod method = new JDiffClassDescription.JDiffMethod(
                "finalMethod", Modifier.PUBLIC, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz, observer);
        observer.validate();
    }

    private static JDiffClassDescription createClass(String name) {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", name);
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC);
        return clz;
    }

    private static void addConstructor(JDiffClassDescription clz, String... paramTypes) {
        JDiffClassDescription.JDiffConstructor constructor = new JDiffClassDescription.JDiffConstructor(
                clz.getShortClassName(), Modifier.PUBLIC);
        if (paramTypes != null) {
            for (String type : paramTypes) {
                constructor.addParam(type);
            }
        }
        clz.addConstructor(constructor);
    }

    private static void addPublicVoidMethod(JDiffClassDescription clz, String name) {
        JDiffClassDescription.JDiffMethod method = new JDiffClassDescription.JDiffMethod(
                name, Modifier.PUBLIC, "void");
        clz.addMethod(method);
    }

    private static void addPublicBooleanField(JDiffClassDescription clz, String name) {
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                name, "boolean", Modifier.PUBLIC, VALUE);
        clz.addField(field);
    }

    /**
     * Documented API and runtime classes are exactly matched.
     */
    public void testExactApiMatch() {
        JDiffClassDescription clz = createClass("SystemApiClass");
        addConstructor(clz);
        addPublicVoidMethod(clz, "apiMethod");
        addPublicBooleanField(clz, "apiField");

        checkSignatureCompliance(clz, true,
                "android.signature.cts.tests.data.PublicApiClass",
                "android.signature.cts.tests.data.ForciblyPublicizedPrivateClass");

        clz = createClass("PublicApiClass");
        addConstructor(clz);
        addPublicVoidMethod(clz, "apiMethod");
        addPublicBooleanField(clz, "apiField");

        checkSignatureCompliance(clz, true,
                "android.signature.cts.tests.data.SystemApiClass",
                "android.signature.cts.tests.data.ForciblyPublicizedPrivateClass");
    }

    /**
     * A constructor is found in the runtime class, but not in the documented API
     */
    public void testDetectUnauthorizedConstructorApi() {
        ExpectFailure observer = new ExpectFailure(FailureType.EXTRA_METHOD);

        JDiffClassDescription clz = createClass("SystemApiClass");
        // (omitted) addConstructor(clz);
        addPublicVoidMethod(clz, "apiMethod");
        addPublicBooleanField(clz, "apiField");

        checkSignatureCompliance(clz, observer, true,
                "android.signature.cts.tests.data.PublicApiClass",
                "android.signature.cts.tests.data.ForciblyPublicizedPrivateClass");
        observer.validate();

        observer = new ExpectFailure(FailureType.EXTRA_METHOD);

        clz = createClass("PublicApiClass");
        // (omitted) addConstructor(clz);
        addPublicVoidMethod(clz, "apiMethod");
        addPublicBooleanField(clz, "apiField");

        checkSignatureCompliance(clz, observer, true,
                "android.signature.cts.tests.data.SystemApiClass",
                "android.signature.cts.tests.data.ForciblyPublicizedPrivateClass");
        observer.validate();
    }

    /**
     * A method is found in the runtime class, but not in the documented API
     */
    public void testDetectUnauthorizedMethodApi() {
        ExpectFailure observer = new ExpectFailure(FailureType.EXTRA_METHOD);

        JDiffClassDescription clz = createClass("SystemApiClass");
        addConstructor(clz);
        // (omitted) addPublicVoidMethod(clz, "apiMethod");
        addPublicBooleanField(clz, "apiField");

        checkSignatureCompliance(clz, observer, true,
                "android.signature.cts.tests.data.PublicApiClass",
                "android.signature.cts.tests.data.ForciblyPublicizedPrivateClass");
        observer.validate();

        observer = new ExpectFailure(FailureType.EXTRA_METHOD);

        clz = createClass("PublicApiClass");
        addConstructor(clz);
        // (omitted) addPublicVoidMethod(clz, "apiMethod");
        addPublicBooleanField(clz, "apiField");

        checkSignatureCompliance(clz, observer, true,
                "android.signature.cts.tests.data.SystemApiClass",
                "android.signature.cts.tests.data.ForciblyPublicizedPrivateClass");
        observer.validate();
    }

    /**
     * A field is found in the runtime class, but not in the documented API
     */
    public void testDetectUnauthorizedFieldApi() {
        ExpectFailure observer = new ExpectFailure(FailureType.EXTRA_FIELD);

        JDiffClassDescription clz = createClass("SystemApiClass");
        addConstructor(clz);
        addPublicVoidMethod(clz, "apiMethod");
        // (omitted) addPublicBooleanField(clz, "apiField");

        checkSignatureCompliance(clz, observer, true,
                "android.signature.cts.tests.data.PublicApiClass",
                "android.signature.cts.tests.data.ForciblyPublicizedPrivateClass");
        observer.validate();

        observer = new ExpectFailure(FailureType.EXTRA_FIELD);

        clz = createClass("PublicApiClass");
        addConstructor(clz);
        addPublicVoidMethod(clz, "apiMethod");
        // (omitted) addPublicBooleanField(clz, "apiField");

        checkSignatureCompliance(clz, observer, true,
                "android.signature.cts.tests.data.SystemApiClass",
                "android.signature.cts.tests.data.ForciblyPublicizedPrivateClass");
        observer.validate();
    }

    /**
     * A class is found in the runtime classes, but not in the documented API
     */
    public void testDetectUnauthorizedClassApi() {
        ExpectFailure observer = new ExpectFailure(FailureType.EXTRA_CLASS);
        JDiffClassDescription clz = createClass("SystemApiClass");
        addConstructor(clz);
        addPublicVoidMethod(clz, "apiMethod");
        addPublicBooleanField(clz, "apiField");

        checkSignatureCompliance(clz, observer, true,
                "android.signature.cts.tests.data.PublicApiClass");
        // Note that ForciblyPublicizedPrivateClass is now included in the runtime classes
        observer.validate();

        observer = new ExpectFailure(FailureType.EXTRA_CLASS);

        clz = createClass("PublicApiClass");
        addConstructor(clz);
        addPublicVoidMethod(clz, "apiMethod");
        addPublicBooleanField(clz, "apiField");

        checkSignatureCompliance(clz, observer, true,
                "android.signature.cts.tests.data.SystemApiClass");
        // Note that ForciblyPublicizedPrivateClass is now included in the runtime classes
        observer.validate();
    }

    /**
     * A member which is declared in an annotated class is currently recognized as an API.
     */
    public void testB71630695() {
        // TODO(b/71630695): currently, some API members are not annotated, because
        // a member is automatically added to the API set if it is in a class with
        // annotation and it is not @hide. This should be fixed, but until then,
        // CTS should respect the existing behavior.
        JDiffClassDescription clz = createClass("SystemApiClass");
        addConstructor(clz);
        addPublicVoidMethod(clz, "apiMethod");
        addPublicBooleanField(clz, "apiField");
        addConstructor(clz, "float"); // this is not annotated

        checkSignatureCompliance(clz, true,
                "android.signature.cts.tests.data.PublicApiClass",
                "android.signature.cts.tests.data.ForciblyPublicizedPrivateClass");

        clz = createClass("SystemApiClass");
        addConstructor(clz);
        addPublicVoidMethod(clz, "apiMethod");
        addPublicBooleanField(clz, "apiField");
        addPublicVoidMethod(clz, "unannotatedApiMethod"); // this is not annotated

        checkSignatureCompliance(clz, true,
                "android.signature.cts.tests.data.PublicApiClass",
                "android.signature.cts.tests.data.ForciblyPublicizedPrivateClass");

        clz = createClass("SystemApiClass");
        addConstructor(clz);
        addPublicVoidMethod(clz, "apiMethod");
        addPublicBooleanField(clz, "apiField");
        addPublicBooleanField(clz, "unannotatedApiField"); // this is not annotated

        checkSignatureCompliance(clz, true,
                "android.signature.cts.tests.data.PublicApiClass",
                "android.signature.cts.tests.data.ForciblyPublicizedPrivateClass");
    }

    /**
     * An API is documented, but isn't annotated in the runtime class. But, due to b/71630695, this
     * test can only be done for public API classes.
     */
    public void testDetectMissingAnnotation() {
        ExpectFailure observer = new ExpectFailure(FailureType.MISSING_ANNOTATION);

        JDiffClassDescription clz = createClass("PublicApiClass");
        addConstructor(clz);
        addPublicVoidMethod(clz, "apiMethod");
        addPublicBooleanField(clz, "apiField");
        addConstructor(clz, "int"); // this is not annotated

        checkSignatureCompliance(clz, observer, true,
                "android.signature.cts.tests.data.SystemApiClass",
                "android.signature.cts.tests.data.ForciblyPublicizedPrivateClass");
        observer.validate();

        observer = new ExpectFailure(FailureType.MISSING_ANNOTATION);

        clz = createClass("PublicApiClass");
        addConstructor(clz);
        addPublicVoidMethod(clz, "apiMethod");
        addPublicBooleanField(clz, "apiField");
        addPublicVoidMethod(clz, "privateMethod"); // this is not annotated

        checkSignatureCompliance(clz, observer, true,
                "android.signature.cts.tests.data.SystemApiClass",
                "android.signature.cts.tests.data.ForciblyPublicizedPrivateClass");
        observer.validate();

        observer = new ExpectFailure(FailureType.MISSING_ANNOTATION);

        clz = createClass("PublicApiClass");
        addConstructor(clz);
        addPublicVoidMethod(clz, "apiMethod");
        addPublicBooleanField(clz, "apiField");
        addPublicBooleanField(clz, "privateField"); // this is not annotated

        checkSignatureCompliance(clz, observer, true,
                "android.signature.cts.tests.data.SystemApiClass",
                "android.signature.cts.tests.data.ForciblyPublicizedPrivateClass");
        observer.validate();
    }

    /**
     * A <code>@hide</code> method should be recognized as API though it is not annotated, if it is
     * overriding a method which is already an API.
     */
    public void testOverriddenHidenMethodIsApi() {
        JDiffClassDescription clz = createClass("PublicApiClass");
        addConstructor(clz);
        addPublicVoidMethod(clz, "apiMethod");
        addPublicBooleanField(clz, "apiField");
        addPublicVoidMethod(clz, "anOverriddenMethod"); // not annotated and @hide, but is API

        checkSignatureCompliance(clz, true,
                "android.signature.cts.tests.data.SystemApiClass",
                "android.signature.cts.tests.data.ForciblyPublicizedPrivateClass");

    }
}
