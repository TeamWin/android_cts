/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.cts.core.runner;

import android.support.test.internal.runner.ClassPathScanner;
import android.support.test.internal.runner.ClassPathScanner.ClassNameFilter;
import android.support.test.internal.runner.TestLoader;
import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Find tests in the current APK.
 */
public class TestClassFinder {

    // Excluded test packages
    private static final String[] DEFAULT_EXCLUDED_PACKAGES = {
            "junit",
            "org.junit",
            "org.hamcrest",
            "org.mockito",// exclude Mockito for performance and to prevent JVM related errors
            "android.support.test.internal.runner.junit3",// always skip AndroidTestSuite
    };

    static Collection<Class<?>> getClasses(List<String> apks, ClassLoader loader) {
        List<Class<?>> classes = new ArrayList<>();
        ClassPathScanner scanner = new ClassPathScanner(apks);

        ClassPathScanner.ChainedClassNameFilter filter =
                new ClassPathScanner.ChainedClassNameFilter();
        // exclude inner classes
        filter.add(new ClassPathScanner.ExternalClassNameFilter());

        // exclude default classes
        for (String defaultExcludedPackage : DEFAULT_EXCLUDED_PACKAGES) {
            filter.add(new ExcludePackageNameFilter(defaultExcludedPackage));
        }

        TestLoader testLoader = new TestLoader();
        testLoader.setClassLoader(loader);

        try {
            Set<String> classNames = scanner.getClassPathEntries(filter);
            for (String className : classNames) {
                Class<?> cls = testLoader.loadIfTest(className);
                if (cls != null) {
                    classes.add(cls);
                }
            }
            return classes;
        } catch (IOException e) {
            Log.e(CoreTestRunner.TAG, "Failed to scan classes", e);
        }

        return testLoader.getLoadedClasses();
    }

    /**
     * A {@link ClassNameFilter} that only rejects a given package names within the given namespace.
     */
    public static class ExcludePackageNameFilter implements ClassNameFilter {

        private final String mPkgName;

        ExcludePackageNameFilter(String pkgName) {
            if (!pkgName.endsWith(".")) {
                mPkgName = String.format("%s.", pkgName);
            } else {
                mPkgName = pkgName;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(String pathName) {
            return !pathName.startsWith(mPkgName);
        }
    }
}
