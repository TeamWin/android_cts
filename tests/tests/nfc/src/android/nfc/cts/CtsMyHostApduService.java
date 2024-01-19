package android.nfc.cts;

import android.content.Intent;
import android.nfc.cardemulation.*;
import android.os.Bundle;

import androidx.test.InstrumentationRegistry;

import java.util.ArrayList;
import java.util.List;

public class CtsMyHostApduService extends HostApduService {
    static final String POLLING_LOOP_RECEIVED_ACTION = "CTS_NFC_POLLING_LOOP";
    static final String SERVICE_NAME_EXTRA = "CTS_NFC_SERVICE_NAME_EXTRA";
    static final String POLLING_FRAMES_EXTRA = "CTS_NFC_POLLING_FRAMES_EXTRA";


    public void ctsNotifyUnhandled() {
      return;
    }

    @Override
    public byte[] processCommandApdu(byte[] apdu, Bundle extras) {
        return new byte[0];
    }

    @Override
    public void onDeactivated(int reason) {
        return;
    }

    @Override
    public void processPollingFrames(List<Bundle> frames) {
        Intent intent = new Intent(POLLING_LOOP_RECEIVED_ACTION);

        intent.putExtra(CtsMyHostApduService.SERVICE_NAME_EXTRA, this.getClass().getName());
        intent.putParcelableArrayListExtra(CtsMyHostApduService.POLLING_FRAMES_EXTRA,
                new ArrayList<Bundle>(frames));
        InstrumentationRegistry.getContext().sendOrderedBroadcast(intent, null);
    }
}
