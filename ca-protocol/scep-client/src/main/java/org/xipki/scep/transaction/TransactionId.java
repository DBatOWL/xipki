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

package org.xipki.scep.transaction;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.xipki.util.Args;
import org.xipki.util.Hex;

import java.io.IOException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

/**
 * Transaction Id.
 *
 * @author Lijun Liao
 */

public class TransactionId {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final String id;

  public TransactionId(String id) {
    this.id = Args.notBlank(id, "id");
  }

  private TransactionId(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw new IllegalArgumentException("bytes must not be empty");
    }
    this.id = Hex.encode(bytes);
  }

  public String getId() {
    return id;
  }

  public static TransactionId randomTransactionId() {
    byte[] bytes = new byte[20];
    RANDOM.nextBytes(bytes);
    return new TransactionId(bytes);
  }

  public static TransactionId sha1TransactionId(SubjectPublicKeyInfo spki)
      throws InvalidKeySpecException {
    Args.notNull(spki, "spki");

    byte[] encoded;
    try {
      encoded = spki.getEncoded();
    } catch (IOException ex) {
      throw new InvalidKeySpecException("IOException while ");
    }

    return sha1TransactionId(encoded);
  }

  public static TransactionId sha1TransactionId(byte[] content) {
    Args.notNull(content, "content");

    SHA1Digest dgst = new SHA1Digest();
    dgst.update(content, 0, content.length);
    byte[] digest = new byte[20];
    dgst.doFinal(digest, 0);
    return new TransactionId(digest);
  }

}
