/*
 *
 * Copyright (c) 2013 - 2020 Lijun Liao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xipki.ocsp.server.store.ejbca;

import static org.xipki.util.Args.notNull;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.bouncycastle.asn1.x509.Certificate;
import org.xipki.ocsp.api.RequestIssuer;
import org.xipki.security.CertRevocationInfo;
import org.xipki.security.CrlReason;
import org.xipki.security.HashAlgo;
import org.xipki.security.X509Cert;
import org.xipki.util.CompareUtil;

/**
 * IssuerEntry for the EJBCA database.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

class EjbcaIssuerEntry {

  private final String id;

  private final Map<HashAlgo, byte[]> issuerHashMap;

  private final Date notBefore;

  private final X509Cert cert;

  private CertRevocationInfo revocationInfo;

  public EjbcaIssuerEntry(X509Cert cert)
      throws CertificateEncodingException {
    this.cert = notNull(cert, "cert");
    this.notBefore = cert.getNotBefore();
    byte[] encodedCert = cert.getEncoded();
    this.id = HashAlgo.SHA1.hexHash(encodedCert);
    this.issuerHashMap = getIssuerHashAndKeys(encodedCert);
  }

  private static Map<HashAlgo, byte[]> getIssuerHashAndKeys(byte[] encodedCert)
      throws CertificateEncodingException {
    byte[] encodedName;
    byte[] encodedKey;
    try {
      Certificate bcCert = Certificate.getInstance(encodedCert);
      encodedName = bcCert.getSubject().getEncoded("DER");
      encodedKey = bcCert.getSubjectPublicKeyInfo().getPublicKeyData().getBytes();
    } catch (IllegalArgumentException | IOException ex) {
      throw new CertificateEncodingException(ex.getMessage(), ex);
    }

    Map<HashAlgo, byte[]> hashes = new HashMap<>();
    for (HashAlgo ha : HashAlgo.values()) {
      int hlen = ha.getLength();
      byte[] nameAndKeyHash = new byte[(2 + hlen) << 1];
      int offset = 0;
      nameAndKeyHash[offset++] = 0x04;
      nameAndKeyHash[offset++] = (byte) hlen;
      System.arraycopy(ha.hash(encodedName), 0, nameAndKeyHash, offset, hlen);
      offset += hlen;

      nameAndKeyHash[offset++] = 0x04;
      nameAndKeyHash[offset++] = (byte) hlen;
      System.arraycopy(ha.hash(encodedKey), 0, nameAndKeyHash, offset, hlen);

      hashes.put(ha, nameAndKeyHash);
    }
    return hashes;
  } // method getIssuerHashAndKeys

  public String getId() {
    return id;
  }

  public byte[] getEncodedHash(HashAlgo hashAlgo) {
    byte[] data = issuerHashMap.get(hashAlgo);
    return Arrays.copyOf(data, data.length);
  }

  public boolean matchHash(RequestIssuer reqIssuer) {
    byte[] issuerHash = issuerHashMap.get(reqIssuer.hashAlgorithm());
    if (issuerHash == null) {
      return false;
    }

    return CompareUtil.areEqual(issuerHash, 0, reqIssuer.getData(),
        reqIssuer.getNameHashFrom(), issuerHash.length);
  }

  public void setRevocationInfo(Date revocationTime) {
    notNull(revocationTime, "revocationTime");
    this.revocationInfo = new CertRevocationInfo(CrlReason.CA_COMPROMISE,
        revocationTime, null);
  }

  public CertRevocationInfo getRevocationInfo() {
    return revocationInfo;
  }

  public Date getNotBefore() {
    return notBefore;
  }

  public X509Cert getCert() {
    return cert;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (!(obj instanceof EjbcaIssuerEntry)) {
      return false;
    }

    EjbcaIssuerEntry other = (EjbcaIssuerEntry) obj;
    return id.equals(other.id)
        && CompareUtil.equalsObject(revocationInfo, other.revocationInfo);
    // The comparison of id implies the comparison of issuerHashMap, notBefore and cert.
  } // method equals

}
