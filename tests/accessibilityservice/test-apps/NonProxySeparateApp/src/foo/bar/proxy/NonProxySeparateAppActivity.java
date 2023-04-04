/*
 * Copyright (C) 2023 The Android Open Source Project
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

package foo.bar.proxy;

import static android.accessibilityservice.cts.utils.MultiProcessUtils.SEPARATE_PROCESS_ACTIVITY_TITLE;
import static android.accessibilityservice.cts.utils.MultiProcessUtils.TOUCH_EXPLORATION_CHANGE_EVENT_TEXT;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

/**
 * Activity used in AccessibilityDisplayProxyTest.
 */
public class NonProxySeparateAppActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.non_proxy_separate_app_activity);
        setTitle(SEPARATE_PROCESS_ACTIVITY_TITLE);
        final AccessibilityManager a11yManager = getSystemService(AccessibilityManager.class);
        final AccessibilityManager.AccessibilityServicesStateChangeListener
                servicesStateChangeListener = manager -> {
                    final List<AccessibilityServiceInfo> enabled =
                            manager.getEnabledAccessibilityServiceList(
                                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
                    final StringBuilder eventText = new StringBuilder();

                    for (AccessibilityServiceInfo info : enabled) {
                        String name = info.getResolveInfo().serviceInfo.name;
                        eventText.append(name);
                    }
                    final AccessibilityEvent textEvent = new AccessibilityEvent(
                            AccessibilityEvent.TYPE_ANNOUNCEMENT);
                    textEvent.getText().add(eventText.toString());
                    a11yManager.sendAccessibilityEvent(textEvent);
                };


        final AccessibilityManager.TouchExplorationStateChangeListener touchExplorationListener =
                enabled -> {
                    final AccessibilityEvent textEvent = new AccessibilityEvent(
                            AccessibilityEvent.TYPE_ANNOUNCEMENT);
                    textEvent.getText().add(TOUCH_EXPLORATION_CHANGE_EVENT_TEXT + enabled);
                    a11yManager.sendAccessibilityEvent(textEvent);
                };

        a11yManager.addAccessibilityServicesStateChangeListener(servicesStateChangeListener);
        a11yManager.addTouchExplorationStateChangeListener(touchExplorationListener);
    }
}
