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
package android.security.cts;

import static org.junit.Assert.assertTrue;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import android.platform.test.annotations.SecurityTest;

@RunWith(AndroidJUnit4.class)
public class CVE_2021_0322 {

    private static final Uri BASE_URI = Uri.parse("content://android.security.cts.local/main");
    private final Context mContext = InstrumentationRegistry.getContext();

    boolean isVulnerable = false;
    boolean onFinishedExecuted = false;

    /**
     * b/159145361
     */
    @SecurityTest(minPatchLevel = "2021-01")
    @Test
    public void testPocCVE_2021_0322() {
        CVE_2021_0322_SliceProvider serviceProvider = new CVE_2021_0322_SliceProvider();
        serviceProvider.attachInfo(mContext, new ProviderInfo());
        PendingIntent pi = serviceProvider.onCreatePermissionRequest(BASE_URI);
        PendingIntent.OnFinished onFinish = new PendingIntent.OnFinished() {
            public void onSendFinished(PendingIntent pi, Intent intent,
                    int resultCode, String resultData, Bundle resultExtras) {
                String packageName = intent.getStringExtra("provider_pkg");
                if(packageName != null) {
                    isVulnerable = true;
                }
                onFinishedExecuted = true;
            }
        };
        /* Execute the intent - the result is not required as the focus is    */
        /* the intent which was used. Hence ignore the exceptions.            */
        try {
            pi.send(0, onFinish, null);
        } catch (Exception e) {
        }
        /* Wait till the intent finishes. PendingIntent.OnFinished would be   */
        /* exectuted and the details of the intent which was executed would   */
        /* checked.                                                           */
        try {
            while(!onFinishedExecuted) {
                Thread.sleep(1000); //Sleep for 1 second
            }
            assertTrue(!isVulnerable);
        } catch (Exception e) {
        }
    }
}
