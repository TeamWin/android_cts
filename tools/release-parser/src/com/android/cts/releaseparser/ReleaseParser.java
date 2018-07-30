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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ReleaseParser {
    // configuration option
    private static final String NOT_SHARDABLE_TAG = "not-shardable";
    // test class option
    private static final String RUNTIME_HIT_TAG = "runtime-hint";
    // com.android.tradefed.testtype.AndroidJUnitTest option
    private static final String PACKAGE_TAG = "package";
    // com.android.compatibility.common.tradefed.testtype.JarHostTest option
    private static final String JAR_NAME_TAG = "jar";
    // com.android.tradefed.testtype.GTest option
    private static final String NATIVE_TEST_DEVICE_PATH_TAG = "native-test-device-path";
    private static final String MODULE_TAG = "module-name";

    private static final String SUITE_API_INSTALLER_TAG =
            "com.android.tradefed.targetprep.suite.SuiteApkInstaller";
    private static final String JAR_HOST_TEST_TAG =
            "com.android.compatibility.common.tradefed.testtype.JarHostTest";
    // com.android.tradefed.targetprep.suite.SuiteApkInstaller option
    private static final String TEST_FILE_NAME_TAG = "test-file-name";
    // com.android.compatibility.common.tradefed.targetprep.FilePusher option
    private static final String PUSH_TAG = "push";

    // test class
    private static final String ANDROID_JUNIT_TEST_TAG =
            "com.android.tradefed.testtype.AndroidJUnitTest";

    private static final String TESTCASES_FOLDER_FORMAT = "testcases/%s";

    private final String mFolderPath;
    private Path mRootPath;
    private ReleaseContent.Builder mRelContentBuilder;
    private Map<String, Entry> mEntries;

    ReleaseParser(String folder) {
        mFolderPath = folder;
        File fFile = new File(mFolderPath);
        mRootPath = Paths.get(fFile.getAbsolutePath());
        mEntries = new HashMap<String, Entry>();
    }

    public String getRelNameVer() {
        ReleaseContent relContent = getReleaseContent();
        return String.format(
                "%s-%s-%s",
                relContent.getName(), relContent.getVersion(), relContent.getBuildNumber());
    }

    public ReleaseContent getReleaseContent() {
        if (mRelContentBuilder == null) {
            mRelContentBuilder = ReleaseContent.newBuilder();
            // also add the root folder entry
            Entry.Builder fBuilder = parseFolder(mFolderPath);
            if (mRelContentBuilder.getName().equals("")) {
                System.err.println("Release Name unknown!");
                mRelContentBuilder.setName(mFolderPath);
            }
            fBuilder.setName(
                    String.format(
                            "%s-%s-%s",
                            mRelContentBuilder.getName(),
                            mRelContentBuilder.getVersion(),
                            mRelContentBuilder.getBuildNumber()));
            fBuilder.setParentFolder(mFolderPath);
            Entry fEntry = fBuilder.build();
            mEntries.put(fEntry.getRelativePath(), fEntry);
            mRelContentBuilder.putAllEntries(mEntries);
        }
        return mRelContentBuilder.build();
    }

    // Parse all files in a folder and return the foler entry builder
    private Entry.Builder parseFolder(String fPath) {
        Entry.Builder folderEntry = Entry.newBuilder();
        File folder = new File(fPath);
        Path folderPath = Paths.get(folder.getAbsolutePath());
        String folderRelativePath = mRootPath.relativize(folderPath).toString();
        File[] fileList = folder.listFiles();
        Long folderSize = 0L;
        List<Entry> entryList = new ArrayList<Entry>();

        // walks through all files
        for (File file : fileList) {
            if (file.isFile()) {
                String fileRelativePath =
                        mRootPath.relativize(Paths.get(file.getAbsolutePath())).toString();
                Entry.Builder fileEntry = Entry.newBuilder();
                fileEntry.setName(file.getName());
                fileEntry.setSize(file.length());
                fileEntry.setRelativePath(fileRelativePath);
                fileEntry.setParentFolder(folderRelativePath);

                FileParser fParser = FileParser.getParser(file);
                fileEntry.setContentId(fParser.getFileContentId());
                fileEntry.setCodeId(fParser.getCodeId());

                Entry.EntryType eType = fParser.getType();

                fileEntry.setType(eType);
                switch (eType) {
                    case TEST_MODULE_CONFIG:
                        TestModuleConfigParser tmcParser = (TestModuleConfigParser) fParser;
                        fileEntry.setTestModuleConfig(tmcParser.getTestModuleConfig());
                        break;
                    case TEST_SUITE_TRADEFED:
                        mRelContentBuilder.setTestSuiteTradefed(fileRelativePath);
                        TestSuiteTradefedParser tstParser = (TestSuiteTradefedParser) fParser;
                        // get [cts]-known-failures.xml
                        mRelContentBuilder.addAllKnownFailures(tstParser.getKnownFailureList());
                        mRelContentBuilder.setName(tstParser.getName());
                        mRelContentBuilder.setFullname(tstParser.getFullName());
                        mRelContentBuilder.setBuildNumber(tstParser.getBuildNumber());
                        mRelContentBuilder.setTargetArch(tstParser.getTargetArch());
                        mRelContentBuilder.setVersion(tstParser.getVersion());
                        break;
                    case BUILD_PROP:
                        BuildPropParser bpParser = (BuildPropParser) fParser;
                        try {
                            mRelContentBuilder.setName(bpParser.getName());
                            mRelContentBuilder.setFullname(bpParser.getFullName());
                            mRelContentBuilder.setBuildNumber(bpParser.getBuildNumber());
                            mRelContentBuilder.setVersion(bpParser.getVersion());
                        } catch (Exception e) {
                            System.err.println(
                                    "No product name, version & etc. in "
                                            + file.getAbsoluteFile()
                                            + ", err:"
                                            + e.getMessage());
                        }
                        break;
                    case RC:
                        RcParser rcParser = (RcParser) fParser;
                        fileEntry.addAllServices(rcParser.getServiceList());
                        break;
                    default:
                }
                // System.err.println("File:" + file.getAbsoluteFile());
                fileEntry.addAllDependencies(fParser.getDependencies());
                fileEntry.setAbiBits(fParser.getAbiBits());
                fileEntry.setAbiArchitecture(fParser.getAbiArchitecture());

                Entry fEntry = fileEntry.build();
                entryList.add(fEntry);
                mEntries.put(fEntry.getRelativePath(), fEntry);
                folderSize += file.length();
            } else if (file.isDirectory()) {
                // Checks subfolders
                Entry.Builder subFolderEntry = parseFolder(file.getAbsolutePath());
                subFolderEntry.setParentFolder(folderRelativePath);
                Entry sfEntry = subFolderEntry.build();
                entryList.add(sfEntry);
                mEntries.put(sfEntry.getRelativePath(), sfEntry);
                folderSize += sfEntry.getSize();
            }
        }
        folderEntry.setName(folderRelativePath);
        folderEntry.setSize(folderSize);
        folderEntry.setType(Entry.EntryType.FOLDER);
        folderEntry.setContentId(getFolderContentId(folderEntry, entryList));
        folderEntry.setRelativePath(folderRelativePath);
        return folderEntry;
    }

    private static String getFolderContentId(Entry.Builder folderEntry, List<Entry> entryList) {
        String id = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Entry entry : entryList) {
                md.update(entry.getContentId().getBytes(StandardCharsets.UTF_8));
            }
            // Converts to Base64 String
            id = Base64.getEncoder().encodeToString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            System.err.println("NoSuchAlgorithmException:" + e.getMessage());
        }
        return id;
    }

    // writes releaes content to a CSV file
    public void writeRelesaeContentCsvFile(String relNameVer, String csvFile) {
        try {
            FileWriter fWriter = new FileWriter(csvFile);
            PrintWriter pWriter = new PrintWriter(fWriter);
            // Header
            pWriter.printf(
                    "release,type,name,size,relative_path,content_id,parent_folder,code_id,architecture,bits,dependencies,services\n");
            for (Entry entry : getFileEntriesList()) {
                pWriter.printf(
                        "%s,%s,%s,%d,%s,%s,%s,%s,%s,%d,%s,%s\n",
                        relNameVer,
                        entry.getType(),
                        entry.getName(),
                        entry.getSize(),
                        entry.getRelativePath(),
                        entry.getContentId(),
                        entry.getParentFolder(),
                        entry.getCodeId(),
                        entry.getAbiArchitecture(),
                        entry.getAbiBits(),
                        String.join(" ", entry.getDependenciesList()),
                        RcParser.toString(entry.getServicesList()));
            }
            pWriter.flush();
            pWriter.close();
        } catch (IOException e) {
            System.err.println("IOException:" + e.getMessage());
        }
    }

    // writes known failures to a CSV file
    public void writeKnownFailureCsvFile(String relNameVer, String csvFile) {
        ReleaseContent relContent = getReleaseContent();
        if (relContent.getKnownFailuresList().size() == 0) {
            // Skip if no Known Failures
            return;
        }

        try {
            FileWriter fWriter = new FileWriter(csvFile);
            PrintWriter pWriter = new PrintWriter(fWriter);
            //Header
            pWriter.printf("release,compatibility:exclude-filter\n");
            for (String kf : relContent.getKnownFailuresList()) {
                pWriter.printf("%s,%s\n", relNameVer, kf);
            }
            pWriter.flush();
            pWriter.close();
        } catch (IOException e) {
            System.err.println("IOException:" + e.getMessage());
        }
    }

    public Collection<Entry> getFileEntriesList() {
        return getReleaseContent().getEntries().values();
    }
}
