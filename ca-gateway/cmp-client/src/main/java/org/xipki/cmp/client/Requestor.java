// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.cmp.client;

import org.bouncycastle.asn1.cmp.PBMParameter;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.GeneralName;
import org.xipki.security.ConcurrentContentSigner;
import org.xipki.security.HashAlgo;
import org.xipki.security.SignAlgo;
import org.xipki.util.Args;

import java.security.SecureRandom;

/**
 * CMP requestor.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

public abstract class Requestor {

  private static final X500Name NULL_GENERALNAME = new X500Name(new RDN[0]);

  private final GeneralName name;

  private Requestor(X500Name name) {
    this.name = new GeneralName(Args.notNull(name, "name"));
  }

  public GeneralName getName() {
    return name;
  }

  public static class PbmMacCmpRequestor extends Requestor {

    private final SecureRandom random = new SecureRandom();

    private final char[] password;

    private final byte[] senderKID;

    private final HashAlgo owf;

    private final int iterationCount;

    private final SignAlgo mac;

    public PbmMacCmpRequestor(char[] password, byte[] senderKID, HashAlgo owf, int iterationCount, SignAlgo mac) {
      super(NULL_GENERALNAME);
      this.password = password;
      this.senderKID = senderKID;
      this.owf = owf;
      this.iterationCount = iterationCount;
      this.mac = mac;
    }

    public char[] getPassword() {
      return password;
    }

    public byte[] getSenderKID() {
      return senderKID;
    }

    public PBMParameter getParameter() {
      return new PBMParameter(randomSalt(), owf.getAlgorithmIdentifier(), iterationCount, mac.getAlgorithmIdentifier());
    }

    private byte[] randomSalt() {
      byte[] bytes = new byte[64];
      random.nextBytes(bytes);
      return bytes;
    }
  } // class PbmMacCmpRequestor

  public static class SignatureCmpRequestor extends Requestor {

    private final ConcurrentContentSigner signer;

    public SignatureCmpRequestor(ConcurrentContentSigner signer) {
      super(getSignerSubject(signer));
      this.signer = signer;
    }

    public ConcurrentContentSigner getSigner() {
      return signer;
    }

    private static X500Name getSignerSubject(ConcurrentContentSigner signer) {
      if (Args.notNull(signer, "signer").getCertificate() == null) {
        throw new IllegalArgumentException("requestor without certificate is not allowed");
      }

      return signer.getCertificate().getSubject();
    }

  } // class SignatureCmpRequestor

}
