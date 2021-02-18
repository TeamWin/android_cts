/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.nene.packages;

import static com.android.bedstead.nene.utils.ParserUtils.extractIndentedSections;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.bedstead.nene.exceptions.AdbParseException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for `adb dumpsys package` on Android O+.
 *
 * <p>This class is structured so that future changes in ADB output can be dealt with by extending
 * this class and overriding the appropriate section parsers.
 */
@RequiresApi(Build.VERSION_CODES.O)
public class AdbPackageParser26 implements AdbPackageParser {

    private static final int PACKAGE_LIST_BASE_INDENTATION = 2;

    private final Packages mPackages;

    AdbPackageParser26(Packages packages) {
        if (packages == null) {
            throw new NullPointerException();
        }
        mPackages = packages;
    }

    @Override
    public ParseResult parse(String dumpsysPackageOutput) throws AdbParseException {
        ParseResult parseResult = new ParseResult();
        parseResult.mFeatures = parseFeatures(dumpsysPackageOutput);
        parseResult.mPackages = parsePackages(dumpsysPackageOutput);
        return parseResult;
    }

    Set<String> parseFeatures(String dumpsysPackageOutput) throws AdbParseException {
        String featuresList = extractFeaturesList(dumpsysPackageOutput);
        Set<String> features = new HashSet<>();
        for (String featureLine : featuresList.split("\n")) {
            features.add(featureLine.trim());
        }
        return features;
    }

    String extractFeaturesList(String dumpsysPackageOutput) throws AdbParseException {
        try {
            return dumpsysPackageOutput.split("Features:\n", 2)[1].split("\n\n", 2)[0];
        } catch (IndexOutOfBoundsException e) {
            throw new AdbParseException("Error extracting features list", dumpsysPackageOutput, e);
        }
    }

    Map<String, Package> parsePackages(String dumpsysUsersOutput) throws AdbParseException {
        String packagesList = extractPackagesList(dumpsysUsersOutput);

        Set<String> packageStrings = extractPackageStrings(packagesList);
        Map<String, Package> packages = new HashMap<>();
        for (String packageString : packageStrings) {
            Package pkg = new Package(mPackages, parsePackage(packageString));
            packages.put(pkg.packageName(), pkg);
        }
        return packages;
    }

    String extractPackagesList(String dumpsysPackageOutput) throws AdbParseException {
        try {
            return dumpsysPackageOutput.split("\nPackages:\n", 2)[1].split("\n\n", 2)[0];
        } catch (IndexOutOfBoundsException e) {
            throw new AdbParseException("Error extracting packages list", dumpsysPackageOutput, e);
        }
    }

    Set<String> extractPackageStrings(String packagesList) throws AdbParseException {
        return extractIndentedSections(packagesList, PACKAGE_LIST_BASE_INDENTATION);
    }

    private static final Pattern USER_INSTALLED_PATTERN =
            Pattern.compile("User (\\d+):.*?installed=(\\w+)");

    Package.MutablePackage parsePackage(String packageString) throws AdbParseException {
        try {
            String packageName = packageString.split("\\[", 2)[1].split("]", 2)[0];
            Package.MutablePackage pkg = new Package.MutablePackage();
            pkg.mPackageName = packageName;
            pkg.mInstalledOnUsers = new HashSet<>();


            Matcher userInstalledMatcher = USER_INSTALLED_PATTERN.matcher(packageString);
            while (userInstalledMatcher.find()) {
                int userId = Integer.parseInt(userInstalledMatcher.group(1));
                boolean isInstalled = Boolean.parseBoolean(userInstalledMatcher.group(2));

                if (isInstalled) {
                    pkg.mInstalledOnUsers.add(mPackages.mTestApis.users().find(userId));
                }
            }

            return pkg;
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            throw new AdbParseException("Error parsing package", packageString, e);
        }
    }
}
