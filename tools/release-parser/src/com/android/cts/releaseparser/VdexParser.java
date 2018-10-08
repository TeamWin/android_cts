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

package com.android.cts.releaseparser;

import com.android.cts.releaseparser.ReleaseProto.*;
import com.google.protobuf.TextFormat;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

// art/runtime/vdex_file.h & vdex_file.cc
public class VdexParser extends FileParser {
    // The magic values for the VDEX identification.
    private static final byte[] VDEX_MAGIC = {(byte) 'v', (byte) 'd', (byte) 'e', (byte) 'x'};
    private static final int HEADER_SIZE = 64;
    private VdexInfo.Builder mVdexInfoBuilder;

    public VdexParser(File file) {
        super(file);
    }

    @Override
    public Entry.EntryType getType() {
        return Entry.EntryType.VDEX;
    }

    public VdexInfo getVdexInfo() {
        if (mVdexInfoBuilder == null) {
            prase();
        }
        return mVdexInfoBuilder.build();
    }

    private void prase() {
        byte[] buffer = new byte[HEADER_SIZE];
        mVdexInfoBuilder = VdexInfo.newBuilder();

        try {
            RandomAccessFile raFile = new RandomAccessFile(getFile(), "r");
            raFile.seek(0);
            raFile.readFully(buffer, 0, HEADER_SIZE);
            raFile.close();

            // ToDo: this is specific for 019 VerifierDepsVersion. Need to handle changes for older
            // versions
            if (buffer[0] != VDEX_MAGIC[0]
                    || buffer[1] != VDEX_MAGIC[1]
                    || buffer[2] != VDEX_MAGIC[2]
                    || buffer[3] != VDEX_MAGIC[3]) {
                String content = new String(buffer);
                System.err.println("Invalid VDEX file:" + getFileName() + " " + content);
                throw new IllegalArgumentException("Invalid VDEX MAGIC");
            }
            int offset = 4;
            String version = new String(Arrays.copyOfRange(buffer, offset, offset + 4));
            mVdexInfoBuilder.setVerifierDepsVersion(version);
            offset += 4;
            String dex_section_version = new String(Arrays.copyOfRange(buffer, offset, offset + 4));
            mVdexInfoBuilder.setDexSectionVersion(dex_section_version);
            offset += 4;
            int numberOfDexFiles = getIntLittleEndian(buffer, offset);
            mVdexInfoBuilder.setNumberOfDexFiles(numberOfDexFiles);
            offset += 4;
            mVdexInfoBuilder.setVerifierDepsSize(getIntLittleEndian(buffer, offset));
            offset += 4;

            for (int i = 0; i < numberOfDexFiles; i++) {
                mVdexInfoBuilder.addChecksums(getIntLittleEndian(buffer, offset));
                offset += 4;
            }

            for (int i = 0; i < numberOfDexFiles; i++) {
                DexSectionHeader.Builder dshBuilder = DexSectionHeader.newBuilder();
                dshBuilder.setDexSize(getIntLittleEndian(buffer, offset));
                offset += 4;
                dshBuilder.setDexSharedDataSize(getIntLittleEndian(buffer, offset));
                offset += 4;
                dshBuilder.setQuickeningInfoSize(getIntLittleEndian(buffer, offset));
                offset += 4;
                mVdexInfoBuilder.addDexSectionHeaders(dshBuilder.build());
                offset += 4;
            }

            for (int i = 0; i < numberOfDexFiles; i++) {
                int quicken_table_off = getIntLittleEndian(buffer, offset);
                offset += 4;
                // Todo processing Dex
            }
        } catch (Exception ex) {
            System.err.println("Invalid VDEX file:" + getFileName());
            mVdexInfoBuilder.setValid(false);
        }
    }

    private static final String USAGE_MESSAGE =
            "Usage: java -jar releaseparser.jar com.android.cts.releaseparser.VdexParser [-options] <path> [args...]\n"
                    + "           to prase an APK for API\n"
                    + "Options:\n"
                    + "\t-i PATH\t VDEX path \n";

    /** Get the argument or print out the usage and exit. */
    private static void printUsage() {
        System.out.printf(USAGE_MESSAGE);
        System.exit(1);
    }

    /** Get the argument or print out the usage and exit. */
    private static String getExpectedArg(String[] args, int index) {
        if (index < args.length) {
            return args[index];
        } else {
            printUsage();
            return null; // Never will happen because printUsage will call exit(1)
        }
    }

    public static void main(String[] args) throws IOException {
        String fileName = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                if ("-i".equals(args[i])) {
                    fileName = getExpectedArg(args, ++i);
                }
            }
        }
        if (fileName == null) {
            printUsage();
        }
        File aFile = new File(fileName);
        VdexParser aParser = new VdexParser(aFile);
        System.out.println(TextFormat.printToString(aParser.getVdexInfo()));
    }
}
