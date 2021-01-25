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

import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.lessThan;

import android.test.AndroidTestCase;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.SecurityTest;
import android.os.SystemClock;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import android.util.Log;
import org.junit.Test;
import org.junit.runner.RunWith;

@SecurityTest
@RunWith(AndroidJUnit4.class)
public class CVE_2021_0339 {

    static final String TAG = CVE_2021_0339.class.getSimpleName();
    private static final String SECURITY_CTS_PACKAGE_NAME = "android.security.cts";
    static final int MAX_TRANSITION_DURATION_MS = 3000; // internal max
    static final int TIME_MEASUREMENT_DELAY_MS = 5000; // tolerance for lag.
    public static boolean testCompleted;

    public FirstActivity fActivity;
    public SecondActivity sActivity;

    private void launchActivity(Class<? extends Activity> clazz) {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SECURITY_CTS_PACKAGE_NAME, clazz.getName());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * b/175817167
     */
    @Test
    @SecurityTest
    public void testPocCVE_2021_0339() throws Exception {

        Log.d(TAG, "test start");
        testCompleted = false;
        launchActivity(FirstActivity.class);

        //wait for SecondActivity animation to complete
        synchronized(CVE_2021_0339.class){
          if(!testCompleted)
            CVE_2021_0339.class.wait();
        }
        Log.d(TAG, "test completed");

        //A duration of a transition from "FirstActivity" to "Second Activity"
        //is set in this test to 10 seconds
        // (res/anim/translate1.xml and res/anim/translate2.xml)
        //The fix is supposed to limit the duration to 3000 ms.
        // testing for > 8s
        assertThat(SecondActivity.duration,
            lessThan(MAX_TRANSITION_DURATION_MS + TIME_MEASUREMENT_DELAY_MS));
    }

    public static class FirstActivity extends Activity {

      @Override
      public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        Intent intent = new Intent(this, SecondActivity.class);
        intent.putExtra("STARTED_TIMESTAMP", SystemClock.uptimeMillis());
        startActivity(intent);
        overridePendingTransition(R.anim.translate2,R.anim.translate1);
        Log.d(TAG,this.getLocalClassName()+" onEnterAnimationComplete()");
      }
    }

    public static class SecondActivity extends Activity{
      public static int duration = 0;

      @Override
      public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        long completedTs = SystemClock.uptimeMillis();
        long startedTs = getIntent().getLongExtra("STARTED_TIMESTAMP", 0);
        duration = (int)(completedTs - startedTs);
        Log.d(TAG, this.getLocalClassName()
          + " onEnterAnimationComplete() duration=" + Long.toString(duration));

        //Notify main thread that the test is completed
        synchronized(CVE_2021_0339.class){
          CVE_2021_0339.testCompleted = true;
          CVE_2021_0339.class.notifyAll();
        }
      }
    }
}
