/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package android.cts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.test.AndroidTestCase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

@SuppressWarnings("deprecation")
public class FileChannelTryLockTest extends AndroidTestCase {

    final static String dirName = "CtsFIleIOTest";

    final static String sharedFileName = "sharedFile";

    public void testFileLockWithMultipleProcess() throws InterruptedException, IOException {
        IntentReceiver receiver = new IntentReceiver();
        getContext().startService(new Intent(getContext(), LockHoldingService.class));
        synchronized (receiver.notifier) {
            receiver.notifier.wait(10000);
        }
        File sharedFile = createFileInDir(dirName, sharedFileName);
        assertNull(new FileOutputStream(sharedFile).getChannel().tryLock());
        getContext().stopService(new Intent(getContext(), LockHoldingService.class));
    }

    public void testFileLockWithSingleProcess() throws IOException {
        File file = createFileInDir(dirName, "sharedFileForSingleProcess");
        FileLock fileLock1 = new FileOutputStream(file).getChannel().tryLock();
        try {
            new FileOutputStream(file).getChannel().tryLock();
            fail();
        } catch (OverlappingFileLockException expected) {
        }
    }

    static File createFileInDir(String dirName, String fileName) throws IOException {
        File dir = new File(Environment.getExternalStorageDirectory(), dirName);
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            throw new IOException("External storage is not mounted");
        } else if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Cannot create directory for device info files");
        } else {
            return new File(dir, fileName);
        }
    }

    static void deleteDir() {
        File dir = new File(Environment.getExternalStorageDirectory(), dirName);
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                new File(dir, children[i]).delete();
            }
            dir.delete();
        }
    }

    @Override
    public void tearDown() throws Exception {
        getContext().stopService(new Intent(getContext(), LockHoldingService.class));
        deleteDir();
    }

    public static class IntentReceiver extends BroadcastReceiver {

        public final static Object notifier = new Object();

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (notifier) {
                notifier.notify();
            }
        }
    }
}

