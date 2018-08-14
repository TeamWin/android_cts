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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SoParser extends FileParser {
    private int mBits;
    private String mArch;
    private List<String> mDependencies;
    private List<String> mDynamicLoadingDependencies;

    public SoParser(File file) {
        super(file);
        mBits = 0;
        mArch = "";
    }

    @Override
    public Entry.EntryType getType() {
        return Entry.EntryType.SO;
    }

    @Override
    public String getCodeId() {
        return getFileContentId();
    }

    @Override
    public List<String> getDependencies() {
        if (mDependencies == null) {
            try {
                ReadElf elf = ReadElf.read(getFile());
                mBits = elf.getBits();
                mArch = elf.getArchitecture();
                mDependencies = elf.getDynamicDependencies();

                // System.out.println(String.format("SoParser: %s, %s", getFileName(),
                // depList.toString()));

                // Check Dynamic Loading dependencies
                mDynamicLoadingDependencies = getDynamicLoadingDependencies(elf);
            } catch (Exception ex) {
                System.err.println(
                        String.format(
                                "err: SoParser can not getDependencies from %s.", getFileName()));
                mDependencies = super.getDependencies();
                mDynamicLoadingDependencies = super.getDynamicLoadingDependencies();
            }
        }
        return mDependencies;
    }

    @Override
    public List<String> getDynamicLoadingDependencies() {
        if (mDynamicLoadingDependencies == null) {
            // This also parses DynamicLoadingDependencies
            getDependencies();
        }
        return mDynamicLoadingDependencies;
    }

    @Override
    public int getAbiBits() {
        if (mBits == 0) {
            try {
                ReadElf elf = ReadElf.read(getFile());
                mBits = elf.getBits();
                mArch = elf.getArchitecture();
            } catch (Exception ex) {
                mBits = -1;
                mArch = "unknown";
            }
        }
        return mBits;
    }

    @Override
    public String getAbiArchitecture() {
        getAbiBits();
        return mArch;
    }

    private List<String> getDynamicLoadingDependencies(ReadElf elf) throws IOException {
        List<String> depList = new ArrayList<>();
        // check if it does refer to dlopen
        if (elf.getDynamicSymbol("dlopen") != null) {
            List<String> roStrings = elf.getRoStrings();
            for (String str : roStrings) {
                // skip ".so" or less
                if (str.length() < 4) {
                    continue;
                }

                if (str.endsWith(".so")) {
                    // skip itself
                    if (str.contains(getFileName())) {
                        continue;
                    }
                    if (str.contains(" ")) {
                        continue;
                    }
                    if (str.contains("?")) {
                        continue;
                    }
                    if (str.contains("%")) {
                        System.err.println("ToDo getDynamicLoadingDependencies: " + str);
                        continue;
                    }
                    if (str.startsWith("_")) {
                        System.err.println("ToDo getDynamicLoadingDependencies: " + str);
                        continue;
                    }
                    depList.add(str);
                }
            }
        }

        // specific for frameworks/native/opengl/libs/EGL/Loader.cpp load_system_driver()
        if ("libEGL.so".equals(getFileName())) {
            depList.add("libEGL*.so");
            depList.add("libGLESv1_CM*.so");
            depList.add("GLESv2*.so");
        }

        return depList;
    }
}
