/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.ListView;

import com.android.cts.verifier.ConditionalTest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * {@link TestListAdapter} that populates the {@link TestListActivity}'s {@link ListView} by
 * reading data from the CTS Verifier's AndroidManifest.xml.
 * <p>
 * Making a new test activity to appear in the list requires the following steps:
 *
 * <ol>
 *     <li>REQUIRED: Add an activity to the AndroidManifest.xml with an intent filter with a
 *         main action and the MANUAL_TEST category.
 *         <pre>
 *             <intent-filter>
 *                <action android:name="android.intent.action.MAIN" />
 *                <category android:name="android.cts.intent.category.MANUAL_TEST" />
 *             </intent-filter>
 *         </pre>
 *     </li>
 *     <li>OPTIONAL: Add a meta data attribute to indicate what category of tests the activity
 *         should belong to. If you don't add this attribute, your test will show up in the
 *         "Other" tests category.
 *         <pre>
 *             <meta-data android:name="test_category" android:value="@string/test_category_security" />
 *         </pre>
 *     </li>
 *     <li>OPTIONAL: Add a meta data attribute to indicate whether this test has a parent test.
 *         <pre>
 *             <meta-data android:name="test_parent" android:value="com.android.cts.verifier.bluetooth.BluetoothTestActivity" />
 *         </pre>
 *     </li>
 *     <li>OPTIONAL: Add a meta data attribute to indicate what features are required to run the
 *         test. If the device does not have all of the required features then it will not appear
 *         in the test list. Use a colon (:) to specify multiple required features.
 *         <pre>
 *             <meta-data android:name="test_required_features" android:value="android.hardware.sensor.accelerometer" />
 *         </pre>
 *     </li>
 *     <li>OPTIONAL: Add a meta data attribute to indicate features such that, if any present, the
 *         test gets excluded from being shown. If the device has any of the excluded features then
 *         the test will not appear in the test list. Use a colon (:) to specify multiple features
 *         to exclude for the test. Note that the colon means "or" in this case.
 *         <pre>
 *             <meta-data android:name="test_excluded_features" android:value="android.hardware.type.television" />
 *         </pre>
 *     </li>
 *     <li>OPTIONAL: Add a meta data attribute to indicate features such that, if any present,
 *         the test is applicable to run. If the device has any of the applicable features then
 *         the test will appear in the test list. Use a colon (:) to specify multiple features
 *         <pre>
 *             <meta-data android:name="test_applicable_features" android:value="android.hardware.sensor.compass" />
 *         </pre>
 *     </li>
 *
 * </ol>
 */
public class ManifestTestListAdapter extends TestListAdapter {

    private static final String TEST_CATEGORY_META_DATA = "test_category";

    private static final String TEST_PARENT_META_DATA = "test_parent";

    private static final String TEST_REQUIRED_FEATURES_META_DATA = "test_required_features";

    private static final String TEST_EXCLUDED_FEATURES_META_DATA = "test_excluded_features";

    private static final String TEST_APPLICABLE_FEATURES_META_DATA = "test_applicable_features";

    private final HashSet<String> mDisabledTests;

    private Context mContext;

    private String mTestParent;

    public ManifestTestListAdapter(Context context, String testParent, String[] disabledTestArray) {
        super(context);
        mContext = context;
        mTestParent = testParent;
        mDisabledTests = new HashSet<>(disabledTestArray.length);
        for (int i = 0; i < disabledTestArray.length; i++) {
            mDisabledTests.add(disabledTestArray[i]);
        }
    }

    public ManifestTestListAdapter(Context context, String testParent) {
        this(context, testParent, context.getResources().getStringArray(R.array.disabled_tests));
    }

