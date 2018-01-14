package com.android.cts.devicepolicy;

public abstract class DeviceAndProfileOwnerHostSideTransferTest extends BaseDevicePolicyTest {
    protected static final String TRANSFER_OWNER_OUTGOING_PKG =
            "com.android.cts.transferowneroutgoing";
    protected static final String TRANSFER_OWNER_OUTGOING_APK = "CtsTransferOwnerOutgoingApp.apk";
    protected static final String TRANSFER_OWNER_OUTGOING_TEST_RECEIVER =
            TRANSFER_OWNER_OUTGOING_PKG
                    + "/com.android.cts.transferowner"
                    + ".DeviceAndProfileOwnerTransferOutgoingTest$BasicAdminReceiver";
    protected static final String TRANSFER_OWNER_INCOMING_PKG =
            "com.android.cts.transferownerincoming";
    protected static final String TRANSFER_OWNER_INCOMING_APK = "CtsTransferOwnerIncomingApp.apk";
    protected static final String INVALID_TARGET_APK = "CtsIntentReceiverApp.apk";

    protected int mUserId;
    protected String mOutgoingTestClassName;
    protected String mIncomingTestClassName;

    public void testTransfer() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(TRANSFER_OWNER_OUTGOING_PKG,
                mOutgoingTestClassName,
                "testTransfer", mUserId);
    }

    public void testTransferSameAdmin() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(TRANSFER_OWNER_OUTGOING_PKG,
                mOutgoingTestClassName,
                "testTransferSameAdmin", mUserId);
    }

    public void testTransferInvalidTarget() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(INVALID_TARGET_APK, mUserId);
        runDeviceTestsAsUser(TRANSFER_OWNER_OUTGOING_PKG,
                mOutgoingTestClassName,
                "testTransferInvalidTarget", mUserId);
    }

    public void testTransferPolicies() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(TRANSFER_OWNER_OUTGOING_PKG,
                mOutgoingTestClassName,
                "testTransferWithPoliciesOutgoing", mUserId);
        runDeviceTestsAsUser(TRANSFER_OWNER_INCOMING_PKG,
                mIncomingTestClassName,
                "testTransferPoliciesAreRetainedAfterTransfer", mUserId);
    }

    public void testTransferOwnerChangedBroadcast() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(TRANSFER_OWNER_OUTGOING_PKG,
                mOutgoingTestClassName,
                "testTransferOwnerChangedBroadcast", mUserId);
    }

    public void testTransferCompleteCallback() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(TRANSFER_OWNER_OUTGOING_PKG,
                mOutgoingTestClassName,
                "testTransferOwner", mUserId);

        waitForBroadcastIdle();

        runDeviceTestsAsUser(TRANSFER_OWNER_INCOMING_PKG,
                mIncomingTestClassName,
                "testTransferCompleteCallbackIsCalled", mUserId);
    }

    protected void setupTestParameters(int userId, String outgoingTestClassName,
            String incomingTestClassName) {
        mUserId = userId;
        mOutgoingTestClassName = outgoingTestClassName;
        mIncomingTestClassName = incomingTestClassName;
    }

    /* TODO: Add tests for:
    * 1. startServiceForOwner
    * 2. passwordOwner
    *
    * */
}
