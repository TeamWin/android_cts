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


import com.android.cts.apicoverage.TestSuiteProto.*;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.Annotation;
import org.jf.dexlib2.iface.AnnotationElement;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.value.StringEncodedValue;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class TestCaseReport {
    // JUNIT3 Test suffix
    private static final String TEST_TAG = "Test;";
    private static final String TEST_PREFIX_TAG = "test";
    private static final String RUN_WITH_ANNOTATION_TAG =
            "Lorg/junit/runner/RunWith;";
    private static final String TEST_ANNOTATION_TAG =
            "Lorg/junit/Test;";
    private static final String PARAMETERS_TAG =
            "Lorg/junit/runners/Parameterized$Parameters";
    private static final String ANDROID_JUNIT4_TEST_TAG =
            "AndroidJUnit4.class";
    private static final String PARAMETERIZED_TAG =
            "Parameterized.class";

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

    private static final String SUITE_API_INSTALLER_TAG = "com.android.tradefed.targetprep.suite.SuiteApkInstaller";
    private static final String JAR_HOST_TEST_TAG = "com.android.compatibility.common.tradefed.testtype.JarHostTest";
    // com.android.tradefed.targetprep.suite.SuiteApkInstaller option
    private static final String TEST_FILE_NAME_TAG = "test-file-name";
    // com.android.compatibility.common.tradefed.targetprep.FilePusher option
    private static final String PUSH_TAG = "push";

    // test class
    private static final String ANDROID_JUNIT_TEST_TAG = "com.android.tradefed.testtype.AndroidJUnitTest";

    // Target File Extensions
    private static final String CONFIG_EXT_TAG = ".config";
    private static final String CONFIG_REGEX = ".config$";
    private static final String JAR_EXT_TAG = ".jar";
    private static final String APK_EXT_TAG = ".apk";
    private static final String SO_EXT_TAG = ".so";

    private static void printUsage() {
        System.out.println("Usage: test-suite-content-report [OPTION]...");
        System.out.println();
        System.out.println("Generates test test list protocal buffer message.");
        System.out.println();
        System.out.println(
                "$ANDROID_HOST_OUT/bin/test-suite-content-report "
                        + "-i out/host/linux-x86/cts/android-cts/testcases "
                        + "-c ./cts-content.pb"
                        + "-o ./cts-list.pb");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -i PATH                path to the Test Suite Folder");
        System.out.println("  -c FILE                input file of Test Content Protocal Buffer");
        System.out.println("  -o FILE                output file of Test Case List Protocal Buffer");
        System.out.println();
        System.exit(1);
    }

    /** Get the argument or print out the usage and exit. */
    private static String getExpectedArg(String[] args, int index) {
        if (index < args.length) {
            return args[index];
        } else {
            printUsage();
            return null;    // Never will happen because print Usage will call exit(1)
        }
    }

    private static boolean hasAnnotation(Set<? extends Annotation> annotations, String tag) {
        for (Annotation annotation : annotations) {
            if (annotation.getType().equals(tag)) {
                return true;
            }
        }
        return false;
    }

    private static TestSuite.Package.Class.Type chkTestType(ClassDef classDef) {
        for (Annotation annotation : classDef.getAnnotations()) {
            if (annotation.getType().equals(RUN_WITH_ANNOTATION_TAG)) {
                for (AnnotationElement annotationEle : annotation.getElements()) {
                    String aName = annotationEle.getName();
                    if (aName.equals(ANDROID_JUNIT4_TEST_TAG)) {
                        return TestSuite.Package.Class.Type.JUNIT4;
                    } else if (aName.equals(PARAMETERIZED_TAG)) {
                        return TestSuite.Package.Class.Type.PARAMETERIZED;
                    }
                }

                return TestSuite.Package.Class.Type.JUNIT4;
            }
        }

        if (classDef.getType().endsWith(TEST_TAG)) {
            return TestSuite.Package.Class.Type.JUNIT3;
        } else {
            return TestSuite.Package.Class.Type.UNKNOWN;
        }
    }

    private static boolean isTargetClass(List<String> pkgList, String className) {
        boolean found = false;
        for (String pkg : pkgList) {
            if (className.startsWith(pkg)) {
                found = true;
                break;
            }
        }
        return found;
    }

    // Get test case list from an APK
    private static TestSuite.Package.Builder parseApkTestCase(List<String> apkList,
            List<String> classList, String tsPath, int api)
            throws Exception {

        TestSuite.Package.Builder tsPkgBuilder = TestSuite.Package.newBuilder();
        for (String apkName : apkList) {
            DexFile dexFile = null;
            String apkPath = Paths.get(tsPath, apkName).toString();
            try {
                dexFile = DexFileFactory.loadDexFile(apkPath, Opcodes.forApi(api));
            } catch (IOException | DexFileFactory.DexFileNotFoundException ex) {
                System.err.println("Unable to load dex file: " + apkPath);
                // ex.printStackTrace();
                continue;
            }

            tsPkgBuilder.setName(apkName);
            for (ClassDef classDef : dexFile.getClasses()) {
                // adjust the format Lclass/y;
                String className = classDef.getType().replace('/','.');
                // remove L...;
                if (className.length() > 2) {
                    className = className.substring(1, className.length() - 1);
                }
                if (isTargetClass(classList, className)){
                    if ((classDef.getAccessFlags() & AccessFlags.PUBLIC.getValue()) != 0) {
                        TestSuite.Package.Class.Builder tClassBuilder = TestSuite.Package.Class.newBuilder();
                        tClassBuilder.setTestType(chkTestType(classDef));
                        tClassBuilder.setApk(apkName);
                        tClassBuilder.setName(className);
                        if (TestSuite.Package.Class.Type.JUNIT3 == tClassBuilder.getTestType()) {
                            for (Method method : classDef.getMethods()) {
                                if ((method.getAccessFlags() & AccessFlags.PUBLIC.getValue()) != 0) {
                                    String mName = method.getName();
                                    if (mName.startsWith(TEST_PREFIX_TAG)) {
                                        TestSuite.Package.Class.Method.Builder methodBuilder =  TestSuite.Package.Class.Method.newBuilder();
                                        methodBuilder.setName(mName);
                                        tClassBuilder.addMethods(methodBuilder);
                                    }
                                }
                            }
                        } else if (TestSuite.Package.Class.Type.JUNIT4 == tClassBuilder.getTestType()) {
                            for (Method method : classDef.getMethods()) {
                                if (hasAnnotation(method.getAnnotations(), TEST_ANNOTATION_TAG)) {
                                    String mName = method.getName();
                                    TestSuite.Package.Class.Method.Builder methodBuilder =  TestSuite.Package.Class.Method.newBuilder();
                                    methodBuilder.setName(mName);
                                    tClassBuilder.addMethods(methodBuilder);
                                }
                            }
                        }
                        tsPkgBuilder.addClasses(tClassBuilder);
                    }
                }
            }
        }
        return tsPkgBuilder;
    }

    // Iterates though all test suite content and prints them.
    static TestSuite.Builder listTestCases(TestSuiteContent tsContent, String tsPath)
            throws Exception {
        TestSuite.Builder tsBuilder = TestSuite.newBuilder();

        int i = 1;
        for (Entry entry: tsContent.getFileEntriesList()) {
            if (Entry.EntryType.CONFIG == entry.getType()) {
                ConfigMetadata config = entry.getFileMetadata().getConfigMetadata();

                // getting package/class list from Test Module Configuration
                ArrayList<String> testClassList = new ArrayList<String> ();
                List<Option> optList;
                List<ConfigMetadata.TestClass> testClassesList = config.getTestClassesList();
                for (ConfigMetadata.TestClass tClass : testClassesList) {
                    optList = tClass.getOptionsList();
                    for (Option opt : optList) {
                        if (PACKAGE_TAG.equalsIgnoreCase(opt.getName())) {
                            testClassList.add(opt.getValue());
                        }
                    }
                }

                // getting apk list from Test Module Configuration
                ArrayList<String> testApkList = new ArrayList<String> ();
                List<ConfigMetadata.TargetPreparer> tPrepList = config.getTargetPreparersList();
                for (ConfigMetadata.TargetPreparer tPrep : tPrepList) {
                    optList = tPrep.getOptionsList();
                    for (Option opt : optList) {
                        if (TEST_FILE_NAME_TAG.equalsIgnoreCase(opt.getName())) {
                            testApkList.add(opt.getValue());
                        }
                    }
                }

                TestSuite.Package.Builder tsPkgBuilder =
                        parseApkTestCase(testApkList, testClassList, tsPath, 27);
                tsPkgBuilder.setName(entry.getName().replaceAll(CONFIG_REGEX, ""));
                tsBuilder.addPackages(tsPkgBuilder);
            }
        }
        return tsBuilder;
    }

    // Iterates though all test suite content and prints them.
    static void printTestSuite(TestSuite ts) {
        //Header
        System.out.println("no,Module,Class,Test,Apk,Type");
        int i = 1;
        for (TestSuite.Package pkg : ts.getPackagesList()) {
            for (TestSuite.Package.Class cls : pkg.getClassesList()) {
                for (TestSuite.Package.Class.Method mtd : cls.getMethodsList()) {
                    System.out.printf("%d,%s,%s,%s,%s,%s\n",
                            i++,
                            pkg.getName(),
                            cls.getName(),
                            mtd.getName(),
                            cls.getApk(),
                            cls.getTestType());
                }
            }
        }
    }

    // Iterates though all test suite content and prints them.
    static void printTestSuiteSummary(TestSuite ts, String fName)
        throws IOException{
        FileWriter fWriter = new FileWriter(fName);
        PrintWriter pWriter = new PrintWriter(fWriter);

        //Header
        pWriter.print("no,Module,Test#,Class#\n");

        int i = 0;
        for (TestSuite.Package pkg : ts.getPackagesList()) {
            int classCnt = 0;
            int methodCnt = 0;
            for (TestSuite.Package.Class cls : pkg.getClassesList()) {
                for (TestSuite.Package.Class.Method mtd : cls.getMethodsList()) {
                    methodCnt++;
                }
                classCnt++;
            }
            pWriter.printf("%d,%s,%d,%d\n",
                    i++,
                    pkg.getName(),
                    methodCnt,
                    classCnt);
        }

        pWriter.close();
    }

    public static void main(String[] args)
            throws IOException, NoSuchAlgorithmException, Exception {
        String tsContentFilePath = "./tsContentMessage.pb";
        String outputTestCaseListFilePath = "./tsTestCaseList.pb";
        String outputSummaryFilePath = "./tsSummary.csv";
        String tsPath = "";

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                if ("-o".equals(args[i])) {
                    outputTestCaseListFilePath = getExpectedArg(args, ++i);
                } else if ("-c".equals(args[i])) {
                    tsContentFilePath = getExpectedArg(args, ++i);
                } else if ("-s".equals(args[i])) {
                    outputSummaryFilePath = getExpectedArg(args, ++i);
                } else if ("-i".equals(args[i])) {
                    tsPath = getExpectedArg(args, ++i);
                    File file = new File(tsPath);
                    // Only acception a folder
                    if (!file.isDirectory()) {
                        printUsage();
                    }
                } else {
                    printUsage();
                }
            }
        }

        // Read message from the file and print them out
        TestSuiteContent tsContent =
                TestSuiteContent.parseFrom(new FileInputStream(tsContentFilePath));

        TestSuite ts = listTestCases(tsContent, tsPath).build();

        // Write test case list message to disk.
        FileOutputStream output = new FileOutputStream(outputTestCaseListFilePath);
        try {
          ts.writeTo(output);
        } finally {
          output.close();
        }

        // Read message from the file and print them out
        TestSuite ts1 = TestSuite.parseFrom(new FileInputStream(outputTestCaseListFilePath));
        printTestSuite(ts1);
        printTestSuiteSummary(ts1, outputSummaryFilePath);
    }
}
