/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.signature.cts.DexApiDocumentParser;
import android.signature.cts.DexApiDocumentParser.DexField;
import android.signature.cts.DexApiDocumentParser.DexMember;
import android.signature.cts.DexApiDocumentParser.DexMethod;
import android.signature.cts.FailureType;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.text.ParseException;

import static android.signature.cts.CurrentApi.API_FILE_DIRECTORY;

/**
 * Checks that it is not possible to access hidden APIs.
 */
public class HiddenApiTest extends AbstractApiTest {

    private String[] hiddenApiFiles;

    @Override
    protected void initializeFromArgs(Bundle instrumentationArgs) throws Exception {
        hiddenApiFiles = getCommaSeparatedList(instrumentationArgs, "hidden-api-files");
    }

    /**
     * Tests that the device does not expose APIs on the provided lists of
     * DEX signatures.
     *
     * Will check the entire API, and then report the complete list of failures
     */
    public void testSignature() {
        System.loadLibrary("cts_hiddenapi");
        runWithTestResultObserver(resultObserver -> {
            parseDexApiFilesAsStream(hiddenApiFiles).forEach(dexMember -> {
                checkSingleMember(dexMember, resultObserver);
            });
        });
    }

    /**
     * Check that a DexMember cannot be discovered with reflection or JNI, and
     * record results in the result
     */
    private void checkSingleMember(DexMember dexMember, TestResultObserver resultObserver) {
        Class<?> klass = findClass(dexMember);
        if (klass == null) {
            // Class not found. Therefore its members are not visible.
            return;
        }

        if (dexMember instanceof DexField) {
            if (hasMatchingField_Reflection(klass, (DexField) dexMember)) {
                resultObserver.notifyFailure(
                        FailureType.EXTRA_FIELD,
                        dexMember.toString(),
                        "Hidden field accessible through reflection");
            }
            if (hasMatchingField_JNI(klass, (DexField) dexMember)) {
                resultObserver.notifyFailure(
                        FailureType.EXTRA_FIELD,
                        dexMember.toString(),
                        "Hidden field accessible through JNI");
            }
        } else if (dexMember instanceof DexMethod) {
            if (hasMatchingMethod_Reflection(klass, (DexMethod) dexMember)) {
                resultObserver.notifyFailure(
                        FailureType.EXTRA_METHOD,
                        dexMember.toString(),
                        "Hidden method accessible through reflection");
            }
            if (hasMatchingMethod_JNI(klass, (DexMethod) dexMember)) {
                resultObserver.notifyFailure(
                        FailureType.EXTRA_METHOD,
                        dexMember.toString(),
                        "Hidden method accessible through JNI");
            }
        } else {
            throw new IllegalStateException("Unexpected type of dex member");
        }
    }

    private boolean typesMatch(Class<?>[] classes, List<String> typeNames) {
        if (classes.length != typeNames.size()) {
            return false;
        }
        for (int i = 0; i < classes.length; ++i) {
            if (!classes[i].getTypeName().equals(typeNames.get(i))) {
                return false;
            }
        }
        return true;
    }

    private Class<?> findClass(DexMember dexMember) {
        Class<?> klass = null;
        try {
            return Class.forName(dexMember.getJavaClassName());
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    private static boolean hasMatchingField_Reflection(Class<?> klass, DexField dexField) {
        try {
            klass.getDeclaredField(dexField.getName());
            return true;
        } catch (NoSuchFieldException ex) {
            return false;
        }
    }

    private static boolean hasMatchingField_JNI(Class<?> klass, DexField dexField) {
        try {
            getField_JNI(klass, dexField.getName(), dexField.getDexType());
            return true;
        } catch (NoSuchFieldError ex) {
        }
        try {
            getStaticField_JNI(klass, dexField.getName(), dexField.getDexType());
            return true;
        } catch (NoSuchFieldError ex) {
        }
        return false;
    }

    private boolean hasMatchingMethod_Reflection(Class<?> klass, DexMethod dexMethod) {
        List<String> methodParams = dexMethod.getJavaParameterTypes();

        if (dexMethod.isConstructor()) {
            for (Constructor constructor : klass.getDeclaredConstructors()) {
                if (typesMatch(constructor.getParameterTypes(), methodParams)) {
                    return true;
                }
            }
        } else {
            for (Method method : klass.getDeclaredMethods()) {
                if (method.getName().equals(dexMethod.getName())
                        && typesMatch(method.getParameterTypes(), methodParams)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasMatchingMethod_JNI(Class<?> klass, DexMethod dexMethod) {
        try {
            getMethod_JNI(klass, dexMethod.getName(), dexMethod.getDexSignature());
            return true;
        } catch (NoSuchMethodError ex) {
        }
        if (!dexMethod.isConstructor()) {
            try {
                getStaticMethod_JNI(klass, dexMethod.getName(), dexMethod.getDexSignature());
                return true;
            } catch (NoSuchMethodError ex) {
            }
        }
        return false;
    }

    private static Stream<DexMember> parseDexApiFilesAsStream(String[] apiFiles) {
        DexApiDocumentParser dexApiDocumentParser = new DexApiDocumentParser();
        return Stream.of(apiFiles)
                .map(name -> new File(API_FILE_DIRECTORY + "/" + name))
                .flatMap(file -> readFile(file))
                .flatMap(stream -> dexApiDocumentParser.parseAsStream(stream));
    }

    private static native boolean getField_JNI(Class<?> klass, String name, String type);
    private static native boolean getStaticField_JNI(Class<?> klass, String name, String type);
    private static native boolean getMethod_JNI(Class<?> klass, String name, String signature);
    private static native boolean getStaticMethod_JNI(Class<?> klass, String name,
            String signature);
}
