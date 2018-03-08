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

package android.signature.cts;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

public class DexMemberChecker {

    public interface Observer {
        void classAccessible(boolean accessible, DexApiDocumentParser.DexMember member);
        void fieldAccessibleViaReflection(boolean accessible, DexApiDocumentParser.DexField field);
        void fieldAccessibleViaJni(boolean accessible, DexApiDocumentParser.DexField field);
        void methodAccessibleViaReflection(boolean accessible,
                DexApiDocumentParser.DexMethod method);
        void methodAccessibleViaJni(boolean accessible, DexApiDocumentParser.DexMethod method);
    }

    public static void init() {
        System.loadLibrary("cts_dexchecker");
    }

    public static void checkSingleMember(DexApiDocumentParser.DexMember dexMember,
            DexMemberChecker.Observer observer) {
        Class<?> klass = findClass(dexMember);
        if (klass == null) {
            // Class not found. Therefore its members are not visible.
            observer.classAccessible(false, dexMember);
            return;
        }
        observer.classAccessible(true, dexMember);

        StringBuilder error = new StringBuilder("Hidden ");
        if (dexMember instanceof DexApiDocumentParser.DexField) {
            DexApiDocumentParser.DexField field = (DexApiDocumentParser.DexField) dexMember;
            observer.fieldAccessibleViaReflection(
                    hasMatchingField_Reflection(klass, field),
                    field);
            observer.fieldAccessibleViaJni(
                    hasMatchingField_JNI(klass, field),
                    field);
        } else if (dexMember instanceof DexApiDocumentParser.DexMethod) {
            DexApiDocumentParser.DexMethod method = (DexApiDocumentParser.DexMethod) dexMember;
            observer.methodAccessibleViaReflection(
                    hasMatchingMethod_Reflection(klass, method),
                    method);
            observer.methodAccessibleViaJni(
                    hasMatchingMethod_JNI(klass, method),
                    method);
        } else {
            throw new IllegalStateException("Unexpected type of dex member");
        }
    }

    private static boolean typesMatch(Class<?>[] classes, List<String> typeNames) {
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

    private static Class<?> findClass(DexApiDocumentParser.DexMember dexMember) {
        Class<?> klass = null;
        try {
            return Class.forName(dexMember.getJavaClassName());
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    private static boolean hasMatchingField_Reflection(Class<?> klass,
            DexApiDocumentParser.DexField dexField) {
        try {
            klass.getDeclaredField(dexField.getName());
            return true;
        } catch (NoSuchFieldException ex) {
            return false;
        }
    }

    private static boolean hasMatchingField_JNI(Class<?> klass,
            DexApiDocumentParser.DexField dexField) {
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

    private static boolean hasMatchingMethod_Reflection(Class<?> klass,
            DexApiDocumentParser.DexMethod dexMethod) {
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

    private static boolean hasMatchingMethod_JNI(Class<?> klass,
            DexApiDocumentParser.DexMethod dexMethod) {
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

    private static native boolean getField_JNI(Class<?> klass, String name, String type);
    private static native boolean getStaticField_JNI(Class<?> klass, String name, String type);
    private static native boolean getMethod_JNI(Class<?> klass, String name, String signature);
    private static native boolean getStaticMethod_JNI(Class<?> klass, String name,
            String signature);

}
