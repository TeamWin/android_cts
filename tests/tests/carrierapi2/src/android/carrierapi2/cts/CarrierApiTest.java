/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.carrierapi2.cts;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.test.AndroidTestCase;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Verifies the APIs for apps with carrier privileges targeting pre-Q.
 *
 * @see android.carrierapi.cts.CarrierApiTest
 */
public class CarrierApiTest extends AndroidTestCase {
    private static final String TAG = "CarrierApi2Test";

    private TelephonyManager mTelephonyManager;
    private PackageManager mPackageManager;
    private boolean hasCellular;
    private String selfPackageName;
    private String selfCertHash;

    private static final String FiDevCert = "24EB92CBB156B280FA4E1429A6ECEEB6E5C1BFE4";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTelephonyManager = (TelephonyManager)
                getContext().getSystemService(Context.TELEPHONY_SERVICE);
        mPackageManager = getContext().getPackageManager();
        selfPackageName = getContext().getPackageName();
        selfCertHash = getCertHash(selfPackageName);
        hasCellular = hasCellular();
        if (!hasCellular) {
            Log.e(TAG, "No cellular support, all tests will be skipped.");
        }
    }

    /**
     * Checks whether the cellular stack should be running on this device.
     */
    private boolean hasCellular() {
        return mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) &&
                mTelephonyManager.getPhoneCount() > 0;
    }

    private boolean isSimCardPresent() {
        return mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE &&
                mTelephonyManager.getSimState() != TelephonyManager.SIM_STATE_ABSENT;
    }

    private String getCertHash(String pkgName) {
        try {
            PackageInfo pInfo = mPackageManager.getPackageInfo(pkgName,
                    PackageManager.GET_SIGNATURES | PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return bytesToHexString(md.digest(pInfo.signatures[0].toByteArray()));
        } catch (PackageManager.NameNotFoundException ex) {
            Log.e(TAG, pkgName + " not found", ex);
        } catch (NoSuchAlgorithmException ex) {
            Log.e(TAG, "Algorithm SHA1 is not found.");
        }
        return "";
    }

    private void failMessage() {
        if (FiDevCert.equalsIgnoreCase(selfCertHash)) {
            fail("This test requires a Project Fi SIM card.");
        } else {
            fail("This test requires a SIM card with carrier privilege rule on it.\n" +
                 "Cert hash: " + selfCertHash + "\n" +
                 "Visit https://source.android.com/devices/tech/config/uicc.html");
        }
    }

    public void testSimCardPresent() {
        if (!hasCellular) return;
        assertTrue("This test requires SIM card.", isSimCardPresent());
    }

    public void testHasCarrierPrivileges() {
        if (!hasCellular) return;
        if (!mTelephonyManager.hasCarrierPrivileges()) {
            failMessage();
        }
    }

    public void testTelephonyApisAreAccessible() {
        if (!hasCellular) return;
        // The following methods may return any value depending on the state of the device. Simply
        // call them to make sure they do not throw any exceptions. Methods that return a device
        // identifier will be accessible to apps with carrier privileges in Q, but this may change
        // in a future release.
        try {
            mTelephonyManager.getDeviceId();
            mTelephonyManager.getImei();
            mTelephonyManager.getMeid();
            mTelephonyManager.getDeviceSoftwareVersion();
            mTelephonyManager.getNai();
            mTelephonyManager.getDataNetworkType();
            mTelephonyManager.getVoiceNetworkType();
            mTelephonyManager.getSimSerialNumber();
            mTelephonyManager.getSubscriberId();
            mTelephonyManager.getGroupIdLevel1();
            mTelephonyManager.getLine1Number();
            mTelephonyManager.getVoiceMailNumber();
            mTelephonyManager.getVisualVoicemailPackageName();
            mTelephonyManager.getVoiceMailAlphaTag();
            mTelephonyManager.getForbiddenPlmns();
            mTelephonyManager.getServiceState();
        } catch (SecurityException e) {
            failMessage();
        }
    }

    // A table mapping from a number to a hex character for fast encoding hex strings.
    private static final char[] HEX_CHARS = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    private static String bytesToHexString(byte[] bytes) {
        StringBuilder ret = new StringBuilder(2 * bytes.length);
        for (int i = 0 ; i < bytes.length ; i++) {
            int b;
            b = 0x0f & (bytes[i] >> 4);
            ret.append(HEX_CHARS[b]);
            b = 0x0f & bytes[i];
            ret.append(HEX_CHARS[b]);
        }
        return ret.toString();
    }
}
