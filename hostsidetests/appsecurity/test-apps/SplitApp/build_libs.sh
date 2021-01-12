#!/bin/bash
#
# Copyright (C) 2014 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Please change NDK_BUILD to point to the appropriate ndk-build in NDK. It's recommended to
# use the NDK with maximum backward compatibility, such as the NDK bundle in Android SDK.
NDK_BUILD="$HOME/Android/android-ndk-r16b/ndk-build"

function generateCopyRightComment {
  local year=$1

  copyrightInMk=$(cat <<COPYRIGHT_COMMENT
# Copyright (C) ${year} The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Automatically generated file from build_libs.sh.
# DO NOT MODIFY THIS FILE.

COPYRIGHT_COMMENT
)
  echo "${copyrightInMk}"
}

function generateLibsAndroidMk {
  local targetFile=$1
  local copyrightInMk=$(generateCopyRightComment "2015")
(
cat <<LIBS_ANDROID_MK
${copyrightInMk}
include \$(call all-subdir-makefiles)
LIBS_ANDROID_MK
) > "${targetFile}"

}

function generateAndroidManifest {
  local targetFile=$1
  local arch=$2
(
cat <<ANDROIDMANIFEST
<?xml version="1.0" encoding="utf-8"?>
<!-- Automatically generated file from build_libs.sh. -->
<!-- DO NOT MODIFY THIS FILE. -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="com.android.cts.splitapp"
        split="lib_${arch}">
    <application android:hasCode="false" />
</manifest>
ANDROIDMANIFEST
) > "${targetFile}"

}

function generateAndroidMk {
  local targetFile="$1"
  local arch="$2"
  local copyrightInMk=$(generateCopyRightComment "2014")
(
cat <<LIBS_ARCH_ANDROID_MK
#
${copyrightInMk}
LOCAL_PATH := \$(call my-dir)

include \$(CLEAR_VARS)

LOCAL_PACKAGE_NAME := CtsSplitApp_${arch}
LOCAL_SDK_VERSION := current

LOCAL_JAVA_RESOURCE_DIRS := raw

# tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts general-tests

LOCAL_CERTIFICATE := cts/hostsidetests/appsecurity/certs/cts-testkey1
LOCAL_AAPT_FLAGS := --version-code 100 --replace-version

include \$(BUILD_CTS_SUPPORT_PACKAGE)
LIBS_ARCH_ANDROID_MK
) > "${targetFile}"
}

# Go build everything
rm -rf libs
cd jni/
$NDK_BUILD clean
$NDK_BUILD
cd ../

for arch in `ls libs/`;
do
    (
    mkdir -p tmp/$arch/raw/lib/$arch/
    mv libs/$arch/* tmp/$arch/raw/lib/$arch/

    generateAndroidManifest "tmp/$arch/AndroidManifest.xml" "${arch//[^a-zA-Z0-9_]/_}"

    generateAndroidMk "tmp/$arch/Android.mk" "$arch"
    )
done

generateLibsAndroidMk "tmp/Android.mk"

rm -rf libs
rm -rf obj

mv tmp libs
