package com.android.compatibility.common.tradefed.result;

import com.android.compatibility.common.tradefed.build.BuildHelper;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildInfo;
import com.android.compatibility.common.tradefed.testtype.CompatibilityTest;
import com.android.compatibility.common.util.AbiUtils;
import com.android.compatibility.common.xml.XmlResultHandler;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFileSaver;
import com.android.tradefed.result.TestSummary;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Reporter for Compatibility test results.
 */
@OptionClass(alias="result-reporter")
public class ResultReporter implements ITestInvocationListener {

    private static final String DEVICE_INFO_COLLECTOR = "com.android.compatibility.deviceinfo";

    @Option(name = CompatibilityTest.PLAN_OPTION,
            shortName = 'p',
            description = "the test plan to run.",
            importance = Importance.IF_UNSET)
    private String mPlanName = null;

    @Option(name = CompatibilityTest.CONTINUE_OPTION,
            shortName = 'c',
            description = "continue a previous session.",
            importance = Importance.IF_UNSET)
    private Integer mContinueSessionId = null;

    @Option(name = CompatibilityTest.RETRY_OPTION,
            shortName = 'r',
            description = "retry a previous session.",
            importance = Importance.IF_UNSET)
    private Integer mRetrySessionId = null;

    private String mDeviceSerial;

