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

package org.xipki.ca.api.mgmt;

import org.xipki.util.Args;

/**
 * How CA assigns the notAfter field in the certificate if the requested notAfter is
 * after CA's validity.
 * <ul>
 *  <li>STRICT: the enrollment request will be rejected.</li>
 *  <li>LAX: Use the requested notAfter.</li>
 *  <li>CUTOFF: Use CA's notAfter.</li>
 * </ul>
 * @author Lijun Liao
 * @since 2.0.0
 */

public enum ValidityMode {

  STRICT,
  LAX,
  CUTOFF;

  public static ValidityMode forName(String text) {
    Args.notNull(text, "text");

    for (ValidityMode value : values()) {
      if (value.name().equalsIgnoreCase(text)) {
        return value;
      }
    }

    throw new IllegalArgumentException("invalid ValidityMode " + text);
  }

}
