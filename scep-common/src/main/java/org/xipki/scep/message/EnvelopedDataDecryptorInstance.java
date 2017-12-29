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

package org.xipki.scep.message;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import org.bouncycastle.cms.KeyTransRecipient;
import org.bouncycastle.cms.RecipientId;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientId;
import org.xipki.scep.util.ScepUtil;

/**
 * @author Lijun Liao
 */

public final class EnvelopedDataDecryptorInstance {

    private final RecipientId recipientId;

    private final KeyTransRecipient recipient;

    public EnvelopedDataDecryptorInstance(X509Certificate recipientCert, PrivateKey privKey) {
        ScepUtil.requireNonNull("recipientCert", recipientCert);
        ScepUtil.requireNonNull("privKey", privKey);

        this.recipientId = new JceKeyTransRecipientId(recipientCert);
        this.recipient = new JceKeyTransEnvelopedRecipient(privKey);
    }

    public KeyTransRecipient recipient() {
        return recipient;
    }

    public RecipientId recipientId() {
        return recipientId;
    }

}
