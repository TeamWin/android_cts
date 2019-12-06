/*
 * Copyright (C) 2019 The Android Open Source Project
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
package android.view.textclassifier.cts;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.google.common.truth.Truth.assertThat;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.compatibility.common.util.SafeCleanerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/**
 * Tests for TextClassifierService query related functions.
 *
 * <p>
 * We use a non-standard TextClassifierService for TextClassifierService-related CTS tests. A
 * non-standard TextClassifierService that is set via device config. This non-standard
 * TextClassifierService is not defined in the trust TextClassifierService, it should only receive
 * queries from clients in the same package.
 */
@RunWith(AndroidJUnit4.class)
public class TextClassifierServiceSwapTest {
    // TODO: Add more tests to verify all the TC APIs call between caller and TCS.
    private static final String TAG = "TextClassifierServiceSwapTest";

    private final TextClassifierTestWatcher mTestWatcher =
            CtsTextClassifierService.getTestWatcher();
    private final SafeCleanerRule mSafeCleanerRule = mTestWatcher.newSafeCleaner();

    @Rule
    public final RuleChain mAllRules = RuleChain
            .outerRule(mTestWatcher)
            .around(mSafeCleanerRule);

    @Test
    public void testOutsideOfPackageActivity_noRequestReceived() throws Exception {
        // Start an Activity from another package to trigger a TextClassifier call
        runQueryTextClassifierServiceActivity();

        // Wait for the TextClassifierService to connect.
        // Note that the system requires a query to the TextClassifierService before it is
        // first connected.
        final CtsTextClassifierService service = mTestWatcher.getService();

        // Wait a delay for the query is delivered.
        service.awaitQuery(1_000);

        // Verify the request was not passed to the service.
        assertThat(service.getRequestSessions()).isEmpty();
    }

    /**
     * Start an Activity from another package that queries the device's TextClassifierService when
     * started and immediately terminates itself. When the Activity finishes, it sends broadcast, we
     * check that whether the finish broadcast is received.
     */
    private void runQueryTextClassifierServiceActivity() {
        final String actionQueryActivityFinish =
                "ACTION_QUERY_SERVICE_ACTIVITY_FINISH_" + SystemClock.uptimeMillis();
        final Context context = InstrumentationRegistry.getTargetContext();

        // register a activity finish receiver
        final BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(context,
                actionQueryActivityFinish);
        receiver.register();

        // Start an Activity from another package
        final Intent outsideActivity = new Intent();
        outsideActivity.setComponent(new ComponentName("android.textclassifier.cts2",
                "android.textclassifier.cts2.QueryTextClassifierServiceActivity"));
        outsideActivity.setFlags(FLAG_ACTIVITY_NEW_TASK);
        final Intent broadcastIntent = new Intent(actionQueryActivityFinish);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, broadcastIntent,
                0);
        outsideActivity.putExtra("finishBroadcast", pendingIntent);
        context.startActivity(outsideActivity);

        TextClassifierTestWatcher.waitForIdle();

        // Verify the finish broadcast is received.
        final Intent intent = receiver.awaitForBroadcast();
        assertThat(intent).isNotNull();

        // unregister receiver
        receiver.unregisterQuietly();
    }
}
