/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.verifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Conditional Test annotation. Use it to add a runtime condition to display or hide a given test.
 *
 * Definitions: The test which is to be configured is called Conditionalized Test. The class
 * implementing the conditional logic is called Condition Class.
 *
 * Condition Classes must follow the template below:
 *
 * @ConditionalTest
 * final class ConditionalClassExample {
 *    @SuppressWarnings("unused")
 *    public static boolean shouldRun(Context context, Class<?> testClass) {
 *       // Conditional logic
 *       return true;
 *    }
 * }
 *
 * Notice the Condition Class also uses the annotation. It is necessary to prevent ProGuard from
 * optimizing the code.
 *
 * If the shouldRun method returns true, the Conditionalized Test will be shown, otherwise it will
 * be hidden.
 *
 * The Conditionalized Test class may also be a Condition Class, implementing the logic itself.
 *
 * The {@link #value()} property indicates the Condition Class.
 * The {@link #instantiate()} property indicates whether an instance of the Condition Class should
 * be created or not.
 * The properties {@link #requiredFeatures()}, {@link #excludedFeatures()} and
 * {@link #applicableFeatures()} are supported to allow the same configuration approach used in the
 * AndroidManifest.xml file (meta-tags).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConditionalTest {
    public static final String CONDITION_CLASS_METHOD_NAME = "shouldRun";

    /**
     * The Condition Class.
     * It defaults to empty string, which means the Conditionalized Test class is also the Condition
     * Class and should implement the shouldRun method itself.
     * If a class name without dots is provided, the Conditional Class has to be in the same package
     * as the Conditionalized Test class. Otherwise, it is expected to be the full qualified class
     * name of the Condition Class.
     *
     * If specified, {@link #requiredFeatures()}, {@link #excludedFeatures()} and
     * {@link #applicableFeatures()} properties must not be present.
     *
     * @return The Condition Class name.
     */
    String value() default "";

    /**
     * Flag indicating whether the Condition Class should be instantiated or not.
     * It defaults to false, so no instance will be created. In this case, the shouldRun method must
     * be static. If set to true, the method may not me static.
     *
     * If specified, {@link #requiredFeatures()}, {@link #excludedFeatures()} and
     * {@link #applicableFeatures()} properties must not be present.
     *
     * @return True if the Condition Class must be instantiated.
     */
    boolean instantiate() default false;

    /**
     * Indicate what features are required to run the test. If the device does not have all of the
     * required features then it will not appear in the test list. Use a colon (:) to specify
     * multiple required features.
     * If specified, {@link #value()} and {@link #instantiate()} properties must not be present.
     */
    String requiredFeatures() default "";

    /**
     * Indicate features such that, if any present, the test gets excluded from being shown. If the
     * device has any of the excluded features then the test will not appear in the test list. Use a
     * colon (:) to specify multiple features to exclude for the test. Note that the colon means "or"
     * in this case.
     * If specified, {@link #value()} and {@link #instantiate()} properties must not be present.
     */
    String excludedFeatures() default "";

    /**
     * Indicate features such that, if any present, the test is applicable to run. If the device has
     * any of the applicable features then the test will appear in the test list. Use a colon (:) to
     * specify multiple features.
     * If specified, {@link #value()} and {@link #instantiate()} properties must not be present.
     */
    String applicableFeatures() default "";
}
