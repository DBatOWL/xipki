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

package org.xipki.audit;

import org.xipki.util.Args;

/**
 * Audit level.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

public enum AuditLevel {

  ERROR(3, "ERROR"),
  WARN(4,  "WARN"),
  INFO(6,  "INFO");

  private final int value;

  private final String text;

  AuditLevel(int value, String text) {
    this.value = value;
    this.text = text;
  }

  public int getValue() {
    return value;
  }

  public static AuditLevel forName(String name) {
    Args.notNull(name, "name");
    for (AuditLevel value : values()) {
      if (value.name().equals(name)) {
        return value;
      }
    }
    throw new IllegalArgumentException("invalid AuditLevel name " + name);
  }

  public static AuditLevel forValue(int value) {
    for (AuditLevel v : values()) {
      if (v.getValue() == value) {
        return v;
      }
    }
    throw new IllegalArgumentException("invalid AuditLevel code " + value);
  }

  public String getText() {
    return text;
  }

}
