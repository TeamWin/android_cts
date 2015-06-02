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

import com.android.compatibility.common.tradefed.build.BuildHelper;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildProvider;
import com.android.compatibility.common.tradefed.result.IInvocationResult;
import com.android.compatibility.common.tradefed.result.IInvocationResultRepo;
import com.android.compatibility.common.tradefed.result.InvocationResultRepo;
import com.android.compatibility.common.tradefed.result.TestStatus;
import com.android.tradefed.command.Console;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RegexTrie;
import com.android.tradefed.util.TableFormatter;
import com.android.tradefed.util.TimeUtil;

import java.io.File;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * An extension of Tradefed's console which adds features specific to compatibility testing.
 */
public abstract class CompatibilityConsole extends Console {

    private BuildHelper mBuild = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        BuildHelper build = getCompatibilityBuild();
        printLine(String.format("Android %s %s (%s)", build.getSuiteName(), build.getSuiteVersion(),
                build.getBuildId()));
        super.run();
    }

    /**
     * Adds the 'list modules' and 'list plans' commands
     */
    @Override
    protected void setCustomCommands(RegexTrie<Runnable> trie, List<String> genericHelp,
            Map<String, String> commandHelp) {
        trie.put(new Runnable() {
            @Override
            public void run() {
                BuildHelper build = getCompatibilityBuild();
                if (build != null) {
                    listPlans(build);
                }
            }
        }, LIST_PATTERN, "p(?:lans)?");
        trie.put(new Runnable() {
            @Override
            public void run() {
                BuildHelper build = getCompatibilityBuild();
                if (build != null) {
                    listModules(build);
                }
            }
        }, LIST_PATTERN, "m(?:modules)?");
        trie.put(new Runnable() {
            @Override
            public void run() {
                BuildHelper build = getCompatibilityBuild();
                if (build != null) {
                    listResults(build);
                }
            }
        }, LIST_PATTERN, "r(?:esults)?");

        // find existing help for 'LIST_PATTERN' commands, and append these commands help
        String listHelp = commandHelp.get(LIST_PATTERN);
        if (listHelp == null) {
            // no help? Unexpected, but soldier on
            listHelp = new String();
        }
        String combinedHelp = listHelp +
                "\tp[lans]\t\tList all test plans" + LINE_SEPARATOR +
                "\tm[modules]\tList all modules" + LINE_SEPARATOR +
                "\tr[esults]\tList all results" + LINE_SEPARATOR;
        commandHelp.put(LIST_PATTERN, combinedHelp);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getConsolePrompt() {
        return String.format("%s-tf > ", getCompatibilityBuild().getSuiteName().toLowerCase());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getGenericHelpString(List<String> genericHelp) {
        BuildHelper build = getCompatibilityBuild();
        StringBuilder helpBuilder = new StringBuilder();
        helpBuilder.append(build.getSuiteFullName());
        helpBuilder.append("\n\n");
        helpBuilder.append(build.getSuiteName());
        helpBuilder.append(" is the test harness for running the Android Compatibility");
        helpBuilder.append("Suite, built on top of the Trade Federation framework.\n\n");
        helpBuilder.append("Available commands and options\n");
        helpBuilder.append("Host:\n");
        helpBuilder.append("  help: show this message\n");
        helpBuilder.append("  help all: show the complete tradefed help\n");
        helpBuilder.append("  version: show the version\n");
        helpBuilder.append("  exit: gracefully exit the compatibiltiy console, waiting until all ");
        helpBuilder.append("invocations have completed\n");
        helpBuilder.append("Run:\n");
        final String runPrompt = String.format("  run %s ", build.getSuiteName());
        helpBuilder.append(runPrompt);
        helpBuilder.append("--plan/-p <plan_name>: run a test plan\n");
        helpBuilder.append(runPrompt);
        helpBuilder.append("--module/-m <module>: run a test module\n");
        helpBuilder.append(runPrompt);
        helpBuilder.append("--module/-m <module> --test/-t <test_name>: run a specific test from");
        helpBuilder.append(" the module. Test name can be <package>, ");
        helpBuilder.append("<package>.<class>, <package>.<class>#<method> or <native_name>");
        helpBuilder.append(runPrompt);
        helpBuilder.append("--continue-session <session_id>: run all not executed tests from a ");
        helpBuilder.append("previous session\n");
        helpBuilder.append(runPrompt);
        helpBuilder.append("--retry-session <session_id>: run all failed tests from a previous ");
        helpBuilder.append("session\n");
        helpBuilder.append(runPrompt);
        helpBuilder.append("[options] --serial/-s <device_id>: run on the specified device\n");
        helpBuilder.append(runPrompt);
        helpBuilder.append("[options] --abi/-a <abi>: the ABI to run the test against\n");
        helpBuilder.append(runPrompt);
        helpBuilder.append("[options] --shard <shards>: shards a run into the given number of ");
        helpBuilder.append("independent chunks, to run on multiple devices in parallel\n");
        helpBuilder.append(runPrompt);
        helpBuilder.append("--help/--help-all: get help for ");
        helpBuilder.append(build.getSuiteFullName());
        helpBuilder.append("\n");
        helpBuilder.append("List:\n");
        helpBuilder.append("  l/list d/devices: list connected devices and their state\n");
        helpBuilder.append("  l/list p/plans: list test plans\n");
        helpBuilder.append("  l/list m/modules: list test modules\n");
        helpBuilder.append("  l/list i/invocations: list invocations aka test runs currently in ");
        helpBuilder.append("progress\n");
        helpBuilder.append("  l/list c/commands: list commands: aka test run commands currently");
        helpBuilder.append("in the queue waiting to be allocated devices\n");
        helpBuilder.append("  l/list r/results: list results currently in the repository\n");
        helpBuilder.append("Dump:\n");
        helpBuilder.append("  d/dump l/logs: dump the tradefed logs for all running invocations\n");
        helpBuilder.append("Options:\n");
        helpBuilder.append("  --disable-reboot : Do not reboot device after running some amount ");
        helpBuilder.append(" of tests.\n");
        return helpBuilder.toString();
    }

    private void listPlans(BuildHelper build) {
        FilenameFilter xmlFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".xml");
            }
        };
        File[] files = build.getPlansDir().listFiles(xmlFilter);
        if (files.length > 0) {
            for (File planFile : files) {
                printLine(FileUtil.getBaseName(planFile.getName()));
            }
        } else {
            printLine(String.format("No plans found in %s", build.getPlansDir()));
        }
    }

    private void listModules(BuildHelper build) {
        FilenameFilter xmlFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".config");
            }
        };
        File[] files = build.getTestsDir().listFiles(xmlFilter);
        if (files.length > 0) {
            for (File moduleFile : files) {
                printLine(FileUtil.getBaseName(moduleFile.getName()));
            }
        } else {
            printLine(String.format("No modules found in %s", build.getTestsDir()));
        }
    }

    private void listResults(BuildHelper build) {
        TableFormatter tableFormatter = new TableFormatter();
        List<List<String>> table = new ArrayList<>();
        table.add(Arrays.asList("Session","Pass", "Fail", "Not Executed", "Start time", "Plan name",
                "Device serial(s)"));
        IInvocationResultRepo testResultRepo = new InvocationResultRepo(build.getResultsDir());
        List<IInvocationResult> results = testResultRepo.getResults();
        if (results.size() > 0) {
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
            printLine(String.format("No results found in %s", build.getResultsDir()));
        }
    }

    public BuildHelper getCompatibilityBuild() {
        if (mBuild == null) {
            mBuild = new BuildHelper(new CompatibilityBuildProvider().getCompatibilityBuild());
        }
        return mBuild;
    }
}