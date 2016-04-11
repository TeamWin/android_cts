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

package android.jni.cts;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

class LinkerNamespacesHelper {
    private final static String VENDOR_CONFIG_FILE = "/vendor/etc/public.libraries.txt";
    public static String runAccessibilityTest() throws IOException {
        List<String> libs = new ArrayList<>();
        File file = new File(VENDOR_CONFIG_FILE);
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    libs.add(line);
                }
            }
        }
        return runAccessibilityTestImpl(libs.toArray(new String[libs.size()]));
    }
    private static native String runAccessibilityTestImpl(String[] publicVendorLibs);
}
