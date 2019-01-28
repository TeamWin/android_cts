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

package android.signature.cts.api;

import android.os.Bundle;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.stream.Stream;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

/**
 * Performs the signature check via a JUnit test.
 */
public class SignatureMultiLibsTest extends SignatureTest {

    private static final String TAG = SignatureMultiLibsTest.class.getSimpleName();

    private String[] libraryNames;
    private String[] versions;
    private String[] api_levels;

    @Override
    protected void initializeFromArgs(Bundle instrumentationArgs) throws Exception {
        libraryNames = getCommaSeparatedList(instrumentationArgs, "library-names");
        versions = getCommaSeparatedList(instrumentationArgs, "versions");
        api_levels = getCommaSeparatedList(instrumentationArgs, "api_levels");
        super.initializeFromArgs(instrumentationArgs);
    }

    /**
     * Tests that the device's API matches the expected set defined in xml.
     * <p/>
     * Will check the entire API, and then report the complete list of failures
     */
    @Override
    public void testSignature() {
        Stream<String> packageListLibraries = getLibraries();
        ArrayList<String> expected = new ArrayList<>();
        packageListLibraries.forEach(lib -> {
            if (Arrays.asList(libraryNames).indexOf(lib) >= 0) {
                for (String ver : versions) {
                    for (String api_level : api_levels) {
                        expected.add(lib + "-" + ver + "-" + api_level + ".api");
                    }
                }
            }
        });
        expectedApiFiles = expected.toArray(new String[expected.size()]);

        super.testSignature();
    }

    private Stream<String> getLibraries() {
        try {
            String result = runShellCommand(getInstrumentation(), "cmd package list libraries");
            return Arrays.stream(result.split("\n")).map(line -> line.split(":")[1]);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
