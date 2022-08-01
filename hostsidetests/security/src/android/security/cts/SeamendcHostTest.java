/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package android.security.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

@RunWith(DeviceJUnit4ClassRunner.class)
public class SeamendcHostTest extends BaseHostJUnit4Test {

    private ITestDevice mDevice;

    private File seamendc;
    private File secilc;
    private File searchpolicy;
    private File libsepolwrap;

    private File devicePolicy;
    private File precompiledSepolicyWithoutApex;
    private File apexSepolicyDecompiledCil;

    private File platSepolicyCil;
    private File apexSepolicyCil;
    private File platPubVersionedCil;
    private File systemExtSepolicyCil;
    private File productSepolicyCil;
    private File vendorSepolicyCil;

    @Before
    public void setUp() throws Exception {
        mDevice = getDevice();

        seamendc = copyResToTempFile("/seamendc");
        seamendc.setExecutable(true);
        secilc = copyResToTempFile("/secilc");
        secilc.setExecutable(true);
        searchpolicy = copyResToTempFile("/searchpolicy");
        searchpolicy.setExecutable(true);
        libsepolwrap = new CompatibilityBuildHelper(getBuild()).getTestFile("libsepolwrap.so");
        libsepolwrap.deleteOnExit();

        devicePolicy = getDeviceFile("/sys/fs/selinux/", "policy");
        precompiledSepolicyWithoutApex = copyResToTempFile("/precompiled_sepolicy-without_apex");
        apexSepolicyDecompiledCil = copyResToTempFile("/apex_sepolicy-decompiled.cil");

        platSepolicyCil = getDeviceFile("/system/etc/selinux/", "plat_sepolicy.cil");
        apexSepolicyCil = copyResToTempFile("/apex_sepolicy.cil");
        platPubVersionedCil = getDeviceFile("/vendor/etc/selinux/", "plat_pub_versioned.cil");
        systemExtSepolicyCil = getDeviceFile("/system_ext/etc/selinux/", "system_ext_sepolicy.cil");
        productSepolicyCil = getDeviceFile("/product/etc/selinux/", "product_sepolicy.cil");
        vendorSepolicyCil = getDeviceFile("/vendor/etc/selinux/", "vendor_sepolicy.cil");
    }

    private File getDeviceFile(String filePath, String fileName) throws Exception {
        String deviceFile = filePath + fileName;
        if (!mDevice.doesFileExist(deviceFile)) {
            throw new FileNotFoundException(deviceFile + " does not exist.");
        }
        File file = File.createTempFile(fileName, ".tmp");
        file.deleteOnExit();
        mDevice.pullFile(deviceFile, file);
        return file;
    }

    private static File copyResToTempFile(String resName) throws IOException {
        InputStream is = SeamendcHostTest.class.getResourceAsStream(resName);
        File tempFile = File.createTempFile(resName, ".tmp");
        FileOutputStream os = new FileOutputStream(tempFile);
        byte[] buf = new byte[1024];
        int len;

        while ((len = is.read(buf)) != -1) {
            os.write(buf, 0, len);
        }
        os.flush();
        os.close();
        tempFile.deleteOnExit();
        return tempFile;
    }

    private static String runProcess(String... args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor();
        BufferedReader result = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        StringBuilder errorString = new StringBuilder();
        while ((line = result.readLine()) != null) {
            errorString.append(line);
            errorString.append("\n");
        }
        return errorString.toString();
    }

    private String searchpolicySource(String policyPath, String name) throws Exception {
        return runProcess(searchpolicy.getAbsolutePath(), "--allow", "-s", name, "--libpath",
                libsepolwrap.getAbsolutePath(), policyPath);
    }

    private String searchpolicyTarget(String policyPath, String name) throws Exception {
        return runProcess(searchpolicy.getAbsolutePath(), "--allow", "-t", name, "--libpath",
                libsepolwrap.getAbsolutePath(), policyPath);
    }

    private String runSeamendc(String basePolicy, String output, String... args) throws Exception {
        return runProcess(
                Stream.concat(Stream.of(seamendc.getAbsolutePath(), "-b", basePolicy, "-o", output),
                        Stream.of(args)).toArray(String[]::new));
    }