    @Override
    protected List<TestListItem> getRows() {

        /*
         * 1. Get all the tests belonging to the test parent.
         * 2. Get all the tests keyed by their category.
         * 3. Flatten the tests and categories into one giant list for the list view.
         */

        List<ResolveInfo> infos = getResolveInfosForParent();
        Map<String, List<TestListItem>> testsByCategory = getTestsByCategory(infos);

        List<String> testCategories = new ArrayList<String>(testsByCategory.keySet());
        Collections.sort(testCategories);

        List<TestListItem> allRows = new ArrayList<TestListItem>();
        for (String testCategory : testCategories) {
            List<TestListItem> tests = testsByCategory.get(testCategory);
            if (!tests.isEmpty()) {
                allRows.add(TestListItem.newCategory(testCategory));
                Collections.sort(tests, new Comparator<TestListItem>() {
                    @Override
                    public int compare(TestListItem item, TestListItem otherItem) {
                        return item.title.compareTo(otherItem.title);
                    }
                });
                allRows.addAll(tests);
            }
        }
        return allRows;
    }

    List<ResolveInfo> getResolveInfosForParent() {
        Intent mainIntent = new Intent(Intent.ACTION_MAIN);
        mainIntent.addCategory(CATEGORY_MANUAL_TEST);
        mainIntent.setPackage(mContext.getPackageName());

        PackageManager packageManager = mContext.getPackageManager();
        List<ResolveInfo> list = packageManager.queryIntentActivities(mainIntent,
                PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA);
        int size = list.size();

        List<ResolveInfo> matchingList = new ArrayList<ResolveInfo>();
        for (int i = 0; i < size; i++) {
            ResolveInfo info = list.get(i);
            String parent = getTestParent(info.activityInfo.metaData);
            if ((mTestParent == null && parent == null)
                    || (mTestParent != null && mTestParent.equals(parent))) {
                matchingList.add(info);
            }
        }
        return matchingList;
    }

    Map<String, List<TestListItem>> getTestsByCategory(List<ResolveInfo> list) {
        Map<String, List<TestListItem>> testsByCategory =
                new HashMap<String, List<TestListItem>>();

        int size = list.size();
        for (int i = 0; i < size; i++) {
            ResolveInfo info = list.get(i);
            if (info.activityInfo == null || mDisabledTests.contains(info.activityInfo.name)) {
                Log.w("CtsVerifier", "ignoring disabled test: " + info.activityInfo.name);
                continue;
            }
            String title = getTitle(mContext, info.activityInfo);
            String testName = info.activityInfo.name;
            Intent intent = getActivityIntent(info.activityInfo);
            String[] requiredFeatures = getRequiredFeatures(info.activityInfo.metaData);
            String[] excludedFeatures = getExcludedFeatures(info.activityInfo.metaData);
            String[] applicableFeatures = getApplicableFeatures(info.activityInfo.metaData);

            if (!checkFeatures(requiredFeatures, excludedFeatures, applicableFeatures)
                || !checkConditionalTest(testName)) {
                continue;
            }

            TestListItem item = TestListItem.newTest(title, testName, intent);
            String testCategory = getTestCategory(mContext, info.activityInfo.metaData);
            addTestToCategory(testsByCategory, testCategory, item);
        }

        return testsByCategory;
    }

    static String getTestCategory(Context context, Bundle metaData) {
        String testCategory = null;
        if (metaData != null) {
            testCategory = metaData.getString(TEST_CATEGORY_META_DATA);
        }
        if (testCategory != null) {
            return testCategory;
        } else {
            return context.getString(R.string.test_category_other);
        }
    }

    static String getTestParent(Bundle metaData) {
        return metaData != null ? metaData.getString(TEST_PARENT_META_DATA) : null;
    }

    static String[] getRequiredFeatures(Bundle metaData) {
        if (metaData == null) {
            return null;
        } else {
            String value = metaData.getString(TEST_REQUIRED_FEATURES_META_DATA);
            if (value == null) {
                return null;
            } else {
                return value.split(":");
            }
        }
    }

    static String[] getExcludedFeatures(Bundle metaData) {
        if (metaData == null) {
            return null;
        } else {
            String value = metaData.getString(TEST_EXCLUDED_FEATURES_META_DATA);
            if (value == null) {
                return null;
            } else {
                return value.split(":");
            }
        }
    }

