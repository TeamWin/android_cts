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

package android.renderscript.cts;

import android.renderscript.cts.Target;

public class FloatyUnitTest extends RSBaseCompute {
    static float subnormalFloat = 10000 * Float.MIN_VALUE;
    static float normalFloat1 = 1.7833920e+16f;
    static float normalFloat2 = -1.9905756e-16f;

    static double subnormalDouble = 10000 * Double.MIN_VALUE;
    static double normalDouble = 1.7833920e+16;

    // Fail if Floaty f doesn't accept value
    private void shouldAccept(Target.Floaty f, double value) {
        if (!f.couldBe(value)) {
            StringBuilder message = new StringBuilder();
            message.append("Floaty: ");
            appendVariableToMessage(message, f);
            message.append("\n");
            message.append("Value: ");
            appendVariableToMessage(message, (float) value);
            message.append("\n");
            assertTrue("Floaty incorrectly doesn't accept value:\n" + message.toString(), false);
        }
    }

    // Fail if Floaty f accepts value
    private void shouldNotAccept(Target.Floaty f, double value) {
        if (f.couldBe(value)) {
            StringBuilder message = new StringBuilder();
            message.append("Floaty: ");
            appendVariableToMessage(message, f);
            message.append("\n");
            message.append("Value: ");
            appendVariableToMessage(message, (float) value);
            message.append("\n");
            assertTrue("Floaty incorrectly accepts value:\n" + message.toString(), false);
        }
    }

    // Test Target that accepts precise 1ulp error for floating values.
    public void testFloat1Ulp() {
        Target t = new Target(Target.FunctionType.NORMAL, Target.ReturnType.FLOAT, false);
        t.setPrecision(1, 1);

        Target.Floaty subnormalFloaty = t.new32(subnormalFloat);
        Target.Floaty normalFloaty = t.new32(normalFloat1);

        // for subnormal
        shouldAccept(subnormalFloaty, (double) subnormalFloat);
        shouldAccept(subnormalFloaty, (double) subnormalFloat + Math.ulp(subnormalFloat));
        shouldAccept(subnormalFloaty, (double) subnormalFloat - Math.ulp(subnormalFloat));
        shouldNotAccept(subnormalFloaty, (double) subnormalFloat + 2 * Math.ulp(subnormalFloat));
        shouldNotAccept(subnormalFloaty, (double) subnormalFloat - 2 * Math.ulp(subnormalFloat));
        shouldNotAccept(subnormalFloaty, (double) normalFloat1);

        // for normalFloaty
        shouldAccept(normalFloaty, (double) normalFloat1);
        shouldAccept(normalFloaty, (double) normalFloat1 + Math.ulp(normalFloat1));
        shouldAccept(normalFloaty, (double) normalFloat1 - Math.ulp(normalFloat1));
        shouldNotAccept(normalFloaty, (double) normalFloat1 + 2 * Math.ulp(normalFloat1));
        shouldNotAccept(normalFloaty, (double) normalFloat1 - 2 * Math.ulp(normalFloat1));
        shouldNotAccept(normalFloaty, (double) subnormalFloat);
    }

    // Test Target that accepts precise 8192ulp error for floating values.
    public void testFloat8192Ulp() {
        Target t = new Target(Target.FunctionType.NORMAL, Target.ReturnType.FLOAT, false);
        t.setPrecision(8192, 8192);

        Target.Floaty subnormalFloaty = t.new32(subnormalFloat);
        Target.Floaty normalFloaty = t.new32(normalFloat2);

        // for subnormalFloaty
        shouldAccept(subnormalFloaty, (double) subnormalFloat);
        shouldAccept(subnormalFloaty, (double) subnormalFloat + 8192 * Math.ulp(subnormalFloat));
        shouldAccept(subnormalFloaty, (double) subnormalFloat - 8192 * Math.ulp(subnormalFloat));
        shouldNotAccept(subnormalFloaty, (double) subnormalFloat + 8193 * Math.ulp(subnormalFloat));
        shouldNotAccept(subnormalFloaty, (double) subnormalFloat - 8193 * Math.ulp(subnormalFloat));
        shouldNotAccept(subnormalFloaty, (double) normalFloat1);

        // for normalFloaty
        shouldAccept(normalFloaty, (double) normalFloat2);
        shouldAccept(normalFloaty, (double) normalFloat2 + 8192 * Math.ulp(normalFloat2));
        shouldAccept(normalFloaty, (double) normalFloat2 - 8192 * Math.ulp(normalFloat2));
        shouldNotAccept(normalFloaty, (double) normalFloat2 + 8193 * Math.ulp(normalFloat2));
        shouldNotAccept(normalFloaty, (double) normalFloat2 - 8193 * Math.ulp(normalFloat2));
        shouldNotAccept(normalFloaty, (double) subnormalFloat);
    }

