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

package org.xipki.cmpclient.internal;

import static org.xipki.util.Args.notBlank;
import static org.xipki.util.Args.notNull;

import org.bouncycastle.asn1.pkcs.CertificationRequest;
import org.xipki.cmpclient.IdentifiedObject;

/**
 * CMP request to enroll certificate for given CSR.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

class CsrEnrollCertRequest extends IdentifiedObject {

  public static class Entry {

    private final CertificationRequest csr;

    private final String profile;

    public Entry(CertificationRequest csr, String profile) {
      this.csr = notNull(csr, "csr");
      this.profile = notNull(profile, "profile");
    }

    public CertificationRequest getCsr() {
      return csr;
    }

    public String getProfile() {
      return profile;
    }

  } // class Entry

  private final String certprofile;

  private final CertificationRequest csr;

  public CsrEnrollCertRequest(String id, String certprofile, CertificationRequest csr) {
    super(id);
    this.certprofile = notBlank(certprofile, "certprofile");
    this.csr = notNull(csr, "csr");
  }

  public CertificationRequest getCsr() {
    return csr;
  }

  public String getCertprofile() {
    return certprofile;
  }

}
