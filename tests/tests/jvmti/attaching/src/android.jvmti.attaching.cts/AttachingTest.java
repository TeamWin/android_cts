/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.jvmti.attaching.cts;

import static org.junit.Assert.assertTrue;

import android.os.Debug;
import android.support.test.runner.AndroidJUnit4;

import dalvik.system.BaseDexClassLoader;

import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AttachingTest {
    private static String sAgentFile;

    @BeforeClass
    public static void copyAgentToFile() throws Exception {
        ClassLoader cl = AttachingTest.class.getClassLoader();
        assertTrue(cl instanceof BaseDexClassLoader);

        File copiedAgent = File.createTempFile("agent", ".so");
        try (InputStream is = new FileInputStream(
                ((BaseDexClassLoader) cl).findLibrary("jvmtiattachingtestagent"))) {
            try (OutputStream os = new FileOutputStream(copiedAgent)) {
                byte[] buffer = new byte[64 * 1024];

                while (true) {
                    int numRead = is.read(buffer);
                    if (numRead == -1) {
                        break;
                    }
                    os.write(buffer, 0, numRead);
                }
            }
        }

        sAgentFile = copiedAgent.getAbsolutePath();
    }

    @Test(expected = IOException.class)
    public void a_attachInvalidAgent() throws Exception {
        Debug.attachJvmtiAgent(File.createTempFile("badAgent", ".so").getAbsolutePath(), null);
    }

    @Test(expected = IOException.class)
    public void a_attachInvalidPath() throws Exception {
        Debug.attachJvmtiAgent(sAgentFile + ".invalid", null);
    }

    @Test(expected = NullPointerException.class)
    public void a_attachNullAgent() throws Exception {
        Debug.attachJvmtiAgent(null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void a_attachWithEquals() throws Exception {
        Debug.attachJvmtiAgent(File.createTempFile("=", ".so").getAbsolutePath(), null);
    }

    @Test(expected = IOException.class)
    public void a_attachWithNullOptions() throws Exception {
        Debug.attachJvmtiAgent(sAgentFile, null);
    }

    @Test(expected = IOException.class)
    public void a_attachWithBadOptions() throws Exception {
        Debug.attachJvmtiAgent(sAgentFile, "b");
    }

    @Test
    public void b_attach() throws Exception {
        Debug.attachJvmtiAgent(sAgentFile, "a");

        assertTrue(isAttached());
    }

    native static boolean isAttached();
}
