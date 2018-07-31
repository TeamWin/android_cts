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
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class FileParser {
    private static final String NO_ID = "";
    private static final int READ_BLOCK_SIZE = 1024;

    // Target File Extensions
    private static final String CONFIG_EXT_TAG = ".config";
    private static final String TEST_SUITE_TRADEFED_TAG = "-tradefed.jar";
    private static final String JAR_EXT_TAG = ".jar";
    private static final String APK_EXT_TAG = ".apk";
    private static final String SO_EXT_TAG = ".so";
    private static final String ODEX_EXT_TAG = ".odex";
    private static final String VDEX_EXT_TAG = ".vdex";
    private static final String BUILD_PROP_TAG = "build.prop";
    private static final String RC_TAG = ".rc";

    protected File mFile;
    protected String mContentId;
    protected String mCodeId;

    public static FileParser getParser(File file) {
        String fName = file.getName();

        // Starts with SymbolicLink
        if (isSymbolicLink(file)) {
            return new SymbolicLinkParser(file);
        } else if (fName.endsWith(APK_EXT_TAG)) {
            return new ApkParser(file);
        } else if (fName.endsWith(CONFIG_EXT_TAG)) {
            return new TestModuleConfigParser(file);
        } else if (fName.endsWith(TEST_SUITE_TRADEFED_TAG)) {
            return new TestSuiteTradefedParser(file);
        } else if (fName.endsWith(JAR_EXT_TAG)) {
            // keeps this after TEST_SUITE_TRADEFED_TAG to avoid missing it
            return new JarParser(file);
        } else if (fName.endsWith(SO_EXT_TAG)) {
            return new SoParser(file);
        } else if (fName.endsWith(ODEX_EXT_TAG)) {
            return new OdexParser(file);
        } else if (fName.endsWith(VDEX_EXT_TAG)) {
            return new VdexParser(file);
        } else if (fName.endsWith(BUILD_PROP_TAG)) {
            return new BuildPropParser(file);
        } else if (fName.endsWith(RC_TAG)) {
            return new RcParser(file);
        } else if (ReadElf.isElf(file)) {
            // keeps this in the end as no Exe Ext name
            return new ExeParser(file);
        } else {
            // Common File Parser
            return new FileParser(file);
        }
    }

    FileParser(File file) {
        mFile = file;
        mCodeId = NO_ID;
        mContentId = NO_ID;
    }

    public File getFile() {
        return mFile;
    }

    public String getFileName() {
        return mFile.getName();
    }

    public Entry.EntryType getType() {
        return Entry.EntryType.FILE;
    }

    public String getFileContentId() {
        if (NO_ID.equals(mContentId)) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                FileInputStream fis = new FileInputStream(mFile);
                byte[] dataBytes = new byte[READ_BLOCK_SIZE];
                int nread = 0;
                while ((nread = fis.read(dataBytes)) != -1) {
                    md.update(dataBytes, 0, nread);
                }
                // Converts to Base64 String
                mContentId = Base64.getEncoder().encodeToString(md.digest());
            } catch (IOException e) {
                System.err.println("IOException:" + e.getMessage());
            } catch (NoSuchAlgorithmException e) {
                System.err.println("NoSuchAlgorithmException:" + e.getMessage());
            }
        }
        return mContentId;
    }

    public int getAbiBits() {
        return 0;
    }

    public String getAbiArchitecture() {
        return NO_ID;
    }

    public String getCodeId() {
        return mCodeId;
    }

    public List<String> getDependencies() {
        return new ArrayList<String>();
    }

    private static boolean isSymbolicLink(File f) {
        // Assumes 0b files are Symbolic Link
        return (f.length() == 0);
    }
}
