## Media CTS Tests
## Test files used in the tests
The test files used by the test suite are available at
[link](https://storage.googleapis.com/android_media/cts/tests/tests/media/CtsMediaTestCases-1.4.zip)
and this is downloaded automatically while running tests.

Manual installation of these can be done using copy_media.sh script in this directory.


## Troubleshooting

#### Too slow / no progress in the first run
Zip containing the media files is quite large (nearly 1 GB) and first execution of the test
(after each time the test is updated to download a different zip file) takes
considerable amount of time (30 minutes or more) to download and push the media files.


#### File not found in /sdcard/test/CtsMediaTestCases-* failures
If the device contains an incomplete directory (from previous incomplete execution of the tests,
Ctrl-C during earlier tests etc),
the test framework doesn't push the remaining files to device.
This leads to tests failing with file not found errors.

Solution in such cases is to remove the /sdcard/test/CtsMediaTestCases-* folder on
the device and executing the atest command again, or running ./tests/tests/media/copy_media.sh to
manually download and copy the test files to the device before running the tests.
