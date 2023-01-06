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

import org.xipki.pkcs11.Functions;

/**
 * Exception indicates unsupported PKCS#11 mechanism.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

public class P11UnsupportedMechanismException extends P11TokenException {

  public P11UnsupportedMechanismException(long mechanism, P11SlotIdentifier slotId) {
    super("mechanism " + Functions.ckmCodeToName(mechanism) + " is not supported by PKCS11 slot " + slotId);
  }

  public P11UnsupportedMechanismException(long mechanism, P11IdentityId identityId) {
    super("mechanism " + Functions.ckmCodeToName(mechanism) + " is not supported by PKCS11 identity " + identityId);
  }

  public P11UnsupportedMechanismException(String message) {
    super(message);
  }

}
