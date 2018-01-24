/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cts.deviceowner;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.content.ComponentName;
import android.telephony.data.ApnSetting;

import java.net.InetAddress;
import java.util.List;

/**
 * Test override APN APIs.
 */
public class OverrideApnTest extends BaseDeviceOwnerTest {
    private static final String TEST_APN_NAME = "testApnName";
    private static final String UPDATE_APN_NAME = "updateApnName";
    private static final String TEST_ENTRY_NAME = "testEntryName";
    private static final String UPDATE_ETNRY_NAME = "updateEntryName";
    private static final String TEST_OPERATOR_NUMERIC = "123456789";
    private static final int TEST_PORT = 123;

    private ApnSetting getTestApn() throws Exception {
        return new ApnSetting.Builder()
                .setApnName(TEST_APN_NAME)
                .setEntryName(TEST_ENTRY_NAME)
                .setOperatorNumeric(TEST_OPERATOR_NUMERIC)
                .setPort(TEST_PORT)
                .setProxy(getTestProxy())
                .build();
    }

    private InetAddress getTestProxy() throws Exception {
        return InetAddress.getByName("123.123.123.123");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        List <ApnSetting> apnList = mDevicePolicyManager.getOverrideApns(getWho());
        for (ApnSetting apn : apnList) {
            boolean deleted = mDevicePolicyManager.removeOverrideApn(getWho(), apn.getId());
            assertTrue("Fail to clean up override APNs.", deleted);
        }
        mDevicePolicyManager.setOverrideApnsEnabled(getWho(), false);
    }

    public void testAddGetRemoveOverrideApn() throws Exception {
        int insertedId = mDevicePolicyManager.addOverrideApn(getWho(), getTestApn());
        assertTrue(insertedId != 0);
        List <ApnSetting> apnList = mDevicePolicyManager.getOverrideApns(getWho());
        assertEquals(1, apnList.size());
        assertEquals(TEST_APN_NAME, apnList.get(0).getApnName());
        assertEquals(TEST_ENTRY_NAME, apnList.get(0).getEntryName());
        assertEquals(TEST_OPERATOR_NUMERIC, apnList.get(0).getOperatorNumeric());
        assertEquals(TEST_PORT, apnList.get(0).getPort());
        assertEquals(getTestProxy(), apnList.get(0).getProxy());
        assertTrue(mDevicePolicyManager.removeOverrideApn(getWho(), insertedId));
        apnList = mDevicePolicyManager.getOverrideApns(getWho());
        assertEquals(0, apnList.size());
    }

    public void testRemoveOverrideApnFailsForIncorrectId() throws Exception {
        assertFalse(mDevicePolicyManager.removeOverrideApn(getWho(), -1));
    }

    public void testUpdateOverrideApn() throws Exception {
        int insertedId = mDevicePolicyManager.addOverrideApn(getWho(), getTestApn());
        assertTrue(insertedId != 0);

        final ApnSetting updateApn = (new ApnSetting.Builder())
                .setEntryName(UPDATE_ETNRY_NAME)
                .setApnName(UPDATE_APN_NAME)
                .build();
        assertTrue(mDevicePolicyManager.updateOverrideApn(getWho(), insertedId, updateApn));

        List <ApnSetting> apnList = mDevicePolicyManager.getOverrideApns(getWho());
        assertEquals(1, apnList.size());
        assertEquals(UPDATE_APN_NAME, apnList.get(0).getApnName());
        assertEquals(UPDATE_ETNRY_NAME, apnList.get(0).getEntryName());
        // The old entry got completely replaced by the new APN.
        assertEquals("", apnList.get(0).getOperatorNumeric());
        assertEquals(-1, apnList.get(0).getPort());
        assertEquals(null, apnList.get(0).getProxy());
        assertTrue(mDevicePolicyManager.removeOverrideApn(getWho(), insertedId));
    }

    public void testSetAndGetOverrideApnEnabled() throws Exception {
        mDevicePolicyManager.setOverrideApnsEnabled(getWho(), true);
        assertTrue(mDevicePolicyManager.isOverrideApnEnabled(getWho()));
        mDevicePolicyManager.setOverrideApnsEnabled(getWho(), false);
        assertFalse(mDevicePolicyManager.isOverrideApnEnabled(getWho()));
    }
}
