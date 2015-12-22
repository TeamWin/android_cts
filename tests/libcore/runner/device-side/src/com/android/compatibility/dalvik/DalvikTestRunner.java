/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.compatibility.dalvik;

import com.android.compatibility.common.util.TestSuiteFilter;

import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestListener;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * Runs tests against the Dalvik VM.
 */
public class DalvikTestRunner {

    private static final String ABI = "--abi=";
    private static final String APK = "--apk=";
    private static final String INCLUDE = "--include-filter=";
    private static final String EXCLUDE = "--exclude-filter=";
    private static final String INCLUDE_FILE = "--include-filter-file=";
    private static final String EXCLUDE_FILE = "--exclude-filter-file=";
    private static final String JUNIT_IGNORE = "org.junit.Ignore";

    public static void main(String[] args) {
        String abiName = null;
        Set<String> apks = new HashSet<>();
        Set<String> includes = new HashSet<>();
        Set<String> excludes = new HashSet<>();
        for (String arg : args) {
            if (arg.startsWith(ABI)) {
                abiName = arg.substring(ABI.length());
            } else if (arg.startsWith(APK)) {
                apks.add(arg.substring(APK.length()));
            } else if (arg.startsWith(INCLUDE)) {
                for (String include : arg.substring(INCLUDE.length()).split(",")) {
                    includes.add(include);
                }
            } else if (arg.startsWith(EXCLUDE)) {
                for (String exclude : arg.substring(EXCLUDE.length()).split(",")) {
                    excludes.add(exclude);
                }
            } else if (arg.startsWith(INCLUDE_FILE)) {
                loadFilters(arg.substring(INCLUDE_FILE.length()), includes);
            } else if (arg.startsWith(EXCLUDE_FILE)) {
                loadFilters(arg.substring(EXCLUDE_FILE.length()), excludes);
            }
        }
        TestListener listener = new DalvikTestListener();
        List<Class<?>> classes = getClasses(apks, abiName);
        TestSuite suite = TestSuiteFilter.createSuite(classes, includes, excludes);
        int count = suite.countTestCases();
        System.out.println(String.format("start-run:%d", count));
        long start = System.currentTimeMillis();
        TestResult result = new TestResult();
        result.addListener(listener);
        suite.run(result);
        long end = System.currentTimeMillis();
        System.out.println(String.format("end-run:%d", end - start));
    }

    private static void loadFilters(String filename, Set<String> filters) {
        try {
            Scanner in = new Scanner(new File(filename));
            while (in.hasNextLine()) {
                filters.add(in.nextLine());
            }
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static List<Class<?>> getClasses(Set<String> apks, String abiName) {
        List<Class<?>> classes = new ArrayList<>();
        for (String apk : apks) {
            try {
                ClassLoader loader = createClassLoader(apk, abiName);
                DexFile file = new DexFile(apk);
                Enumeration<String> entries = file.entries();
                while (entries.hasMoreElements()) {
                    String e = entries.nextElement();
                    Class<?> cls = loader.loadClass(e);
                    if (isTestClass(cls)) {
                        classes.add(cls);
                    }
                }
            } catch (IllegalAccessError | IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return classes;
    }

    private static ClassLoader createClassLoader(String apk, String abiName) {
        StringBuilder libPath = new StringBuilder();
        libPath.append(apk).append("!/lib/").append(abiName);
        return new PathClassLoader(apk, libPath.toString(), DalvikTestRunner.class.getClassLoader());
    }

    private static boolean isTestClass(Class<?> cls) {
        // FIXME(b/25154702): have to have a null check here because some
        // classes such as
        // SQLite.JDBC2z.JDBCPreparedStatement can be found in the classes.dex
        // by DexFile.entries
        // but trying to load them with DexFile.loadClass returns null.
        if (cls == null) {
            return false;
        }
        for (Annotation a : cls.getAnnotations()) {
            if (a.annotationType().getName().equals(JUNIT_IGNORE)) {
                return false;
            }
        }
        // TODO: Add junit4 support here
        int modifiers = cls.getModifiers();
        return (Test.class.isAssignableFrom(cls)
                && Modifier.isPublic(modifiers)
                && !Modifier.isStatic(modifiers)
                && !Modifier.isInterface(modifiers)
                && !Modifier.isAbstract(modifiers));
    }

    // TODO: expand this to setup and teardown things needed by Dalvik tests.
    private static class DalvikTestListener implements TestListener {
        /**
         * {@inheritDoc}
         */
        @Override
        public void startTest(Test test) {
            System.out.println(String.format("start-test:%s", getId(test)));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void endTest(Test test) {
            System.out.println(String.format("end-test:%s", getId(test)));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addFailure(Test test, AssertionFailedError error) {
            System.out.println(String.format("failure:%s", stringify(error)));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addError(Test test, Throwable error) {
            System.out.println(String.format("failure:%s", stringify(error)));
        }

        private String getId(Test test) {
            String className = test.getClass().getName();
            if (test instanceof TestCase) {
                return String.format("%s#%s", className, ((TestCase) test).getName());
            }
            return className;
        }

        private String stringify(Throwable error) {
            return Arrays.toString(error.getStackTrace()).replaceAll("\n", " ");
        }
    }
}
