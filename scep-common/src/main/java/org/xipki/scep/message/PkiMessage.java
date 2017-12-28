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

import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.cms.CMSAbsentContent;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.RecipientInfoGenerator;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.SimpleAttributeTableGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.xipki.scep.exception.MessageEncodingException;
import org.xipki.scep.transaction.FailInfo;
import org.xipki.scep.transaction.MessageType;
import org.xipki.scep.transaction.Nonce;
import org.xipki.scep.transaction.PkiStatus;
import org.xipki.scep.transaction.TransactionId;
import org.xipki.scep.util.ScepUtil;

/**
 * @author Lijun Liao
 */

public class PkiMessage {

    private static final Set<ASN1ObjectIdentifier> SCEP_ATTR_TYPES
            = new HashSet<ASN1ObjectIdentifier>();

    private final MessageType messageType;

    private final Nonce senderNonce;

    private final TransactionId transactionId;

    private Nonce recipientNonce;

    private PkiStatus pkiStatus;

    private FailInfo failInfo;

    private ASN1Encodable messageData;

    private final Map<ASN1ObjectIdentifier, ASN1Encodable> signedAttributes =
            new HashMap<ASN1ObjectIdentifier, ASN1Encodable>();

    private final Map<ASN1ObjectIdentifier, ASN1Encodable> unsignedAttributes =
            new HashMap<ASN1ObjectIdentifier, ASN1Encodable>();

    static {
        SCEP_ATTR_TYPES.add(ScepObjectIdentifiers.ID_FAILINFO);
        SCEP_ATTR_TYPES.add(ScepObjectIdentifiers.ID_MESSAGE_TYPE);
        SCEP_ATTR_TYPES.add(ScepObjectIdentifiers.ID_PKI_STATUS);
        SCEP_ATTR_TYPES.add(ScepObjectIdentifiers.ID_RECIPIENT_NONCE);
        SCEP_ATTR_TYPES.add(ScepObjectIdentifiers.ID_SENDER_NONCE);
        SCEP_ATTR_TYPES.add(ScepObjectIdentifiers.ID_TRANSACTION_ID);
        SCEP_ATTR_TYPES.add(CMSAttributes.signingTime);
    }

    public PkiMessage(final TransactionId transactionId, final MessageType messageType) {
        this(transactionId, messageType, Nonce.randomNonce());
    }

    public PkiMessage(final TransactionId transactionId, final MessageType messageType,
            final Nonce senderNonce) {
        this.transactionId = ScepUtil.requireNonNull("transactionId", transactionId);
        this.messageType = ScepUtil.requireNonNull("messageType", messageType);
        this.senderNonce = ScepUtil.requireNonNull("senderNonce", senderNonce);
    }

    public TransactionId transactionId() {
        return transactionId;
    }

    public Nonce senderNonce() {
        return senderNonce;
    }

    public final MessageType messageType() {
        return messageType;
    }

    public Nonce recipientNonce() {
        return recipientNonce;
    }

    public void setRecipientNonce(final Nonce recipientNonce) {
        this.recipientNonce = recipientNonce;
    }

    public PkiStatus pkiStatus() {
        return pkiStatus;
    }

    public void setPkiStatus(final PkiStatus pkiStatus) {
        this.pkiStatus = pkiStatus;
    }

    public FailInfo failInfo() {
        return failInfo;
    }

    public void setFailInfo(final FailInfo failInfo) {
        this.failInfo = failInfo;
    }

    public ASN1Encodable messageData() {
        return messageData;
    }

    public void setMessageData(final ASN1Encodable messageData) {
        this.messageData = messageData;
    }

    public ASN1Encodable addSignendAttribute(final ASN1ObjectIdentifier type,
            final ASN1Encodable value) {
        if (SCEP_ATTR_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Adding SCEP attribute via addSignedAttribute() method is not permitted");
        }
        return signedAttributes.put(type, value);
    }

    public ASN1Encodable addUnsignendAttribute(final ASN1ObjectIdentifier type,
            final ASN1Encodable value) {
        return unsignedAttributes.put(type, value);
    }

    public ASN1Encodable removeSignedAttribute(final ASN1ObjectIdentifier type) {
        return signedAttributes.remove(type);
    }

    public ASN1Encodable removeUnsignedAttribute(final ASN1ObjectIdentifier type) {
        return unsignedAttributes.remove(type);
    }

    public ASN1Encodable getSignedAtrributeValue(final ASN1ObjectIdentifier type) {
        return signedAttributes.get(type);
    }

    public ASN1Encodable getUnsignedAtrributeValue(final ASN1ObjectIdentifier type) {
        return unsignedAttributes.get(type);
    }

    private AttributeTable getSignedAttributes() {
        ASN1EncodableVector vec = new ASN1EncodableVector();
        // messageType
        addAttribute(vec, ScepObjectIdentifiers.ID_MESSAGE_TYPE,
                new DERPrintableString(Integer.toString(messageType.code())));

        // senderNonce
        addAttribute(vec, ScepObjectIdentifiers.ID_SENDER_NONCE,
                new DEROctetString(senderNonce.bytes()));

        // transactionID
        addAttribute(vec, ScepObjectIdentifiers.ID_TRANSACTION_ID,
                new DERPrintableString(transactionId.id()));

        // failInfo
        if (failInfo != null) {
            addAttribute(vec, ScepObjectIdentifiers.ID_FAILINFO,
                    new DERPrintableString(Integer.toString(failInfo.code())));
        }

        // pkiStatus
        if (pkiStatus != null) {
            addAttribute(vec, ScepObjectIdentifiers.ID_PKI_STATUS,
                    new DERPrintableString(Integer.toString(pkiStatus.code())));
        }

        // recipientNonce
        if (recipientNonce != null) {
            addAttribute(vec, ScepObjectIdentifiers.ID_RECIPIENT_NONCE,
                    new DEROctetString(recipientNonce.bytes()));
        }

        for (ASN1ObjectIdentifier type : signedAttributes.keySet()) {
            addAttribute(vec, type, signedAttributes.get(type));
        }
        return new AttributeTable(vec);
    }

