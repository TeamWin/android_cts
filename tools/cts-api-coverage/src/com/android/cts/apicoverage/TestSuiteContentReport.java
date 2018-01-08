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

package com.android.cts.apicoverage;


import com.android.cts.apicoverage.TestSuiteProto.Entry;
import com.android.cts.apicoverage.TestSuiteProto.Entry.EntryType;
import com.android.cts.apicoverage.TestSuiteProto.TestSuiteContent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

class TestSuiteContentReport {
    private static void printUsage() {
        System.out.println("Usage: test-suite-content-report [OPTION]...");
        System.out.println();
        System.out.println("Generates test suite content protocal buffer message.");
        System.out.println();
        System.out.println(
                "$ANDROID_HOST_OUT/bin/test-suite-content-report "
                        + "-i out/host/linux-x86/cts/android-cts "
                        + "-o ./cts-content.pb");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -o FILE                output file or standard out if not given");
        System.out.println("  -i PATH                path to the Test Suite Folder");
        System.out.println();
        System.exit(1);
    }

    /** Get the argument or print out the usage and exit. */
    private static String getExpectedArg(String[] args, int index) {
        if (index < args.length) {
            return args[index];
        } else {
            printUsage();
            return null;    // Never will happen because printUsage will call exit(1)
        }
    }

    public static TestSuiteContent parseTestSuiteFolder(String testSuitePath)
            throws IOException, NoSuchAlgorithmException {

        TestSuiteContent.Builder testSuiteContent = TestSuiteContent.newBuilder();
        testSuiteContent.addFileEntries(parseFolder(testSuiteContent, testSuitePath, testSuitePath));
        return testSuiteContent.build();
    }

    // Parse a folder to add all entries
    private static Entry.Builder parseFolder(TestSuiteContent.Builder testSuiteContent, String fPath, String rPath)
            throws IOException, NoSuchAlgorithmException {
        Entry.Builder folderEntry = Entry.newBuilder();

        File folder = new File(fPath);
        File rFolder = new File(rPath);
        Path folderPath = Paths.get(folder.getAbsolutePath());
        Path rootPath = Paths.get(rFolder.getAbsolutePath());
        String folderRelativePath = rootPath.relativize(folderPath).toString();
        String folderId = getId(folderRelativePath);
        File[] fileList = folder.listFiles();
        Long folderSize = 0L;
        List <Entry> entryList = new ArrayList<Entry> ();
        for (File file : fileList){
            if (file.isFile()){
                String fileRelativePath = rootPath.relativize(Paths.get(file.getAbsolutePath())).toString();
                Entry.Builder fileEntry = Entry.newBuilder();
                fileEntry.setId(getId(fileRelativePath));
                fileEntry.setName(file.getName());
                fileEntry.setSize(file.length());
                fileEntry.setType(EntryType.FILE);
                fileEntry.setContentId(getFileContentId(file));
                fileEntry.setRelativePath(fileRelativePath);
                fileEntry.setParentId(folderId);
                testSuiteContent.addFileEntries(fileEntry);
                entryList.add(fileEntry.build());
                folderSize += file.length();
            } else if (file.isDirectory()){
                Entry.Builder subFolderEntry = parseFolder(testSuiteContent, file.getAbsolutePath(), rPath);
                subFolderEntry.setParentId(folderId);
                testSuiteContent.addFileEntries(subFolderEntry);
                folderSize += subFolderEntry.getSize();
                entryList.add(subFolderEntry.build());
            }
        }

        folderEntry.setId(folderId);
        folderEntry.setName(folderRelativePath);
        folderEntry.setSize(folderSize);
        folderEntry.setType(EntryType.FOLDER);
        folderEntry.setContentId(getFolderContentId(folderEntry, entryList));
        folderEntry.setRelativePath(folderRelativePath);
        return folderEntry;
    }

    private static String getFileContentId(File file)
            throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        FileInputStream fis = new FileInputStream(file);

        byte[] dataBytes = new byte[10240];

        int nread = 0;
        while ((nread = fis.read(dataBytes)) != -1) {
          md.update(dataBytes, 0, nread);
        };
        byte[] mdbytes = md.digest();

        // Converts to Hex String
        StringBuffer hexString = new StringBuffer();
        for (int i=0;i<mdbytes.length;i++) {
            hexString.append(Integer.toHexString(0xFF & mdbytes[i]));
        }
        return hexString.toString();
    }

    private static String getFolderContentId(Entry.Builder folderEntry, List<Entry> entryList)
            throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");

        for (Entry entry: entryList) {
            md.update(entry.getContentId().getBytes(StandardCharsets.UTF_8));
        }
        byte[] mdbytes = md.digest();

        // Converts to Hex String
        StringBuffer hexString = new StringBuffer();
        for (int i=0;i<mdbytes.length;i++) {
            hexString.append(Integer.toHexString(0xFF & mdbytes[i]));
        }
        return hexString.toString();
    }

    private static String getId(String name)
            throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(name.getBytes(StandardCharsets.UTF_8));
        byte[] mdbytes = md.digest();

        // Converts to Hex String
        StringBuffer hexString = new StringBuffer();
        for (int i=0;i<mdbytes.length;i++) {
            hexString.append(Integer.toHexString(0xFF & mdbytes[i]));
        }
        return hexString.toString();
    }

    // Iterates though all test suite content and prints them.
    static void Print(TestSuiteContent tsContent) {
        System.out.printf("no,name,size,relativePath,id,cid,pid\n");
        int i = 1;
        for(Entry entry: tsContent.getFileEntriesList()) {
            System.out.printf("%d,%s,%d,%s,%s,%s,%s\n", i++,
                entry.getName(), entry.getSize(),
                entry.getRelativePath(),
                entry.getId(), entry.getContentId(),entry.getParentId());
        }
    }

    public static void main(String[] args)
            throws IOException, NoSuchAlgorithmException {
        String outputFilePath = "./tsContentMessage.pb";
        String tsPath = "";

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                if ("-o".equals(args[i])) {
                    outputFilePath = getExpectedArg(args, ++i);
                } else if ("-i".equals(args[i])) {
                    tsPath = getExpectedArg(args, ++i);
                    File file = new File(tsPath);
                    if (file.isDirectory()) {
                        //
                    } else {
                        printUsage();
                    }
                } else {
                    printUsage();
                }
            }
        }

        TestSuiteContent tsContent = parseTestSuiteFolder(tsPath);

        // Write test suite content message to disk.
        FileOutputStream output = new FileOutputStream(outputFilePath);
        try {
          tsContent.writeTo(output);
        } finally {
          output.close();
        }

        // Read message from the file and print them out
        TestSuiteContent tsContent1 =
          TestSuiteContent.parseFrom(new FileInputStream(outputFilePath));
        Print(tsContent1);
    }
}
