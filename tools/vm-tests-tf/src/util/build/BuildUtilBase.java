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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper base class for code generators.
 */
public abstract class BuildUtilBase {

    public static boolean DEBUG = true;

    // TODO: Move this to loading from jar.
    public String JAVASRC_FOLDER;

    public static class MethodData {
        String methodBody, constraint, title;
    }

    public interface TestHandler {
        public void handleTest(String fqcn, List<String> methods);
    }

    public void run(TestHandler handler) {
        System.out.println("Collecting all junit tests...");
        JUnitTestCollector tests = new JUnitTestCollector(getClass().getClassLoader());

        handleTests(tests, handler);
    }

    protected void handleTests(JUnitTestCollector tests, TestHandler handler) {
        System.out.println("collected " + tests.testMethodsCnt + " test methods in " +
                tests.testClassCnt + " junit test classes");

        for (Entry<String, List<String>> entry : tests.map.entrySet()) {
            handler.handleTest(entry.getKey(), entry.getValue());
        }
    }

    protected MethodData parseTestMethod(String pname, String classOnlyName,
            String method) {

        String path = pname.replaceAll("\\.", "/");
        String absPath = JAVASRC_FOLDER + "/" + path + "/" + classOnlyName + ".java";
        File f = new File(absPath);

        Scanner scanner;
        try {
            scanner = new Scanner(f);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("error while reading to file: " + e.getClass().getName() +
                    ", msg:" + e.getMessage());
        }

        String methodPattern = "public\\s+void\\s+" + method + "[^\\{]+\\{";

        String token = scanner.findWithinHorizon(methodPattern, (int) f.length());
        if (token == null) {
            throw new RuntimeException("cannot find method source of 'public void " + method +
                    "' in file '" + absPath + "'");
        }

        MatchResult result = scanner.match();
        result.start();
        result.end();

        StringBuilder builder = new StringBuilder();

        try {
            FileReader reader = new FileReader(f);
            reader.skip(result.end());

            int readResult;
            int blocks = 1;
            while ((readResult = reader.read()) != -1 && blocks > 0) {
                char currentChar = (char) readResult;
                switch (currentChar) {
                    case '}': {
                        blocks--;
                        builder.append(currentChar);
                        break;
                    }
                    case '{': {
                        blocks++;
                        builder.append(currentChar);
                        break;
                    }
                    default: {
                        builder.append(currentChar);
                        break;
                    }
                }
            }
            if (reader != null) {
                reader.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to parse", e);
        }

        // find the @title/@constraint in javadoc comment for this method
        // using platform's default charset
        String all = new String(FileUtil.readFile(f));
        // System.out.println("grepping javadoc found for method " + method +
        // " in " + pname + "," + classOnlyName);
        String commentPattern = "/\\*\\*([^{]*)\\*/\\s*" + methodPattern;
        Pattern p = Pattern.compile(commentPattern, Pattern.DOTALL);
        Matcher m = p.matcher(all);
        String title = null, constraint = null;
        if (m.find()) {
            String res = m.group(1);
            // System.out.println("res: " + res);
            // now grep @title and @constraint
            Matcher titleM = Pattern.compile("@title (.*)", Pattern.DOTALL)
            .matcher(res);
            if (titleM.find()) {
                title = titleM.group(1).replaceAll("\\n     \\*", "");
                title = title.replaceAll("\\n", " ");
                title = title.trim();
                // System.out.println("title: " + title);
            } else {
                System.err.println("warning: no @title found for method " + method + " in " + pname +
                        "," + classOnlyName);
            }
            // constraint can be one line only
            Matcher constraintM = Pattern.compile("@constraint (.*)").matcher(
                    res);
            if (constraintM.find()) {
                constraint = constraintM.group(1);
                constraint = constraint.trim();
                // System.out.println("constraint: " + constraint);
            } else if (method.contains("VFE")) {
                System.err
                .println("warning: no @constraint for for a VFE method:" + method + " in " +
                        pname + "," + classOnlyName);
            }
        } else {
            System.err.println("warning: no javadoc found for method " + method + " in " + pname +
                    "," + classOnlyName);
        }
        MethodData md = new MethodData();
        md.methodBody = builder.toString();
        md.constraint = constraint;
        md.title = title;
        if (scanner != null) {
            scanner.close();
        }
        return md;
    }

    /**
     * @param pName
     * @param classOnlyName
     * @param methodSource
     * @return testclass names
     */
    protected static List<String> parseTestClassName(String pName, String classOnlyName,
            String methodSource) {
        List<String> entries = new ArrayList<String>(2);
        String opcodeName = classOnlyName.substring(5);

        Scanner scanner = new Scanner(methodSource);

        String[] patterns = new String[] {"new\\s(T_" + opcodeName + "\\w*)",
                "(T_" + opcodeName + "\\w*)", "new\\s(T\\w*)"};

        String token = null;
        for (String pattern : patterns) {
            token = scanner.findWithinHorizon(pattern, methodSource.length());
            if (token != null) {
                break;
            }
        }

        if (token == null) {
            System.err.println("warning: failed to find dependent test class name: " + pName +
                    ", " + classOnlyName + " in methodSource:\n" + methodSource);
            return entries;
        }

        MatchResult result = scanner.match();

        entries.add((pName + ".d." + result.group(1)).trim());

        // search additional @uses directives
        Pattern p = Pattern.compile("@uses\\s+(.*)\\s+", Pattern.MULTILINE);
        Matcher m = p.matcher(methodSource);
        while (m.find()) {
            String res = m.group(1);
            entries.add(0, res.trim());
        }

        // search for " load(\"...\" " and add as dependency
        Pattern loadPattern = Pattern.compile("load\\(\"([^\"]*)\"", Pattern.MULTILINE);
        Matcher loadMatcher = loadPattern.matcher(methodSource);
        while (loadMatcher.find()) {
            String res = loadMatcher.group(1);
            entries.add(res.trim());
        }

        // search for " loadAndRun(\"...\" " and add as dependency
        Pattern loadAndRunPattern = Pattern.compile("loadAndRun\\(\"([^\"]*)\"", Pattern.MULTILINE);
        Matcher loadAndRunMatcher = loadAndRunPattern.matcher(methodSource);
        while (loadAndRunMatcher.find()) {
            String res = loadAndRunMatcher.group(1);
            entries.add(res.trim());
        }

        // lines with the form @uses
        // dot.junit.opcodes.add_double.jm.T_add_double_2
        // one dependency per one @uses
        // TODO

        return entries;
    }

    public static void writeToFileMkdir(File file, String content) {
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new RuntimeException("failed to create directory: " + parent.getAbsolutePath());
        }
        writeToFile(file, content);
    }

    public static void writeToFile(File file, String content) {
        try {
            if (file.exists() && file.length() == content.length()) {
                FileReader reader = new FileReader(file);
                char[] charContents = new char[(int) file.length()];
                reader.read(charContents);
                reader.close();
                String contents = new String(charContents);
                if (contents.equals(content)) {
                    // System.out.println("skipping identical: "
                    // + file.getAbsolutePath());
                    return;
                }
            }

            //System.out.println("writing file " + file.getAbsolutePath());

            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), "utf-8"));
            bw.write(content);
            bw.close();
        } catch (Exception e) {
            throw new RuntimeException("error while writing to file: " + e.getClass().getName() +
                    ", msg:" + e.getMessage());
        }
    }

}
