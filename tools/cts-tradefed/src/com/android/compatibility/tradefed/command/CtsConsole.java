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
package com.android.compatibility.tradefed.command;

import com.android.compatibility.common.tradefed.command.CompatibilityConsole;
import com.android.tradefed.command.Console;
import com.android.tradefed.config.ConfigurationException;

/**
 * An extension of {@link CompatibilityConsole} for running CTS tests.
 *
 * This file mainly exists to provide package name space from which the suite-specific values in the
 * MANIFEST.mf can be read.
 */
public class CtsConsole extends CompatibilityConsole {

    public static void main(String[] args) throws InterruptedException, ConfigurationException {
        Console console = new CtsConsole();
        Console.startConsole(console, args);
    }
}
