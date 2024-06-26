// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.server.mgmt;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.ca.api.PublicCaInfo;
import org.xipki.ca.api.mgmt.entry.CaEntry;
import org.xipki.ca.api.mgmt.entry.CaEntry.CaSignerConf;
import org.xipki.ca.api.profile.Certprofile;
import org.xipki.ca.api.profile.CertprofileException;
import org.xipki.ca.api.profile.ExtensionValues;
import org.xipki.ca.server.CaUtil;
import org.xipki.ca.server.IdentifiedCertprofile;
import org.xipki.pki.BadCertTemplateException;
import org.xipki.pki.ErrorCode;
import org.xipki.pki.OperationException;
import org.xipki.security.ConcurrentContentSigner;
import org.xipki.security.NoIdleSignerException;
import org.xipki.security.SecurityFactory;
import org.xipki.security.SignAlgo;
import org.xipki.security.SignerConf;
import org.xipki.security.X509Cert;
import org.xipki.security.XiContentSigner;
import org.xipki.security.XiSecurityException;
import org.xipki.security.util.KeyUtil;
import org.xipki.security.util.X509Util;
import org.xipki.util.Args;
import org.xipki.util.CollectionUtil;
import org.xipki.util.ConfPairs;
import org.xipki.util.StringUtil;
import org.xipki.util.Validity;
import org.xipki.util.exception.InvalidConfException;
import org.xipki.util.exception.ObjectCreationException;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Self-signed certificate builder.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

