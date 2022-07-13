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

import android.os.Bundle;
import android.signature.cts.DexApiDocumentParser;
import android.signature.cts.DexField;
import android.signature.cts.DexMember;
import android.signature.cts.DexMemberChecker;
import android.signature.cts.DexMethod;
import android.signature.cts.FailureType;
import android.signature.cts.VirtualPath;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.base.Suppliers;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Checks that it is not possible to access hidden APIs.
 */
public class HiddenApiTest extends AbstractApiTest {

    private static final String HIDDENAPI_FILES_ARG = "hiddenapi-files";
    private static final String HIDDENAPI_FILTER_FILE_ARG = "hiddenapi-filter-file";
    private static final String HIDDENAPI_TEST_FLAGS_ARG = "hiddenapi-test-flags";

    private static final Supplier<List<DexMember>> DEX_MEMBERS = getSupplierOfDexMembers();

    private String[] hiddenapiTestFlags;

    @Override
    protected void initializeFromArgs(Bundle instrumentationArgs) throws Exception {
        hiddenapiTestFlags =
                getCommaSeparatedListOptional(instrumentationArgs, HIDDENAPI_TEST_FLAGS_ARG);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        DexMemberChecker.init();
    }

    // We have four methods to split up the load, keeping individual test runs small.

    private final static Predicate<DexMember> METHOD_FILTER =
            dexMember -> (dexMember instanceof DexMethod);

    private final static Predicate<DexMember> FIELD_FILTER =
            dexMember -> (dexMember instanceof DexField);

    @Test(timeout = 120000)
    public void testSignatureMethodsThroughReflection() {
        doTestSignature(METHOD_FILTER,/* reflection= */ true, /* jni= */ false);
    }

    @Test
    public void testSignatureMethodsThroughJni() {
        doTestSignature(METHOD_FILTER, /* reflection= */ false, /* jni= */ true);
    }

    @Test
    public void testSignatureFieldsThroughReflection() {
        doTestSignature(FIELD_FILTER, /* reflection= */ true, /* jni= */ false);
    }

    @Test
    public void testSignatureFieldsThroughJni() {
        doTestSignature(FIELD_FILTER, /* reflection= */ false, /* jni= */ true);
    }

    /**
     * Tests that the device does not expose APIs on the provided lists of
     * DEX signatures.
     *
     * Will check the entire API, and then report the complete list of failures
     */
    private void doTestSignature(Predicate<DexMember> memberFilter, boolean reflection,
            boolean jni) {
        runWithTestResultObserver(resultObserver -> {
            DexMemberChecker.Observer observer = new DexMemberChecker.Observer() {
                @Override
                public void classAccessible(boolean accessible, DexMember member) {
                }

                @Override
                public void fieldAccessibleViaReflection(boolean accessible, DexField field) {
                    if (accessible) {
                        synchronized(resultObserver) {
                            resultObserver.notifyFailure(
                                    FailureType.EXTRA_FIELD,
                                    field.toString(),
                                    "Hidden field accessible through reflection");
                        }
                    }
                }

                @Override
                public void fieldAccessibleViaJni(boolean accessible, DexField field) {
                    if (accessible) {
                        synchronized(resultObserver) {
                            resultObserver.notifyFailure(
                                    FailureType.EXTRA_FIELD,
                                    field.toString(),
                                    "Hidden field accessible through JNI");
                        }
                    }
                }

                @Override
                public void methodAccessibleViaReflection(boolean accessible, DexMethod method) {
                    if (accessible) {
                        synchronized(resultObserver) {
                            resultObserver.notifyFailure(
                                    FailureType.EXTRA_METHOD,
                                    method.toString(),
                                    "Hidden method accessible through reflection");
                        }
                    }
                }

                @Override
                public void methodAccessibleViaJni(boolean accessible, DexMethod method) {
                    if (accessible) {
                        synchronized(resultObserver) {
                            resultObserver.notifyFailure(
                                    FailureType.EXTRA_METHOD,
                                    method.toString(),
                                    "Hidden method accessible through JNI");
                        }
                    }
                }
            };

            List<DexMember> dexMembers = DEX_MEMBERS.get();
            dexMembers.parallelStream()
                    .filter(memberFilter)
                    .filter(m -> shouldTestMember(m))
                    .forEach(
                            m -> DexMemberChecker.checkSingleMember(m, reflection, jni, observer));
        });
    }

