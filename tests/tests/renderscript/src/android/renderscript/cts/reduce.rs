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

#include "shared.rsh"

float negInf, posInf;

/////////////////////////////////////////////////////////////////////////

#pragma rs reduce(addint) \
  accumulator(aiAccum)

static void aiAccum(int *accum, int val) { *accum += val; }

/////////////////////////////////////////////////////////////////////////

#pragma rs reduce(dp) \
  accumulator(dpAccum) combiner(dpSum)

static void dpAccum(float *accum, float in1, float in2) {
  *accum += in1*in2;
}

// combiner function
static void dpSum(float *accum, const float *val) { *accum += *val; }

/////////////////////////////////////////////////////////////////////////

#pragma rs reduce(findMinAndMax) \
  initializer(fMMInit) accumulator(fMMAccumulator) \
  combiner(fMMCombiner) outconverter(fMMOutConverter)

typedef struct {
  float val;
  int idx;
} IndexedVal;

typedef struct {
  IndexedVal min, max;
} MinAndMax;

static void fMMInit(MinAndMax *accum) {
  accum->min.val = posInf;
  accum->min.idx = -1;
  accum->max.val = negInf;
  accum->max.idx = -1;
}

static void fMMAccumulator(MinAndMax *accum, float in, int x) {
  IndexedVal me;
  me.val = in;
  me.idx = x;

  if (me.val < accum->min.val)
    accum->min = me;
  if (me.val > accum->max.val)
    accum->max = me;
}

static void fMMCombiner(MinAndMax *accum,
                        const MinAndMax *val) {
  if (val->min.val < accum->min.val)
    accum->min = val->min;
  if (val->max.val > accum->max.val)
    accum->max = val->max;
}

static void fMMOutConverter(int2 *result,
                            const MinAndMax *val) {
  result->x = val->min.idx;
  result->y = val->max.idx;
}

/////////////////////////////////////////////////////////////////////////

#pragma rs reduce(fz) \
  initializer(fzInit) \
  accumulator(fzAccum) combiner(fzCombine)

static void fzInit(int *accumIdx) { *accumIdx = -1; }

static void fzAccum(int *accumIdx,
                    int inVal, int x /* special arg */) {
  if (inVal==0) *accumIdx = x;
}

static void fzCombine(int *accumIdx, const int *accumIdx2) {
  if (*accumIdx2 >= 0) *accumIdx = *accumIdx2;
}

/////////////////////////////////////////////////////////////////////////

#pragma rs reduce(fz2) \
  initializer(fz2Init) \
  accumulator(fz2Accum) combiner(fz2Combine)

static void fz2Init(int2 *accum) { accum->x = accum->y = -1; }

static void fz2Accum(int2 *accum,
                     int inVal,
                     int x /* special arg */,
                     int y /* special arg */) {
  if (inVal==0) {
    accum->x = x;
    accum->y = y;
  }
}

static void fz2Combine(int2 *accum, const int2 *accum2) {
  if (accum2->x >= 0) *accum = *accum2;
}

/////////////////////////////////////////////////////////////////////////

#pragma rs reduce(fz3) \
  initializer(fz3Init) \
  accumulator(fz3Accum) combiner(fz3Combine)

static void fz3Init(int3 *accum) { accum->x = accum->y = accum->z = -1; }

static void fz3Accum(int3 *accum,
                     int inVal,
                     int x /* special arg */,
                     int y /* special arg */,
                     int z /* special arg */) {
  if (inVal==0) {
    accum->x = x;
    accum->y = y;
    accum->z = z;
  }
}

static void fz3Combine(int3 *accum, const int3 *accum2) {
  if (accum->x >= 0) *accum = *accum2;
}

/////////////////////////////////////////////////////////////////////////

#pragma rs reduce(histogram) \
  accumulator(hsgAccum) combiner(hsgCombine)

#define BUCKETS 256
typedef uint32_t Histogram[BUCKETS];

static void hsgAccum(Histogram *h, uchar in) { ++(*h)[in]; }

