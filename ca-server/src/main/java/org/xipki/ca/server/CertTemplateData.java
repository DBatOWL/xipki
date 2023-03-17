// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.server;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.xipki.util.Args;

import java.math.BigInteger;
import java.util.Date;

/**
 * Certificate template data.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

public class CertTemplateData {

  private final X500Name subject;

  private final SubjectPublicKeyInfo publicKeyInfo;

  private final Date notBefore;

  private final Date notAfter;

  private final String certprofileName;

  private final boolean serverkeygen;

  private final Extensions extensions;

  private final BigInteger certReqId;

  private boolean forCrossCert;

  public CertTemplateData(X500Name subject, SubjectPublicKeyInfo publicKeyInfo, Date notBefore,
                          Date notAfter, Extensions extensions, String certprofileName) {
    this(subject, publicKeyInfo, notBefore, notAfter, extensions, certprofileName, null, false);
  }

  public CertTemplateData(X500Name subject, SubjectPublicKeyInfo publicKeyInfo, Date notBefore, Date notAfter,
                          Extensions extensions, String certprofileName, BigInteger certReqId, boolean serverkeygen) {
    this.publicKeyInfo = publicKeyInfo;
    this.subject = Args.notNull(subject, "subject");
    this.certprofileName = Args.toNonBlankLower(certprofileName, "certprofileName");
    this.extensions = extensions;
    this.notBefore = notBefore;
    this.notAfter = notAfter;
    this.certReqId = certReqId;
    this.serverkeygen = serverkeygen;
  }

  public boolean isForCrossCert() {
    return forCrossCert;
  }

  public void setForCrossCert(boolean forCrossCert) {
    this.forCrossCert = forCrossCert;
  }

  public X500Name getSubject() {
    return subject;
  }

  public SubjectPublicKeyInfo getPublicKeyInfo() {
    return publicKeyInfo;
  }

  public boolean isServerkeygen() {
    return serverkeygen;
  }

  public Date getNotBefore() {
    return notBefore;
  }

  public Date getNotAfter() {
    return notAfter;
  }

  public String getCertprofileName() {
    return certprofileName;
  }

  public Extensions getExtensions() {
    return extensions;
  }

  public BigInteger getCertReqId() {
    return certReqId;
  }

}
