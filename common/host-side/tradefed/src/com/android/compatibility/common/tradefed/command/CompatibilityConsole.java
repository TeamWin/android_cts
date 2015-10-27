/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.compatibility.common.tradefed.command;

import com.android.compatibility.SuiteInfo;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildProvider;
import com.android.compatibility.common.tradefed.result.IInvocationResultRepo;
import com.android.compatibility.common.tradefed.result.InvocationResultRepo;
import com.android.compatibility.common.tradefed.testtype.ModuleRepo;
import com.android.compatibility.common.util.IInvocationResult;
import com.android.compatibility.common.util.TestStatus;
import com.android.tradefed.command.Console;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RegexTrie;
import com.android.tradefed.util.TableFormatter;
import com.android.tradefed.util.TimeUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * An extension of Tradefed's console which adds features specific to compatibility testing.
 */
public class CompatibilityConsole extends Console {

    private CompatibilityBuildHelper mBuildHelper;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        printLine(String.format("Android %s %s (%s)", SuiteInfo.FULLNAME, SuiteInfo.VERSION,
                SuiteInfo.BUILD_NUMBER));
        super.run();
    }

    /**
     * Adds the 'list plans', 'list modules' and 'list results' commands
     */
    @Override
    protected void setCustomCommands(RegexTrie<Runnable> trie, List<String> genericHelp,
            Map<String, String> commandHelp) {
        trie.put(new Runnable() {
            @Override
            public void run() {
                // TODO(stuartscott)" listPlans(buildHelper);
            }
        }, LIST_PATTERN, "p(?:lans)?");
        trie.put(new Runnable() {
            @Override
            public void run() {
                listModules();
            }
        }, LIST_PATTERN, "m(?:odules)?");
        trie.put(new Runnable() {
            @Override
            public void run() {
                listResults();
            }
        }, LIST_PATTERN, "r(?:esults)?");

        // find existing help for 'LIST_PATTERN' commands, and append these commands help
        String listHelp = commandHelp.get(LIST_PATTERN);
        if (listHelp == null) {
            // no help? Unexpected, but soldier on
            listHelp = new String();
        }
        String combinedHelp = listHelp +
                "\tp[lans]\tList all plans" + LINE_SEPARATOR +
                "\tm[odules]\tList all modules" + LINE_SEPARATOR +
                "\tr[esults]\tList all results" + LINE_SEPARATOR;
        commandHelp.put(LIST_PATTERN, combinedHelp);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getConsolePrompt() {
        return String.format("%s-tf > ", SuiteInfo.NAME.toLowerCase());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getGenericHelpString(List<String> genericHelp) {
        StringBuilder helpBuilder = new StringBuilder();
        helpBuilder.append(SuiteInfo.FULLNAME);
        helpBuilder.append("\n\n");
        helpBuilder.append(SuiteInfo.NAME);
        helpBuilder.append(" is the test harness for running the Android Compatibility Suite, ");
        helpBuilder.append("built on top of Trade Federation.\n\n");
        helpBuilder.append("Available commands and options\n");
        helpBuilder.append("Host:\n");
        helpBuilder.append("  help: show this message.\n");
        helpBuilder.append("  help all: show the complete tradefed help.\n");
        helpBuilder.append("  version: show the version.\n");
        helpBuilder.append("  exit: gracefully exit the compatibiltiy console, waiting until all ");
        helpBuilder.append("invocations have completed.\n");
        helpBuilder.append("Run:\n");
        final String runPrompt = "  run <plan> ";
        helpBuilder.append(runPrompt);
        helpBuilder.append("--module/-m <module>: run a test module.\n");
        helpBuilder.append(runPrompt);
        helpBuilder.append("--module/-m <module> --test/-t <test_name>: run a specific test from");
        helpBuilder.append(" the module. Test name can be <package>.<class>, ");
        helpBuilder.append("<package>.<class>#<method> or <native_name>.\n");
        helpBuilder.append(runPrompt);
        helpBuilder.append("--retry <session_id>: run all failed tests from a previous session.\n");
        helpBuilder.append(runPrompt);
        helpBuilder.append("--help/--help-all: get help for ");
        helpBuilder.append(SuiteInfo.FULLNAME);
        helpBuilder.append(".\n");
        helpBuilder.append("Options:\n");
        helpBuilder.append("  --serial/-s <device_id>: The device to run the test on.\n");
        helpBuilder.append("  --abi/-a <abi>: The ABI to run the test against.\n");
        helpBuilder.append("  --shard <shards>: Shards a run into the given number of independant");
        helpBuilder.append(" chunks, to run on multiple devices in parallel.\n");
        helpBuilder.append("  --logcat-on-failure: Capture logcat when a test fails.\n");
        helpBuilder.append("  --bugreport-on-failure: Capture a bugreport when a test fails.\n");
        helpBuilder.append("  --screenshot-on-failure: Capture a screenshot when a test fails.\n");
        helpBuilder.append("List:\n");
        helpBuilder.append("  l/list d/devices: list connected devices and their state\n");
        helpBuilder.append("  l/list m/modules: list test modules\n");
        helpBuilder.append("  l/list i/invocations: list invocations aka test runs currently in ");
        helpBuilder.append("progress\n");
        helpBuilder.append("  l/list c/commands: list commands aka test run commands currently");
        helpBuilder.append(" in the queue waiting to be allocated devices\n");
        helpBuilder.append("  l/list r/results: list results currently in the repository\n");
        helpBuilder.append("Dump:\n");
        helpBuilder.append("  d/dump l/logs: dump the tradefed logs for all running invocations\n");
        return helpBuilder.toString();
    }

    private void listModules() {
        File[] files = null;
        try {
            files = getBuildHelper().getTestsDir().listFiles(new ModuleRepo.ConfigFilter());
        } catch (FileNotFoundException e) {
            printLine(e.getMessage());
            e.printStackTrace();
        }
        if (files != null && files.length > 0) {
            List<String> modules = new ArrayList<>();
            for (File moduleFile : files) {
                modules.add(FileUtil.getBaseName(moduleFile.getName()));
            }
            Collections.sort(modules);
            for (String module : modules) {
                printLine(module);
            }
        } else {
            printLine("No modules found");
        }
    }

    private void listResults() {
        TableFormatter tableFormatter = new TableFormatter();
        List<List<String>> table = new ArrayList<>();
        table.add(Arrays.asList("Session","Pass", "Fail", "Not Executed", "Start Time", "Test Plan",
                "Device serial(s)"));
        IInvocationResultRepo testResultRepo = null;
        List<IInvocationResult> results = null;
        try {
            testResultRepo = new InvocationResultRepo(getBuildHelper().getResultsDir());
            results = testResultRepo.getResults();
        } catch (FileNotFoundException e) {
            printLine(e.getMessage());
            e.printStackTrace();
        }
        if (testResultRepo != null && results.size() > 0) {
            for (int i = 0; i < results.size(); i++) {
                IInvocationResult result = results.get(i);
                table.add(Arrays.asList(Integer.toString(i),
                        Integer.toString(result.countResults(TestStatus.PASS)),
                        Integer.toString(result.countResults(TestStatus.FAIL)),
                        Integer.toString(result.countResults(TestStatus.NOT_EXECUTED)),
                        TimeUtil.formatTimeStamp(result.getStartTime()),
                        result.getTestPlan(),
                        ArrayUtil.join(", ", result.getDeviceSerials())));
            }
            tableFormatter.displayTable(table, new PrintWriter(System.out, true));
        } else {
            printLine(String.format("No results found"));
        }
    }

    private CompatibilityBuildHelper getBuildHelper() {
        if (mBuildHelper == null) {
            CompatibilityBuildProvider buildProvider = new CompatibilityBuildProvider();
            mBuildHelper = new CompatibilityBuildHelper(buildProvider.getBuild());
            mBuildHelper.init("" /* suite plan */, "" /* dynamic config url */);
        }
        return mBuildHelper;
    }

    public static void main(String[] args) throws InterruptedException, ConfigurationException {
        Console console = new CompatibilityConsole();
        Console.startConsole(console, args);
    }
}
