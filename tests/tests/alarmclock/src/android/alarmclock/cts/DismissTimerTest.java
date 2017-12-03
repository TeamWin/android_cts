package android.alarmclock.cts;

import android.alarmclock.common.Utils;

public class DismissTimerTest extends AlarmClockTestBase {

    public void testAll() throws Exception {
        assertEquals(Utils.COMPLETION_RESULT, runTest(Utils.TestcaseType.SET_TIMER_FOR_DISMISSAL));
        assertEquals(Utils.COMPLETION_RESULT, runTest(Utils.TestcaseType.DISMISS_TIMER));
    }
}
