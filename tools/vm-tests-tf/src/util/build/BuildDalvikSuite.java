/*
 * Copyright (C) 2011 The Android Open Source Project
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

package util.build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Main class to generate data from the test suite to later run from a shell
 * script. the project's home folder.<br>
 * <project-home>/src must contain the java sources<br>
 * <project-home>/src/<for-each-package>/Main_testN1.java will be generated<br>
 * (one Main class for each test method in the Test_... class
 */
public class BuildDalvikSuite extends BuildUtilBase {

    public static final String TARGET_MAIN_FILE = "mains.jar";

    // the folder for the generated junit-files for the cts host (which in turn
    // execute the real vm tests using adb push/shell etc)
    private static String HOSTJUNIT_SRC_OUTPUT_FOLDER = "";
    private static String OUTPUT_FOLDER = "";
    private static String COMPILED_CLASSES_FOLDER = "";

    private static String HOSTJUNIT_CLASSES_OUTPUT_FOLDER = "";

    private static String CLASS_PATH = "";

    private static final String TARGET_JAR_ROOT_PATH = "/data/local/tmp/vm-tests";

    private String JAVASRC_FOLDER;

    /**
     * @param args
     *            args 0 must be the project root folder (where src, lib etc.
     *            resides)
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        BuildDalvikSuite cat = new BuildDalvikSuite();
        if (!cat.parseArgs(args)) {
          printUsage();
          System.exit(-1);
        }

        long start = System.currentTimeMillis();
        cat.run(null);
        long end = System.currentTimeMillis();

        System.out.println("elapsed seconds: " + (end - start) / 1000);
    }

    private boolean parseArgs(String[] args) {
      if (args.length == 5) {
          JAVASRC_FOLDER = args[0];
          OUTPUT_FOLDER = args[1];
          CLASS_PATH = args[2];

          COMPILED_CLASSES_FOLDER = args[3];

          HOSTJUNIT_SRC_OUTPUT_FOLDER = args[4];
          HOSTJUNIT_CLASSES_OUTPUT_FOLDER = HOSTJUNIT_SRC_OUTPUT_FOLDER + "/classes";
          return true;
      } else {
          return false;
      }
    }

    private static void printUsage() {
        System.out.println("usage: java-src-folder output-folder classpath " +
                           "generated-main-files compiled_output generated-main-files " +
                           "[restrict-to-opcode]");
    }

    private SourceBuildStep hostJunitBuildStep;

    private static class HostState {
        private String fileName;
        private StringBuilder fileData;

        public HostState(String fileName) {
            this.fileName = fileName;
            fileData = new StringBuilder();
        }

        public void append(String s) {
            fileData.append(s);
        }

        private void addCTSHostMethod(String pName, String method,
                Collection<String> dependentTestClassNames) {
            fileData.append("public void " + method + "() throws Exception {\n");
            final String targetCoreJarPath = String.format("%s/dot/junit/dexcore.jar",
                    TARGET_JAR_ROOT_PATH);

            String mainsJar = String.format("%s/%s", TARGET_JAR_ROOT_PATH, TARGET_MAIN_FILE);

            String cp = String.format("%s:%s", targetCoreJarPath, mainsJar);
            for (String depFqcn : dependentTestClassNames) {
                String sourceName = depFqcn.replaceAll("\\.", "/") + ".jar";
                String targetName= String.format("%s/%s", TARGET_JAR_ROOT_PATH,
                        sourceName);
                cp += ":" + targetName;
                // dot.junit.opcodes.invoke_interface_range.ITest
                // -> dot/junit/opcodes/invoke_interface_range/ITest.jar
            }

            //"dot.junit.opcodes.add_double_2addr.Main_testN2";
            String mainclass = pName + ".Main_" + method;
            fileData.append(getShellExecJavaLine(cp, mainclass));
            fileData.append("\n}\n\n");
        }

        public void end() {
            fileData.append("\n}\n");
        }

        public File getFileToWrite() {
            return new File(fileName);
        }
        public String getBuildStep() {
            return new File(fileName).getAbsolutePath();
        }
        public String getData() {
            return fileData.toString();
        }
    }

    private void flushHostState(HostState state) {
        state.end();

        File toWrite = state.getFileToWrite();
        writeToFileMkdir(toWrite, state.getData());

        hostJunitBuildStep.addSourceFile(state.getBuildStep());
    }

    private HostState openCTSHostFileFor(String pName, String classOnlyName) {
        String sourceName = classOnlyName;

        String modPackage = pName;
        {
            // Given a class name of "Test_zzz" and a package of "xxx.yyy.zzz," strip
            // "zzz" from the package to reduce duplication (and dashboard clutter).
            int lastDot = modPackage.lastIndexOf('.');
            if (lastDot > 0) {
                String lastPackageComponent = modPackage.substring(lastDot + 1);
                if (classOnlyName.equals("Test_" + lastPackageComponent)) {
                    // Drop the duplication.
                    modPackage = modPackage.substring(0, lastDot);
                }
            }
        }

        String fileName = HOSTJUNIT_SRC_OUTPUT_FOLDER + "/" + modPackage.replaceAll("\\.", "/")
                + "/" + sourceName + ".java";

        HostState newState = new HostState(fileName);

        newState.append(getWarningMessage());
        newState.append("package " + modPackage + ";\n");
        newState.append("import java.io.IOException;\n" +
                "import java.util.concurrent.TimeUnit;\n\n" +
                "import com.android.tradefed.device.CollectingOutputReceiver;\n" +
                "import com.android.tradefed.testtype.IAbi;\n" +
                "import com.android.tradefed.testtype.IAbiReceiver;\n" +
                "import com.android.tradefed.testtype.DeviceTestCase;\n" +
                "import com.android.tradefed.util.AbiFormatter;\n" +
                "\n");
        newState.append("public class " + sourceName + " extends DeviceTestCase implements " +
                "IAbiReceiver {\n");

        newState.append("\n" +
                "protected IAbi mAbi;\n" +
                "@Override\n" +
                "public void setAbi(IAbi abi) {\n" +
                "    mAbi = abi;\n" +
                "}\n\n");

        return newState;
    }

    private static String getShellExecJavaLine(String classpath, String mainclass) {
      String cmd = String.format("ANDROID_DATA=%s dalvikvm|#ABI#| -Xmx512M -Xss32K -Xnodex2oat " +
              "-Djava.io.tmpdir=%s -classpath %s %s", TARGET_JAR_ROOT_PATH, TARGET_JAR_ROOT_PATH,
              classpath, mainclass);
      StringBuilder code = new StringBuilder();
      code.append("    String cmd = AbiFormatter.formatCmdForAbi(\"")
          .append(cmd)
          .append("\", mAbi.getBitness());\n")
          .append("    CollectingOutputReceiver receiver = new CollectingOutputReceiver();\n")
          .append("    getDevice().executeShellCommand(cmd, receiver, 6, TimeUnit.MINUTES, 1);\n")
          .append("    // A sucessful adb shell command returns an empty string.\n")
          .append("    assertEquals(cmd, \"\", receiver.getOutput());");
      return code.toString();
    }

    private String getWarningMessage() {
        return "//Autogenerated code by " + this.getClass().getName() + "; do not edit.\n";
    }

    class MyTestHandler implements TestHandler {
        public String datafileContent = "";
        Set<BuildStep> targets = new TreeSet<BuildStep>();

        @Override
        public void handleTest(String fqcn, List<String> methods) {
            int lastDotPos = fqcn.lastIndexOf('.');
            String pName = fqcn.substring(0, lastDotPos);
            String classOnlyName = fqcn.substring(lastDotPos + 1);

            HostState hostState = openCTSHostFileFor(pName, classOnlyName);

            Collections.sort(methods, new Comparator<String>() {
                @Override
                public int compare(String s1, String s2) {
                    // TODO sort according: test ... N, B, E, VFE
                    return s1.compareTo(s2);
                }
            });
            for (String method : methods) {
                // e.g. testN1
                if (!method.startsWith("test")) {
                    throw new RuntimeException("no test method: " + method);
                }

                // generate the Main_xx java class

                // a Main_testXXX.java contains:
                // package <packagenamehere>;
                // public class Main_testxxx {
                // public static void main(String[] args) {
                // new dxc.junit.opcodes.aaload.Test_aaload().testN1();
                // }
                // }
                MethodData md = parseTestMethod(pName, classOnlyName, method);
                String methodContent = md.methodBody;

                List<String> dependentTestClassNames = parseTestClassName(pName,
                        classOnlyName, methodContent);

                hostState.addCTSHostMethod(pName, method, dependentTestClassNames);

                if (dependentTestClassNames.isEmpty()) {
                    continue;
                }

                // prepare the entry in the data file for the bash script.
                // e.g.
                // main class to execute; opcode/constraint; test purpose
                // dxc.junit.opcodes.aaload.Main_testN1;aaload;normal case test
                // (#1)

                char ca = method.charAt("test".length()); // either N,B,E,
                // or V (VFE)
                String comment;
                switch (ca) {
                case 'N':
                    comment = "Normal #" + method.substring(5);
                    break;
                case 'B':
                    comment = "Boundary #" + method.substring(5);
                    break;
                case 'E':
                    comment = "Exception #" + method.substring(5);
                    break;
                case 'V':
                    comment = "Verifier #" + method.substring(7);
                    break;
                default:
                    throw new RuntimeException("unknown test abbreviation:"
                            + method + " for " + fqcn);
                }

                String line = pName + ".Main_" + method + ";";
                for (String className : dependentTestClassNames) {
                    line += className + " ";
                }


                // test description
                String[] pparts = pName.split("\\.");
                // detail e.g. add_double
                String detail = pparts[pparts.length-1];
                // type := opcode | verify
                String type = pparts[pparts.length-2];

                String description;
                if ("format".equals(type)) {
                    description = "format";
                } else if ("opcodes".equals(type)) {
                    // Beautify name, so it matches the actual mnemonic
                    detail = detail.replaceAll("_", "-");
                    detail = detail.replace("-from16", "/from16");
                    detail = detail.replace("-high16", "/high16");
                    detail = detail.replace("-lit8", "/lit8");
                    detail = detail.replace("-lit16", "/lit16");
                    detail = detail.replace("-4", "/4");
                    detail = detail.replace("-16", "/16");
                    detail = detail.replace("-32", "/32");
                    detail = detail.replace("-jumbo", "/jumbo");
                    detail = detail.replace("-range", "/range");
                    detail = detail.replace("-2addr", "/2addr");

                    // Unescape reserved words
                    detail = detail.replace("opc-", "");

                    description = detail;
                } else if ("verify".equals(type)) {
                    description = "verifier";
                } else {
                    description = type + " " + detail;
                }

                String details = (md.title != null ? md.title : "");
                if (md.constraint != null) {
                    details = " Constraint " + md.constraint + ", " + details;
                }
                if (details.length() != 0) {
                    details = details.substring(0, 1).toUpperCase()
                            + details.substring(1);
                }

                line += ";" + description + ";" + comment + ";" + details;

                datafileContent += line + "\n";
                generateBuildStepFor(dependentTestClassNames, targets);
            }

            flushHostState(hostState);
        }
    }

    @Override
    protected void handleTests(JUnitTestCollector tests, TestHandler ignored) {
        hostJunitBuildStep = new JavacBuildStep(
                HOSTJUNIT_CLASSES_OUTPUT_FOLDER, CLASS_PATH);

        MyTestHandler handler = new MyTestHandler();
        super.handleTests(tests, handler);

        File scriptDataDir = new File(OUTPUT_FOLDER + "/data/");
        scriptDataDir.mkdirs();
        writeToFile(new File(scriptDataDir, "scriptdata"), handler.datafileContent);

        if (!hostJunitBuildStep.build()) {
            System.out.println("main javac cts-host-hostjunit-classes build step failed");
            System.exit(1);
        }

        for (BuildStep buildStep : handler.targets) {
            if (!buildStep.build()) {
                System.out.println("building failed. buildStep: " +
                        buildStep.getClass().getName() + ", " + buildStep);
                System.exit(1);
            }
        }
    }

    private void generateBuildStepFor(Collection<String> dependentTestClassNames,
            Set<BuildStep> targets) {
        for (String dependentTestClassName : dependentTestClassNames) {
            generateBuildStepForDependant(dependentTestClassName, targets);
        }
    }

    private void generateBuildStepForDependant(String dependentTestClassName,
            Set<BuildStep> targets) {

        File sourceFolder = new File(JAVASRC_FOLDER);
        String fileName = dependentTestClassName.replace('.', '/').trim();

        if (new File(sourceFolder, fileName + ".dfh").exists()) {
            // Handled in vmtests-dfh-dex-generated build rule.
            return;
        }

        if (new File(sourceFolder, fileName + ".d").exists()) {
            // Handled in vmtests-dasm-dex-generated build rule.
            return;
        }

        {
            // Build dex from a single '*.smali' file or a *.smalis' dir
            // containing multiple smali files.
            File dexFile = null;
            SmaliBuildStep buildStep = null;
            File smaliFile = new File(sourceFolder, fileName + ".smali");
            File smalisDir = new File(sourceFolder, fileName + ".smalis");

            if (smaliFile.exists()) {
                dexFile = new File(OUTPUT_FOLDER, fileName + ".dex");
                buildStep = new SmaliBuildStep(
                    Collections.singletonList(smaliFile.getAbsolutePath()), dexFile);
            } else if (smalisDir.exists() && smalisDir.isDirectory()) {
                List<String> inputFiles = new ArrayList<>();
                for (File f: smalisDir.listFiles()) {
                    inputFiles.add(f.getAbsolutePath());
                }
                dexFile = new File(OUTPUT_FOLDER, fileName + ".dex");
                buildStep = new SmaliBuildStep(inputFiles, dexFile);
            }

            if (buildStep != null) {
                BuildStep.BuildFile jarFile = new BuildStep.BuildFile(
                        OUTPUT_FOLDER, fileName + ".jar");
                JarBuildStep jarBuildStep = new JarBuildStep(new BuildStep.BuildFile(dexFile),
                        "classes.dex", jarFile, true);
                jarBuildStep.addChild(buildStep);
                targets.add(jarBuildStep);
                return;
            }
        }

        File srcFile = new File(sourceFolder, fileName + ".java");
        if (srcFile.exists()) {
            BuildStep dexBuildStep;
            dexBuildStep = generateDexBuildStep(
              COMPILED_CLASSES_FOLDER, fileName);
            targets.add(dexBuildStep);
            return;
        }

        try {
            if (Class.forName(dependentTestClassName) != null) {
                BuildStep dexBuildStep = generateDexBuildStep(
                    COMPILED_CLASSES_FOLDER, fileName);
                targets.add(dexBuildStep);
                return;
            }
        } catch (ClassNotFoundException e) {
            // do nothing
        }

        throw new RuntimeException("neither .dfh,.d,.java file of dependant test class found : " +
                dependentTestClassName + ";" + fileName);
    }

    private BuildStep generateDexBuildStep(String classFileFolder,
            String classFileName) {
        BuildStep.BuildFile classFile = new BuildStep.BuildFile(
                classFileFolder, classFileName + ".class");

        BuildStep.BuildFile tmpJarFile = new BuildStep.BuildFile(
                OUTPUT_FOLDER,
                classFileName + "_tmp.jar");

        JarBuildStep jarBuildStep = new JarBuildStep(classFile,
                classFileName + ".class", tmpJarFile, false);

        BuildStep.BuildFile outputFile = new BuildStep.BuildFile(
                OUTPUT_FOLDER,
                classFileName + ".jar");

        D8BuildStep dexBuildStep = new D8BuildStep(tmpJarFile,
                outputFile,
                true);

        dexBuildStep.addChild(jarBuildStep);
        return dexBuildStep;
    }
}
