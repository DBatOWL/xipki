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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.security.NoSuchProviderException;
import java.security.cert.CRLException;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.DERUniversalString;
import org.bouncycastle.asn1.pkcs.CertificationRequest;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.DirectoryString;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.DSAParameter;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.security.BadInputException;
import org.xipki.security.EdECConstants;
import org.xipki.security.FpIdCalculator;
import org.xipki.security.KeyUsage;
import org.xipki.security.ObjectIdentifiers;
import org.xipki.security.X509Cert;
import org.xipki.util.Args;
import org.xipki.util.Base64;
import org.xipki.util.CollectionUtil;
import org.xipki.util.CompareUtil;
import org.xipki.util.ConfPairs;
import org.xipki.util.Hex;
import org.xipki.util.IoUtil;
import org.xipki.util.LogUtil;
import org.xipki.util.PemEncoder;
import org.xipki.util.PemEncoder.PemLabel;
import org.xipki.util.StringUtil;

/**
 * X.509 certificate utility class.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

public class X509Util {
  private static final Logger LOG = LoggerFactory.getLogger(X509Util.class);

  private static final byte[] BEGIN_PEM = StringUtil.toUtf8Bytes("-----BEGIN");

  private static final byte[] END_PEM = StringUtil.toUtf8Bytes("-----END");

  private static final byte[] PEM_SEP = StringUtil.toUtf8Bytes("-----");

  private static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";

  private static final String END_CERTIFICATE = "-----END CERTIFICATE-----";

  private static CertificateFactory certFact;

  private static Object certFactLock = new Object();

  private X509Util() {
  }

  public static String getCommonName(X500Name name) {
    Args.notNull(name, "name");
    RDN[] rdns = name.getRDNs(ObjectIdentifiers.DN.CN);
    if (rdns != null && rdns.length > 0) {
      RDN rdn = rdns[0];
      AttributeTypeAndValue atv = null;
      if (rdn.isMultiValued()) {
        for (AttributeTypeAndValue m : rdn.getTypesAndValues()) {
          if (m.getType().equals(ObjectIdentifiers.DN.CN)) {
            atv = m;
            break;
          }
        }
      } else {
        atv = rdn.getFirst();
      }
      return (atv == null) ? null : rdnValueToString(atv.getValue());
    }
    return null;
  } // method getCommonName

  public static X500Name reverse(X500Name name) {
    RDN[] orig = Args.notNull(name, "name").getRDNs();
    final int n = orig.length;
    RDN[] newRdn = new RDN[n];
    for (int i = 0; i < n; i++) {
      newRdn[i] = orig[n - 1 - i];
    }
    return new X500Name(newRdn);
  }

  public static X509Cert parseCert(File file)
      throws IOException, CertificateException {
    Args.notNull(file, "file");
    InputStream in = Files.newInputStream(IoUtil.expandFilepath(file).toPath());
    try {
      return parseCert(in);
    } finally {
      in.close();
    }
  }

  public static X509Cert parseCert(InputStream certStream)
      throws IOException, CertificateException {
    Args.notNull(certStream, "certStream");
    return parseCert(IoUtil.read(certStream));
  }

  public static X509Cert parseCert(byte[] certBytes)
      throws CertificateEncodingException {
    Args.notNull(certBytes, "certBytes");
    byte[] derBytes = toDerEncoded(certBytes);
    X509CertificateHolder certHolder;
    try {
      certHolder = new X509CertificateHolder(derBytes);
    } catch (IOException ex) {
      throw new CertificateEncodingException("error decoding certificate: " + ex.getMessage(), ex);
    }
    return new X509Cert(certHolder, derBytes);
  }

  public static CertificationRequest parseCsr(File file) throws IOException {
    Args.notNull(file, "file");
    InputStream in = Files.newInputStream(IoUtil.expandFilepath(file).toPath());
    try {
      return parseCsr(in);
    } finally {
      in.close();
    }
  }

  private static CertificationRequest parseCsr(InputStream csrStream) throws IOException {
    Args.notNull(csrStream, "csrStream");
    return parseCsr(IoUtil.read(csrStream));
  }

  public static CertificationRequest parseCsr(byte[] csrBytes) {
    Args.notNull(csrBytes, "csrBytes");
    return CertificationRequest.getInstance(toDerEncoded(csrBytes));
  }

  public static byte[] toDerEncoded(byte[] bytes) {
    final int len = bytes.length;

    if (len > 23) {
      // check if PEM encoded
      if (CompareUtil.areEqual(bytes, 0, BEGIN_PEM, 0, BEGIN_PEM.length)) {
        int base64Start = -1;
        int base64End = -1;

        for (int i = BEGIN_PEM.length + 1; i < len; i++) {
          if (CompareUtil.areEqual(bytes, i, PEM_SEP, 0, PEM_SEP.length)) {
            base64Start = i + PEM_SEP.length;
            break;
          }
        }

        if (bytes[base64Start] == '\n') {
          base64Start++;
        }

        for (int i = len - END_PEM.length - 6; i > 0; i--) {
          if (CompareUtil.areEqual(bytes, i, END_PEM, 0, END_PEM.length)) {
            base64End = i - 1;
            break;
          }
        }

        if (bytes[base64End - 1] == '\r') {
          base64End--;
        }

        byte[] base64Bytes = new byte[base64End - base64Start + 1];
        System.arraycopy(bytes, base64Start, base64Bytes, 0, base64Bytes.length);
        return Base64.decode(base64Bytes);
      }
    }

    // check whether base64 encoded
    if (Base64.containsOnlyBase64Chars(bytes, 0, 10)) {
      return Base64.decode(bytes);
    }

    return bytes;
  } // method toDerEncoded

  private static CertificateFactory getCertFactory() throws CertificateException {
    synchronized (certFactLock) {
      if (certFact == null) {
        try {
          certFact = CertificateFactory.getInstance("X.509", "BC");
        } catch (NoSuchProviderException ex) {
          throw new CertificateException("NoSuchProviderException: " + ex.getMessage());
        }
      }
      return certFact;
    }
  }

  public static String toPemCert(X509Cert cert) throws CertificateException {
    Args.notNull(cert, "cert");
    return new String(PemEncoder.encode(cert.getEncoded(), PemLabel.CERTIFICATE));
  }

  public static X509Certificate parseX509Certificate(InputStream crlStream)
      throws CertificateException {
    Args.notNull(crlStream, "crlStream");
    return (X509Certificate) getCertFactory().generateCertificate(crlStream);
  }

  public static X509CRLHolder parseCrl(File file)
      throws IOException, CRLException {
    Args.notNull(file, "file");
    return parseCrl(Files.readAllBytes(IoUtil.expandFilepath(file).toPath()));
  }

  public static X509CRLHolder parseCrl(byte[] encodedCrl)
      throws CRLException {
    Args.notNull(encodedCrl, "encodedCrl");

    byte[] derBytes = toDerEncoded(encodedCrl);
    try {
      return new X509CRLHolder(derBytes);
    } catch (IOException ex) {
      throw new CRLException("the given one is not a valid X.509 CRL");
    }
  }

  public static String getRfc4519Name(X500Name name) {
    Args.notNull(name, "name");
    return RFC4519Style.INSTANCE.toString(name);
  }

  public static long fpCanonicalizedName(X500Name name) {
    Args.notNull(name, "name");
    String canonicalizedName = canonicalizName(name);
    byte[] encoded = StringUtil.toUtf8Bytes(canonicalizedName);
    return FpIdCalculator.hash(encoded);
  }

  public static String canonicalizName(X500Name name) {
    Args.notNull(name, "name");
    ASN1ObjectIdentifier[] tmpTypes = name.getAttributeTypes();
    int len = tmpTypes.length;
    List<String> types = new ArrayList<>(len);
    for (ASN1ObjectIdentifier type : tmpTypes) {
      types.add(type.getId());
    }

    Collections.sort(types);

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < len; i++) {
      String type = types.get(i);
      if (i > 0) {
        sb.append(",");
      }
      sb.append(type).append("=");
      RDN[] rdns = name.getRDNs(new ASN1ObjectIdentifier(type));

      List<String> values = new ArrayList<>(1);
      for (int j = 0; j < rdns.length; j++) {
        RDN rdn = rdns[j];
        if (rdn.isMultiValued()) {
          AttributeTypeAndValue[] atvs = rdn.getTypesAndValues();
          for (AttributeTypeAndValue atv : atvs) {
            if (type.equals(atv.getType().getId())) {
              String textValue = IETFUtils.valueToString(atv.getValue()).toLowerCase();
              values.add(textValue);
            }
          }
        } else {
          String textValue = IETFUtils.valueToString(rdn.getFirst().getValue()).toLowerCase();
          values.add(textValue);
        }
      } // end for(j)

      sb.append(values.get(0));

      final int n2 = values.size();
      if (n2 > 1) {
        for (int j = 1; j < n2; j++) {
          sb.append(";").append(values.get(j));
        }
      }
    } // end for(i)

    return sb.toString();
  } // method canonicalizName

  public static String rdnValueToString(ASN1Encodable value) {
    Args.notNull(value, "value");
    if (value instanceof ASN1String && !(value instanceof DERUniversalString)) {
      return ((ASN1String) value).getString();
    } else {
      try {
        return "#" + Hex.encode(
            value.toASN1Primitive().getEncoded(ASN1Encoding.DER));
      } catch (IOException ex) {
        throw new IllegalArgumentException("other value has no encoded form");
      }
    }
  }

  public static org.bouncycastle.asn1.x509.KeyUsage createKeyUsage(Set<KeyUsage> usages) {
    if (CollectionUtil.isEmpty(usages)) {
      return null;
    }

    int usage = 0;
    for (KeyUsage keyUsage : usages) {
      usage |= keyUsage.getBcUsage();
    }

    return new org.bouncycastle.asn1.x509.KeyUsage(usage);
  }

  public static ExtendedKeyUsage createExtendedUsage(Collection<ASN1ObjectIdentifier> usages) {
    if (CollectionUtil.isEmpty(usages)) {
      return null;
    }

    List<ASN1ObjectIdentifier> list = new ArrayList<>(usages);
    List<ASN1ObjectIdentifier> sortedUsages = sortOidList(list);
    KeyPurposeId[] kps = new KeyPurposeId[sortedUsages.size()];

    int idx = 0;
    for (ASN1ObjectIdentifier oid : sortedUsages) {
      kps[idx++] = KeyPurposeId.getInstance(oid);
    }

    return new ExtendedKeyUsage(kps);
  }

  // sort the list and remove duplicated OID.
  private static List<ASN1ObjectIdentifier> sortOidList(List<ASN1ObjectIdentifier> oids) {
    Args.notNull(oids, "oids");
    List<String> list = new ArrayList<>(oids.size());
    for (ASN1ObjectIdentifier m : oids) {
      list.add(m.getId());
    }
    Collections.sort(list);

    List<ASN1ObjectIdentifier> sorted = new ArrayList<>(oids.size());
    for (String m : list) {
      for (ASN1ObjectIdentifier n : oids) {
        if (m.equals(n.getId()) && !sorted.contains(n)) {
          sorted.add(n);
        }
      }
    }
    return sorted;
  }

  public static byte[] getCoreExtValue(Extensions extensions, ASN1ObjectIdentifier extnType) {
    Args.notNull(extensions, "extensions");
    Args.notNull(extnType, "extnType");
    Extension extn = extensions.getExtension(extnType);
    if (extn == null) {
      return null;
    }

    return extn.getExtnValue().getOctets();
  }

  /**
   * Build the certificate path. Cross certificate will not be considered.
   * @param targetCert certificate for which the certificate path will be built
   * @param certs collection of certificates.
   * @return the certificate path
   * @throws CertPathBuilderException
   *           If cannot build a valid certificate path.
   */
  public static X509Cert[] buildCertPath(X509Cert targetCert, Collection<X509Cert> certs)
      throws CertPathBuilderException {
    return buildCertPath(targetCert, certs, true);
  }

  /**
   * Build the certificate path. Cross certificate will not be considered.
   * @param targetCert certificate for which the certificate path will be built
   * @param certs collection of certificates.
   * @param includeTargetCert whether to include {@code targetCert} in the result.
   * @return the certificate path
   * @throws CertPathBuilderException
   *           If cannot build a valid certificate path.
   */
  public static X509Cert[] buildCertPath(X509Cert targetCert,
      Collection<X509Cert> certs, boolean includeTargetCert)
          throws CertPathBuilderException {
    Args.notNull(targetCert, "cert");
    List<X509Cert> certChain = new LinkedList<>();
    certChain.add(targetCert);
    try {
      if (certs != null && !targetCert.isSelfSigned()) {
        while (true) {
          X509Cert caCert = getCaCertOf(certChain.get(certChain.size() - 1), certs);
          if (caCert == null) {
            break;
          }
          certChain.add(caCert);
          if (caCert.isSelfSigned()) {
            // reaches root self-signed certificate
            break;
          }
        }
      }
    } catch (CertificateEncodingException ex) {
      LOG.warn("CertificateEncodingException: {}", ex.getMessage());
    }

    final int n = certChain.size();
    if (n == 1) {
      return includeTargetCert ? certChain.toArray(new X509Cert[0]) : null;
    }

    // check the basicConstrains
    int minPathLen;
    int targetPathLen = targetCert.getBasicConstraints();
    if (targetPathLen != -1) {
      // targetCert is non-CA
      minPathLen = 0;
    } else {
      if (targetPathLen == Integer.MAX_VALUE) {
        minPathLen = 1;
      } else {
        minPathLen = targetPathLen + 1;
      }
    }

    for (int i = 1; i < n; i++) {
      int pathLen = certChain.get(i).getBasicConstraints();
      if (pathLen < minPathLen) {
        throw new CertPathBuilderException("PathLen too small of certificate "
            + certChain.get(i).getSubject().toString());
      } else {
        minPathLen = 1 + (pathLen == Integer.MAX_VALUE ? minPathLen : pathLen);
      }
    }

    if (!includeTargetCert) {
      certChain.remove(0);
    }

    return certChain.toArray(new X509Cert[0]);
  } // method buildCertPath

  public static String encodeCertificates(X509Cert[] certchain)
      throws CertificateException, IOException {
    if (CollectionUtil.isEmpty(certchain)) {
      return null;
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < certchain.length; i++) {
      X509Cert m = certchain[i];
      if (i != 0) {
        sb.append("\r\n");
      }
      sb.append(new String(PemEncoder.encode(m.getEncoded(), PemLabel.CERTIFICATE)));
    }
    return sb.toString();
  }

  public static List<X509Cert> listCertificates(String encodedCerts)
      throws CertificateException, IOException {
    List<X509Cert> certs = new LinkedList<>();
    try (BufferedReader reader = new BufferedReader(new StringReader(encodedCerts))) {
      String line;

      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      while ((line = reader.readLine()) != null) {
        if (BEGIN_CERTIFICATE.equals(line)) {
          bout.reset();
        } else if (END_CERTIFICATE.equals(line)) {
          certs.add(parseCert(bout.toByteArray()));
          bout.reset();
        } else {
          bout.write(StringUtil.toUtf8Bytes(line));
        }
      }
    }
    return certs;
  }

  private static X509Cert getCaCertOf(X509Cert cert, Collection<X509Cert> caCerts)
      throws CertificateEncodingException {
    Args.notNull(cert, "cert");
    if (cert.isSelfSigned()) {
      return null;
    }

    for (X509Cert caCert : caCerts) {
      if (!issues(caCert, cert)) {
        continue;
      }

      try {
        cert.verify(caCert.getPublicKey());
        return caCert;
      } catch (Exception ex) {
        LOG.warn("could not verify certificate: {}", ex.getMessage());
      }
    }

    return null;
  }

  public static boolean issues(X509Cert issuerCert, X509Cert cert)
      throws CertificateEncodingException {
    Args.notNull(issuerCert, "issuerCert");
    Args.notNull(cert, "cert");

    boolean issues = issuerCert.getSubject().equals(cert.getIssuer());
    if (issues) {
      byte[] ski = issuerCert.getSubjectKeyId();
      byte[] aki = cert.getAuthorityKeyId();
      if (ski != null && aki != null) {
        issues = Arrays.equals(ski, aki);
      }
    }

    if (issues) {
      long issuerNotBefore = issuerCert.getNotBefore().getTime();
      long issuerNotAfter = issuerCert.getNotAfter().getTime();
      long notBefore = cert.getNotBefore().getTime();
      issues = notBefore <= issuerNotAfter && notBefore >= issuerNotBefore;
    }

    return issues;
  } // method issues

  public static SubjectPublicKeyInfo toRfc3279Style(SubjectPublicKeyInfo publicKeyInfo)
      throws InvalidKeySpecException {
    Args.notNull(publicKeyInfo, "publicKeyInfo");
    ASN1ObjectIdentifier algOid = publicKeyInfo.getAlgorithm().getAlgorithm();
    ASN1Encodable keyParameters = publicKeyInfo.getAlgorithm().getParameters();

    if (PKCSObjectIdentifiers.rsaEncryption.equals(algOid)) {
      if (DERNull.INSTANCE.equals(keyParameters)) {
        return publicKeyInfo;
      } else {
        AlgorithmIdentifier keyAlgId = new AlgorithmIdentifier(algOid, DERNull.INSTANCE);
        return new SubjectPublicKeyInfo(keyAlgId, publicKeyInfo.getPublicKeyData().getBytes());
      }
    } else if (X9ObjectIdentifiers.id_dsa.equals(algOid)) {
      if (keyParameters == null) {
        return publicKeyInfo;
      } else if (DERNull.INSTANCE.equals(keyParameters)) {
        AlgorithmIdentifier keyAlgId = new AlgorithmIdentifier(algOid);
        return new SubjectPublicKeyInfo(keyAlgId, publicKeyInfo.getPublicKeyData().getBytes());
      } else {
        try {
          DSAParameter.getInstance(keyParameters);
        } catch (IllegalArgumentException ex) {
          throw new InvalidKeySpecException("keyParameters is not null and Dss-Parms");
        }
        return publicKeyInfo;
      }
    } else if (X9ObjectIdentifiers.id_ecPublicKey.equals(algOid)) {
      if (keyParameters == null) {
        throw new InvalidKeySpecException("keyParameters is not an OBJECT IDENTIFIER");
      }
      try {
        ASN1ObjectIdentifier.getInstance(keyParameters);
      } catch (IllegalArgumentException ex) {
        throw new InvalidKeySpecException("keyParameters is not an OBJECT IDENTIFIER");
      }
      return publicKeyInfo;
    } else if (EdECConstants.isEdwardsOrMontgomeryCurve(algOid)) {
      if (keyParameters == null) {
        return publicKeyInfo;
      } else {
        return new SubjectPublicKeyInfo(new AlgorithmIdentifier(algOid),
            publicKeyInfo.getPublicKeyData().getBytes());
      }
    } else {
      return publicKeyInfo;
    }
  } // method toRfc3279Style

  public static String cutText(String text, int maxLen) {
    Args.notNull(text, "text");
    if (text.length() <= maxLen) {
      return text;
    }
    return StringUtil.concat(text.substring(0, maxLen - 13), "...skipped...");
  }

  public static String cutX500Name(X500Name name, int maxLen) {
    String text = getRfc4519Name(name);
    return cutText(text, maxLen);
  }

  public static Extension createExtnSubjectAltName(List<String> taggedValues, boolean critical)
      throws BadInputException {
    GeneralNames names = createGeneralNames(taggedValues);
    if (names == null) {
      return null;
    }

    try {
      return new Extension(Extension.subjectAlternativeName, critical, names.getEncoded());
    } catch (IOException ex) {
      throw new IllegalStateException(ex.getMessage(), ex);
    }
  } // method createExtnSubjectAltName

  public static Extension createExtnSubjectInfoAccess(List<String> accessMethodAndLocations,
      boolean critical) throws BadInputException {
    if (CollectionUtil.isEmpty(accessMethodAndLocations)) {
      return null;
    }

    ASN1EncodableVector vector = new ASN1EncodableVector();
    for (String accessMethodAndLocation : accessMethodAndLocations) {
      vector.add(createAccessDescription(accessMethodAndLocation));
    }
    ASN1Sequence seq = new DERSequence(vector);
    try {
      return new Extension(Extension.subjectInfoAccess, critical, seq.getEncoded());
    } catch (IOException ex) {
      throw new IllegalStateException(ex.getMessage(), ex);
    }
  } // method createExtnSubjectInfoAccess

  private static AccessDescription createAccessDescription(String accessMethodAndLocation)
      throws BadInputException {
    Args.notNull(accessMethodAndLocation, "accessMethodAndLocation");
    ConfPairs pairs;
    try {
      pairs = new ConfPairs(accessMethodAndLocation);
    } catch (IllegalArgumentException ex) {
      throw new BadInputException("invalid accessMethodAndLocation " + accessMethodAndLocation);
    }

    Set<String> oids = pairs.names();
    if (oids == null || oids.size() != 1) {
      throw new BadInputException("invalid accessMethodAndLocation " + accessMethodAndLocation);
    }

    String accessMethodS = oids.iterator().next();
    String taggedValue = pairs.value(accessMethodS);
    ASN1ObjectIdentifier accessMethod = new ASN1ObjectIdentifier(accessMethodS);

    GeneralName location = createGeneralName(taggedValue);
    return new AccessDescription(accessMethod, location);
  } // method createAccessDescription

  private static GeneralNames createGeneralNames(List<String> taggedValues)
      throws BadInputException {
    if (CollectionUtil.isEmpty(taggedValues)) {
      return null;
    }

    int len = taggedValues.size();
    GeneralName[] names = new GeneralName[len];
    for (int i = 0; i < len; i++) {
      names[i] = createGeneralName(taggedValues.get(i));
    }
    return new GeneralNames(names);
  } // method createGeneralNames

  /**
  * Creates {@link GeneralName} from the tagged value.
  * @param taggedValue [tag]value, and the value for tags otherName and ediPartyName is
  *     type=value.
  * @return the created {@link GeneralName}
  * @throws BadInputException
  *         if the {@code taggedValue} is invalid.
  */
  private static GeneralName createGeneralName(String taggedValue) throws BadInputException {
    Args.notBlank(taggedValue, "taggedValue");

    int tag = -1;
    String value = null;
    if (taggedValue.charAt(0) == '[') {
      int idx = taggedValue.indexOf(']', 1);
      if (idx > 1 && idx < taggedValue.length() - 1) {
        String tagS = taggedValue.substring(1, idx);
        try {
          tag = Integer.parseInt(tagS);
          value = taggedValue.substring(idx + 1);
        } catch (NumberFormatException ex) {
          throw new BadInputException("invalid tag '" + tagS + "'");
        }
      }
    }

    if (tag == -1) {
      throw new BadInputException("invalid taggedValue " + taggedValue);
    }

    switch (tag) {
      case GeneralName.otherName:
        if (value == null) {
          throw new BadInputException("invalid otherName: no value specified");
        }

        int idxSep = value.indexOf("=");
        if (idxSep == -1 || idxSep == 0 || idxSep == value.length() - 1) {
          throw new BadInputException("invalid otherName " + value);
        }
        String otherTypeOid = value.substring(0, idxSep);
        ASN1ObjectIdentifier type = new ASN1ObjectIdentifier(otherTypeOid);
        String otherValue = value.substring(idxSep + 1);
        ASN1EncodableVector vector = new ASN1EncodableVector();
        vector.add(type);
        vector.add(new DERTaggedObject(true, 0, new DERUTF8String(otherValue)));
        DERSequence seq = new DERSequence(vector);
        return new GeneralName(GeneralName.otherName, seq);
      case GeneralName.rfc822Name:
        return new GeneralName(tag, value);
      case GeneralName.dNSName:
        return new GeneralName(tag, value);
      case GeneralName.directoryName:
        X500Name x500Name = reverse(new X500Name(value));
        return new GeneralName(GeneralName.directoryName, x500Name);
      case GeneralName.ediPartyName:
        if (value == null) {
          throw new BadInputException("invalid ediPartyName: no value specified");
        }
        idxSep = value.indexOf("=");
        if (idxSep == -1 || idxSep == value.length() - 1) {
          throw new BadInputException("invalid ediPartyName " + value);
        }
        String nameAssigner = (idxSep == 0) ? null : value.substring(0, idxSep);
        String partyName = value.substring(idxSep + 1);
        vector = new ASN1EncodableVector();
        if (nameAssigner != null) {
          vector.add(new DERTaggedObject(false, 0, new DirectoryString(nameAssigner)));
        }
        vector.add(new DERTaggedObject(false, 1, new DirectoryString(partyName)));
        seq = new DERSequence(vector);
        return new GeneralName(GeneralName.ediPartyName, seq);
      case GeneralName.uniformResourceIdentifier:
        return new GeneralName(tag, value);
      case GeneralName.iPAddress:
        return new GeneralName(tag, value);
      case GeneralName.registeredID:
        return new GeneralName(tag, value);
      default:
        throw new IllegalStateException("unsupported tag " + tag);
    } // end switch (tag)
  } // method createGeneralName

  public static String formatCert(X509Cert cert, boolean verbose) {
    if (cert == null) {
      return "  null";
    }

    StringBuilder sb = new StringBuilder(verbose ? 1000 : 100);
    sb.append("  issuer:  ")
      .append(X509Util.getRfc4519Name(cert.getIssuer())).append('\n');
    sb.append("  serialNumber: ").append(LogUtil.formatCsn(cert.getSerialNumber())).append('\n');
    sb.append("  subject: ")
      .append(X509Util.getRfc4519Name(cert.getSubject())).append('\n');
    sb.append("  notBefore: ").append(cert.getNotBefore()).append("\n");
    sb.append("  notAfter:  ").append(cert.getNotAfter());

    if (verbose) {
      sb.append("\n  encoded: ");
      sb.append(Base64.encodeToString(cert.getEncoded()));
    }

    return sb.toString();
  } // method formatCert

}
