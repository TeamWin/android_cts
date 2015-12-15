package android.server.app;

import static android.content.Intent.FLAG_ACTIVITY_LAUNCH_TO_SIDE;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class LaunchToSideActivity extends Activity {
    private static final String TAG = "LaunchToSide";

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        final Bundle extras = intent.getExtras();
         if (extras != null && extras.getBoolean("launch_to_the_side")) {
            Intent newIntent = new Intent("android.settings.SETTINGS");
            newIntent.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_LAUNCH_TO_SIDE);
            startActivity(newIntent);
        }
    }
}
