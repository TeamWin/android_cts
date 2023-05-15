This directory contains the source code for the test jar required for
ZipPathValidatorTest#
loadingApksWillNotCallZipPathValidator_changeEnabledOrDisabled.

The Android build system doesn't support dynamically producing
resources in any practical way. To update the resource, use the script
build.sh in this directory, which copies the resulting file into the
test resources directory.
