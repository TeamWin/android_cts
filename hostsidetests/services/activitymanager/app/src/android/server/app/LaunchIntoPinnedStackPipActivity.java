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

package android.server.app;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Rect;

import java.lang.Exception;
import java.lang.IllegalStateException;
import java.lang.reflect.Method;

public class LaunchIntoPinnedStackPipActivity extends Activity {
    @Override
    protected void onResume() {
        super.onResume();
        final Intent intent = new Intent(this, AlwaysFocusablePipActivity.class);

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchBounds(new Rect(0, 0, 500, 500));
        try {
            setLaunchStackId(options);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        startActivity(intent, options.toBundle());
    }

    /** ActivityOptions#setLaunchStackId is a @hidden API so we access it through reflection...*/
    void setLaunchStackId(ActivityOptions options) throws Exception {
        final Method method = options.getClass().getDeclaredMethod(
                "setLaunchStackId", new Class[] { int.class });

        method.setAccessible(true);
        method.invoke(options, 4 /* ActivityManager.StackId.PINNED_STACK_ID */);
    }
}
