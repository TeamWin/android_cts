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

import com.android.compatibility.common.util.ReadElf;
import com.android.cts.releaseparser.ReleaseProto.*;
import com.google.protobuf.TextFormat;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

// Oat is embedded in an ELF file .rodata secion
// art/runtime/oat.h & oat.cc
public class OatParser extends FileParser {
    // The magic values for the OAT identification.
    private static final byte[] OAT_MAGIC = {(byte) 'o', (byte) 'a', (byte) 't', (byte) 0x0A};
    private static final int HEADER_SIZE = 64;
    private static String FIRST_NO_PIC_VERSION = "162\0";
    private OatInfo.Builder mOatInfoBuilder;
    private int mBits;
    private String mArch;
    private byte[] mRoData;

    public OatParser(File file) {
        super(file);
    }

    public OatInfo getOatInfo() {
        if (mOatInfoBuilder == null) {
            prase();
        }
        return mOatInfoBuilder.build();
    }

    private void prase() {
        mOatInfoBuilder = OatInfo.newBuilder();
        try {
            ReadElf elf = ReadElf.read(getFile());
            mOatInfoBuilder.setBits(elf.getBits());
            mOatInfoBuilder.setArchitecture(elf.getArchitecture());
            mRoData = elf.getRoData();
            praseOat(mRoData);
            mOatInfoBuilder.setValid(true);
        } catch (Exception ex) {
            System.err.println("Invalid OAT file:" + getFileName());
            ex.printStackTrace(System.out);
            mOatInfoBuilder.setValid(false);
        }
    }

    private void praseOat(byte[] buffer) throws IllegalArgumentException {
        if (buffer[0] != OAT_MAGIC[0]
                || buffer[1] != OAT_MAGIC[1]
                || buffer[2] != OAT_MAGIC[2]
                || buffer[3] != OAT_MAGIC[3]) {
            String content = new String(buffer);
            System.err.println("Invalid OAT file:" + getFileName() + " " + content);
            throw new IllegalArgumentException("Invalid OAT MAGIC");
        }
        int offset = 4;
        String version = new String(Arrays.copyOfRange(buffer, offset, offset + 4));
        mOatInfoBuilder.setVersion(version);
        offset += 4;
        mOatInfoBuilder.setAdler32Checksum(getIntLittleEndian(buffer, offset));
        offset += 4;
        mOatInfoBuilder.setInstructionSet(getIntLittleEndian(buffer, offset));
        offset += 4;
        mOatInfoBuilder.setInstructionSetFeaturesBitmap(getIntLittleEndian(buffer, offset));
        offset += 4;
        mOatInfoBuilder.setDexFileCount(getIntLittleEndian(buffer, offset));
        offset += 4;
        mOatInfoBuilder.setOatDexFilesOffset(getIntLittleEndian(buffer, offset));
        offset += 4;
        mOatInfoBuilder.setExecutableOffset(getIntLittleEndian(buffer, offset));
        offset += 4;
        mOatInfoBuilder.setInterpreterToInterpreterBridgeOffset(getIntLittleEndian(buffer, offset));
        offset += 4;
        mOatInfoBuilder.setInterpreterToCompiledCodeBridgeOffset(
                getIntLittleEndian(buffer, offset));
        offset += 4;
        mOatInfoBuilder.setJniDlsymLookupOffset(getIntLittleEndian(buffer, offset));
        offset += 4;
        mOatInfoBuilder.setQuickGenericJniTrampolineOffset(getIntLittleEndian(buffer, offset));
        offset += 4;
        mOatInfoBuilder.setQuickImtConflictTrampolineOffset(getIntLittleEndian(buffer, offset));
        offset += 4;
        mOatInfoBuilder.setQuickResolutionTrampolineOffset(getIntLittleEndian(buffer, offset));
        offset += 4;
        mOatInfoBuilder.setQuickToInterpreterBridgeOffset(getIntLittleEndian(buffer, offset));
        offset += 4;

        // for backward compatibility, removed from version 162, see
        // aosp/e0669326c0282b5b645aba75160425eef9d57617
        if (version.compareTo(FIRST_NO_PIC_VERSION) < 0) {
            mOatInfoBuilder.setImagePatchDelta(getIntLittleEndian(buffer, offset));
            offset += 4;
        }

        mOatInfoBuilder.setImageFileLocationOatChecksum(getIntLittleEndian(buffer, offset));
        offset += 4;

        // for backward compatibility, removed from version 162, see
        // aosp/e0669326c0282b5b645aba75160425eef9d57617
        if (version.compareTo(FIRST_NO_PIC_VERSION) < 0) {
            mOatInfoBuilder.setImageFileLocationOatDataBegin(getIntLittleEndian(buffer, offset));
            offset += 4;
        }
        int storeSize = getIntLittleEndian(buffer, offset);
        mOatInfoBuilder.setKeyValueStoreSize(storeSize);
        offset += 4;

        mOatInfoBuilder.putAllKeyValueStore(getKeyValuePairMap(buffer, offset, storeSize));
    }

    // as art/runtime/oat.cc GetStoreValueByKey
    private Map<String, String> getKeyValuePairMap(byte[] buffer, int start, int size) {
        HashMap<String, String> keyValuePairMap = new HashMap<String, String>();
        int currentPosition = start;
        int end = start + size;
        String key, value;
        while (currentPosition < end) {
            key = getString(buffer, currentPosition, end);
            currentPosition += key.length() + 1;
            value = getString(buffer, currentPosition, end);
            currentPosition += value.length() + 1;
            keyValuePairMap.put(key, value);
        }
        return keyValuePairMap;
    }

    private String getString(byte[] buffer, int start, int end) {
        String str = null;
        int currentPosition = start;
        while (currentPosition < end) {
            if (buffer[currentPosition] == 0x0) {
                str = new String(Arrays.copyOfRange(buffer, start, currentPosition));
                break;
            } else {
                currentPosition++;
            }
        }
        return str;
    }

    @Override
    public Entry.EntryType getType() {
        return Entry.EntryType.OAT;
    }

    private static final String USAGE_MESSAGE =
            "Usage: java -jar releaseparser.jar com.android.cts.releaseparser.OatParser [-options] <path> [args...]\n"
                    + "           to prase an OAT file \n"
                    + "Options:\n"
                    + "\t-i PATH\t OAT path \n";

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
        OatParser aParser = new OatParser(aFile);
        System.out.println(TextFormat.printToString(aParser.getOatInfo()));
    }
}
