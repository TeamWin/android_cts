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
import java.io.FileOutputStream;
import java.io.IOException;

import java.nio.file.Paths;

/** Main of release parser */
public class Main {

    private static final String USAGE_MESSAGE =
            "Usage: java -jar releaseparser.jar [-options] <folder> [args...]\n"
                    + "           to prase a release content in the folder\n"
                    + "Options:\n"
                    + "\t-i PATH\t path to a release folder \n"
                    + "\t-o PATH\t path to output files \n";

    private Main() {}

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

    public static void main(final String[] args) {
        String relNameVer;
        String relFolder = "";
        String outputPath = "";

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                if ("-o".equals(args[i])) {
                    outputPath = getExpectedArg(args, ++i);
                    File file = new File(outputPath);
                    // Only acception a folder
                    if (!file.isDirectory()) {
                        printUsage();
                    }
                } else if ("-i".equals(args[i])) {
                    relFolder = getExpectedArg(args, ++i);
                    File file = new File(relFolder);
                    // Only acception a folder
                    if (!file.isDirectory()) {
                        printUsage();
                    }
                } else {
                    printUsage();
                }
            }
        }

        if ("".equals(relFolder) || "".equals(outputPath)) {
            printUsage();
        }

        ReleaseParser relParser = new ReleaseParser(relFolder);
        relNameVer = relParser.getRelNameVer();
        relParser.writeCsvFile(
                Paths.get(outputPath, String.format("%s-RC.csv", relNameVer)).toString());
        relParser.writeKnownFailureCsvFile(
                Paths.get(outputPath, String.format("%s-KF.csv", relNameVer)).toString());

        // Write release content message to disk.
        ReleaseContent relContent = relParser.getReleaseContent();
        try {
            FileOutputStream output =
                    new FileOutputStream(
                            Paths.get(outputPath, String.format("%s-RC.pb", relNameVer))
                                    .toString());
            try {
                relContent.writeTo(output);
            } finally {
                output.close();
            }
        } catch (IOException e) {
            System.err.println("IOException:" + e.getMessage());
        }

        TestSuiteParser tsParser = new TestSuiteParser(relContent, relFolder);
        tsParser.writeCsvFile(
                Paths.get(outputPath, String.format("%s-TC.csv", relNameVer)).toString());
        tsParser.writeSummaryCsvFile(
                Paths.get(outputPath, String.format("%s-TS.csv", relNameVer)).toString());

        // Write test suite content message to disk.
        TestSuite testSuite = tsParser.getTestSuite();
        try {
            FileOutputStream output =
                    new FileOutputStream(
                            Paths.get(outputPath, String.format("%s-TC.pb", relNameVer))
                                    .toString());
            try {
                testSuite.writeTo(output);
            } finally {
                output.close();
            }
        } catch (IOException e) {
            System.err.println("IOException:" + e.getMessage());
        }
    }
}