    /**
     * Determines whether to test the member.
     *
     * @param member the member
     * @return true if the member should be tested, false otherwise.
     */
    protected boolean shouldTestMember(DexMember member) {
        // Test the member if it supports ANY of the flags specified in the hiddenapi-test-flags
        // argument.
        Set<String> flags = member.getHiddenapiFlags();
        for (String testFlag : hiddenapiTestFlags) {
            if (flags.contains(testFlag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether this method/field is included in the filter file. If so, we should not test
     * it. If not, we should test it.
     *
     * @param line is the line from the hiddenapi-flags.csv indicating which method/field to check
     * @return true if the method/field is to be filtered out, false otherwise
     */
    private static boolean isFiltered(String line, Set<String> hiddenapiFilterSet) {
        if (line == null) {
            return false;
        }
        // Need to remove which list the method/field is a part of (at the end of the line)
        int commaIndex = line.indexOf(',');
        return commaIndex > 0 && hiddenapiFilterSet.contains(line.substring(0, commaIndex));
    }

    /**
     * Loads the filter file and inserts each line of the file into a Set
     */
    static Set<String> loadFilters(String hiddenapiFilterFile) {
        // Avoids testing members in filter file (only a single filter file can be supplied)
        Set<String> hiddenapiFilterSet = new HashSet<>();
        if (hiddenapiFilterFile != null) {
            try {
                VirtualPath.ResourcePath resourcePath =
                        VirtualPath.get(HiddenApiTest.class.getClassLoader(), hiddenapiFilterFile);
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(resourcePath.newInputStream()));
                String filterFileLine = reader.readLine();
                while (filterFileLine != null) {
                    if (!filterFileLine.startsWith("#")) {
                        hiddenapiFilterSet.add(filterFileLine);
                    }
                    filterFileLine = reader.readLine();
                }
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to load filters", ioe);
            }
        }
        return hiddenapiFilterSet;
    }

    /**
     * Reads DEX method and fields from hiddenapi files.
     *
     * <p>This method is expensive, it typically takes ~10s to run.
     *
     * @return a list of {@link DexMember} objects for the fields and methods in the hiddenapi files
     */
    private static List<DexMember> readDexMembers() {
        final Bundle arguments = InstrumentationRegistry.getArguments();
        final String hiddenapiFilterFile = arguments.getString(HIDDENAPI_FILTER_FILE_ARG);
        final String[] hiddenapiFiles =
                getCommaSeparatedListRequired(arguments, HIDDENAPI_FILES_ARG);
        final Set<String> hiddenapiFilterSet = loadFilters(hiddenapiFilterFile);
        ArrayList<DexMember> dexMembers = new ArrayList<>();
        for (String apiFile : hiddenapiFiles) {
            try {
                VirtualPath.ResourcePath resourcePath =
                        VirtualPath.get(HiddenApiTest.class.getClassLoader(), apiFile);
                try (BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(resourcePath.newInputStream()),
                                1024 * 1024)) {
                    int lineIndex = 1;
                    String line = reader.readLine();
                    while (line != null) {
                        DexMember dexMember = DexApiDocumentParser.parseLine(line, lineIndex);
                        if (!isFiltered(line, hiddenapiFilterSet)) {
                            dexMembers.add(dexMember);
                        }
                        line = reader.readLine();
                        lineIndex++;
                    }
                }
            } catch (IOException | ParseException e) {
                throw new RuntimeException("Failed to read DEX members", e);
            }
        }
        return dexMembers;
    }

    static Supplier<List<DexMember>> getSupplierOfDexMembers() {
        return Suppliers.memoize(() -> readDexMembers())::get;
    }
}
