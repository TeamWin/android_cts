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

import java.io.IOException;
import java.util.Enumeration;
import java.util.stream.Stream;

import android.signature.cts.ClassProvider;
import dalvik.system.DexFile;

@SuppressWarnings("deprecation")
public class BootClassPathClassesProvider extends ClassProvider {
    private Stream<Class<?>> allClasses = null;

    @Override
    public Stream<Class<?>> getAllClasses() {
        Stream.Builder<Class<?>> builder = Stream.builder();
        if (allClasses == null) {
            for (String file : getBootJarPaths()) {
                try {
                    DexFile dexFile = new DexFile(file);
                    Enumeration<String> entries = dexFile.entries();
                    while (entries.hasMoreElements()) {
                        String className = entries.nextElement();
                        Class<?> clazz = getClass(className);
                        if (clazz != null) {
                            builder.add(clazz);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to parse dex in " + file, e);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Error while loading class in " + file, e);
                }
            }
            allClasses = builder.build();
        }
        return allClasses;
    }

    private String[] getBootJarPaths() {
        return System.getProperty("java.boot.class.path").split(":");
    }
}