public class SelfSignedCertBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(SelfSignedCertBuilder.class);

  private SelfSignedCertBuilder() {
  }

  public static X509Cert generateSelfSigned(
      SecurityFactory securityFactory, String signerType, String signerConf, IdentifiedCertprofile certprofile,
      String subject, String serialNumber, Instant notBefore, Instant notAfter)
      throws OperationException, InvalidConfException {
    Args.notNull(securityFactory, "securityFactory");
    Args.notBlank(signerType, "signerType");
    Args.notBlank(subject, "subject");

    BigInteger serialOfThisCert;
    if (StringUtil.startsWithIgnoreCase(serialNumber, "0x")) {
      serialOfThisCert = new BigInteger(serialNumber.substring(2), 16);
    } else {
      serialOfThisCert = new BigInteger(serialNumber);
    }

    if (serialOfThisCert.signum() != 1) {
      throw new IllegalArgumentException("serialNumber may not be non-positive: " + serialNumber);
    }

    Certprofile.CertLevel level = Args.notNull(certprofile, "certprofile").getCertLevel();
    if (Certprofile.CertLevel.RootCA != level) {
      throw new IllegalArgumentException("certprofile is not of level " + Certprofile.CertLevel.RootCA);
    }

    if (StringUtil.orEqualsIgnoreCase(signerType, "PKCS12", "JCEKS")) {
      ConfPairs keyValues = new ConfPairs(signerConf);
      Optional.ofNullable(keyValues.value("keystore")).orElseThrow(() ->
          new InvalidConfException("required parameter 'keystore' for types PKCS12 and JCEKS, is not specified"));
    }

    ConcurrentContentSigner signer;
    try {
      List<CaSignerConf> signerConfs = CaEntry.splitCaSignerConfs(signerConf);
      List<SignAlgo> restrictedSigAlgos = certprofile.getSignatureAlgorithms();

      String thisSignerConf = null;
      if (CollectionUtil.isEmpty(restrictedSigAlgos)) {
        thisSignerConf = signerConfs.get(0).getConf();
      } else {
        for (SignAlgo algo : restrictedSigAlgos) {
          for (CaSignerConf m : signerConfs) {
            if (m.getAlgo() == algo) {
              thisSignerConf = m.getConf();
              break;
            }
          }

          if (thisSignerConf != null) {
            break;
          }
        }
      }

      if (thisSignerConf == null) {
        throw new OperationException(ErrorCode.SYSTEM_FAILURE,
          "CA does not support any signature algorithm restricted by the cert profile");
      }

      signer = securityFactory.createSigner(signerType, new SignerConf(thisSignerConf), (X509Cert[]) null);
    } catch (XiSecurityException | ObjectCreationException ex) {
      throw new OperationException(ErrorCode.SYSTEM_FAILURE, ex);
    }

    return generateCertificate(signer, certprofile, subject, serialOfThisCert, notBefore, notAfter);
  }

  private static X509Cert generateCertificate(
      ConcurrentContentSigner signer, IdentifiedCertprofile certprofile,
      String subject, BigInteger serialNumber, Instant notBefore, Instant notAfter)
      throws OperationException {
    SubjectPublicKeyInfo publicKeyInfo;
    try {
      publicKeyInfo = KeyUtil.createSubjectPublicKeyInfo(signer.getPublicKey());
    } catch (InvalidKeyException ex) {
      LOG.warn("KeyUtil.createSubjectPublicKeyInfo", ex);
      throw new OperationException(ErrorCode.SYSTEM_FAILURE, ex);
    }

    try {
      publicKeyInfo = X509Util.toRfc3279Style(publicKeyInfo);
    } catch (InvalidKeySpecException ex) {
      LOG.warn("SecurityUtil.toRfc3279Style", ex);
      throw new OperationException(ErrorCode.BAD_CERT_TEMPLATE, ex);
    }

    PublicKey signerPublicKey = signer.getPublicKey();
    // make sure that the signer's public key is the same the requested one
    PublicKey csrPublicKey;
    try {
      csrPublicKey = KeyUtil.generatePublicKey(publicKeyInfo);
    } catch (InvalidKeySpecException ex) {
      throw new OperationException(ErrorCode.SYSTEM_FAILURE, ex.getMessage());
    }

    if (!signerPublicKey.equals(csrPublicKey)) {
      throw new OperationException(ErrorCode.BAD_REQUEST, "Public keys of the signer's token and of CSR are different");
    }

    try {
      certprofile.checkPublicKey(publicKeyInfo);
    } catch (CertprofileException ex) {
      throw new OperationException(ErrorCode.SYSTEM_FAILURE, "exception in cert profile " + certprofile.getIdent());
    } catch (BadCertTemplateException ex) {
      LOG.warn("certprofile.checkPublicKey", ex);
      throw new OperationException(ErrorCode.BAD_CERT_TEMPLATE, ex);
    }

    X500Name requestedSubject = new X500Name(subject);

    Certprofile.SubjectInfo subjectInfo;
    // subject
    try {
      subjectInfo = certprofile.getSubject(requestedSubject, publicKeyInfo);
    } catch (CertprofileException ex) {
      throw new OperationException(ErrorCode.SYSTEM_FAILURE, "exception in cert profile " + certprofile.getIdent());
    } catch (BadCertTemplateException ex) {
      LOG.warn("certprofile.getSubject", ex);
      throw new OperationException(ErrorCode.BAD_CERT_TEMPLATE, ex);
    }

    notBefore = certprofile.getNotBefore(notBefore);
    if (notBefore == null) {
      notBefore = Instant.now();
    }

    Validity validity = Optional.ofNullable(certprofile.getValidity())
        .orElseThrow(() -> new OperationException(ErrorCode.BAD_CERT_TEMPLATE,
            "no validity specified in the profile " + certprofile.getIdent()));

    Instant maxNotAfter = validity.add(notBefore);
    if (notAfter == null) {
      notAfter = maxNotAfter;
    } else if (notAfter.isAfter(maxNotAfter)) {
      notAfter = maxNotAfter;
    }

    X500Name grantedSubject = subjectInfo.getGrantedSubject();

    X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(grantedSubject, serialNumber,
        Date.from(notBefore), Date.from(notAfter), grantedSubject, publicKeyInfo);

    try {
      SubjectKeyIdentifier ski = certprofile.getSubjectKeyIdentifier(publicKeyInfo);
      PublicCaInfo publicCaInfo = new PublicCaInfo(grantedSubject, grantedSubject, serialNumber,
          null, ski.getKeyIdentifier(), null, null);

      ExtensionValues extensionTuples = certprofile.getExtensions(requestedSubject, grantedSubject,
          null, publicKeyInfo, publicCaInfo, null, notBefore, notAfter);
      CaUtil.addExtensions(extensionTuples, certBuilder);

      XiContentSigner signer0 = signer.borrowSigner();
      X509CertificateHolder certHolder;
      try {
        certHolder = certBuilder.build(signer0);
      } finally {
        signer.requiteSigner(signer0);
      }
      return new X509Cert(certHolder);
    } catch (BadCertTemplateException ex) {
      throw new OperationException(ErrorCode.BAD_CERT_TEMPLATE, ex);
    } catch (NoIdleSignerException | IOException | CertprofileException ex) {
      throw new OperationException(ErrorCode.SYSTEM_FAILURE, ex);
    }
  } // method generateCertificate

}
