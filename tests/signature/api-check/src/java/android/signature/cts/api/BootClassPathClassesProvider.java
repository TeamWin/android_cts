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

import android.os.Debug;
import android.signature.cts.ClassProvider;
import dalvik.system.BaseDexClassLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.stream.Stream;

@SuppressWarnings("deprecation")
public class BootClassPathClassesProvider extends ClassProvider {
    private static boolean sJvmtiAttached = false;

    @Override
    public Stream<Class<?>> getAllClasses() {
        if (!sJvmtiAttached) {
            try {
                Debug.attachJvmtiAgent(copyAgentToFile("classdescriptors").getAbsolutePath(), null,
                        BootClassPathClassesProvider.class.getClassLoader());
                sJvmtiAttached = true;
                initialize();
            } catch (Exception e) {
                throw new RuntimeException("Error while attaching JVMTI agent", e);
            }
        }
        return Arrays.stream(getClassloaderDescriptors(Object.class.getClassLoader()))
                .map(descriptor -> {
                    System.err.println("Class name = " + descriptor);
                    String classname = descriptor.replace('/', '.');
                    // omit L and ; at the front and at the end
                    return classname.substring(1, classname.length() - 1);
                })
                .map(classname -> {
                    try {
                        return getClass(classname);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException("Cannot load " + classname, e);
                    }
                });
    }

    private static File copyAgentToFile(String lib) throws Exception {
        ClassLoader cl = BootClassPathClassesProvider.class.getClassLoader();

        File copiedAgent = File.createTempFile("agent", ".so");
        try (InputStream is = new FileInputStream(
                ((BaseDexClassLoader) cl).findLibrary(lib))) {
            try (OutputStream os = new FileOutputStream(copiedAgent)) {
                byte[] buffer = new byte[64 * 1024];

                while (true) {
                    int numRead = is.read(buffer);
                    if (numRead == -1) {
                        break;
                    }
                    os.write(buffer, 0, numRead);
                }
            }
        }
        return copiedAgent;
    }

    private static native void initialize();

    private static native String[] getClassloaderDescriptors(ClassLoader loader);
}
