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

package org.xipki.ca.certprofile.xijson;

import org.bouncycastle.asn1.*;
import org.xipki.util.Args;

/**
 * Type of the DirectoryString.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

public enum DirectoryStringType {

  teletexString,
  printableString,
  utf8String,
  bmpString;

  public ASN1Encodable createDirectoryString(String text) {
    Args.notNull(text, "text");

    if (teletexString == this) {
      return new DERT61String(text);
    } else if (printableString == this) {
      return new DERPrintableString(text);
    } else if (utf8String == this) {
      return new DERUTF8String(text);
    } else if (bmpString == this) {
      return new DERBMPString(text);
    } else {
      throw new IllegalStateException("should not reach here, unknown DirectoryStringType " + this.name());
    }
  } // method createDirectoryString

}
