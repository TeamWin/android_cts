/*
 * Copyright (C) 2008 The Android Open Source Project
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
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

public class JavacBuildStep extends SourceBuildStep {

    private final String destPath;
    private final String classPath;
    private final Set<String> sourceFiles = new HashSet<String>();
    public JavacBuildStep(String destPath, String classPath) {
        super(new File(destPath));
        this.destPath = destPath;
        this.classPath = classPath;
    }

    @Override
    public void addSourceFile(String sourceFile)
    {
        sourceFiles.add(sourceFile);
    }

    @Override
    boolean build() {
        if (super.build())
        {
            if (sourceFiles.isEmpty())
            {
                return true;
            }

            File destFile = new File(destPath);
            if (!destFile.exists() && !destFile.mkdirs())
            {
                System.err.println("failed to create destination dir");
                return false;
            }
            final int args = 9;
            String[] commandLine = new String[sourceFiles.size()+args];
            commandLine[0] = "javac";
            commandLine[1] = "-classpath";
            commandLine[2] = classPath;
            commandLine[3] = "-d";
            commandLine[4] = destPath;
            commandLine[5] = "-source";
            commandLine[6] = "1.7";
            commandLine[7] = "-target";
            commandLine[8] = "1.7";

            String[] files = new String[sourceFiles.size()];
            sourceFiles.toArray(files);

            System.arraycopy(files, 0, commandLine, args, files.length);

            try {
                Process p = Runtime.getRuntime().exec(commandLine);
                return p.waitFor() == 0;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj))
        {
            JavacBuildStep other = (JavacBuildStep) obj;
            return destPath.equals(other.destPath)
                && classPath.equals(other.classPath)
                && sourceFiles.equals(other.sourceFiles);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return destPath.hashCode() ^ classPath.hashCode() ^ sourceFiles.hashCode();
    }
}
