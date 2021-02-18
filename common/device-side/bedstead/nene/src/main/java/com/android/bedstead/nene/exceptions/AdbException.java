/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.nene.exceptions;

/**
 * An exception that gets thrown when interacting with Adb.
 */
public class AdbException extends Exception {

    private final String command;
    private final String output;

    public AdbException(String message, String command, String output) {
        super(message);
        if (command == null) {
            throw new NullPointerException();
        }
        this.command = command;
        this.output = output;
    }

    public AdbException(String message, String command, String output, Throwable cause) {
        super(message, cause);
        if (command == null) {
            throw new NullPointerException();
        }
        this.command = command;
        this.output = output;
    }

    public String command() {
        return command;
    }

    public String output() {
        return output;
    }

    @Override
    public String toString() {
        return super.toString() + "[command=\"" + command + "\" output=\"" + output + "\"]";
    }
}
