/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.jack.CLILogConfiguration;
import com.android.jack.CLILogConfiguration.LogConfigurationException;

import util.build.BuildStep.BuildFile;

import com.android.jack.Jack;
import com.android.jack.Main;
import com.android.jack.Options;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JackBuildStep extends SourceBuildStep {

    static {
        try {
              CLILogConfiguration.setupLogs();
            } catch (LogConfigurationException e) {
              throw new Error("Failed to setup logs", e);
            }
    }

    private final String destPath;
    private final String classPath;
    private final Set<String> sourceFiles = new HashSet<String>();

    public JackBuildStep(String destPath, String classPath) {
        super(new File(destPath));
        this.destPath = destPath;
        this.classPath = classPath;
    }

    @Override
    public void addSourceFile(String sourceFile) {
        sourceFiles.add(sourceFile);
    }

    @Override
    boolean build() {
        if (super.build()) {
            if (sourceFiles.isEmpty()) {
                return true;
            }

            File outDir = new File(destPath).getParentFile();
            if (!outDir.exists() && !outDir.mkdirs()) {
                System.err.println("failed to create output dir: "
                        + outDir.getAbsolutePath());
                return false;
            }

            File tmpOutDir = new File(outDir, outputFile.fileName.getName() + ".dexTmp");
            if (!tmpOutDir.exists() && !tmpOutDir.mkdirs()) {
                System.err.println("failed to create temp dir: "
                        + tmpOutDir.getAbsolutePath());
                return false;
            }
            File tmpDex = new File(tmpOutDir, "classes.dex");

            try {
                List<String> commandLine = new ArrayList<String>(6 + sourceFiles.size());
                commandLine.add("--verbose");
                commandLine.add("error");
                commandLine.add("--classpath");
                commandLine.add(classPath);
                commandLine.add("--output-dex");
                commandLine.add(tmpOutDir.getAbsolutePath());
                commandLine.addAll(sourceFiles);

                Options options = Main.parseCommandLine(commandLine);
                Jack.checkAndRun(options);

                JarBuildStep jarStep = new JarBuildStep(
                    new BuildFile(tmpDex),
                    "classes.dex",
                    outputFile,
                    /* deleteInputFileAfterBuild = */ true);
                if (!jarStep.build()) {
                  throw new IOException("Failed to make jar: " + outputFile.getPath());
                }
                return true;
            } catch (Throwable ex) {
                ex.printStackTrace();
                return false;
            } finally {
              tmpDex.delete();
              tmpOutDir.delete();
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            JackBuildStep other = (JackBuildStep) obj;
            return destPath.equals(other.destPath) && classPath.equals(other.classPath)
                    && sourceFiles.equals(other.sourceFiles);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return destPath.hashCode() ^ classPath.hashCode() ^ sourceFiles.hashCode();
    }
}
