package com.android.cts.devicepolicy;

/** Host-side tests to run the CtsPasswordComplexity device-side tests. */
public class PasswordComplexityTest extends BaseDevicePolicyTest {

    private static final String APP = "CtsPasswordComplexity.apk";
    private static final String PKG = "com.android.cts.passwordcomplexity";
    private static final String CLS = ".GetPasswordComplexityTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        if (!getDevice().executeShellCommand("cmd lock_settings verify")
                .startsWith("Lock credential verified successfully")) {
            fail("Please remove the device screen lock before running this test");
        }

        installAppAsUser(APP, mPrimaryUserId);
    }

    @Override
    protected void tearDown() throws Exception {
        getDevice().uninstallPackage(PKG);

        super.tearDown();
    }

    public void testGetPasswordComplexity() throws Exception {
        runDeviceTestsAsUser(PKG, CLS, mPrimaryUserId);
    }
}
