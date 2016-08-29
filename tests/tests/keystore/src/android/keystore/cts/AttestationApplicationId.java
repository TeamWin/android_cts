/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package android.keystore.cts;

import com.android.org.bouncycastle.asn1.ASN1Encodable;
import com.android.org.bouncycastle.asn1.ASN1Sequence;
import com.android.org.bouncycastle.asn1.ASN1Set;

import java.security.cert.CertificateParsingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;

public class AttestationApplicationId implements java.lang.Comparable<AttestationApplicationId> {
    private static final int PACKAGE_INFOS_INDEX = 0;

    private final List<AttestationPackageInfo> packageInfos;

    public AttestationApplicationId(Context context)
            throws NoSuchAlgorithmException, NameNotFoundException {
        PackageManager pm = context.getPackageManager();
        int uid = context.getApplicationInfo().uid;
        String[] packageNames = pm.getPackagesForUid(uid);
        if (packageNames == null) {
            throw new NameNotFoundException("No names found for uid");
        }
        packageInfos = new ArrayList<AttestationPackageInfo>();
        for (String packageName : packageNames) {
            // get the package info for the given package name including
            // the signatures
            PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            // compute the sha256 digests of the signature blobs
            List<byte[]> sigDigests = new ArrayList<byte[]>();
            for (Signature signature : packageInfo.signatures) {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                sigDigests.add(sha256.digest(signature.toByteArray()));
            }
            packageInfos.add(
                    new AttestationPackageInfo(packageName, packageInfo.versionCode, sigDigests));
        }
        // The infos must be sorted, the implementation of Comparable relies on it.
        packageInfos.sort(null);
    }

    public AttestationApplicationId(ASN1Encodable asn1Encodable)
            throws CertificateParsingException {
        if (!(asn1Encodable instanceof ASN1Sequence)) {
            throw new CertificateParsingException(
                    "Expected sequence for AttestationApplicationId, found "
                            + asn1Encodable.getClass().getName());
        }

        ASN1Sequence sequence = (ASN1Sequence) asn1Encodable;
        packageInfos = parseAttestationPackageInfos(sequence.getObjectAt(PACKAGE_INFOS_INDEX));
        // The infos must be sorted, the implementation of Comparable relies on it.
        packageInfos.sort(null);
    }

    public List<AttestationPackageInfo> getAttestationPackageInfos() {
        return packageInfos;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int noOfInfos = packageInfos.size();
        int i = 1;
        for (AttestationPackageInfo info : packageInfos) {
            sb.append("\n### Package info " + i + "/" + noOfInfos + " ###");
            sb.append(info);
        }
        return sb.toString();
    }

    @Override
    public int compareTo(AttestationApplicationId other) {
        int res = Integer.compare(packageInfos.size(), other.packageInfos.size());
        if (res != 0) return res;
        for (int i = 0; i < packageInfos.size(); ++i) {
            res = packageInfos.get(i).compareTo(other.packageInfos.get(i));
            if (res != 0) return res;
        }
        return res;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof AttestationApplicationId)
                && (0 == compareTo((AttestationApplicationId) o));
    }

    private List<AttestationPackageInfo> parseAttestationPackageInfos(ASN1Encodable asn1Encodable)
            throws CertificateParsingException {
        if (!(asn1Encodable instanceof ASN1Set)) {
            throw new CertificateParsingException(
                    "Expected set for AttestationApplicationsInfos, found "
                            + asn1Encodable.getClass().getName());
        }

        ASN1Set set = (ASN1Set) asn1Encodable;
        List<AttestationPackageInfo> result = new ArrayList<AttestationPackageInfo>();
        for (ASN1Encodable e: set) {
            result.add(new AttestationPackageInfo(e));
        }
        return result;
    }
}
