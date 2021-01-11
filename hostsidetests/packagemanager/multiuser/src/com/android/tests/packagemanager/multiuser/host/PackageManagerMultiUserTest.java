package com.android.tests.packagemanager.multiuser.host;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class PackageManagerMultiUserTest extends PackageManagerMultiUserTestBase {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @AppModeFull
    public void testGetInstalledModules() throws Exception {
        int newUserId = createUser();
        getDevice().startUser(newUserId);
        runDeviceTestAsUser("testGetInstalledModules", newUserId, null);
    }
}
