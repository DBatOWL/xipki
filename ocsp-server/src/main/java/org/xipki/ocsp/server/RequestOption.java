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

package org.xipki.ocsp.server;

import org.xipki.security.CertpathValidationModel;
import org.xipki.security.HashAlgo;
import org.xipki.security.Securities.KeystoreConf;
import org.xipki.security.X509Cert;
import org.xipki.security.util.KeyUtil;
import org.xipki.security.util.X509Util;
import org.xipki.util.InvalidConfException;
import org.xipki.util.IoUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import static org.xipki.util.Args.notNull;

/**
 * OCSP request option.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

public class RequestOption {

  static final Set<HashAlgo> SUPPORTED_HASH_ALGORITHMS = new HashSet<>();

  static {
    SUPPORTED_HASH_ALGORITHMS.add(HashAlgo.SHA1);
    SUPPORTED_HASH_ALGORITHMS.add(HashAlgo.SHA224);
    SUPPORTED_HASH_ALGORITHMS.add(HashAlgo.SHA256);
    SUPPORTED_HASH_ALGORITHMS.add(HashAlgo.SHA384);
    SUPPORTED_HASH_ALGORITHMS.add(HashAlgo.SHA512);
    SUPPORTED_HASH_ALGORITHMS.add(HashAlgo.SHA3_224);
    SUPPORTED_HASH_ALGORITHMS.add(HashAlgo.SHA3_256);
    SUPPORTED_HASH_ALGORITHMS.add(HashAlgo.SHA3_384);
    SUPPORTED_HASH_ALGORITHMS.add(HashAlgo.SHA3_512);
    SUPPORTED_HASH_ALGORITHMS.add(HashAlgo.SHAKE128);
    SUPPORTED_HASH_ALGORITHMS.add(HashAlgo.SHAKE256);
    SUPPORTED_HASH_ALGORITHMS.add(HashAlgo.SM3);
  }

  private final boolean supportsHttpGet;

  private final boolean signatureRequired;

  private final boolean validateSignature;

  private final int maxRequestListCount;

  private final int maxRequestSize;

  private final Collection<Integer> versions;

  private final QuadrupleState nonceOccurrence;

  private final int nonceMinLen;

  private final int nonceMaxLen;

  private final Set<HashAlgo> hashAlgos;

  private final Set<X509Cert> trustAnchors;

  private final Set<X509Cert> certs;

  private final CertpathValidationModel certpathValidationModel;

  RequestOption(OcspServerConf.RequestOption conf)
      throws InvalidConfException {
    notNull(conf, "conf");

    supportsHttpGet = conf.isSupportsHttpGet();
    signatureRequired = conf.isSignatureRequired();
    validateSignature = conf.isValidateSignature();

    // Request nonce
    OcspServerConf.Nonce nonceConf = conf.getNonce();
    nonceOccurrence = conf.getNonce().getOccurrence();

    nonceMinLen = nonceConf.getMinLen() != null ? nonceConf.getMinLen() : 4;

    nonceMaxLen = nonceConf.getMaxLen() != null ? nonceConf.getMaxLen() : 96;

    if (nonceMinLen < 0) {
      throw new InvalidConfException("invalid nonceMinLen (<1): " + nonceMinLen);
    }

    if (nonceMinLen > nonceMaxLen) {
      throw new InvalidConfException("nonceMinLen > nonceMaxLen");
    }

    maxRequestListCount = conf.getMaxRequestListCount();
    if (maxRequestListCount < 1) {
      throw new InvalidConfException("invalid maxRequestListCount " + maxRequestListCount);
    }

    maxRequestSize = conf.getMaxRequestSize();
    if (maxRequestSize < 100) {
      throw new InvalidConfException("invalid maxRequestSize " + maxRequestSize);
    }

    // Request versions

    versions = new HashSet<>();
    for (String m : conf.getVersions()) {
      if ("v1".equalsIgnoreCase(m)) {
        versions.add(0);
      } else {
        throw new InvalidConfException("invalid OCSP request version '" + m + "'");
      }
    }

    // Request hash algorithms
    hashAlgos = new HashSet<>();
    if (conf.getHashAlgorithms().isEmpty()) {
      hashAlgos.addAll(SUPPORTED_HASH_ALGORITHMS);
    } else {
      for (String token : conf.getHashAlgorithms()) {
        HashAlgo algo;
        try {
          algo = HashAlgo.getInstance(token);
        } catch (NoSuchAlgorithmException ex) {
          throw new InvalidConfException(ex.getMessage());
        }

        if (SUPPORTED_HASH_ALGORITHMS.contains(algo)) {
          hashAlgos.add(algo);
        } else {
          throw new InvalidConfException("hash algorithm " + token + " is unsupported");
        }
      }
    }

    // certpath validation
    OcspServerConf.CertpathValidation certpathConf = conf.getCertpathValidation();
    if (certpathConf == null) {
      if (validateSignature) {
        throw new InvalidConfException("certpathValidation is not specified");
      }
      trustAnchors = null;
      certs = null;
      certpathValidationModel = CertpathValidationModel.PKIX;
      return;
    }

    certpathValidationModel = certpathConf.getValidationModel();

    try {
      Set<X509Cert> tmpCerts = getCerts(certpathConf.getTrustAnchors());
      trustAnchors = new HashSet<>(tmpCerts.size());
      trustAnchors.addAll(tmpCerts);
    } catch (Exception ex) {
      throw new InvalidConfException(
          "could not initialize the trustAnchors: " + ex.getMessage(), ex);
    }

    OcspServerConf.CertCollection certsType = certpathConf.getCerts();
    try {
      certs = (certsType == null) ? null : getCerts(certsType);
    } catch (Exception ex) {
      throw new InvalidConfException("could not initialize the certs: " + ex.getMessage(), ex);
    }
  } // constructor

  public Set<HashAlgo> getHashAlgos() {
    return hashAlgos;
  }

  public boolean isSignatureRequired() {
    return signatureRequired;
  }

  public boolean isValidateSignature() {
    return validateSignature;
  }

  public boolean supportsHttpGet() {
    return supportsHttpGet;
  }

  public QuadrupleState getNonceOccurrence() {
    return nonceOccurrence;
  }

  public int getMaxRequestListCount() {
    return maxRequestListCount;
  }

  public int getMaxRequestSize() {
    return maxRequestSize;
  }

  public int getNonceMinLen() {
    return nonceMinLen;
  }

  public int getNonceMaxLen() {
    return nonceMaxLen;
  }

  public boolean allows(HashAlgo hashAlgo) {
    return hashAlgo != null && hashAlgos.contains(hashAlgo);
  }

  public CertpathValidationModel getCertpathValidationModel() {
    return certpathValidationModel;
  }

  public Set<X509Cert> getTrustAnchors() {
    return trustAnchors;
  }

  public boolean isVersionAllowed(Integer version) {
    return versions == null || versions.contains(version);
  }

  public Set<X509Cert> getCerts() {
    return certs;
  }

  private static Set<X509Cert> getCerts(OcspServerConf.CertCollection conf)
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
    notNull(conf, "conf");
    Set<X509Cert> tmpCerts = new HashSet<>();

    if (conf.getKeystore() != null) {
      KeystoreConf ksConf = conf.getKeystore();
      KeyStore trustStore = KeyUtil.getKeyStore(ksConf.getType());

      String fileName = ksConf.getKeystore().getFile();
      try (InputStream is = (fileName != null)
          ? Files.newInputStream(Paths.get(IoUtil.expandFilepath(fileName, true)))
          : new ByteArrayInputStream(ksConf.getKeystore().getBinary())) {
        char[] password = (ksConf.getPassword() == null)  ? null
            : ksConf.getPassword().toCharArray();
        trustStore.load(is, password);
      }

      Enumeration<String> aliases = trustStore.aliases();
      while (aliases.hasMoreElements()) {
        String alias = aliases.nextElement();
        if (trustStore.isCertificateEntry(alias)) {
          tmpCerts.add(
              new X509Cert(
                  (X509Certificate) trustStore.getCertificate(alias)));
        }
      }
    } else if (conf.getDir() != null) {
      File dir = new File(conf.getDir());
      File[] files = dir.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.exists() && file.isFile()) {
            tmpCerts.add(X509Util.parseCert(file));
          }
        }
      }
    } else {
      throw new IllegalStateException("should not happen, neither keystore nor dir is defined");
    }

    return tmpCerts;
  } // method getCerts

}