    private boolean mInitialized;
    private IInvocationResult mResult;
    private File mResultDir;
    private File mLogDir;
    private long mStartTime;
    private boolean mIsDeviceInfoRun;
    private IModuleResult mCurrentModuleResult;
    private IResult mCurrentResult;
    private BuildHelper mBuildHelper;

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IBuildInfo buildInfo) {
        CLog.d("ResultReporter.invocationStarted(%s)", buildInfo.toString());
        mInitialized = false;
        mBuildHelper = new BuildHelper((CompatibilityBuildInfo) buildInfo);
        mDeviceSerial = buildInfo.getDeviceSerial();
        if (mDeviceSerial == null) {
            mDeviceSerial = "unknown_device";
        }
        long time = System.currentTimeMillis();
        String dirSuffix = getDirSuffix(time);
        if (mContinueSessionId != null) {
            CLog.d("Continuing session %d", mContinueSessionId);
            initializeFromSession(mContinueSessionId);
        } else if (mRetrySessionId != null) {
            CLog.d("Retrying session %d", mRetrySessionId);
            initializeFromSession(mRetrySessionId);
        } else {
            mStartTime = time;
            mResultDir = new File(mBuildHelper.getResultsDir(), dirSuffix);
            if (mResultDir.mkdirs()) {
                CLog.logAndDisplay(LogLevel.INFO, "Created result dir %s",
                        mResultDir.getAbsolutePath());
            } else {
                throw new IllegalArgumentException(String.format("Could not create result dir %s",
                        mResultDir.getAbsolutePath()));
            }
            mResult = new InvocationResult(mStartTime, mResultDir);
        }
        mLogDir = new File(mBuildHelper.getLogsDir(), dirSuffix);
        if (mLogDir.mkdirs()) {
            CLog.logAndDisplay(LogLevel.INFO, "Created log dir %s", mLogDir.getAbsolutePath());
        } else {
            throw new IllegalArgumentException(String.format("Could not create log dir %s",
                    mLogDir.getAbsolutePath()));
        }
        mInitialized = true;
    }

    /**
     * Initializes this {@link ResultReporter} from the given session.
     */
    private void initializeFromSession(Integer session) {
        List<IInvocationResult> results = XmlResultHandler.getResults(mBuildHelper.getResultsDir());
        if (session >= 0 && session < results.size()) {
            mResult = results.get(session);
        } else {
            throw new IllegalArgumentException(String.format("Could not find session %d",session));
        }
        mPlanName = mResult.getTestPlan();
        mStartTime = mResult.getStartTime();
        mResultDir = mResult.getResultDir();
    }

    /**
     * @return a {@link String} to use for directory suffixes created from the given time.
     */
    private String getDirSuffix(long time) {
        return new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss").format(new Date(time));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStarted(String id, int numTests) {
        CLog.d("ResultReporter.testRunStarted(%s, %d)", id, numTests);
        mIsDeviceInfoRun = AbiUtils.parseTestName(id).equals(DEVICE_INFO_COLLECTOR);
        if (!mIsDeviceInfoRun) {
            CLog.logAndDisplay(LogLevel.INFO, "Starting %s with %s tests", id, numTests);
            mCurrentModuleResult = mResult.getOrCreateModule(id);
            mCurrentModuleResult.setDeviceSerial(mDeviceSerial);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testStarted(TestIdentifier test) {
        CLog.d("ResultReporter.testStarted(%s)", test.toString());
        if (!mIsDeviceInfoRun) {
            mCurrentResult = mCurrentModuleResult.getOrCreateResult(test);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testEnded(TestIdentifier test, Map<String, String> metrics) {
        CLog.d("ResultReporter.testEnded(%s, %s)", test.toString(), metrics.toString());
        if (!mIsDeviceInfoRun) {
            mCurrentModuleResult.reportTestEnded(test, metrics);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testIgnored(TestIdentifier test) {
        CLog.d("ResultReporter.testIgnored(%s)", test.toString());
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestIdentifier test, String trace) {
        CLog.d("ResultReporter.testFailed(%s, %s)", test.toString(), trace);
        if (!mIsDeviceInfoRun) {
            mCurrentModuleResult.reportTestFailure(test, TestStatus.FAIL, trace);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testAssumptionFailure(TestIdentifier test, String trace) {
        CLog.d("ResultReporter.testAssumptionFailure(%s, %s)", test.toString(), trace);
        if (!mIsDeviceInfoRun) {
            mCurrentModuleResult.reportTestFailure(test, TestStatus.FAIL, trace);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStopped(long elapsedTime) {
        CLog.d("ResultReporter.testRunStopped(%d)", elapsedTime);
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> metrics) {
        CLog.d("ResultReporter.testRunEnded(%d, %s)", elapsedTime, metrics.toString());
        if (mIsDeviceInfoRun) {
            mResult.populateDeviceInfoMetrics(metrics);
        } else {
            CLog.logAndDisplay(LogLevel.INFO, "%s completed in %dms", mCurrentModuleResult.getId(),
                    elapsedTime);
            mCurrentModuleResult.populateMetrics(metrics);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunFailed(String id) {
        CLog.d("ResultReporter.testRunFailed(%s)", id);
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestSummary getSummary() {
        // ignore
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        CLog.d("ResultReporter.invocationEnded(%d)", elapsedTime);
        if (mInitialized) {
            CLog.logAndDisplay(LogLevel.INFO,
                    "Invocation finished. Passed %d, Failed %d, Not Executed %d",
                    mResult.countResults(TestStatus.PASS),
                    mResult.countResults(TestStatus.FAIL),
                    mResult.countResults(TestStatus.NOT_EXECUTED));
            XmlResultHandler.writeResults(mBuildHelper.getSuiteName(),
                    mBuildHelper.getSuiteVersion(), mPlanName, mResult, mResultDir, mLogDir,
                    mStartTime, elapsedTime + mStartTime);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(Throwable cause) {
        CLog.d("ResultReporter.invocationFailed(%s)", cause.toString());
        mInitialized = false;
        // Clean up
        mResultDir.delete();
        mLogDir.delete();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLog(String name, LogDataType type, InputStreamSource stream) {
        CLog.d("ResultReporter.testLog(%s, %s, %s)", name, type.toString(), stream.toString());
        try {
            LogFileSaver saver = new LogFileSaver(mLogDir);
            File logFile = saver.saveAndZipLogData(name, type, stream.createInputStream());
            CLog.i("Saved logs for %s in %s", name, logFile.getAbsolutePath());
        } catch (IOException e) {
            CLog.e("Failed to write log for %s", name);
            e.printStackTrace();
        }
    }

}
