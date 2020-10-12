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

package org.xipki.ca.api.mgmt.entry;

import org.xipki.ca.api.NameId;
import org.xipki.ca.api.mgmt.CaMgmtException;
import org.xipki.util.Args;
import org.xipki.util.StringUtil;

/**
 * Management Entry Add User.
 * @author Lijun Liao
 *
 */

public class AddUserEntry extends MgmtEntry {

  private NameId ident;

  private boolean active;

  private String password;

  // For the deserialization only
  @SuppressWarnings("unused")
  private AddUserEntry() {
  }

  public AddUserEntry(NameId ident, boolean active, String password)
      throws CaMgmtException {
    this.ident = Args.notNull(ident, "ident");
    this.active = active;
    this.password = Args.notBlank(password, "password");
  }

  public void setIdent(NameId ident) {
    this.ident = ident;
  }

  public NameId getIdent() {
    return ident;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public boolean isActive() {
    return active;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getPassword() {
    return password;
  }

  @Override
  public String toString() {
    return StringUtil.concatObjectsCap(200, "id: ", ident.getId(), "\nname: ", ident.getName(),
        "\nactive: ", active, "\npassword: ****\n");
  }

} // class AddUser
