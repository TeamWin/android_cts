/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.verifier.camera.its;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.camera2.CameraManager;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.DialogTestListActivity;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestResult;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.rules.TestName;

/**
 * Test for Camera features that require that the camera be aimed at a specific test scene.
 * This test activity requires a USB connection to a computer, and a corresponding host-side run of
 * the python scripts found in the CameraITS directory.
 */
public class ItsTestActivity extends DialogTestListActivity {
    private static final String TAG = "ItsTestActivity";
    private static final String EXTRA_CAMERA_ID = "camera.its.extra.CAMERA_ID";
    private static final String EXTRA_RESULTS = "camera.its.extra.RESULTS";
    private static final String EXTRA_VERSION = "camera.its.extra.VERSION";
    private static final String CURRENT_VERSION = "1.0";
    private static final String ACTION_ITS_RESULT =
            "com.android.cts.verifier.camera.its.ACTION_ITS_RESULT";

    private static final String RESULT_PASS = "PASS";
    private static final String RESULT_FAIL = "FAIL";
    private static final String RESULT_NOT_EXECUTED = "NOT_EXECUTED";
    private static final Set<String> RESULT_VALUES = new HashSet<String>(
            Arrays.asList(new String[] {RESULT_PASS, RESULT_FAIL, RESULT_NOT_EXECUTED}));
    private static final int MAX_SUMMARY_LEN = 200;

    private static final Pattern MPC12_CAMERA_LAUNCH_PATTERN =
            Pattern.compile("camera_launch_time_ms:(\\d+(\\.\\d+)?)");
    private static final Pattern MPC12_JPEG_CAPTURE_PATTERN =
            Pattern.compile("1080p_jpeg_capture_time_ms:(\\d+(\\.\\d+)?)");

    private final ResultReceiver mResultsReceiver = new ResultReceiver();
    private boolean mReceiverRegistered = false;

    public final TestName mTestName = new TestName();

    // Initialized in onCreate
    List<String> mToBeTestedCameraIds = null;
    String mPrimaryRearCameraId = null;
    String mPrimaryFrontCameraId = null;

    // Scenes
    private static final ArrayList<String> mSceneIds = new ArrayList<String> () {{
            add("scene0");
            add("scene1_1");
            add("scene1_2");
            add("scene2_a");
            add("scene2_b");
            add("scene2_c");
            add("scene2_d");
            add("scene2_e");
            add("scene3");
            add("scene4");
            add("scene5");
            add("scene6");
            add("sensor_fusion");
        }};
    // This must match scenes of SUB_CAMERA_TESTS in tools/run_all_tests.py
    private static final ArrayList<String> mHiddenPhysicalCameraSceneIds =
            new ArrayList<String> () {{
                    add("scene0");
                    add("scene1_1");
                    add("scene1_2");
                    add("scene2_a");
                    add("scene4");
                    add("sensor_fusion");
            }};
    // TODO: cache the following in saved bundle
    private Set<ResultKey> mAllScenes = null;
    // (camera, scene) -> (pass, fail)
    private final HashMap<ResultKey, Boolean> mExecutedScenes = new HashMap<>();
    // map camera id to ITS summary report path
    private final HashMap<ResultKey, String> mSummaryMap = new HashMap<>();
    // All primary cameras for which MPC level test has run
    private Set<ResultKey> mExecutedMpcTests = null;
    private static final String MPC_LAUNCH_REQ_NUM = "2.2.7.2/7.5/H-1-6";
    private static final String MPC_JPEG_CAPTURE_REQ_NUM = "2.2.7.2/7.5/H-1-5";
    // Performance class evaluator used for writing test result
    PerformanceClassEvaluator mPce = new PerformanceClassEvaluator(mTestName);
    PerformanceClassEvaluator.CameraLatencyRequirement mJpegLatencyReq =
            mPce.addR7_5__H_1_5();
    PerformanceClassEvaluator.CameraLatencyRequirement mLaunchLatencyReq =
            mPce.addR7_5__H_1_6();


    final class ResultKey {
        public final String cameraId;
        public final String sceneId;

