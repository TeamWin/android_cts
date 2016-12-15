package com.android.cts.comp.provisioning;


import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.test.AndroidTestCase;
import com.android.cts.comp.AdminReceiver;
import java.util.Arrays;
import java.util.List;

public class AffiliationTest extends AndroidTestCase {

    public void testSetAffiliationId1() {
        setAffiliationId("id.number.1");
    }

    public void testSetAffiliationId2() {
        setAffiliationId("id.number.2");
    }

    private void setAffiliationId(String id) {
        ComponentName admin = AdminReceiver.getComponentName(getContext());
        DevicePolicyManager dpm = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        List<String> ids = Arrays.asList(id);
        dpm.setAffiliationIds(admin, ids);
        assertEquals(ids, dpm.getAffiliationIds(admin));
    }
}
