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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Checks that it is not possible to access hidden APIs.
 */
public class HiddenApiTest extends AbstractApiTest {

    private String[] hiddenapiFiles;
    private String[] hiddenapiTestFlags;

    @Override
    protected void initializeFromArgs(Bundle instrumentationArgs) {
        hiddenapiFiles = getCommaSeparatedList(instrumentationArgs, "hiddenapi-files");
        hiddenapiTestFlags = getCommaSeparatedList(instrumentationArgs, "hiddenapi-test-flags");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        DexMemberChecker.init();
    }

    // We have four methods to split up the load, keeping individual test runs small.

    private final static Predicate<DexMember> METHOD_FILTER =
            dexMember -> (dexMember instanceof DexMethod);

    private final static Predicate<DexMember> FIELD_FILTER =
            dexMember -> (dexMember instanceof DexField);

    public void testSignatureMethodsThroughReflection() {
        doTestSignature(METHOD_FILTER,/* reflection= */ true, /* jni= */ false);
    }

    public void testSignatureMethodsThroughJni() {
        doTestSignature(METHOD_FILTER, /* reflection= */ false, /* jni= */ true);
    }

    public void testSignatureFieldsThroughReflection() {
        doTestSignature(FIELD_FILTER, /* reflection= */ true, /* jni= */ false);
    }

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
            parseDexApiResourcesAsStream(hiddenapiFiles)
                    .filter(memberFilter)
                    .forEach(dexMember -> {
                        if (shouldTestMember(dexMember)) {
                            DexMemberChecker.checkSingleMember(dexMember, reflection, jni,
                                    observer);
                        }
                    });
        });
    }

    private Stream<DexMember> parseDexApiResourcesAsStream(String[] apiFiles) {
        DexApiDocumentParser dexApiDocumentParser = new DexApiDocumentParser();
        // To allow parallelization with a DexMember output type, we need two
        // pipes.
        Stream<Stream<DexMember>> inputsAsStreams = Stream.of(apiFiles).parallel()
                .flatMap(this::readResourceOptimized)
                .map(dexApiDocumentParser::parseAsStream);
        // The flatMap inherently serializes the pipe. The number of inputs is
        // still small here, so reduce by concatenating (note the caveats of
        // concats).
        return inputsAsStreams.reduce(null, (prev, stream) -> {
            if (prev == null) {
                return stream;
            }
            return Stream.concat(prev, stream);
        });
    }

    private Stream<Object> readResourceOptimized(String resourceName) {
        try {
            VirtualPath.ResourcePath resourcePath =
                    VirtualPath.get(getClass().getClassLoader(), resourceName);
            // Extract to a temporary file and read from there. Accessing it via an InputStream
            // directly is too slow because the file has to be read serially and that results in
            // the tests taking too long. Saving it as a file allows it to be mapped as a
            // ByteBuffer and processed in parallel.
            Path file = extractResourceToFile(resourceName, resourcePath.newInputStream());

            // Map the file into a ByteBuffer, see http://b/123986482 for some background.
            try (FileChannel fileChannel = (FileChannel) Files.newByteChannel(file,
                    EnumSet.of(StandardOpenOption.READ))) {
                ByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0,
                        fileChannel.size());
                if (mappedByteBuffer == null) {
                    throw new IllegalStateException("Could not map " + file);
                }
                return Stream.of(mappedByteBuffer);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean shouldTestMember(DexMember member) {
        for (String testFlag : hiddenapiTestFlags) {
            for (String memberFlag : member.getHiddenapiFlags()) {
                if (testFlag.equals(memberFlag)) {
                    return true;
                }
            }
        }
        return false;
    }

}
