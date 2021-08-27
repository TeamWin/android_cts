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

package com.android.bedstead.remoteframeworkclasses.processor;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * A collection of {@link MethodSignature} for accessible methods.
 */
public final class Apis {

    private static final String[] API_FILES =
            {"current.txt", "test-current.txt", "wifi-current.txt"};

    private static final Set<String> API_TXTS = initialiseApiTxts();
    private static final Map<String, Apis> sPackageToApi = new HashMap<>();

    private static Set<String> initialiseApiTxts() {
        return Arrays.stream(API_FILES)
                .map(f -> {
                    try {
                        return Resources.toString(Processor.class.getResource("/" + f),
                                StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new IllegalStateException("Could not read file " + f);
                    }
                })
                .collect(Collectors.toSet());
    }

    /**
     * Get public and test APIs for a given class name.
     */
    public static Apis forClass(String className, Types types, Elements elements) {
        if (sPackageToApi.containsKey(className)) {
            return sPackageToApi.get(className);
        }

        ImmutableSet.Builder<MethodSignature> methods = ImmutableSet.builder();
        for (String apiTxt : API_TXTS) {
            methods.addAll(parseApiTxt(apiTxt, className, types, elements));
        }

        return new Apis(methods.build());
    }

    private static Set<MethodSignature> parseApiTxt(
            String apiTxt, String className, Types types, Elements elements) {
        int separatorPosition = className.lastIndexOf(".");
        String packageName = className.substring(0, separatorPosition);
        String simpleClassName = className.substring(separatorPosition + 1);

        String[] packageSplit = apiTxt.split("package " + packageName + " \\{", 2);
        if (packageSplit.length < 2) {
            System.out.println("Package " + packageName + " not in file");
            // Package not in this file
            return new HashSet<>();
        }
        String[] classSplit = packageSplit[1].split("class " + simpleClassName + " \\{", 2);
        if (classSplit.length < 2) {
            System.out.println("Class " + simpleClassName + " not in file");
            // Class not in this file
            return new HashSet<>();
        }
        String[] lines = classSplit[1].split("\n");
        Set<MethodSignature> methodSignatures = new HashSet<>();

        for (String line : lines) {
            String methodLine = line.trim();
            if (methodLine.isEmpty()) {
                continue;
            }

            if (methodLine.startsWith("ctor")) {
                // Skip constructors
                continue;
            }

            if (!methodLine.startsWith("method")) {
                return methodSignatures;
            }
            if (methodLine.contains(" static ")) {
                continue; // We don't expose static methods
            }


            try {
                // Strip "method" and semicolon
                methodLine = methodLine.substring(7, methodLine.length() - 1);
                methodSignatures.add(MethodSignature.forApiString(methodLine, types, elements));
            } catch (RuntimeException e) {
                throw new IllegalStateException("Error parsing method " + line, e);
            }
        }

        return methodSignatures;
    }

    private final ImmutableSet<MethodSignature> mMethods;

    private Apis(ImmutableSet<MethodSignature> methods) {
        mMethods = methods;
    }

    /**
     * Get methods in the API set.
     */
    public ImmutableSet<MethodSignature> methods() {
        return mMethods;
    }
}