    // Test Target that accepts relaxed 1ulp error for floating values.
    public void testFloat1UlpRelaxed() {
        Target t = new Target(Target.FunctionType.NORMAL, Target.ReturnType.FLOAT, true);
        t.setPrecision(1, 1);

        Target.Floaty subnormalFloaty = t.new32(subnormalFloat);

        // for subnormal
        shouldAccept(subnormalFloaty, (double) subnormalFloat);
        // In relaxed mode, Floaty uses the smallest normal as the ULP if ULP is subnormal.
        shouldAccept(subnormalFloaty, (double) Float.MIN_NORMAL + Float.MIN_NORMAL);
        shouldAccept(subnormalFloaty, (double) 0.f - Float.MIN_NORMAL);
        shouldNotAccept(subnormalFloaty, (double) Float.MIN_NORMAL + 2 * Float.MIN_NORMAL);
        shouldNotAccept(subnormalFloaty, (double) 0.f - 2 * Float.MIN_NORMAL);
        shouldNotAccept(subnormalFloaty, (double) normalFloat1);
    }

    // Test Target that accepts relaxed 8192ulp error for floating values.
    public void testFloat8192UlpRelaxed() {
        Target t = new Target(Target.FunctionType.NORMAL, Target.ReturnType.FLOAT, true);
        t.setPrecision(8192, 8192);

        Target.Floaty subnormalFloaty = t.new32(subnormalFloat);

        // for subnormalFloaty
        shouldAccept(subnormalFloaty, (double) subnormalFloat);
        // In relaxed mode, Floaty uses the smallest normal as the ULP if ULP is subnormal.
        shouldAccept(subnormalFloaty, (double) Float.MIN_NORMAL + 8192 * Float.MIN_NORMAL);
        shouldAccept(subnormalFloaty, (double) 0.f - 8192 * Float.MIN_NORMAL);
        shouldNotAccept(subnormalFloaty, (double) Float.MIN_NORMAL + 8193 * Float.MIN_NORMAL);
        shouldNotAccept(subnormalFloaty, (double) 0.f - 8193 * Float.MIN_NORMAL);
        shouldNotAccept(subnormalFloaty, (double) normalFloat1);
    }

    // Test Target that accepts precise 1ulp error for double values.
    public void testDouble1Ulp() {
        Target t = new Target(Target.FunctionType.NORMAL, Target.ReturnType.DOUBLE, false);
        t.setPrecision(1, 1);

        Target.Floaty subnormalFloaty = t.new64(subnormalDouble);
        Target.Floaty normalFloaty = t.new64(normalDouble);

        // for subnormal
        shouldAccept(subnormalFloaty, subnormalDouble);
        shouldAccept(subnormalFloaty, subnormalDouble + Math.ulp(subnormalDouble));
        shouldAccept(subnormalFloaty, subnormalDouble - Math.ulp(subnormalDouble));
        shouldNotAccept(subnormalFloaty, subnormalDouble + 2 * Math.ulp(subnormalDouble));
        shouldNotAccept(subnormalFloaty, subnormalDouble - 2 * Math.ulp(subnormalDouble));
        shouldNotAccept(subnormalFloaty, normalDouble);

        // for normalFloaty
        shouldAccept(normalFloaty, normalDouble);
        shouldAccept(normalFloaty, normalDouble + Math.ulp(normalDouble));
        shouldAccept(normalFloaty, normalDouble - Math.ulp(normalDouble));
        shouldNotAccept(normalFloaty, subnormalDouble + 2 * Math.ulp(normalDouble));
        shouldNotAccept(normalFloaty, subnormalDouble - 2 * Math.ulp(normalDouble));
        shouldNotAccept(normalFloaty, subnormalDouble);
    }