    private AttributeTable getUnsignedAttributes() {
        if (unsignedAttributes.isEmpty()) {
            return null;
        }
        ASN1EncodableVector vec = new ASN1EncodableVector();

        for (ASN1ObjectIdentifier type : unsignedAttributes.keySet()) {
            addAttribute(vec, type, unsignedAttributes.get(type));
        }
        return new AttributeTable(vec);
    }

    public ContentInfo encode(final PrivateKey signerKey, final String signatureAlgorithm,
            final X509Certificate signerCert, final X509Certificate[] signerCertSet,
            final X509Certificate recipientCert, final ASN1ObjectIdentifier encAlgId)
            throws MessageEncodingException {
        ScepUtil.requireNonNull("signerKey", signerKey);
        ContentSigner signer;
        try {
            signer = new JcaContentSignerBuilder(signatureAlgorithm).build(signerKey);
        } catch (OperatorCreationException ex) {
            throw new MessageEncodingException(ex);
        }
        return encode(signer, signerCert, signerCertSet, recipientCert, encAlgId);
    }

    public ContentInfo encode(final ContentSigner signer, final X509Certificate signerCert,
            final X509Certificate[] cmsCertSet, final X509Certificate recipientCert,
            final ASN1ObjectIdentifier encAlgId) throws MessageEncodingException {
        ScepUtil.requireNonNull("signer", signer);
        ScepUtil.requireNonNull("signerCert", signerCert);
        if (messageData != null) {
            ScepUtil.requireNonNull("recipientCert", recipientCert);
            ScepUtil.requireNonNull("encAlgId", encAlgId);
        }

        CMSTypedData content;
        if (messageData == null) {
            content = new CMSAbsentContent();
        } else {
            CMSEnvelopedData envelopedData = encrypt(recipientCert, encAlgId);
            byte[] encoded;
            try {
                encoded = envelopedData.getEncoded();
            } catch (IOException ex) {
                throw new MessageEncodingException(ex);
            }
            content = new CMSProcessableByteArray(CMSObjectIdentifiers.envelopedData, encoded);
        }

        try {
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();

            // signerInfo
            JcaSignerInfoGeneratorBuilder signerInfoBuilder = new JcaSignerInfoGeneratorBuilder(
                    new BcDigestCalculatorProvider());

            signerInfoBuilder.setSignedAttributeGenerator(
                    new DefaultSignedAttributeTableGenerator(getSignedAttributes()));

            AttributeTable attrTable = getUnsignedAttributes();
            if (attrTable != null) {
                signerInfoBuilder.setUnsignedAttributeGenerator(
                        new SimpleAttributeTableGenerator(attrTable));
            }

            // certificateSet
            ScepUtil.addCmsCertSet(generator, cmsCertSet);

            SignerInfoGenerator signerInfo;
            try {
                signerInfo = signerInfoBuilder.build(signer, signerCert);
            } catch (Exception ex) {
                throw new MessageEncodingException(ex);
            }

            generator.addSignerInfoGenerator(signerInfo);

            CMSSignedData signedData = generator.generate(content, true);
            return signedData.toASN1Structure();
        } catch (CMSException ex) {
            throw new MessageEncodingException(ex);
        } catch (Exception ex) {
            throw new MessageEncodingException(ex);
        }
    } // method encode

    private CMSEnvelopedData encrypt(final X509Certificate recipient,
            final ASN1ObjectIdentifier encAlgId) throws MessageEncodingException {
        ScepUtil.requireNonNull("recipient", recipient);
        ScepUtil.requireNonNull("encAlgId", encAlgId);

        byte[] messageDataBytes;
        try {
            messageDataBytes = messageData.toASN1Primitive().getEncoded();
        } catch (IOException ex) {
            throw new MessageEncodingException(ex);
        }

        CMSEnvelopedDataGenerator edGenerator = new CMSEnvelopedDataGenerator();
        CMSTypedData envelopable = new CMSProcessableByteArray(messageDataBytes);
        RecipientInfoGenerator recipientGenerator;
        try {
            recipientGenerator = new JceKeyTransRecipientInfoGenerator(recipient);
        } catch (CertificateEncodingException ex) {
            throw new MessageEncodingException(ex);
        }
        edGenerator.addRecipientInfoGenerator(recipientGenerator);
        try {
            OutputEncryptor encryptor = new JceCMSContentEncryptorBuilder(encAlgId).build();
            CMSEnvelopedData pkcsPkiEnvelope = edGenerator.generate(envelopable, encryptor);
            return pkcsPkiEnvelope;
        } catch (CMSException ex) {
            throw new MessageEncodingException(ex);
        }
    }

    private static void addAttribute(final ASN1EncodableVector vector,
            final ASN1ObjectIdentifier attrType, final ASN1Encodable attrValue) {
        vector.add(new Attribute(attrType, new DERSet(attrValue)));
    }

}
