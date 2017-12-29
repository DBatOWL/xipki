/*
 *
 * Copyright (c) 2013 - 2018 Lijun Liao
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

package org.xipki.scep.client;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import org.xipki.scep.crypto.ScepHashAlgoType;

/**
 * @author Lijun Liao
 */

public abstract class FingerprintCertificateValidator implements CaCertValidator {

    private static final ScepHashAlgoType DEFAULT_HASHALGO = ScepHashAlgoType.SHA256;

    private ScepHashAlgoType hashAlgo;

    public ScepHashAlgoType hashAlgo() {
        return hashAlgo;
    }

    public void setHashAlgo(ScepHashAlgoType hashAlgo) {
        this.hashAlgo = hashAlgo;
    }

    @Override
    public boolean isTrusted(X509Certificate cert) {
        ScepHashAlgoType algo = (hashAlgo == null) ? DEFAULT_HASHALGO : hashAlgo;
        byte[] actual;
        try {
            actual = algo.digest(cert.getEncoded());
        } catch (CertificateEncodingException ex) {
            return false;
        }

        return isCertTrusted(algo, actual);
    }

    /**
     *
     * @param hashAlgo
     *          Hash algorithm. Must not be {@code null}.
     * @param hashValue
     *          Hash value of the certificate to be checked. Must not be {@code null}.
     * @return
     */
    protected abstract boolean isCertTrusted(ScepHashAlgoType hashAlgo,
            byte[] hashValue);

}