    private String runSecilc(String fileContexts, String output, String... args) throws Exception {
        return runProcess(Stream
                .concat(Stream.of(secilc.getAbsolutePath(), "-m", "-M", "true", "-G", "-N", "-c",
                        "30", "-f", fileContexts, "-o", output), Stream.of(args))
                .toArray(String[]::new));
    }

    /**
     * Verifies the output of seamendc against the precompiled sepolicy on the device. The binary
     * policies must be the same.
     *
     * @throws Exception
     */
    @Ignore // Until b/240573438 is fixed.
    @Test
    public void testSeamendcAgainstPrecompiledPolicies() throws Exception {
        File seamendcBinary = File.createTempFile("seamendc+apex", "binary");
        seamendcBinary.deleteOnExit();
        String errorString = runSeamendc(precompiledSepolicyWithoutApex.getAbsolutePath(),
                seamendcBinary.getAbsolutePath(), apexSepolicyDecompiledCil.getAbsolutePath());
        assertTrue(errorString, errorString.length() == 0);

        assertEquals("Binary policy mismatch.",
                searchpolicySource(devicePolicy.getAbsolutePath(), "sdk_sandbox"),
                searchpolicySource(seamendcBinary.getAbsolutePath(), "sdk_sandbox"));

        assertEquals("Binary policy mismatch.",
                searchpolicyTarget(devicePolicy.getAbsolutePath(), "sdk_sandbox"),
                searchpolicyTarget(seamendcBinary.getAbsolutePath(), "sdk_sandbox"));
    }

    /**
     * Verifies the output of seamendc against the binary policy obtained by secilc-compiling the
     * CIL policies on the device. The binary policies must be the same.
     *
     * @throws Exception
     */
    @Ignore // Until b/240573438 is fixed.
    @Test
    public void testSeamendcAgainstSecilc() throws Exception {
        File secilcBinaryWithApex = File.createTempFile("secilc+apex", "binary");
        secilcBinaryWithApex.deleteOnExit();
        File fileContextsFile = File.createTempFile("file_contexts", ".txt");
        fileContextsFile.deleteOnExit();
        String errorString = runSecilc(fileContextsFile.getAbsolutePath(),
                secilcBinaryWithApex.getAbsolutePath(), platSepolicyCil.getAbsolutePath(),
                apexSepolicyCil.getAbsolutePath(), platPubVersionedCil.getAbsolutePath(),
                systemExtSepolicyCil.getAbsolutePath(), productSepolicyCil.getAbsolutePath(),
                vendorSepolicyCil.getAbsolutePath());
        assertTrue(errorString, errorString.length() == 0);

        File secilcBinary = File.createTempFile("secilc", "binary");
        secilcBinary.deleteOnExit();
        errorString = runSecilc(fileContextsFile.getAbsolutePath(), secilcBinary.getAbsolutePath(),
                platSepolicyCil.getAbsolutePath(), platPubVersionedCil.getAbsolutePath(),
                systemExtSepolicyCil.getAbsolutePath(), productSepolicyCil.getAbsolutePath(),
                vendorSepolicyCil.getAbsolutePath());
        assertTrue(errorString, errorString.length() == 0);

        File seamendcBinaryWithApex = File.createTempFile("seamendc+apex", "binary");
        seamendcBinaryWithApex.deleteOnExit();
        errorString = runSeamendc(secilcBinary.getAbsolutePath(),
                seamendcBinaryWithApex.getAbsolutePath(),
                apexSepolicyDecompiledCil.getAbsolutePath());
        assertTrue(errorString, errorString.length() == 0);

        assertEquals("Binary policy mismatch.",
                searchpolicySource(secilcBinaryWithApex.getAbsolutePath(), "sdk_sandbox"),
                searchpolicySource(seamendcBinaryWithApex.getAbsolutePath(), "sdk_sandbox"));

        assertEquals("Binary policy mismatch.",
                searchpolicyTarget(secilcBinaryWithApex.getAbsolutePath(), "sdk_sandbox"),
                searchpolicyTarget(seamendcBinaryWithApex.getAbsolutePath(), "sdk_sandbox"));
    }

}
