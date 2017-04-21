package android.location.cts;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.compatibility.common.util.CddTest;
import java.util.concurrent.TimeUnit;

/**
 * Tests for the ttff (time to the first fix) validating whether TTFF is
 * below the expected thresholds in differnt scenario
 */
public class GnssTtffTests extends GnssTestCase {

  private static final String TAG = "GnssTtffTests";
  private static final int LOCATION_TO_COLLECT_COUNT = 1;
  private static final int STATUS_TO_COLLECT_COUNT = 3;
  private static final int AIDING_DATA_RESET_DELAY_SECS = 10;
  // Threshold values
  private static final int TTFF_WITH_WIFI_CELLUAR_WARM_TH_SECS = 7;
  private static final int TTFF_WITH_WIFI_CELLUAR_HOT_TH_SECS = 5;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mTestLocationManager = new TestLocationManager(getContext());
  }

  /**
   * Test the TTFF in the case where there is a network connection for both
   * warm and hot start TTFF cases.
   * @throws Exception
   */
  @CddTest(requirement="7.3.3")
  public void testTtffWithNetwork() throws Exception {
    checkTtffWarmWithCellularAndWifiOn();
    checkTtffHotWithCellularAndWifiOn();
  }

  /**
   * Test Scenario 1
   * check whether TTFF is below the threshold on the warm start, without any network
   * 1) Turn on wifi, turn on mobile data
   * 2) Get GPS, check the TTFF value
   */
  public void checkTtffWarmWithCellularAndWifiOn() throws Exception {
    ensureNetworkStatus();
    SoftAssert softAssert = new SoftAssert(TAG);
    mTestLocationManager.sendExtraCommand("delete_aiding_data");
    Thread.sleep(TimeUnit.SECONDS.toMillis(AIDING_DATA_RESET_DELAY_SECS));
    checkTtffByThreshold("checkTtffWarmWithCellularAndWifiOn",
        TimeUnit.SECONDS.toMillis(TTFF_WITH_WIFI_CELLUAR_WARM_TH_SECS), softAssert);
    softAssert.assertAll();
  }

  /**
   * Test Scenario 2
   * check whether TTFF is below the threhold on the hot start, without any network
   * 1) Turn on wifi, turn on mobile data
   * 2) Get GPS, check the TTFF value
   */
  public void checkTtffHotWithCellularAndWifiOn() throws Exception {
    ensureNetworkStatus();
    SoftAssert softAssert = new SoftAssert(TAG);
    checkTtffByThreshold("checkTtffHotWithCellularAndWifiOn",
        TimeUnit.SECONDS.toMillis(TTFF_WITH_WIFI_CELLUAR_HOT_TH_SECS), softAssert);
    softAssert.assertAll();

  }

  /**
   * Make sure the device has mobile data and wifi connection
   */
  private void ensureNetworkStatus(){
    assertTrue("Device has to connect to Wifi to complete this test.",
        isConnectedToWifi(getContext()));
    ConnectivityManager connManager = getConnectivityManager(getContext());
    NetworkInfo mobileNetworkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
    // check whether the mobile data is ON if the device has cellular capability
    if (mobileNetworkInfo != null) {
      TelephonyManager telephonyManager = (TelephonyManager) getContext().getApplicationContext()
          .getSystemService(getContext().TELEPHONY_SERVICE);
      assertTrue("Device has to have mobile data ON to complete this test.",
          telephonyManager.isDataEnabled());
    }
    else {
      Log.i(TAG, "This is a wifi only device.");
    }

  }

  /*
   * Check whether TTFF is below the threshold
   * @param testName
   * @param threshold, the threshold for the TTFF value
   */
  private void checkTtffByThreshold(String testName,
      long threshold, SoftAssert softAssert) throws Exception {
    TestLocationListener networkLocationListener
        = new TestLocationListener(LOCATION_TO_COLLECT_COUNT);
    // fetch the networklocation first to make sure the ttff is not flaky
    mTestLocationManager.requestNetworkLocationUpdates(networkLocationListener);
    networkLocationListener.await();

    TestGnssStatusCallback testGnssStatusCallback =
        new TestGnssStatusCallback(TAG, STATUS_TO_COLLECT_COUNT);
    mTestLocationManager.registerGnssStatusCallback(testGnssStatusCallback);

    TestLocationListener locationListener = new TestLocationListener(LOCATION_TO_COLLECT_COUNT);
    mTestLocationManager.requestLocationUpdates(locationListener);


    long startTimeMillis = System.currentTimeMillis();
    boolean success = testGnssStatusCallback.awaitTtff();
    long ttffTimeMillis = System.currentTimeMillis() - startTimeMillis;

    SoftAssert.failOrWarning(isMeasurementTestStrict(),
            "Test case:" + testName
            + ". Threshold exceeded without getting a location."
            + " Possibly, the test has been run deep indoors."
            + " Consider retrying test outdoors.",
        success);
    mTestLocationManager.removeLocationUpdates(locationListener);
    mTestLocationManager.unregisterGnssStatusCallback(testGnssStatusCallback);
    softAssert.assertTrue("Test case: " + testName +", TTFF should be less than " + threshold
        + " . In current test, TTFF value is: " + ttffTimeMillis, ttffTimeMillis < threshold);
  }

  /**
   * Returns whether the device is currently connected to a wifi.
   *
   * @param context {@link Context} object
   * @return {@code true} if connected to Wifi; {@code false} otherwise
   */
  public static boolean isConnectedToWifi(Context context) {
    NetworkInfo info = getActiveNetworkInfo(context);
    return info != null
        && info.isConnected()
        && info.getType() == ConnectivityManager.TYPE_WIFI;
  }

  /**
   * Gets the active network info.
   *
   * @param context {@link Context} object
   * @return {@link NetworkInfo}
   */
  private static NetworkInfo getActiveNetworkInfo(Context context) {
    ConnectivityManager cm = getConnectivityManager(context);
    if (cm != null) {
      return cm.getActiveNetworkInfo();
    }
    return null;
  }

  /**
   * Gets the connectivity manager.
   *
   * @param context {@link Context} object
   * @return {@link ConnectivityManager}
   */
  private static ConnectivityManager getConnectivityManager(Context context) {
    return (ConnectivityManager) context.getApplicationContext()
        .getSystemService(Context.CONNECTIVITY_SERVICE);
  }

}