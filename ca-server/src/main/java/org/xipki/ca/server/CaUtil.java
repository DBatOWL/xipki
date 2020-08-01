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

package org.xipki.ca.server;

import static org.xipki.util.Args.notEmpty;
import static org.xipki.util.Args.notNull;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber;
import org.bouncycastle.asn1.crmf.DhSigStatic;
import org.bouncycastle.asn1.pkcs.Attribute;
import org.bouncycastle.asn1.pkcs.CertificationRequest;
import org.bouncycastle.asn1.pkcs.CertificationRequestInfo;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.xipki.ca.api.profile.Certprofile.CertLevel;
import org.xipki.ca.api.profile.SubjectDnSpec;
import org.xipki.security.AlgorithmValidator;
import org.xipki.security.DHSigStaticKeyCertPair;
import org.xipki.security.EdECConstants;
import org.xipki.security.ObjectIdentifiers.Xipki;
import org.xipki.security.SecurityFactory;
import org.xipki.util.CollectionUtil;

/**
 * Util class of CA.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

public class CaUtil {

  private CaUtil() {
  }

  public static Extensions getExtensions(CertificationRequestInfo csr) {
    notNull(csr, "csr");
    ASN1Set attrs = csr.getAttributes();
    for (int i = 0; i < attrs.size(); i++) {
      Attribute attr = Attribute.getInstance(attrs.getObjectAt(i));
      if (PKCSObjectIdentifiers.pkcs_9_at_extensionRequest.equals(attr.getAttrType())) {
        return Extensions.getInstance(attr.getAttributeValues()[0]);
      }
    }
    return null;
  } // method getExtensions

  public static String getChallengePassword(CertificationRequestInfo csr) {
    notNull(csr, "csr");
    ASN1Set attrs = csr.getAttributes();
    for (int i = 0; i < attrs.size(); i++) {
      Attribute attr = Attribute.getInstance(attrs.getObjectAt(i));
      if (PKCSObjectIdentifiers.pkcs_9_at_challengePassword.equals(attr.getAttrType())) {
        ASN1String str = (ASN1String) attr.getAttributeValues()[0];
        return str.getString();
      }
    }
    return null;
  } // method getChallengePassword

  public static BasicConstraints createBasicConstraints(CertLevel level, Integer pathLen) {
    BasicConstraints basicConstraints;
    if (level == CertLevel.RootCA || level == CertLevel.SubCA) {
      basicConstraints = (pathLen != null)  ? new BasicConstraints(pathLen)
          : new BasicConstraints(true);
    } else if (level == CertLevel.EndEntity) {
      basicConstraints = new BasicConstraints(false);
    } else {
      throw new IllegalStateException("unknown CertLevel " + level);
    }
    return basicConstraints;
  } // method createBasicConstraints

  public static AuthorityInformationAccess createAuthorityInformationAccess(
      List<String> caIssuerUris, List<String> ocspUris) {
    if (CollectionUtil.isEmpty(caIssuerUris) && CollectionUtil.isEmpty(ocspUris)) {
      throw new IllegalArgumentException("caIssuerUris and ospUris may not be both empty");
    }

    List<AccessDescription> accessDescriptions = new ArrayList<>(ocspUris.size());

    if (CollectionUtil.isNotEmpty(caIssuerUris)) {
      for (String uri : caIssuerUris) {
        GeneralName gn = new GeneralName(GeneralName.uniformResourceIdentifier, uri);
        accessDescriptions.add(
            new AccessDescription(X509ObjectIdentifiers.id_ad_caIssuers, gn));
      }
    }

    if (CollectionUtil.isNotEmpty(ocspUris)) {
      for (String uri : ocspUris) {
        GeneralName gn = new GeneralName(GeneralName.uniformResourceIdentifier, uri);
        accessDescriptions.add(new AccessDescription(X509ObjectIdentifiers.id_ad_ocsp, gn));
      }
    }

    DERSequence seq = new DERSequence(accessDescriptions.toArray(new AccessDescription[0]));
    return AuthorityInformationAccess.getInstance(seq);
  } // method createAuthorityInformationAccess

  public static CRLDistPoint createCrlDistributionPoints(List<String> crlUris, X500Name caSubject,
      X500Name crlSignerSubject) {
    notEmpty(crlUris, "crlUris");
    int size = crlUris.size();
    DistributionPoint[] points = new DistributionPoint[1];

    GeneralName[] names = new GeneralName[size];
    for (int i = 0; i < size; i++) {
      names[i] = new GeneralName(GeneralName.uniformResourceIdentifier, crlUris.get(i));
    }
    // Distribution Point
    GeneralNames gns = new GeneralNames(names);
    DistributionPointName pointName = new DistributionPointName(gns);

    GeneralNames crlIssuer = null;
    if (crlSignerSubject != null && !crlSignerSubject.equals(caSubject)) {
      GeneralName crlIssuerName = new GeneralName(crlSignerSubject);
      crlIssuer = new GeneralNames(crlIssuerName);
    }

    points[0] = new DistributionPoint(pointName, null, crlIssuer);

    return new CRLDistPoint(points);
  } // method createCrlDistributionPoints

  public static X500Name sortX509Name(X500Name name) {
    notNull(name, "name");
    RDN[] requstedRdns = name.getRDNs();

    List<RDN> rdns = new LinkedList<>();

    List<ASN1ObjectIdentifier> sortedDNs = SubjectDnSpec.getForwardDNs();
    int size = sortedDNs.size();
    for (int i = 0; i < size; i++) {
      ASN1ObjectIdentifier type = sortedDNs.get(i);
      RDN[] thisRdns = getRdns(requstedRdns, type);
      if (thisRdns == null) {
        continue;
      }
      if (thisRdns.length == 0) {
        continue;
      }

      for (RDN m : thisRdns) {
        rdns.add(m);
      }
    }

    return new X500Name(rdns.toArray(new RDN[0]));
  } // method sortX509Name

  public static boolean verifyCsr(CertificationRequest csr, SecurityFactory securityFactory,
      AlgorithmValidator algorithmValidator, DhpocControl dhpocControl) {
    notNull(csr, "csr");

    ASN1ObjectIdentifier algOid = csr.getSignatureAlgorithm().getAlgorithm();

    DHSigStaticKeyCertPair kaKeyAndCert = null;
    if (Xipki.id_alg_dhPop_x25519_sha256.equals(algOid)
        || Xipki.id_alg_dhPop_x448_sha512.equals(algOid)) {
      if (dhpocControl != null) {
        DhSigStatic dhSigStatic = DhSigStatic.getInstance(csr.getSignature().getBytes());
        IssuerAndSerialNumber isn = dhSigStatic.getIssuerAndSerial();

        ASN1ObjectIdentifier keyOid = csr.getCertificationRequestInfo().getSubjectPublicKeyInfo()
                                        .getAlgorithm().getAlgorithm();
        kaKeyAndCert = dhpocControl.getKeyCertPair(isn.getName(), isn.getSerialNumber().getValue(),
            EdECConstants.getName(keyOid));
      }

      if (kaKeyAndCert == null) {
        return false;
      }
    }

    return securityFactory.verifyPopo(csr, algorithmValidator, kaKeyAndCert);
  } // method verifyCsr

  private static RDN[] getRdns(RDN[] rdns, ASN1ObjectIdentifier type) {
    notNull(rdns, "rdns");
    notNull(type, "type");
    List<RDN> ret = new ArrayList<>(1);
    for (int i = 0; i < rdns.length; i++) {
      RDN rdn = rdns[i];
      if (rdn.getFirst().getType().equals(type)) {
        ret.add(rdn);
      }
    }

    return CollectionUtil.isEmpty(ret) ? null : ret.toArray(new RDN[0]);
  } // method getRdns

}
