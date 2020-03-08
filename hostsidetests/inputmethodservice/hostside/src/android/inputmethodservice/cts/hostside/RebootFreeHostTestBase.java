/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.inputmethodservice.cts.hostside;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.util.Objects;
import java.util.WeakHashMap;

/**
 * A utility class that enforces the device under test must never reboot during the test runs.
 *
 * <p>For instance, suppose the following test class.</p>
 * <pre>{@code
 * @RunWith(DeviceJUnit4ClassRunner.class)
 * public class MyTestClass extends RebootFreeHostTestBase {
 *     @Test
 *     public void test1() throws Exception {
 *         // do something;
 *     }
 *
 *     @Test
 *     public void test2() throws Exception {
 *         // do something;
 *     }
 *
 *     @Test
 *     public void test3() throws Exception {
 *         // do something;
 *     }
 * }
 * }</pre>
 *
 * <p>If the device (soft-)rebooted between {@code test1()} and {@code test2()}, then this base
 * class would let {@code test2()} and {@code test3()} immediately fail by throwing
 * {@link IllegalStateException} with saying that the device reboot is detected and the last
 * test method before reboot is {@code test1()}</p>
 *
 * <p>Note that reboot-free is enforced only within each subclass of {@link RebootFreeHostTestBase}.
 * Another sub-class such as {@code class MyTestClass2 extends RebootFreeHostTestBase} will not
 * immediately fail because of device restart detected while running {@code class MyTestClass}.</p>
 */
public class RebootFreeHostTestBase extends BaseHostJUnit4Test {
    private static final String PROP_SYSTEM_SERVER_START_COUNT = "sys.system_server.start_count";

    private static final WeakHashMap<Class<?>, String> sExpectedSystemStartCount =
            new WeakHashMap<>();
    private static final WeakHashMap<Class<?>, String> sLastRunningTestName = new WeakHashMap<>();

    /**
     * A {@link TestName} object to receive the currently running test name.
     *
     * <p>Note this field needs to be {@code public} due to a restriction of JUnit4.</p>
     */
    @Rule
    public TestName mTestName = new TestName();

    /**
     * @return device start count as {@link String}.
     * @throws DeviceNotAvailableException
     */
    private String getSystemStartCountAsString() throws DeviceNotAvailableException  {
        final String countString = getDevice().getProperty(PROP_SYSTEM_SERVER_START_COUNT);
        if (countString == null) {
            throw new IllegalStateException(
                    String.format("Property %s must not be null", PROP_SYSTEM_SERVER_START_COUNT));
        }
        return countString;
    }

    /**
     * Ensures there was no unexpected device reboot.
     *
     * @throws IllegalStateException when an unexpected device reboot is detected.
     */
    @Before
    public void ensureNoSystemRestart() throws Exception {
        final Class<?> myClass = getClass();
        final String currentCount = getSystemStartCountAsString();
        final String expectedCount = sExpectedSystemStartCount.get(myClass);
        if (expectedCount == null) {
            // This is the first time for the given test class to run.
            // Just remember the current system start count.
            sExpectedSystemStartCount.put(myClass, currentCount);
        } else if (!Objects.equals(expectedCount, currentCount)) {
            final String lastTest = sLastRunningTestName.getOrDefault(myClass, "<unknown>");
            throw new IllegalStateException(String.format(
                    "Unexpected device restart detected [%s: %s -> %s]. "
                            + "lastTestBeforeRestart=%s. Check the device log!",
                    PROP_SYSTEM_SERVER_START_COUNT, expectedCount, currentCount, lastTest));
        }
        sLastRunningTestName.put(myClass, mTestName.getMethodName());
    }
}
