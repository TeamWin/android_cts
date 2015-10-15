#!/bin/bash

# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Helper script for running unit tests for compatibility libraries

checkFile() {
    if [ ! -f "$1" ]; then
        echo "Unable to locate $1"
        exit
    fi;
}

# check if in Android build env
if [ ! -z ${ANDROID_BUILD_TOP} ]; then
    HOST=`uname`
    if [ "$HOST" == "Linux" ]; then
        OS="linux-x86"
    elif [ "$HOST" == "Darwin" ]; then
        OS="darwin-x86"
    else
        echo "Unrecognized OS"
        exit
    fi;
fi;

############### Build the tests ###############
make compatibility-common-util-tests compatibility-host-util-tests compatibility-device-util-tests compatibility-tradefed-tests cts-tradefed-tests_v2 compatibility-device-info-tests compatibility-manifest-generator-tests compatibility-host-media-preconditions-tests CompatibilityTestApp -j32

############### Run the device side tests ###############
JAR_DIR=${ANDROID_HOST_OUT}/framework
JARS="
    ddmlib-prebuilt\
    hosttestlib\
    tradefed-prebuilt"
JAR_PATH=
for JAR in $JARS; do
    checkFile ${JAR_DIR}/${JAR}.jar
    JAR_PATH=${JAR_PATH}:${JAR_DIR}/${JAR}.jar
done

APK=${ANDROID_PRODUCT_OUT}/data/app/CompatibilityTestApp/CompatibilityTestApp.apk
checkFile ${APK}

TF_CONSOLE=com.android.tradefed.command.Console
COMMON_PACKAGE=com.android.compatibility.common
RUNNER=android.support.test.runner.AndroidJUnitRunner
adb install -r -g ${APK}
java $RDBG_FLAG -cp ${JAR_PATH} ${TF_CONSOLE} run singleCommand instrument --package ${COMMON_PACKAGE} --runner ${RUNNER}
adb uninstall ${COMMON_PACKAGE}

############### Run the host side tests ###############
JARS="
    compatibility-common-util-hostsidelib\
    compatibility-common-util-tests\
    compatibility-host-util\
    compatibility-host-util-tests\
    compatibility-mock-tradefed\
    compatibility-tradefed-tests\
    ddmlib-prebuilt\
    hosttestlib\
    tradefed-prebuilt"
JAR_PATH=
for JAR in $JARS; do
    checkFile ${JAR_DIR}/${JAR}.jar
    JAR_PATH=${JAR_PATH}:${JAR_DIR}/${JAR}.jar
done

TEST_CLASSES="
    com.android.compatibility.common.tradefed.UnitTests\
    com.android.compatibility.common.util.HostUnitTests\
    com.android.compatibility.common.util.UnitTests"

for CLASS in ${TEST_CLASSES}; do
    java $RDBG_FLAG -cp ${JAR_PATH} ${TF_CONSOLE} run singleCommand host -n --class ${CLASS} "$@"
done

############### Run the cts tests ###############
JARS="
    compatibility-common-util-hostsidelib\
    compatibility-host-util\
    cts-tradefed-tests_v2\
    cts-tradefed_v2\
    ddmlib-prebuilt\
    hosttestlib\
    tradefed-prebuilt"
JAR_PATH=
for JAR in $JARS; do
    checkFile ${JAR_DIR}/${JAR}.jar
    JAR_PATH=${JAR_PATH}:${JAR_DIR}/${JAR}.jar
done

TEST_CLASSES="
    com.android.compatibility.tradefed.CtsTradefedTest"

for CLASS in ${TEST_CLASSES}; do
    java $RDBG_FLAG -cp ${JAR_PATH} ${TF_CONSOLE} run singleCommand host -n --class ${CLASS} "$@"
done

############### Run the manifest generator tests ###############
JARS="
    compatibility-manifest-generator\
    compatibility-manifest-generator-tests\
    ddmlib-prebuilt\
    hosttestlib\
    tradefed-prebuilt"
JAR_PATH=
for JAR in $JARS; do
    checkFile ${JAR_DIR}/${JAR}.jar
    JAR_PATH=${JAR_PATH}:${JAR_DIR}/${JAR}.jar
done

TEST_CLASSES="
    com.android.compatibility.common.generator.ManifestGeneratorTest"

for CLASS in ${TEST_CLASSES}; do
    java $RDBG_FLAG -cp ${JAR_PATH} ${TF_CONSOLE} run singleCommand host -n --class ${CLASS} "$@"
done

############### Run precondition tests ###############
JARS="
    tradefed-prebuilt\
    compatibility-host-util\
    cts-tradefed_v2\
    compatibility-host-media-preconditions\
    compatibility-host-media-preconditions-tests"
JAR_PATH=
for JAR in $JARS; do
    checkFile ${JAR_DIR}/${JAR}.jar
    JAR_PATH=${JAR_PATH}:${JAR_DIR}/${JAR}.jar
done

TEST_CLASSES="
    android.mediastress.cts.preconditions.MediaPreparerTest"

for CLASS in ${TEST_CLASSES}; do
    java $RDBG_FLAG -cp ${JAR_PATH} ${TF_CONSOLE} run singleCommand host -n --class ${CLASS} "$@"
done
