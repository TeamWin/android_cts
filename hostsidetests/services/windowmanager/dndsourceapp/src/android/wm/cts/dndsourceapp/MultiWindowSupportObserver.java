package android.wm.cts.dndsourceapp;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;

public class MultiWindowSupportObserver extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int id = Resources.getSystem().getIdentifier("config_supportsMultiWindow", "bool", "android");
        boolean support = Resources.getSystem().getBoolean(id);
        Log.i(getClass().getSimpleName(), "HEAD=OK");
        Log.i(getClass().getSimpleName(), "DROP=OK");
        Log.i(getClass().getSimpleName(), "config_supportsMultiWindow="+support);
    }
}
