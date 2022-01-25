/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.app.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.content.Context;
import android.util.Dumpable;
import android.util.Log;

import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public final class ActivityDumpTest {

    private static final String TAG = ActivityDumpTest.class.getSimpleName();

    private CustomActivity mActivity;

    @Before
    @UiThreadTest // Needed to create activity
    public void setActivity() throws Exception {
        mActivity = new CustomActivity(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void testAddDumpable_oneOnly() throws Exception {
        String baselineDump = dump(mActivity);
        assertWithMessage("baseline dump()").that(baselineDump).isNotNull();

        mActivity.addDumpable(new CustomDumpable("Dump Able", "The name is Able, DumpAble!"));

        String dump = dump(mActivity);

        assertWithMessage("dump() (expected to have baseline)").that(dump).contains(baselineDump);
        assertWithMessage("dump() (expected to have name)").that(dump).contains("Dump Able");
        assertWithMessage("dump() (expected to have content)").that(dump)
                .contains("The name is Able, DumpAble!");
    }

    @Test
    public void testAddDumpable_twoWithDistinctNames() throws Exception {
        mActivity.addDumpable(new CustomDumpable("dump1", "able1"));
        mActivity.addDumpable(new CustomDumpable("dump2", "able2"));

        String dump = dump(mActivity);

        assertWithMessage("dump() (expected to have name1)").that(dump).contains("dump1");
        assertWithMessage("dump() (expected to have content1)").that(dump).contains("able1");
        assertWithMessage("dump() (expected to have name2)").that(dump).contains("dump2");
        assertWithMessage("dump() (expected to have content2)").that(dump).contains("able2");
    }

    @Test
    public void testAddDumpable_twoWithSameName() throws Exception {
        mActivity.addDumpable(new CustomDumpable("dump", "able1"));
        mActivity.addDumpable(new CustomDumpable("dump", "able2"));

        String dump = dump(mActivity);

        assertWithMessage("dump() (expected to have name)").that(dump).contains("dump");
        assertWithMessage("dump() (expected to have content1)").that(dump).contains("able1");
        assertWithMessage("dump() (expected to NOT have content2)").that(dump)
                .doesNotContain("able2");
    }

    private String dump(Activity activity) throws IOException {
        Log.d(TAG, "dumping " + activity);
        String dump;
        try (StringWriter sw = new StringWriter(); PrintWriter writer = new PrintWriter(sw)) {
            activity.dump(/* prefix= */ "", /* fd= */ null, writer, /* args = */ null);
            dump = sw.toString();
        }
        Log.v(TAG, "result (" + dump.length() + " chars):\n" + dump);
        return dump;
    }

    // Needs a custom class to call attachBaseContext(), otherwise dump() would fail because
    // getResources() and other methods (like getSystemService(...) would return null.
    private static final class CustomActivity extends Activity {

        CustomActivity(Context context) {
            attachBaseContext(context);
        }
    }

    private static final class CustomDumpable implements Dumpable {
        public final String name;
        public final String content;

        private CustomDumpable(String name, String content) {
            this.name = name;
            this.content = content;
        }

        @Override
        public String getDumpableName() {
            return name;
        }

        @Override
        public void dump(PrintWriter writer, String[] args) {
            writer.println(content);
        }
    }
}
