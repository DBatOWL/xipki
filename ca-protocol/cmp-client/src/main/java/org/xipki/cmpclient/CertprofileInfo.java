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

package org.xipki.cmpclient;

import org.xipki.util.Args;
import org.xipki.util.StringUtil;

/**
 * Certprofile information.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

public class CertprofileInfo {

  private final String name;

  private final String type;

  private final String conf;

  public CertprofileInfo(String name, String type, String conf) {
    this.name = Args.toNonBlankLower(name, "name");
    this.type = StringUtil.isBlank(type) ? null : type;
    this.conf = StringUtil.isBlank(conf) ? null : conf;
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public String getConf() {
    return conf;
  }

}