        public ResultKey(String cameraId, String sceneId) {
            this.cameraId = cameraId;
            this.sceneId = sceneId;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null) return false;
            if (this == o) return true;
            if (o instanceof ResultKey) {
                final ResultKey other = (ResultKey) o;
                return cameraId.equals(other.cameraId) && sceneId.equals(other.sceneId);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int h = cameraId.hashCode();
            h = ((h << 5) - h) ^ sceneId.hashCode();
            return h;
        }
    }

    public ItsTestActivity() {
        super(R.layout.its_main,
                R.string.camera_its_test,
                R.string.camera_its_test_info,
                R.string.camera_its_test);
    }

    private final Comparator<ResultKey> mComparator = new Comparator<ResultKey>() {
        @Override
        public int compare(ResultKey k1, ResultKey k2) {
            if (k1.cameraId.equals(k2.cameraId))
                return k1.sceneId.compareTo(k2.sceneId);
            return k1.cameraId.compareTo(k2.cameraId);
        }
    };

    class ResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received result for Camera ITS tests");
            if (ACTION_ITS_RESULT.equals(intent.getAction())) {
                String version = intent.getStringExtra(EXTRA_VERSION);
                if (version == null || !version.equals(CURRENT_VERSION)) {
                    Log.e(TAG, "Its result version mismatch: expect " + CURRENT_VERSION +
                            ", got " + ((version == null) ? "null" : version));
                    ItsTestActivity.this.showToast(R.string.its_version_mismatch);
                    return;
                }

                String cameraId = intent.getStringExtra(EXTRA_CAMERA_ID);
                String results = intent.getStringExtra(EXTRA_RESULTS);
                if (cameraId == null || results == null) {
                    Log.e(TAG, "cameraId = " + ((cameraId == null) ? "null" : cameraId) +
                            ", results = " + ((results == null) ? "null" : results));
                    return;
                }

                if (!mToBeTestedCameraIds.contains(cameraId)) {
                    Log.e(TAG, "Unknown camera id " + cameraId + " reported to ITS");
                    return;
                }

                try {
                    /* Sample JSON results string
                    {
                       "scene0":{
                          "result":"PASS",
                          "summary":"/sdcard/cam0_scene0.txt"
                       },
                       "scene1":{
                          "result":"NOT_EXECUTED"
                       },
                       "scene2":{
                          "result":"FAIL",
                          "summary":"/sdcard/cam0_scene2.txt"
                       }
                    }
                    */
                    JSONObject jsonResults = new JSONObject(results);
                    Log.d(TAG,"Results received:" + jsonResults.toString());
                    Set<String> scenes = new HashSet<>();
                    Iterator<String> keys = jsonResults.keys();
                    while (keys.hasNext()) {
                        scenes.add(keys.next());
                    }

                    // Update test execution results
                    for (String scene : scenes) {
                        JSONObject sceneResult = jsonResults.getJSONObject(scene);
                        Log.v(TAG, sceneResult.toString());
                        String result = sceneResult.getString("result");
                        if (result == null) {
                            Log.e(TAG, "Result for " + scene + " is null");
                            return;
                        }
                        Log.i(TAG, "ITS camera" + cameraId + " " + scene + ": result:" + result);
                        if (!RESULT_VALUES.contains(result)) {
                            Log.e(TAG, "Unknown result for " + scene + ": " + result);
                            return;
                        }
                        ResultKey key = new ResultKey(cameraId, scene);
                        if (result.equals(RESULT_PASS) || result.equals(RESULT_FAIL)) {
                            boolean pass = result.equals(RESULT_PASS);
                            mExecutedScenes.put(key, pass);
                            // Get start/end time per camera/scene for result history collection.
                            mStartTime = sceneResult.getLong("start");
                            mEndTime = sceneResult.getLong("end");
                            setTestResult(testId(cameraId, scene), pass ?
                                    TestResult.TEST_RESULT_PASSED : TestResult.TEST_RESULT_FAILED);
                            Log.e(TAG, "setTestResult for " + testId(cameraId, scene) + ": " + result);
                            String summary = sceneResult.optString("summary");
                            if (!summary.equals("")) {
                                mSummaryMap.put(key, summary);
                            }
                        } // do nothing for NOT_EXECUTED scenes

                        if (sceneResult.isNull("mpc_metrics")) {
                            continue;
                        }
                        // Update MPC level
                        JSONArray metrics = sceneResult.getJSONArray("mpc_metrics");
                        for (int i = 0; i < metrics.length(); i++) {
                            String mpcResult = metrics.getString(i);
                            if (!matchMpcResult(cameraId, mpcResult)) {
                                Log.e(TAG, "Error parsing MPC result string:" + mpcResult);
                                return;
                            }
                        }
                    }
                } catch (org.json.JSONException e) {
                    Log.e(TAG, "Error reading json result string:" + results , e);
                    return;
                }

                // Set summary if all scenes reported
                if (mSummaryMap.keySet().containsAll(mAllScenes)) {
                    // Save test summary
                    StringBuilder summary = new StringBuilder();
                    for (String path : mSummaryMap.values()) {
                        appendFileContentToSummary(summary, path);
                    }
                    if (summary.length() > MAX_SUMMARY_LEN) {
                        Log.w(TAG, "ITS summary report too long: len: " + summary.length());
                    }
                    ItsTestActivity.this.getReportLog().setSummary(
                            summary.toString(), 1.0, ResultType.NEUTRAL, ResultUnit.NONE);
                }

                // Display current progress
                StringBuilder progress = new StringBuilder();
                for (ResultKey k : mAllScenes) {
                    String status = RESULT_NOT_EXECUTED;
                    if (mExecutedScenes.containsKey(k)) {
                        status = mExecutedScenes.get(k) ? RESULT_PASS : RESULT_FAIL;
                    }
                    progress.append(String.format("Cam %s, %s: %s\n",
                            k.cameraId, k.sceneId, status));
                }
                TextView progressView = (TextView) findViewById(R.id.its_progress);
                progressView.setMovementMethod(new ScrollingMovementMethod());
                progressView.setText(progress.toString());


                // Enable pass button if all scenes pass
                boolean allScenesPassed = true;
                for (ResultKey k : mAllScenes) {
                    Boolean pass = mExecutedScenes.get(k);
                    if (pass == null || pass == false) {
                        allScenesPassed = false;
                        break;
                    }
                }
                if (allScenesPassed) {
                    Log.i(TAG, "All scenes passed.");
                    // Enable pass button
                    ItsTestActivity.this.getPassButton().setEnabled(true);
                    ItsTestActivity.this.setTestResultAndFinish(true);
                } else {
                    ItsTestActivity.this.getPassButton().setEnabled(false);
                }
            }
        }

        private void appendFileContentToSummary(StringBuilder summary, String path) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(path));
                String line = null;
                do {
                    line = reader.readLine();
                    if (line != null) {
                        summary.append(line);
                    }
                } while (line != null);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Cannot find ITS summary file at " + path);
                summary.append("Cannot find ITS summary file at " + path);
            } catch (IOException e) {
                Log.e(TAG, "IO exception when trying to read " + path);
                summary.append("IO exception when trying to read " + path);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                    }
                }
            }
        }

        private boolean matchMpcResult(String cameraId, String mpcResult) {
            Matcher launchMatcher = MPC12_CAMERA_LAUNCH_PATTERN.matcher(mpcResult);
            boolean launchMatches = launchMatcher.matches();

            Matcher jpegMatcher = MPC12_JPEG_CAPTURE_PATTERN.matcher(mpcResult);
            boolean jpegMatches = jpegMatcher.matches();

            if (!launchMatches && !jpegMatches) {
                return false;
            }
            if (!cameraId.equals(mPrimaryRearCameraId) &&
                    !cameraId.equals(mPrimaryFrontCameraId)) {
                return false;
            }

            if (launchMatches) {
                float latency = Float.parseFloat(launchMatcher.group(1));
                if (cameraId.equals(mPrimaryRearCameraId)) {
                    mLaunchLatencyReq.setRearCameraLatency(latency);
                } else {
                    mLaunchLatencyReq.setFrontCameraLatency(latency);
                }
                mExecutedMpcTests.add(new ResultKey(cameraId, MPC_LAUNCH_REQ_NUM));
            } else {
                float latency = Float.parseFloat(jpegMatcher.group(1));
                if (cameraId.equals(mPrimaryRearCameraId)) {
                    mJpegLatencyReq.setRearCameraLatency(latency);
                } else {
                    mJpegLatencyReq.setFrontCameraLatency(latency);
                }
                mExecutedMpcTests.add(new ResultKey(cameraId, MPC_JPEG_CAPTURE_REQ_NUM));
            }

            // Save MPC info once both front primary and rear primary data are collected.
            if (mExecutedMpcTests.size() == 4) {
                mPce.submit();
            }
            return true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Hide the test if all camera devices are legacy
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            ItsUtils.ItsCameraIdList cameraIdList =
                    ItsUtils.getItsCompatibleCameraIds(manager);
            mToBeTestedCameraIds = cameraIdList.mCameraIdCombos;
            mPrimaryRearCameraId = cameraIdList.mPrimaryRearCameraId;
            mPrimaryFrontCameraId = cameraIdList.mPrimaryFrontCameraId;
        } catch (ItsException e) {
            Toast.makeText(ItsTestActivity.this,
                    "Received error from camera service while checking device capabilities: "
                            + e, Toast.LENGTH_SHORT).show();
        }

        super.onCreate(savedInstanceState);
        if (mToBeTestedCameraIds.size() == 0) {
            showToast(R.string.all_exempted_devices);
            ItsTestActivity.this.getReportLog().setSummary(
                    "PASS: all cameras on this device are exempted from ITS"
                    , 1.0, ResultType.NEUTRAL, ResultUnit.NONE);
            setTestResultAndFinish(true);
        }
        // Default locale must be set to "en-us"
        Locale locale = Locale.getDefault();
        if (!Locale.US.equals(locale)) {
            String toastMessage = "Unsupported default language " + locale + "! " +
                    "Please switch the default language to English (United States) in " +
                    "Settings > Language & input > Languages";
            Toast.makeText(ItsTestActivity.this, toastMessage, Toast.LENGTH_LONG).show();
            ItsTestActivity.this.getReportLog().setSummary(
                    "FAIL: Default language is not set to " + Locale.US,
                    1.0, ResultType.NEUTRAL, ResultUnit.NONE);
            setTestResultAndFinish(false);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void showManualTestDialog(final DialogTestListItem test,
            final DialogTestListItem.TestCallback callback) {
        //Nothing todo for ITS
    }

    protected String testTitle(String cam, String scene) {
        return "Camera: " + cam + ", " + scene;
    }

    protected String testId(String cam, String scene) {
        return "Camera_ITS_" + cam + "_" + scene;
    }

    protected void setupItsTests(ArrayTestListAdapter adapter) {
        for (String cam : mToBeTestedCameraIds) {
            List<String> scenes = cam.contains(ItsUtils.CAMERA_ID_TOKENIZER) ?
                    mHiddenPhysicalCameraSceneIds : mSceneIds;
            for (String scene : scenes) {
                // Add camera and scene combinations in mAllScenes to avoid adding n/a scenes for
                // devices with sub-cameras.
                if(mAllScenes == null){
                    mAllScenes = new TreeSet<>(mComparator);
                }
                mAllScenes.add(new ResultKey(cam, scene));
                adapter.add(new DialogTestListItem(this,
                testTitle(cam, scene),
                testId(cam, scene)));
            }
            if (mExecutedMpcTests == null) {
                mExecutedMpcTests = new TreeSet<>(mComparator);
            }
            Log.d(TAG,"Total combinations to test on this device:" + mAllScenes.size());
        }
    }

    @Override
    protected void setupTests(ArrayTestListAdapter adapter) {
        setupItsTests(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            showToast(R.string.no_camera_manager);
        } else {
            Log.d(TAG, "register ITS result receiver");
            IntentFilter filter = new IntentFilter(ACTION_ITS_RESULT);
            registerReceiver(mResultsReceiver, filter);
            mReceiverRegistered = true;
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "unregister ITS result receiver");
        if (mReceiverRegistered) {
            unregisterReceiver(mResultsReceiver);
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.its_main);
        setInfoResources(R.string.camera_its_test, R.string.camera_its_test_info, -1);
        setPassFailButtonClickListeners();
    }
}
