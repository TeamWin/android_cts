/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.harrier;

import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation;

import com.google.common.base.Objects;

import org.junit.Test;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A JUnit test runner for use with Bedstead.
 */
public final class BedsteadJUnit4 extends BlockJUnit4ClassRunner {

    /**
     * {@link FrameworkMethod} subclass which allows modifying the test name and annotations.
     */
    public static final class BedsteadFrameworkMethod extends FrameworkMethod {

        private final String mSuffix;
        private final Set<Annotation> mAnnotations;

        public BedsteadFrameworkMethod(Method method) {
            this(method, /* parameterizedAnnotation= */ null);
        }

        public BedsteadFrameworkMethod(Method method, Annotation parameterizedAnnotation) {
            super(method);
            if (parameterizedAnnotation == null) {
                mSuffix = null;
                mAnnotations = new HashSet<>();
            } else {
                this.mSuffix = parameterizedAnnotation.annotationType().getSimpleName();
                this.mAnnotations = new HashSet<>(
                        Arrays.asList(parameterizedAnnotation.annotationType().getAnnotations()));
            }
        }

        @Override
        public String getName() {
            if (mSuffix == null) {
                return super.getName();
            }
            return super.getName() + "[" + mSuffix + "]";
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            if (!(obj instanceof BedsteadFrameworkMethod)) {
                return false;
            }

            BedsteadFrameworkMethod other = (BedsteadFrameworkMethod) obj;

            return Objects.equal(mSuffix, other.mSuffix);
        }

        @Override
        public Annotation[] getAnnotations() {
            Set<Annotation> allAnnotations = new HashSet<>(mAnnotations);
            allAnnotations.addAll(Arrays.asList(getMethod().getAnnotations()));

            return allAnnotations.toArray(new Annotation[allAnnotations.size()]);
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
            for (Annotation a : mAnnotations) {
                if (annotationType.isInstance(a)) {
                    return (T) a;
                }
            }

            return super.getAnnotation(annotationType);
        }
    }

    public BedsteadJUnit4(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    protected List<FrameworkMethod> computeTestMethods() {
        TestClass testClass = getTestClass();

        List<FrameworkMethod> basicTests = testClass.getAnnotatedMethods(Test.class);
        List<FrameworkMethod> modifiedTests = new ArrayList<>();

        for (FrameworkMethod m : basicTests) {
            Set<Annotation> parameterizedAnnotations = getParameterizedAnnotations(m);

            if (parameterizedAnnotations.isEmpty()) {
                // Unparameterized, just add the original
                modifiedTests.add(new BedsteadFrameworkMethod(m.getMethod()));
            }

            for (Annotation annotation : parameterizedAnnotations) {
                modifiedTests.add(
                        new BedsteadFrameworkMethod(m.getMethod(), annotation));
            }
        }

        return modifiedTests;
    }

    private Set<Annotation> getParameterizedAnnotations(FrameworkMethod method) {
        Set<Annotation> parameterizedAnnotations = new HashSet<>();

        for (Annotation annotation : method.getMethod().getAnnotations()) {
            if (annotation.annotationType().getAnnotation(ParameterizedAnnotation.class) != null) {
                parameterizedAnnotations.add(annotation);
            }
        }

        return parameterizedAnnotations;
    }
}
