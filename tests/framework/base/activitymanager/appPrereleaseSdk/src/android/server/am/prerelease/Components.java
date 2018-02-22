package android.server.am.prerelease;

import android.content.ComponentName;

// Because we are using fake-framework, we can't extend ComponentsBase here.
public class Components {

    private static final String PACKAGE_NAME = Components.class.getPackage().getName();

    public static final ComponentName MAIN_ACTIVITY = new ComponentName(
            PACKAGE_NAME, PACKAGE_NAME + ".MainActivity");
}