    // Test Target that accepts precise 8192ulp error for double values.
    public void testDouble8192Ulp() {
        Target t = new Target(Target.FunctionType.NORMAL, Target.ReturnType.DOUBLE, false);
        t.setPrecision(8192, 8192);

        Target.Floaty subnormalFloaty = t.new64(subnormalDouble);
        Target.Floaty normalFloaty = t.new64(normalDouble);

        // for subnormal
        shouldAccept(subnormalFloaty, subnormalDouble);
        shouldAccept(subnormalFloaty, subnormalDouble + 8192 * Math.ulp(subnormalDouble));
        shouldAccept(subnormalFloaty, subnormalDouble - 8192 * Math.ulp(subnormalDouble));
        shouldNotAccept(subnormalFloaty, subnormalDouble + 8193 * Math.ulp(subnormalDouble));
        shouldNotAccept(subnormalFloaty, subnormalDouble - 8193 * Math.ulp(subnormalDouble));
        shouldNotAccept(subnormalFloaty, normalDouble);

        // for normalFloaty
        shouldAccept(normalFloaty, normalDouble);
        shouldAccept(normalFloaty, normalDouble + 8192 * Math.ulp(normalDouble));
        shouldAccept(normalFloaty, normalDouble - 8192 * Math.ulp(normalDouble));
        shouldNotAccept(normalFloaty, subnormalDouble + 8193 * Math.ulp(normalDouble));
        shouldNotAccept(normalFloaty, subnormalDouble - 8193 * Math.ulp(normalDouble));
        shouldNotAccept(normalFloaty, subnormalDouble);
    }

    // Test that range of allowed error is trimmed at the zero boundary.  This function tests both
    // float and double Targets.
    public void testRangeDoesNotAcrossZero() {
        Target t;
        Target.Floaty floaty;

        t = new Target(Target.FunctionType.NORMAL, Target.ReturnType.FLOAT, false);
        t.setPrecision(4, 4);

        floaty = t.new32(Float.MIN_VALUE);
        shouldAccept(floaty, (double) Float.MIN_VALUE);
        shouldAccept(floaty, (double) Float.MIN_VALUE + 4 * Float.MIN_VALUE);
        shouldAccept(floaty, (double) 0.f);
        shouldNotAccept(floaty, (double) 0.f - Float.MIN_VALUE);

        floaty = t.new32(-Float.MIN_VALUE);
        shouldAccept(floaty, (double) -Float.MIN_VALUE);
        shouldAccept(floaty, (double) -Float.MIN_VALUE - 4 * Float.MIN_VALUE);
        shouldAccept(floaty, (double) 0.f);
        shouldNotAccept(floaty, (double) 0.f + Float.MIN_VALUE);

        t = new Target(Target.FunctionType.NORMAL, Target.ReturnType.DOUBLE, false);
        t.setPrecision(4, 4);

        floaty = t.new64(Double.MIN_VALUE);
        shouldAccept(floaty, Double.MIN_VALUE);
        shouldAccept(floaty, Double.MIN_VALUE + 4 * Double.MIN_VALUE);
        shouldAccept(floaty, 0.f);
        shouldNotAccept(floaty, 0.f - Double.MIN_VALUE);

        floaty = t.new64(-Double.MIN_VALUE);
        shouldAccept(floaty, -Double.MIN_VALUE);
        shouldAccept(floaty, -Double.MIN_VALUE - 4 * Double.MIN_VALUE);
        shouldAccept(floaty, 0.f);
        shouldNotAccept(floaty, 0.f + Double.MIN_VALUE);
    }
}
