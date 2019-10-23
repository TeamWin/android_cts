package android.location.cts.fine;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.cts.common.GnssTestCase;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Test sending SMS and MMS using {@link android.telephony.SmsManager}.
 */
public class EmergencyCallMessageTest extends GnssTestCase {

    private static final String TAG = "EmergencyCallMSGTest";

    private static final long DEFAULT_EXPIRY_TIME_SECS = TimeUnit.DAYS.toSeconds(7);
    private static final long MMS_CONFIG_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(1);
    private static final short DEFAULT_DATA_SMS_PORT = 8091;
    private static final String PHONE_NUMBER_KEY = "android.cts.emergencycall.phonenumber";
    private static final String SMS_MESSAGE_BODY = "CTS Emergency Call Sms test message body";
    private static final String SMS_DATA_MESSAGE_BODY =
        "CTS Emergency Call Sms data test message body";
    private static final long SENT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5); // 5 minutes
    private static final String PROVIDER_AUTHORITY = "emergencycallverifier";

    private Random mRandom;
    private TelephonyManager mTelephonyManager;
    private PackageManager mPackageManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mRandom = new Random(System.currentTimeMillis());
        mTelephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mPackageManager = mContext.getPackageManager();
    }

    public void testSendSmsMessage() {
        // this test is only for cts verifier
        if (!isCtsVerifierTest()) {
            return;
        }
        SmsManager smsManager = SmsManager.getDefault();
        final String selfNumber = getPhoneNumber(mContext);
        smsManager.sendTextMessage(selfNumber, null, SMS_MESSAGE_BODY, null, null);
    }

    public void testSendSmsDataMessage() {
        // this test is only for cts verifier
        if (!isCtsVerifierTest()) {
            return;
        }
        SmsManager smsManager = SmsManager.getDefault();
        final String selfNumber = getPhoneNumber(mContext);
        smsManager.sendDataMessage(selfNumber, null, DEFAULT_DATA_SMS_PORT,
            SMS_DATA_MESSAGE_BODY.getBytes(), null, null);
    }

    private static String getPhoneNumber(Context context) {
        final TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(
                Context.TELEPHONY_SERVICE);
        String phoneNumber = telephonyManager.getLine1Number();
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            phoneNumber = System.getProperty(PHONE_NUMBER_KEY);
        }
        return phoneNumber;
    }

    private static boolean shouldParseContentDisposition() {
        return SmsManager
                .getDefault()
                .getCarrierConfigValues()
                .getBoolean(SmsManager.MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION, true);
    }

    private static boolean doesSupportMMS() {
        return SmsManager
                .getDefault()
                .getCarrierConfigValues()
                .getBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED, true);
    }

}