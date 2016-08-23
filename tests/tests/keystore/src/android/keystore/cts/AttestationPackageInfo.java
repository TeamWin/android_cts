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

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

public class AttestationPackageInfo implements java.lang.Comparable<AttestationPackageInfo> {
    private static final int PACKAGE_NAME_INDEX = 0;
    private static final int VERSION_INDEX = 1;
    private static final int SIGNATURE_DIGESTS_INDEX = 2;

    private final String packageName;
    private final int version;
    private final List<byte[]> signatureDigests;

    public AttestationPackageInfo(String packageName, int version, List<byte[]> signatureDigests) {
        this.packageName = packageName;
        this.version = version;
        this.signatureDigests = signatureDigests;
        // digests must be sorted. the implementation of Comparable relies on it
        signatureDigests.sort(new ByteArrayComparator());
    }

    public AttestationPackageInfo(ASN1Encodable asn1Encodable) throws CertificateParsingException {
        if (!(asn1Encodable instanceof ASN1Sequence)) {
            throw new CertificateParsingException(
                    "Expected sequence for AttestationPackageInfo, found "
                            + asn1Encodable.getClass().getName());
        }

        ASN1Sequence sequence = (ASN1Sequence) asn1Encodable;
        try {
            packageName = Asn1Utils.getStringFromAsn1OctetStreamAssumingUTF8(
                    sequence.getObjectAt(PACKAGE_NAME_INDEX));
        } catch (UnsupportedEncodingException e) {
            throw new CertificateParsingException(
                    "Converting octet stream to String triggered an UnsupportedEncodingException",
                    e);
        }
        version = Asn1Utils.getIntegerFromAsn1(sequence.getObjectAt(VERSION_INDEX));
        signatureDigests = parseSignatures(sequence.getObjectAt(SIGNATURE_DIGESTS_INDEX));
        // digests must be sorted. the implementation of Comparable relies on it
        signatureDigests.sort(new ByteArrayComparator());
    }

    public String getPackageName() {
        return packageName;
    }

    public int getVersion() {
        return version;
    }

    public List<byte[]> getSignatureDigests() {
        return signatureDigests;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append("\nPackage name: ").append(getPackageName())
                .append("\nVersion: " + getVersion());
        int i = 1;
        int noOfSigs = getSignatureDigests().size();
        for (byte[] sig : getSignatureDigests()) {
            sb.append("\nSignature digest " + i++ + "/" + noOfSigs + ":");
            for (byte b : sig) {
                sb.append(String.format(" %02X", b));
            }
        }
        return sb.toString();
    }

    @Override
    public int compareTo(AttestationPackageInfo other) {
        int res = packageName.compareTo(other.packageName);
        if (res != 0) return res;
        res = Integer.compare(version, other.version);
        if (res != 0) return res;
        res = Integer.compare(signatureDigests.size(), other.signatureDigests.size());
        if (res != 0) return res;
        ByteArrayComparator cmp = new ByteArrayComparator();
        for (int i = 0; i < signatureDigests.size(); ++i) {
            res = cmp.compare(signatureDigests.get(i), other.signatureDigests.get(i));
            if (res != 0) return res;
        }
        return res;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof AttestationPackageInfo)
                && (0 == compareTo((AttestationPackageInfo) o));
    }

    private class ByteArrayComparator implements java.util.Comparator<byte[]> {
        @Override
        public int compare(byte[] a, byte[] b) {
            int res = Integer.compare(a.length, b.length);
            if (res != 0) return res;
            for (int i = 0; i < a.length; ++i) {
                res = Byte.compare(a[i], b[i]);
                if (res != 0) return res;
            }
            return res;
        }
    }

    private List<byte[]> parseSignatures(ASN1Encodable asn1Encodable)
            throws CertificateParsingException {
        if (!(asn1Encodable instanceof ASN1Set)) {
            throw new CertificateParsingException("Expected set for Signature digests, found "
                    + asn1Encodable.getClass().getName());
        }

        ASN1Set set = (ASN1Set) asn1Encodable;
        List<byte[]> result = new ArrayList<byte[]>();

        for (ASN1Encodable e: set) {
            result.add(Asn1Utils.getByteArrayFromAsn1(e));
        }
        return result;
    }
}
