package android.server.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;

/**
 * An on-top launcher activity always shows on top of other activities.
 * When it's relaunched (i.e. receives a new intent), it should be moved to back.
 */
public class OnTopLauncherActivity extends Activity {
    // This activity is moved to back and dismisses if this is true.
    private boolean mShouldDismissOnResume;
    // mOnPauseCalled is true when this activity is in a state between onPause and onStop.
    // It's used to distinguish the two following cases:
    // 1. this activity receives a new intent (onPause -> onNewIntent -> onResume);
    // 2. this activity is moved to back and then brought up again (onPause -> onStop -> onNewIntent
    //    -> onResume);
    // For case 1, this activity should dismiss when onResume is called. But for case 2, this
    // activity should remain visible.
    private boolean mOnPauseCalled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Window w = getWindow();
        w.setBackgroundDrawable(new ColorDrawable(0x88ff00ff));
        mShouldDismissOnResume = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mShouldDismissOnResume) {
            mOnPauseCalled = false;
            mShouldDismissOnResume = false;
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (mOnPauseCalled) {
            mShouldDismissOnResume = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mOnPauseCalled = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        mOnPauseCalled = false;
    }
}
