// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ocsp.server.type;

import org.xipki.ocsp.api.RequestIssuer;

import java.math.BigInteger;

/**
 * ASN.1 CertID.
 *
 * @author Lijun Liao
 * @since 2.2.0
 */

public class CertID extends ASN1Type {

  private final RequestIssuer issuer;

  private final BigInteger serialNumber;

  private final int bodyLength;

  private final int encodedLength;

  public CertID(RequestIssuer issuer, BigInteger serialNumber) {
    this.issuer = issuer;
    this.serialNumber = serialNumber;

    int len = issuer.getLength();

    int snBytesLen = 1 + serialNumber.bitLength() / 8;
    len += getLen(snBytesLen);

    this.bodyLength = len;
    this.encodedLength = getLen(bodyLength);
  }

  public RequestIssuer getIssuer() {
    return issuer;
  }

  public BigInteger getSerialNumber() {
    return serialNumber;
  }

  @Override
  public int getEncodedLength() {
    return encodedLength;
  }

  public int write(byte[] out, int offset) {
    int idx = offset;
    idx += writeHeader((byte) 0x30, bodyLength, out, idx);
    idx += issuer.write(out, idx);

    // serialNumbers
    byte[] snBytes = serialNumber.toByteArray();
    idx += writeHeader((byte) 0x02, snBytes.length, out, idx);
    idx += arraycopy(snBytes, out, idx);

    return idx - offset;
  }

}