static void hsgCombine(Histogram *accum, const Histogram *addend) {
  for (int i = 0; i < BUCKETS; ++i)
    (*accum)[i] += (*addend)[i];
}

#pragma rs reduce(mode) \
  accumulator(hsgAccum) combiner(hsgCombine) \
  outconverter(modeOutConvert)

static void modeOutConvert(int2 *result, const Histogram *h) {
  uint32_t mode = 0;
  for (int i = 1; i < BUCKETS; ++i)
    if ((*h)[i] > (*h)[mode]) mode = i;
  result->x = mode;
  result->y = (*h)[mode];
}

/////////////////////////////////////////////////////////////////////////

// Simple test case where there are two inputs
#pragma rs reduce(sumxor) accumulator(sxAccum) combiner(sxCombine)

static void sxAccum(int *accum, int inVal1, int inVal2) { *accum += (inVal1 ^ inVal2); }

static void sxCombine(int *accum, const int *accum2) { *accum += *accum2; }

/////////////////////////////////////////////////////////////////////////

// Test case where inputs are of different types
#pragma rs reduce(sillysum) accumulator(ssAccum) combiner(ssCombine)

static void ssAccum(long *accum, char c, float f, int3 i3) {
  *accum += ((((c + (long)ceil(log(f))) + i3.x) + i3.y) + i3.z);
}

static void ssCombine(long *accum, const long *accum2) { *accum += *accum2; }

/////////////////////////////////////////////////////////////////////////

// Test out-of-range result.
// We don't care about the input at all.
// We use these globals to configure the generation of the result.
ulong oorrGoodResult;     // the value of a good result
ulong oorrBadResultHalf;  // half the value of a bad result
                          //   ("half" because Java can only set the global from long not from ulong)
int   oorrBadPos;         // position of bad result

#define oorrBadResult (2*oorrBadResultHalf)

static void oorrAccum(int *accum, int val) { }

#pragma rs reduce(oorrSca) accumulator(oorrAccum) outconverter(oorrScaOut)
static void oorrScaOut(ulong *out, const int *accum) {
  *out = (oorrBadPos ? oorrGoodResult : oorrBadResult);
}

#pragma rs reduce(oorrVec4) accumulator(oorrAccum) outconverter(oorrVec4Out)
static void oorrVec4Out(ulong4 *out, const int *accum) {
  out->x = (oorrBadPos==0 ? oorrBadResult : oorrGoodResult);
  out->y = (oorrBadPos==1 ? oorrBadResult : oorrGoodResult);
  out->z = (oorrBadPos==2 ? oorrBadResult : oorrGoodResult);
  out->w = (oorrBadPos==3 ? oorrBadResult : oorrGoodResult);
}

#pragma rs reduce(oorrArr9) accumulator(oorrAccum) outconverter(oorrArr9Out)
typedef ulong Arr9[9];
static void oorrArr9Out(Arr9 *out, const int *accum) {
  for (int i = 0; i < 9; ++i)
    (*out)[i] = (i == oorrBadPos ? oorrBadResult : oorrGoodResult);
}

#pragma rs reduce(oorrArr9Vec4) accumulator(oorrAccum) outconverter(oorrArr9Vec4Out)
typedef ulong4 Arr9Vec4[9];
static void oorrArr9Vec4Out(Arr9Vec4 *out, const int *accum) {
  const int badIdx = (oorrBadPos >= 0 ? oorrBadPos / 4: -1);
  const int badComp = (oorrBadPos >= 0 ? oorrBadPos % 4: -1);
  for (int i = 0; i < 9; ++i) {
    (*out)[i].x = ((i==badIdx) && (0==badComp)) ? oorrBadResult : oorrGoodResult;
    (*out)[i].y = ((i==badIdx) && (1==badComp)) ? oorrBadResult : oorrGoodResult;
    (*out)[i].z = ((i==badIdx) && (2==badComp)) ? oorrBadResult : oorrGoodResult;
    (*out)[i].w = ((i==badIdx) && (3==badComp)) ? oorrBadResult : oorrGoodResult;
  }
}
