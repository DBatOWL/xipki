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

package org.xipki.security.pkcs11;

import org.bouncycastle.crypto.Digest;

import java.io.IOException;
import java.io.OutputStream;

/**
 * {@link OutputStream} with a {@link Digest} as the backend.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

public class DigestOutputStream extends OutputStream {

  private final Digest digest;

  public DigestOutputStream(Digest digest) {
    this.digest = digest;
  }

  public void reset() {
    digest.reset();
  }

  @Override
  public void write(byte[] bytes, int off, int len)
      throws IOException {
    digest.update(bytes, off, len);
  }

  @Override
  public void write(byte[] bytes)
      throws IOException {
    digest.update(bytes, 0, bytes.length);
  }

  @Override
  public void write(int oneByte)
      throws IOException {
    digest.update((byte) oneByte);
  }

  public byte[] digest() {
    byte[] result = new byte[digest.getDigestSize()];
    digest.doFinal(result, 0);
    reset();
    return result;
  }

}
