# CTS mediaediting tests
The tests are organized into following testcases

| TestCase | Description |
|------------------------|----------------------|
| TranscodeQualityTest   | Transcode input and validate output using ssim |
| VideoResolutionTest    | Transform resolution of input videos and validate output resolution |
| TransformReverseTransformIdentityTest | Test verify that quality shouldn't be reduced too much when scaling/resizing and then reversing the operation. |
| TransformHdrToSdrToneMapTest | Test transform HDR to SDR ToneMapping for given input and verify that ouput shouldn't have HDR profile. |
| TransformVideoAspectRatio | Test transform aspects ratio of input videos and validate the output resolution according to the requested aspect ratio. |


## List of tests and helper classes imported from [androidx.media3.transformer](https://github.com/androidx/media/tree/release/libraries/transformer when it was at commit https://github.com/androidx/media/commit/2ff5dab0039c44d767dc831fec92724254e5e0aa)and changes done in them.

### AndroidTestUtil.java
No Change.

### FileUtil.java
No Change.

### MssimCalculator.java
No change.

### SsimHelper.java
No change.

### TransformationTestResult.java
No change.

### TransformerAndroidTestRunner.java
TransformerAndroidTestRunner is using `decoderFactory` and `encodeFactory` to instantiates CodecNameForwardingCodecFactory which are the private members of Transformer class and so can't be used directly (need to apply java reflection method to access it). So, I have removed CodecNameForwardingCodecFactory reference from the file to keep minimal changes w.r.t the original file so that it can be easily reviewed/merged/updated in the future.

### TranscodeQualityTest.java
Parameterize `TranscodeQualityTest` and added more test vectors in it.


## List of new helper class and Tests added.

###MediaEditingUtil.java
It has paths for the input clips required for TranscodeQualityTest. Test now uses MediaPreparer to download zip file mentioned in DynamicConfig.xml and use input clips from sdcard.
Added util function to parse width, height and roationDegree from muxed output.

### VideoResolutionTest.java
Test transform resolution of input videos and validate the output resolution.

### TransformReverseTransformIdentityTest.java
Test verify that quality shouldn't be reduced too much when scaling/resizing and then reversing the operation.

### TransformHdrToSdrToneMapTest.java
Test transform HDR to SDR ToneMapping for given input and verify that ouput shouldn't have HDR profile.

### TransformVideoAspectRatio.java
Test transform aspects ratio of input videos and validate the output resolution according to the requested aspect ratio.
