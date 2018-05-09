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

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

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

    // Target File Extensions
    private static final String CONFIG_EXT_TAG = ".config";
    private static final String JAR_EXT_TAG = ".jar";
    private static final String APK_EXT_TAG = ".apk";
    private static final String SO_EXT_TAG = ".so";
    private static final String TEST_SUITE_TRADEFED_TAG = "-tradefed.jar";

    private final String mFolderPath;
    private Path mRootPath;
    private ReleaseContent.Builder mRelContentBuilder;

    ReleaseParser(String folder) {
        mFolderPath = folder;
        File fFile = new File(mFolderPath);
        mRootPath = Paths.get(fFile.getAbsolutePath());
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
            mRelContentBuilder.addFileEntries(fBuilder);
        }
        return mRelContentBuilder.build();
    }

    // Parse all files in a folder, add them to mRelContentBuilder.fileEntry and return the foler entry builder
    private Entry.Builder parseFolder(String fPath) {
        Entry.Builder folderEntry = Entry.newBuilder();

        File folder = new File(fPath);
        Path folderPath = Paths.get(folder.getAbsolutePath());
        String folderRelativePath = mRootPath.relativize(folderPath).toString();
        String folderId = getId(folderRelativePath);
        File[] fileList = folder.listFiles();
        Long folderSize = 0L;
        List<Entry> entryList = new ArrayList<Entry>();
        for (File file : fileList) {
            if (file.isFile()) {
                String fileRelativePath =
                        mRootPath.relativize(Paths.get(file.getAbsolutePath())).toString();
                Entry.Builder fileEntry = Entry.newBuilder();
                fileEntry.setId(getId(fileRelativePath));
                fileEntry.setName(file.getName());
                fileEntry.setSize(file.length());
                fileEntry.setContentId(getFileContentId(file));
                fileEntry.setRelativePath(fileRelativePath);
                fileEntry.setParentId(folderId);
                try {
                    FileMetadata fMetadata = parseFileMetadata(fileEntry, file);
                    if (null != fMetadata) {
                        fileEntry.setFileMetadata(fMetadata);
                    }
                    // get [cts]-known-failures.xml
                    if (file.getName().endsWith(TEST_SUITE_TRADEFED_TAG)) {
                        mRelContentBuilder.setTestSuiteTradefed(fileRelativePath);
                        TestSuiteTradefedParser tstParser = new TestSuiteTradefedParser(file);
                        mRelContentBuilder.addAllKnownFailures(tstParser.getKnownFailureList());
                        mRelContentBuilder.setName(tstParser.getName());
                        mRelContentBuilder.setFullname(tstParser.getFullname());
                        mRelContentBuilder.setBuildNumber(tstParser.getBuildNumber());
                        mRelContentBuilder.setTargetArch(tstParser.getTargetArch());
                        mRelContentBuilder.setVersion(tstParser.getVersion());
                    }
                } catch (Exception ex) {
                    System.err.println(String.format("Cannot parse %s", file.getAbsolutePath()));
                    ex.printStackTrace();
                }
                mRelContentBuilder.addFileEntries(fileEntry);
                entryList.add(fileEntry.build());
                folderSize += file.length();
            } else if (file.isDirectory()) {
                Entry.Builder subFolderEntry = parseFolder(file.getAbsolutePath());
                subFolderEntry.setParentId(folderId);
                mRelContentBuilder.addFileEntries(subFolderEntry);
                folderSize += subFolderEntry.getSize();
                entryList.add(subFolderEntry.build());
            }
        }
        folderEntry.setId(folderId);
        folderEntry.setName(folderRelativePath);
        folderEntry.setSize(folderSize);
        folderEntry.setType(Entry.EntryType.FOLDER);
        folderEntry.setContentId(getFolderContentId(folderEntry, entryList));
        folderEntry.setRelativePath(folderRelativePath);
        return folderEntry;
    }

    // Parse a file
    private static FileMetadata parseFileMetadata(Entry.Builder fEntry, File file)
            throws Exception {
        if (file.getName().endsWith(CONFIG_EXT_TAG)) {
            fEntry.setType(Entry.EntryType.CONFIG);
            return parseConfigFile(file);
        } else if (file.getName().endsWith(APK_EXT_TAG)) {
            fEntry.setType(Entry.EntryType.APK);
        } else if (file.getName().endsWith(JAR_EXT_TAG)) {
            fEntry.setType(Entry.EntryType.JAR);
        } else if (file.getName().endsWith(SO_EXT_TAG)) {
            fEntry.setType(Entry.EntryType.SO);
        } else {
            // Just file in general
            fEntry.setType(Entry.EntryType.FILE);
        }
        return null;
    }

    private static FileMetadata parseConfigFile(File file) throws Exception {
        XMLReader xmlReader = XMLReaderFactory.createXMLReader();
        TestModuleConfigHandler testModuleXmlHandler = new TestModuleConfigHandler(file.getName());
        xmlReader.setContentHandler(testModuleXmlHandler);
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(file);
            xmlReader.parse(new InputSource(fileReader));
            return testModuleXmlHandler.getFileMetadata();
        } finally {
            if (null != fileReader) {
                fileReader.close();
            }
        }
    }

    private static String getFileContentId(File file) {
        String id = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            FileInputStream fis = new FileInputStream(file);
            byte[] dataBytes = new byte[10240];
            int nread = 0;
            while ((nread = fis.read(dataBytes)) != -1) {
                md.update(dataBytes, 0, nread);
            }
            // Converts to Base64 String
            id = Base64.getEncoder().encodeToString(md.digest());
        } catch (IOException e) {
            System.err.println("IOException:" + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            System.err.println("NoSuchAlgorithmException:" + e.getMessage());
        }
        return id;
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

    private static String getId(String name) {
        String id = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(name.getBytes(StandardCharsets.UTF_8));
            // Converts to Base64 String
            id = Base64.getEncoder().encodeToString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            System.err.println("NoSuchAlgorithmException:" + e.getMessage());
        }
        return id;
    }

    // write releaes content to a CSV file
    public void writeCsvFile(String csvFile) {
        ReleaseContent relContent = getReleaseContent();
        try {
            FileWriter fWriter = new FileWriter(csvFile);
            PrintWriter pWriter = new PrintWriter(fWriter);
            //Header
            pWriter.printf(
                    "type,name,size,relative path,id,content id,parent id,description,test class");
            // test class header
            pWriter.printf(
                    ",%s,%s,%s,%s,%s",
                    RUNTIME_HIT_TAG,
                    PACKAGE_TAG,
                    JAR_NAME_TAG,
                    NATIVE_TEST_DEVICE_PATH_TAG,
                    MODULE_TAG);
            // target preparer header
            pWriter.printf(",%s,%s\n", TEST_FILE_NAME_TAG, PUSH_TAG);
            int i = 1;
            for (Entry entry : relContent.getFileEntriesList()) {
                pWriter.printf(
                        "%s,%s,%d,%s,%s,%s,%s",
                        entry.getType(),
                        entry.getName(),
                        entry.getSize(),
                        entry.getRelativePath(),
                        entry.getId(),
                        entry.getContentId(),
                        entry.getParentId());

                if (Entry.EntryType.CONFIG == entry.getType()) {
                    ConfigMetadata config = entry.getFileMetadata().getConfigMetadata();
                    pWriter.printf(",%s", entry.getFileMetadata().getDescription());
                    List<Option> optList;
                    List<ConfigMetadata.TestClass> testClassesList = config.getTestClassesList();
                    String rtHit = "";
                    String pkg = "";
                    String jar = "";
                    String ntdPath = "";
                    String module = "";

                    for (ConfigMetadata.TestClass tClass : testClassesList) {
                        pWriter.printf(",%s", tClass.getTestClass());
                        optList = tClass.getOptionsList();
                        for (Option opt : optList) {
                            if (RUNTIME_HIT_TAG.equalsIgnoreCase(opt.getName())) {
                                rtHit = rtHit + opt.getValue() + " ";
                            } else if (PACKAGE_TAG.equalsIgnoreCase(opt.getName())) {
                                pkg = pkg + opt.getValue() + " ";
                            } else if (JAR_NAME_TAG.equalsIgnoreCase(opt.getName())) {
                                jar = jar + opt.getValue() + " ";
                            } else if (NATIVE_TEST_DEVICE_PATH_TAG.equalsIgnoreCase(
                                    opt.getName())) {
                                ntdPath = ntdPath + opt.getValue() + " ";
                            } else if (MODULE_TAG.equalsIgnoreCase(opt.getName())) {
                                module = module + opt.getValue() + " ";
                            }
                        }
                    }
                    pWriter.printf(
                            ",%s,%s,%s,%s,%s",
                            rtHit.trim(), pkg.trim(), jar.trim(), module.trim(), ntdPath.trim());

                    List<ConfigMetadata.TargetPreparer> tPrepList = config.getTargetPreparersList();
                    String testFile = "";
                    String pushList = "";
                    for (ConfigMetadata.TargetPreparer tPrep : tPrepList) {
                        optList = tPrep.getOptionsList();
                        for (Option opt : optList) {
                            if (TEST_FILE_NAME_TAG.equalsIgnoreCase(opt.getName())) {
                                testFile = testFile + opt.getValue() + " ";
                            } else if (PUSH_TAG.equalsIgnoreCase(opt.getName())) {
                                pushList = pushList + opt.getValue() + " ";
                            }
                        }
                    }
                    pWriter.printf(",%s,%s", testFile.trim(), pushList.trim());
                }
                pWriter.printf("\n");
            }
            pWriter.flush();
            pWriter.close();
        } catch (IOException e) {
            System.err.println("IOException:" + e.getMessage());
        }
    }

    // write known failures to a CSV file
    public void writeKnownFailureCsvFile(String csvFile) {
        ReleaseContent relContent = getReleaseContent();
        try {
            FileWriter fWriter = new FileWriter(csvFile);
            PrintWriter pWriter = new PrintWriter(fWriter);
            //Header
            pWriter.printf("compatibility:exclude-filter\n");
            for (String kf : relContent.getKnownFailuresList()) {
                pWriter.printf("%s\n", kf);
            }
            pWriter.flush();
            pWriter.close();
        } catch (IOException e) {
            System.err.println("IOException:" + e.getMessage());
        }
    }
}
