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

package org.xipki.security.util;

import java.math.BigInteger;
import java.security.spec.EllipticCurve;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECFieldElement;
import org.bouncycastle.util.BigIntegers;
import org.xipki.util.Hex;

/**
 * Chinese GM/SM Util class.
 * @author Lijun Liao
 *
 */
// CHECKSTYLE:SKIP
public class GMUtil {

  private static final byte[] defaultIDA =
      new byte[]{0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38,
          0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38}; // the default value

  private static final BigInteger sm2primev2CurveA = new BigInteger(1,
      Hex.decode("28E9FA9E9D9F5E344D5A9E4BCF6509A7F39789F515AB8F92DDBCBD414D940E93"));

  private GMUtil() {
  }

  public static byte[] getSM2Z(ASN1ObjectIdentifier curveOid, BigInteger pubPointX,
      BigInteger pubPointY) {
    return getSM2Z(defaultIDA, curveOid, pubPointX, pubPointY);
  }

  // CHECKSTYLE:SKIP
  public static byte[] getDefaultIDA() {
    return defaultIDA.clone();
  }

  // CHECKSTYLE:SKIP
  public static byte[] getSM2Z(byte[] userID, ASN1ObjectIdentifier curveOid,
      BigInteger pubPointX, BigInteger pubPointY) {
    SM3Digest digest = new SM3Digest();

    addUserId(digest, userID);

    X9ECParameters ecParams = GMNamedCurves.getByOID(curveOid);
    addFieldElement(digest, ecParams.getCurve().getA());
    addFieldElement(digest, ecParams.getCurve().getB());
    addFieldElement(digest, ecParams.getG().getAffineXCoord());
    addFieldElement(digest, ecParams.getG().getAffineYCoord());

    int fieldSize = (ecParams.getCurve().getFieldSize() + 7) / 8;
    byte[] bytes = BigIntegers.asUnsignedByteArray(fieldSize, pubPointX);
    digest.update(bytes, 0, fieldSize);

    bytes = BigIntegers.asUnsignedByteArray(fieldSize, pubPointY);
    digest.update(bytes, 0, fieldSize);

    byte[] result = new byte[digest.getDigestSize()];
    digest.doFinal(result, 0);
    return result;
  } // method getSM2Z

  private static void addUserId(Digest digest, byte[] userId) {
    int len = userId.length * 8;
    if (len > 0xFFFF) {
      throw new IllegalArgumentException("userId too long");
    }

    digest.update((byte)(len >> 8 & 0xFF));
    digest.update((byte)(len & 0xFF));
    digest.update(userId, 0, userId.length);
  }

  private static void addFieldElement(Digest digest, ECFieldElement element) {
    byte[] encoded = element.getEncoded();
    digest.update(encoded, 0, encoded.length);
  }

  public static boolean isSm2primev2Curve(EllipticCurve curve) {
    return curve.getB().equals(sm2primev2CurveA);
  }

  public static boolean isSm2primev2Curve(ECCurve curve) {
    return curve.getB().toBigInteger().equals(sm2primev2CurveA);

  }

}
