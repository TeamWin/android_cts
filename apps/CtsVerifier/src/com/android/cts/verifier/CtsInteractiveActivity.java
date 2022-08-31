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

package com.android.cts.verifier;

import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.junit.Assume;


/** An activity to show tester instructions when integrating with CTSInteractive. */
public class CtsInteractiveActivity extends Activity {

    // TODO: There is a bug in this logic - if i launch via launcher and then press back the state
    //  goes bad and it'll show a previous cts test rather than the "please wait" page

    @Override
    protected void onStart() {
        super.onStart();

        if (getIntent() != null) {
            String activityName = getIntent().getStringExtra("ACTIVITY_NAME");
            if (activityName != null) {
                String requirements = getIntent().getStringExtra("REQUIREMENTS");
                if (requirements != null) {
                    if (!ManifestTestListAdapter.matchAllConfigs(
                            this, requirements.split(","))) {
                        // Store assumption failed
                        TestResultsProvider.setTestResult(
                                this, "com.android.cts.verifier" + activityName,
                                3 /* assumption failed */, null, /* reportLog= */ null,
                                /* historyCollection= */ null, /* screenshotsMetadata= */ null);
                        return;
                    }
                }


                getSharedPreferences("CTSInteractive", Context.MODE_PRIVATE).edit()
                        .putString("ACTIVITY_NAME", activityName).commit();

                Intent intent = new Intent();
                intent.setComponent(new ComponentName(getPackageName(),
                        getPackageName() + activityName));

                setIntent(null); // Remove the intent so we don't relaunch when it returns

                startActivityForResult(intent, 0);
            } else {
                relaunchTest();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ctsinteractive_activity);
    }

    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        // Back button comes here - doesn't go above
        if (resultCode != Activity.RESULT_CANCELED) {
            // We have a result - no need to restart the test
            getSharedPreferences("CTSInteractive", Context.MODE_PRIVATE).edit().clear().commit();
        } else {
            relaunchTest();
        }
    }

    private void relaunchTest() {
        String activityName = getSharedPreferences("CTSInteractive", Context.MODE_PRIVATE)
                .getString("ACTIVITY_NAME", null);
        if (activityName == null) {
            Toast.makeText(this, "No current test", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(getPackageName(),
                    getPackageName() + activityName));
            startActivityForResult(intent, 0);
        }
    }
}