    static String[] getApplicableFeatures(Bundle metaData) {
        if (metaData == null) {
            return null;
        } else {
            String value = metaData.getString(TEST_APPLICABLE_FEATURES_META_DATA);
            if (value == null) {
                return null;
            } else {
                return value.split(":");
            }
        }
    }

    static String getTitle(Context context, ActivityInfo activityInfo) {
        if (activityInfo.labelRes != 0) {
            return context.getString(activityInfo.labelRes);
        } else {
            return activityInfo.name;
        }
    }

    static Intent getActivityIntent(ActivityInfo activityInfo) {
        Intent intent = new Intent();
        intent.setClassName(activityInfo.packageName, activityInfo.name);
        return intent;
    }

    static void addTestToCategory(Map<String, List<TestListItem>> testsByCategory,
            String testCategory, TestListItem item) {
        List<TestListItem> tests;
        if (testsByCategory.containsKey(testCategory)) {
            tests = testsByCategory.get(testCategory);
        } else {
            tests = new ArrayList<TestListItem>();
        }
        testsByCategory.put(testCategory, tests);
        tests.add(item);
    }

    private boolean hasAnyFeature(String[] features) {
        if (features != null) {
            PackageManager packageManager = mContext.getPackageManager();
            for (String feature : features) {
                if (packageManager.hasSystemFeature(feature)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAllFeatures(String[] features) {
        if (features != null) {
            PackageManager packageManager = mContext.getPackageManager();
            for (String feature : features) {
                if (!packageManager.hasSystemFeature(feature)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean checkFeatures(String[] requiredFeatures, String[] excludedFeatures,
        String[] applicableFeatures) {

        if (!hasAnyFeature(excludedFeatures) && hasAllFeatures(requiredFeatures)) {
            if (applicableFeatures == null || hasAnyFeature(applicableFeatures)) {
                return true;
            }
        }
        return false;
    }

    private String[] parseFeaturesString(String features) {
        if (features == null || features.isEmpty()) {
            return null;
        } else {
            return features.split(":");
        }
    }

    private boolean checkConditionalTest(String testName) {
        try {
            Class<?> testClass = Class.forName(testName);
            if (!testClass.isAnnotationPresent(ConditionalTest.class)) {
                return true;
            }

            ConditionalTest annotation = testClass.getAnnotation(ConditionalTest.class);

            String condFQName = annotation.value();
            boolean instantiate = annotation.instantiate();
            String[] requiredFts = parseFeaturesString(annotation.requiredFeatures());
            String[] excludedFts = parseFeaturesString(annotation.excludedFeatures());
            String[] applicableFts = parseFeaturesString(annotation.applicableFeatures());

            if (requiredFts != null || excludedFts != null || applicableFts != null) {
                if (!condFQName.isEmpty() || instantiate) {
                    Log.e("CtsVerifier", "ConditionalTest restrictions violated: " +
                        "Feature properties can not be used together with value or instantiate.");
                    // TODO: Dialog
                    return false;
                }

                return checkFeatures(requiredFts, excludedFts, applicableFts);
            }

            Class<?> condClass = testClass;
            if (!condFQName.isEmpty()) {
                if (!condFQName.contains(".")) {
                    condFQName = testClass.getPackage().getName() + "." + condFQName;
                }
                condClass = Class.forName(condFQName);
            }

            Method shouldRun = condClass.getMethod(ConditionalTest.CONDITION_CLASS_METHOD_NAME,
                Context.class, Class.class);
            Object instance = (instantiate) ? condClass.newInstance() : null;
            return (Boolean) shouldRun.invoke(instance, mContext, testClass);
        } catch (Exception e) {
            Log.w("CtsVerifier", "ConditionalTest exception: " + e.getMessage());
            e.printStackTrace();
            // TODO: Dialog
            return true;
        }
    }
}
