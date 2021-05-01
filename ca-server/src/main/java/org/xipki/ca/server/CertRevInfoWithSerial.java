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

package org.xipki.ca.server;

import org.xipki.security.CertRevocationInfo;
import org.xipki.security.CrlReason;
import org.xipki.util.Args;

import java.math.BigInteger;
import java.util.Date;

/**
 * Certificate revocation information with serial number and database table id.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

public class CertRevInfoWithSerial extends CertRevocationInfo
    implements Comparable<CertRevInfoWithSerial> {

  private final long id;

  private final BigInteger serial;

  public CertRevInfoWithSerial(long id, BigInteger serial, CrlReason reason,
      Date revocationTime, Date invalidityTime) {
    super(reason, revocationTime, invalidityTime);
    this.id = id;
    this.serial = Args.notNull(serial, "serial");
  } // method constructor

  public CertRevInfoWithSerial(long id, BigInteger serial, int reasonCode,
      Date revocationTime, Date invalidityTime) {
    super(reasonCode, revocationTime, invalidityTime);
    this.id = id;
    this.serial = Args.notNull(serial, "serial");
  } // method constructor

  public BigInteger getSerial() {
    return serial;
  }

  public long getId() {
    return id;
  }

  @Override
  public int compareTo(CertRevInfoWithSerial other) {
    return serial.compareTo(other.serial);
  }

}
