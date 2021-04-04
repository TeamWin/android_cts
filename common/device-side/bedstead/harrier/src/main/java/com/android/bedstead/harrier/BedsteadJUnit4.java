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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A JUnit test runner for use with Bedstead.
 */
public final class BedsteadJUnit4 extends BlockJUnit4ClassRunner {

    // These are annotations which are not included indirectly
    private static final Set<String> sIgnoredAnnotationPackages = new HashSet<>();
    static {
        sIgnoredAnnotationPackages.add("java.lang.annotation");
        sIgnoredAnnotationPackages.add("com.android.bedstead.harrier.annotations.meta");
    }

    /**
     * {@link FrameworkMethod} subclass which allows modifying the test name and annotations.
     */
    public static final class BedsteadFrameworkMethod extends FrameworkMethod {

        private final Class<? extends Annotation> mParameterizedAnnotation;
        private final Map<Class<? extends Annotation>, Annotation> mAnnotationsMap =
                new HashMap<>();
        private Annotation[] mAnnotations;

        public BedsteadFrameworkMethod(Method method) {
            this(method, /* parameterizedAnnotation= */ null);
        }

        public BedsteadFrameworkMethod(Method method, Annotation parameterizedAnnotation) {
            super(method);
            this.mParameterizedAnnotation = (parameterizedAnnotation == null) ? null
                    : parameterizedAnnotation.annotationType();

            calculateAnnotations();
        }

        private void calculateAnnotations() {
            List<Annotation> annotations = new ArrayList<>(
                    Arrays.asList(getMethod().getAnnotations()));

            int index = 0;
            while (index < annotations.size()) {
                Annotation annotation = annotations.get(index);
                annotations.remove(index);
                List<Annotation> replacementAnnotations = getReplacementAnnotations(annotation);
                annotations.addAll(index, replacementAnnotations);
                index += replacementAnnotations.size();
            }

            this.mAnnotations = annotations.toArray(new Annotation[0]);
            for (Annotation annotation : annotations) {
                mAnnotationsMap.put(annotation.annotationType(), annotation);
            }
        }

        private List<Annotation> getReplacementAnnotations(Annotation annotation) {
            List<Annotation> replacementAnnotations = new ArrayList<>();

            if (annotation.annotationType().getAnnotation(ParameterizedAnnotation.class) != null
                    && !annotation.annotationType().equals(mParameterizedAnnotation)) {
                return replacementAnnotations;
            }

            for (Annotation indirectAnnotation : annotation.annotationType().getAnnotations()) {
                if (sIgnoredAnnotationPackages.contains(
                        indirectAnnotation.annotationType().getPackage().getName())) {
                    continue;
                }

                replacementAnnotations.addAll(getReplacementAnnotations(indirectAnnotation));
            }

            replacementAnnotations.add(annotation);

            return replacementAnnotations;
        }

        @Override
        public String getName() {
            if (mParameterizedAnnotation == null) {
                return super.getName();
            }
            return super.getName() + "[" + mParameterizedAnnotation.getSimpleName() + "]";
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

            return Objects.equal(mParameterizedAnnotation, other.mParameterizedAnnotation);
        }

        @Override
        public Annotation[] getAnnotations() {
            return mAnnotations;
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
            return (T) mAnnotationsMap.get(annotationType);
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
