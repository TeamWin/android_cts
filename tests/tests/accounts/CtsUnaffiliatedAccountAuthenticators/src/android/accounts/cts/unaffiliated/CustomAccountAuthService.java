package android.accounts.cts.unaffiliated;

import android.accounts.cts.common.Fixtures;
import android.accounts.cts.common.CustomTestAccountAuthenticator;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * a basic Mock Service for wrapping the CustomAccountAuthenticator.
 */
public class CustomAccountAuthService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        CustomTestAccountAuthenticator auth = new CustomTestAccountAuthenticator(this,
                Fixtures.TYPE_CUSTOM_UNAFFILIATED);
        return auth.getIBinder();
    }
}

