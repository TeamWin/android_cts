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
 * limitations under the License.
 */
package android.telephony.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class TestUtils {
    public static void assertSyntheticMethodOverloadExists(
            Class<?> clazz, String methodName, Class[] parameterTypes,
            Class<?> originalReturnType, Class<?> syntheticReturnType,
            boolean requireIdenticalExceptions) throws Exception {

        if (parameterTypes == null) {
            parameterTypes = new Class[0];
        }
        String fullMethodName = clazz + "." + methodName;

        // Assert we find the original, non-synthetic version using getDeclaredMethod().
        Method declaredMethod = clazz.getDeclaredMethod(methodName, parameterTypes);
        assertEquals(originalReturnType, declaredMethod.getReturnType());

        // Assert both versions of the method are returned from getDeclaredMethods().
        Method original = null;
        Method synthetic = null;
        for (Method method : clazz.getDeclaredMethods()) {
            if (methodMatches(methodName, parameterTypes, method)) {
                if (method.getReturnType().equals(syntheticReturnType)) {
                    synthetic = method;
                } else if (method.getReturnType().equals(originalReturnType)) {
                    original = method;
                }
            }
        }
        assertNotNull("Unable to find original signature: " + fullMethodName
                + ", returning " + originalReturnType, original);
        assertNotNull("Unable to find synthetic signature: " + fullMethodName
                + ", returning " + syntheticReturnType, synthetic);

        // Check modifiers are as expected.
        assertFalse(original.isSynthetic());
        assertFalse(original.isBridge());
        assertTrue(synthetic.isSynthetic());
        assertTrue(synthetic.isBridge());

        int originalModifiers = original.getModifiers();
        int syntheticModifiers = synthetic.getModifiers();

        // These masks aren't in the public API but are defined in the dex spec.
        int syntheticMask = 0x00001000;
        int bridgeMask = 0x00000040;
        int mask = syntheticMask | bridgeMask;
        assertEquals("Method modifiers for " + fullMethodName
                        + " are expected to be identical except for SYNTHETIC and BRIDGE."
                        + " original=" + Modifier.toString(originalModifiers)
                        + ", synthetic=" + Modifier.toString(syntheticModifiers),
                originalModifiers | mask,
                syntheticModifiers | mask);

        // Exceptions are not required at method resolution time but we check they're the same in
        // most cases for completeness.
        if (requireIdenticalExceptions) {
            assertArrayEquals("Exceptions for " + fullMethodName + " must be compatible",
                    original.getExceptionTypes(), synthetic.getExceptionTypes());
        }

        // Android doesn't support runtime type annotations so nothing to do for them.

        // Type parameters are *not* copied because they're not needed at method resolution time.
        assertEquals(0, synthetic.getTypeParameters().length);

        // Check method annotations.
        Annotation[] annotations = original.getDeclaredAnnotations();
        assertArrayEquals("Annotations differ between original and synthetic versions of "
                + fullMethodName, annotations, synthetic.getDeclaredAnnotations());
        Annotation[][] parameterAnnotations = original.getParameterAnnotations();
        // Check parameter annotations.
        assertArrayEquals("Annotations differ between original and synthetic versions of "
                + fullMethodName, parameterAnnotations, synthetic.getParameterAnnotations());
    }

    private static boolean methodMatches(String methodName, Class[] parameterTypes, Method method) {
        return method.getName().equals(methodName)
                && Arrays.equals(parameterTypes, method.getParameterTypes());
    }
}
