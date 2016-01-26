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

import android.renderscript.*;
import java.lang.Float;
import java.util.Random;

public class ReduceTest extends RSBaseCompute {
    private ScriptC_reduce mScript;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mScript = new ScriptC_reduce(mRS);
        mScript.set_negInf(Float.NEGATIVE_INFINITY);
        mScript.set_posInf(Float.POSITIVE_INFINITY);
    }

    ///////////////////////////////////////////////////////////////////

    private void assertEquals(Int2 javaRslt, Int2 rsRslt) {
        assertEquals("x", javaRslt.x, rsRslt.x);
        assertEquals("y", javaRslt.y, rsRslt.y);
    }

    private byte[] createInputArrayByte(int len, int seed) {
        byte[] array = new byte[len];
        (new Random(seed)).nextBytes(array);
        return array;
    }

    private int[] createInputArrayInt(int len, int seed) {
        Random rand = new Random(seed);
        int[] array = new int[len];
        for (int i = 0; i < len; ++i)
            array[i] = rand.nextInt();
        return array;
    }

    private int[] createInputArrayInt(int len, int seed, int eltRange) {
        Random rand = new Random(seed);
        int[] array = new int[len];
        for (int i = 0; i < len; ++i)
            array[i] = rand.nextInt(eltRange);
        return array;
    }

    private float[] createInputArrayFloat(int len, int seed) {
        Random rand = new Random(seed);
        float[] array = new float[len];
        for (int i = 0; i < len; ++i)
            array[i] = rand.nextFloat();
        return array;
    }

    ///////////////////////////////////////////////////////////////////

    private int addint(int[] input) {
        int rslt = 0;
        for (int idx = 0; idx < input.length; ++idx)
            rslt += input[idx];
        return rslt;
    }

    public void testAddInt1D() {
        final int[] input = createInputArrayInt(100000, 0, 1 << 13);

        final int javaRslt = addint(input);
        final int rsRslt = mScript.reduce_addint(input).get();

        assertEquals(javaRslt, rsRslt);
    }

    public void testAddInt2D() {
        final int dimX = 450, dimY = 225;

        final int[] inputArray = createInputArrayInt(dimX * dimY, 1, 1 << 13);
        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32(mRS));
        typeBuilder.setX(dimX).setY(dimY);
        Allocation inputAllocation = Allocation.createTyped(mRS, typeBuilder.create());
        inputAllocation.copy2DRangeFrom(0, 0, dimX, dimY, inputArray);

        final int javaRslt = addint(inputArray);
        final int rsRslt = mScript.reduce_addint(inputAllocation).get();

        assertEquals(javaRslt, rsRslt);
    }

    ///////////////////////////////////////////////////////////////////

    private Int2 findMinAndMax(float[] input) {
        float minVal = Float.POSITIVE_INFINITY;
        int minIdx = -1;
        float maxVal = Float.NEGATIVE_INFINITY;
        int maxIdx = -1;

        for (int idx = 0; idx < input.length; ++idx) {
            if (input[idx] < minVal) {
                minVal = input[idx];
                minIdx = idx;
            }
            if (input[idx] > maxVal) {
                maxVal = input[idx];
                maxIdx = idx;
            }
        }

        return new Int2(minIdx, maxIdx);
    }

    public void testFindMinAndMax() {
        final float[] input = createInputArrayFloat(100000, 4);

        final Int2 javaRslt = findMinAndMax(input);
        final Int2 rsRslt = mScript.reduce_findMinAndMax(input).get();

        assertEquals(javaRslt, rsRslt);
    }

    ///////////////////////////////////////////////////////////////////

    public void testFz() {
        final int inputLen = 100000;
        int[] input = createInputArrayInt(inputLen, 5);
        // just in case we got unlucky
        input[(new Random(6)).nextInt(inputLen)] = 0;

        final int rsRslt = mScript.reduce_fz(input).get();

        assertEquals("input[" + rsRslt + "]", 0, input[rsRslt]);
    }

    ///////////////////////////////////////////////////////////////////

    public void testFz2() {
        final int dimX = 225, dimY = 450;
        final int inputLen = dimX * dimY;

        int[] inputArray = createInputArrayInt(inputLen, 7);
        // just in case we got unlucky
        inputArray[(new Random(8)).nextInt(inputLen)] = 0;

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32(mRS));
        typeBuilder.setX(dimX).setY(dimY);
        Allocation inputAllocation = Allocation.createTyped(mRS, typeBuilder.create());
        inputAllocation.copy2DRangeFrom(0, 0, dimX, dimY, inputArray);

        final Int2 rsRslt = mScript.reduce_fz2(inputAllocation).get();

        final int cellVal = inputArray[rsRslt.x + dimX * rsRslt.y];

        assertEquals("input[" + rsRslt.x + ", " + rsRslt.y + "]", 0, cellVal);
    }

    ///////////////////////////////////////////////////////////////////

    public void testFz3() {
        final int dimX = 59, dimY = 48, dimZ = 37;
        final int inputLen = dimX * dimY * dimZ;

        int[] inputArray = createInputArrayInt(inputLen, 9);
        // just in case we got unlucky
        inputArray[(new Random(10)).nextInt(inputLen)] = 0;

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32(mRS));
        typeBuilder.setX(dimX).setY(dimY).setZ(dimZ);
        Allocation inputAllocation = Allocation.createTyped(mRS, typeBuilder.create());
        inputAllocation.copy3DRangeFrom(0, 0, 0, dimX, dimY, dimZ, inputArray);

        final Int3 rsRslt = mScript.reduce_fz3(inputAllocation).get();

        final int cellVal = inputArray[rsRslt.x + dimX * rsRslt.y + dimX * dimY * rsRslt.z];

        assertEquals("input[" + rsRslt.x + ", " + rsRslt.y + ", " + rsRslt.z + "]", 0, cellVal);
    }

    ///////////////////////////////////////////////////////////////////

    private static final int histogramBucketCount = 256;

    private long[] histogram(final byte[] inputArray) {
        Allocation inputAllocation = Allocation.createSized(mRS, Element.U8(mRS), inputArray.length);
        inputAllocation.copyFrom(inputArray);

        Allocation outputAllocation = Allocation.createSized(mRS, Element.U32(mRS), histogramBucketCount);

        ScriptIntrinsicHistogram scriptHsg = ScriptIntrinsicHistogram.create(mRS, Element.U8(mRS));
        scriptHsg.setOutput(outputAllocation);
        scriptHsg.forEach(inputAllocation);

        int[] outputArrayMistyped = new int[histogramBucketCount];
        outputAllocation.copyTo(outputArrayMistyped);

        long[] outputArray = new long[histogramBucketCount];
        for (int i = 0; i < histogramBucketCount; ++i)
            outputArray[i] = outputArrayMistyped[i] & (long)0xffffffff;
        return outputArray;
    }

    public void testHistogram() {
        final byte[] inputArray = createInputArrayByte(100000, 11);

        final long[] javaRslt = histogram(inputArray);
        assertEquals("javaRslt unexpected length", histogramBucketCount, javaRslt.length);
        final long[] rsRslt = mScript.reduce_histogram(inputArray).get();
        assertEquals("rsRslt unexpected length", histogramBucketCount, rsRslt.length);

        for (int i = 0; i < histogramBucketCount; ++i) {
            assertEquals("histogram[" + i + "]", javaRslt[i], rsRslt[i]);
        }
    }

    //-----------------------------------------------------------------

    private Int2 mode(final byte[] inputArray) {
        long[] hsg = histogram(inputArray);

        int modeIdx = 0;
        for (int i = 1; i < hsg.length; ++i)
            if (hsg[i] > hsg[modeIdx]) modeIdx =i;
        return new Int2(modeIdx, (int)hsg[modeIdx]);
    }

    public void testMode() {
        final byte[] inputArray = createInputArrayByte(100000, 12);

        final Int2 javaRslt = mode(inputArray);
        final Int2 rsRslt = mScript.reduce_mode(inputArray).get();

        assertEquals(javaRslt, rsRslt);
    }
}
