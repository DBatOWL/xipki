/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2013 - 2016 Lijun Liao
 * Author: Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 *
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.commons.security.api;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.api.p11.P11Constants;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public enum HashAlgoType {

    SHA1(20, "1.3.14.3.2.26", "SHA1", "S1"),
    SHA224(28, "2.16.840.1.101.3.4.2.4", "SHA224", "S224"),
    SHA256(32, "2.16.840.1.101.3.4.2.1", "SHA256", "S256"),
    SHA384(48, "2.16.840.1.101.3.4.2.2", "SHA384", "S384"),
    SHA512(64, "2.16.840.1.101.3.4.2.3", "SHA512", "S512");

    private final int length;

    private final String oid;

    private final String name;

    private final String shortName;

    HashAlgoType(
            final int length,
            final String oid,
            final String name,
            final String shortName) {
        this.length = length;
        this.oid = oid;
        this.name = name;
        this.shortName = shortName;
    }

    public int getLength() {
        return length;
    }

    public String getOid() {
        return oid;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public static HashAlgoType getHashAlgoType(
            final ASN1ObjectIdentifier oid) {
        String oidStr = ParamUtil.requireNonNull("oid", oid).getId();
        for (HashAlgoType hashAlgo : values()) {
            if (hashAlgo.oid.equals(oidStr)) {
                return hashAlgo;
            }
        }
        return null;
    }

    public static HashAlgoType getHashAlgoType(
            final String nameOrOid) {
        String tmpNameOrOid = ParamUtil.requireNonBlank("nameOrOid", nameOrOid);
        for (HashAlgoType hashAlgo : values()) {
            if (hashAlgo.oid.equals(tmpNameOrOid)) {
                return hashAlgo;
            }

            if (tmpNameOrOid.indexOf('-') != -1) {
                tmpNameOrOid = tmpNameOrOid.replace("-", "");
            }

            if (hashAlgo.name.equalsIgnoreCase(tmpNameOrOid)
                    || hashAlgo.shortName.equalsIgnoreCase(tmpNameOrOid)) {
                return hashAlgo;
            }
        }

        return null;
    }

    public static HashAlgoType getInstanceForPkcs11HashMech(
            long hashMech) {
        if (hashMech == P11Constants.CKM_SHA_1) {
            return HashAlgoType.SHA1;
        } else if (hashMech == P11Constants.CKM_SHA224) {
            return HashAlgoType.SHA224;
        } else if (hashMech == P11Constants.CKM_SHA256) {
            return HashAlgoType.SHA256;
        } else if (hashMech == P11Constants.CKM_SHA384) {
            return HashAlgoType.SHA384;
        } else if (hashMech == P11Constants.CKM_SHA512) {
            return HashAlgoType.SHA512;
        } else {
            return null;
        }
    }

    public static HashAlgoType getInstanceForPkcs11MgfMech(
            long hashMech) {
        if (hashMech == P11Constants.CKG_MGF1_SHA1) {
            return HashAlgoType.SHA1;
        } else if (hashMech == P11Constants.CKG_MGF1_SHA224) {
            return HashAlgoType.SHA224;
        } else if (hashMech == P11Constants.CKG_MGF1_SHA256) {
            return HashAlgoType.SHA256;
        } else if (hashMech == P11Constants.CKG_MGF1_SHA384) {
            return HashAlgoType.SHA384;
        } else if (hashMech == P11Constants.CKG_MGF1_SHA512) {
            return HashAlgoType.SHA512;
        } else {
            return null;
        }
    }
}
