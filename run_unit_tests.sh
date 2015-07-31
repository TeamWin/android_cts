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

JAR_DIR=${ANDROID_HOST_OUT}/framework
JARS="
    compatibility-common-util-hostsidelib\
    compatibility-common-util-tests\
    compatibility-host-util\
    compatibility-host-util-tests\
    compatibility-tradefed-tests\
    compatibility-tradefed\
    cts-tradefed-tests_v2\
    cts-tradefed_v2\
    ddmlib-prebuilt\
    hosttestlib\
    tradefed-prebuilt"

for JAR in $JARS; do
    checkFile ${JAR_DIR}/${JAR}.jar
    JAR_PATH=${JAR_PATH}:${JAR_DIR}/${JAR}.jar
done

APK=${ANDROID_PRODUCT_OUT}/data/app/CompatibilityTestApp/CompatibilityTestApp.apk
checkFile ${APK}

TF_CONSOLE=com.android.tradefed.command.Console
COMMON_PACKAGE=com.android.compatibility.common
RUNNER=android.support.test.runner.AndroidJUnitRunner
adb install -r ${APK}
java $RDBG_FLAG -cp ${JAR_PATH} ${TF_CONSOLE} run singleCommand instrument --package ${COMMON_PACKAGE} --runner ${RUNNER}
adb uninstall ${COMMON_PACKAGE}

TEST_CLASSES="
    com.android.compatibility.common.tradefed.build.CompatibilityBuildInfoTest\
    com.android.compatibility.common.tradefed.build.CompatibilityBuildProviderTest\
    com.android.compatibility.common.tradefed.command.CompatibilityConsoleTest\
    com.android.compatibility.common.tradefed.result.ResultReporterTest\
    com.android.compatibility.common.tradefed.testtype.CompatibilityTestTest\
    com.android.compatibility.common.tradefed.testtype.ModuleDefTest\
    com.android.compatibility.common.tradefed.testtype.ModuleRepoTest\
    com.android.compatibility.common.util.AbiUtilsTest\
    com.android.compatibility.common.util.CaseResultTest\
    com.android.compatibility.common.util.DynamicConfigTest\
    com.android.compatibility.common.util.MetricsStoreTest\
    com.android.compatibility.common.util.MetricsXmlSerializerTest\
    com.android.compatibility.common.util.ModuleResultTest\
    com.android.compatibility.common.util.ReportLogTest\
    com.android.compatibility.common.util.TestFilterTest\
    com.android.compatibility.common.util.TestResultTest\
    com.android.compatibility.common.util.XmlResultHandlerTest\
    com.android.compatibility.tradefed.CtsTradefedTest"

for CLASS in ${TEST_CLASSES}; do
    java $RDBG_FLAG -cp ${JAR_PATH} ${TF_CONSOLE} run singleCommand host -n --class ${CLASS} "$@"
done
