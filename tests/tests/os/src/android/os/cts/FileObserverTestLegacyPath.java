/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.os.cts;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.FileObserver;
import android.provider.MediaStore;
import android.test.AndroidTestCase;
import android.util.Log;
import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class FileObserverTestLegacyPath extends AndroidTestCase {
    Context con;
    File testdir;
    PathFileObserver fileObserver;
    String imageName;

    final static int mask = FileObserver.OPEN | FileObserver.CREATE | FileObserver.MODIFY;

    @Override
    protected void setUp() throws Exception {
        con = getContext();

        testdir = new File("/sdcard/DCIM/testdir");
        testdir.delete();
        testdir.mkdirs();

        fileObserver = new PathFileObserver(testdir, mask);
        fileObserver.startWatching();
    }

    @Override
    protected void tearDown() throws Exception {
        fileObserver.stopWatching();
        testdir.delete();
    }

    public void testCreateFile() throws Exception {
        /* Create an image file and write some test data */
        imageName = "image" + System.currentTimeMillis() + ".jpg";

        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Files.FileColumns.DISPLAY_NAME, imageName);
        cv.put(MediaStore.Files.FileColumns.RELATIVE_PATH, "DCIM/testdir");
        cv.put(MediaStore.Files.FileColumns.MIME_TYPE, "image/jpg");

        Uri imageUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);

        Uri fileUri = con.getContentResolver().insert(imageUri, cv);

        OutputStream os = con.getContentResolver().openOutputStream(fileUri);
        os.write("TEST".getBytes("UTF-8"));
        os.close();

        /* Wait for 2 seconds for the inotify events to be catched */
        Thread.sleep(2000);

        /* Verify if the received events correspond to the ones that were requested */
        int catched_events = fileObserver.getEvents().getOrDefault(imageName, 0);
        assertEquals("Uncatched some of the events", PathFileObserver.eventsToSet(mask),
                PathFileObserver.eventsToSet(catched_events & mask));
    }

    static public class PathFileObserver extends FileObserver {
        static final String TAG = "FileObserverTestLegacyPath";

        final int mask;
        HashMap<String, Integer> events_map = new HashMap<>();

        public PathFileObserver(final File root, final int mask) {
            super(root, mask);
            this.mask = mask;
        }

        public PathFileObserver(final String path, final int mask) {
            super(path, mask);
            this.mask = mask;
        }

        public HashMap<String, Integer> getEvents() { return events_map; }

        public void onEvent(final int event, final String path) {
            /* There might be some extra flags introduced by inotify.h.  Remove
             * them. */
            final int filtered_event = event & FileObserver.ALL_EVENTS;
            if (filtered_event == 0)
                return;

            /* For every received event update the event bitmap of the
             * associated file. */
            int detected_events = events_map.getOrDefault(path, 0).intValue();
            events_map.put(path, detected_events | filtered_event);

            final String str_event = event2str(filtered_event);
            final String txt = "Test Success onEvent: \n\t- event: " + str_event + " ("
                    + filtered_event + ");\n\t- path: " + path + "\n";
            Log.d(TAG, txt);
        }

        public void close() { super.finalize(); }

        static public HashSet<String> eventsToSet(int events) {
            HashSet<String> set = new HashSet<String>();
            while (events != 0) {
                int lowestEvent = Integer.lowestOneBit(events);
                set.add(event2str(lowestEvent));
                events &= ~lowestEvent;
            }
            return set;
        }

        static public String event2str(int event) {
            switch (event) {
                case FileObserver.ACCESS:
                    return "ACCESS";
                case FileObserver.ATTRIB:
                    return "ATTRIB";
                case FileObserver.CLOSE_NOWRITE:
                    return "CLOSE_NOWRITE";
                case FileObserver.CLOSE_WRITE:
                    return "CLOSE_WRITE";
                case FileObserver.CREATE:
                    return "CREATE";
                case FileObserver.DELETE:
                    return "DELETE";
                case FileObserver.DELETE_SELF:
                    return "DELETE_SELF";
                case FileObserver.MODIFY:
                    return "MODIFY";
                case FileObserver.MOVED_FROM:
                    return "MOVED_FROM";
                case FileObserver.MOVED_TO:
                    return "MOVED_TO";
                case FileObserver.MOVE_SELF:
                    return "MOVE_SELF";
                case FileObserver.OPEN:
                    return "OPEN";
                default:
                    return "???";
            }
        }
    }
}
