/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2013 - 2016 Lijun Liao
 * Author: Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 *
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.commons.security.shell.p12;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERGeneralizedTime;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.console.karaf.completer.FilePathCompleter;
import org.xipki.commons.security.api.ConcurrentContentSigner;
import org.xipki.commons.security.api.ObjectIdentifiers;
import org.xipki.commons.security.api.SignatureAlgoControl;
import org.xipki.commons.security.api.util.SignerConfUtil;
import org.xipki.commons.security.shell.CertRequestGenCommandSupport;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

@Command(scope = "xipki-tk", name = "req-p12-complex",
        description = "generate complex PKCS#10 request with PKCS#12 keystore")
@Service
public class P12ComplexCertRequestGenCmd extends CertRequestGenCommandSupport {

    @Option(name = "--p12",
            required = true,
            description = "PKCS#12 keystore file\n"
                    + "(required)")
    @Completion(FilePathCompleter.class)
    private String p12File;

    @Option(name = "--password",
            description = "password of the PKCS#12 file")
    private String password;

    private char[] getPassword() {
        char[] pwdInChar = readPasswordIfNotSet(password);
        if (pwdInChar != null) {
            password = new String(pwdInChar);
        }
        return pwdInChar;
    }

    public KeyStore getKeyStore()
    throws Exception {
        KeyStore ks;
        try (FileInputStream in = new FileInputStream(expandFilepath(p12File))) {
            ks = KeyStore.getInstance("PKCS12", "BC");
            ks.load(in, getPassword());
        }
        return ks;
    }

    @Override
    protected ConcurrentContentSigner getSigner(
            final SignatureAlgoControl signatureAlgoControl)
    throws Exception {
        ParamUtil.requireNonNull("signatureAlgoControl", signatureAlgoControl);
        char[] pwd = getPassword();
        String signerConf = SignerConfUtil.getKeystoreSignerConfWithoutAlgo(p12File,
                new String(pwd));
        return securityFactory.createSigner(
                "PKCS12", signerConf, hashAlgo, signatureAlgoControl, (X509Certificate[]) null);
    }

    @Override
    protected X500Name getSubject(
            final String subject) {
        X500Name name = new X500Name(subject);
        List<RDN> list = new LinkedList<>();
        RDN[] rs = name.getRDNs();
        for (RDN m : rs) {
            list.add(m);
        }

        ASN1ObjectIdentifier id;

        // dateOfBirth
        id = ObjectIdentifiers.DN_DATE_OF_BIRTH;
        RDN[] rdns = name.getRDNs(id);

        if (rdns == null || rdns.length == 0) {
            ASN1Encodable atvValue = new DERGeneralizedTime("19950102000000Z");
            RDN rdn = new RDN(id, atvValue);
            list.add(rdn);
        }

        // postalAddress
        id = ObjectIdentifiers.DN_POSTAL_ADDRESS;
        rdns = name.getRDNs(id);

        if (rdns == null || rdns.length == 0) {
            ASN1EncodableVector vec = new ASN1EncodableVector();
            vec.add(new DERUTF8String("my street 1"));
            vec.add(new DERUTF8String("12345 Germany"));

            ASN1Sequence atvValue = new DERSequence(vec);
            RDN rdn = new RDN(id, atvValue);
            list.add(rdn);
        }

        // DN_UNIQUE_IDENTIFIER
        id = ObjectIdentifiers.DN_UNIQUE_IDENTIFIER;
        rdns = name.getRDNs(id);

        if (rdns == null || rdns.length == 0) {
            DERUTF8String atvValue = new DERUTF8String("abc-def-ghi");
            RDN rdn = new RDN(id, atvValue);
            list.add(rdn);
        }

        return new X500Name(list.toArray(new RDN[0]));
    }

}
