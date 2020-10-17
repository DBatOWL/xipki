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

package org.xipki.ca.server.db;

import static org.xipki.util.Args.notBlank;

/**
 * CA system event.
 * @author Lijun Liao
 *
 */
public class SystemEvent {

  private final String name;

  private final String owner;

  private final long eventTime;

  public SystemEvent(String name, String owner, long eventTime) {
    this.name = notBlank(name, "name");
    this.owner = notBlank(owner, "owner");
    this.eventTime = eventTime;
  }

  public String getName() {
    return name;
  }

  public String getOwner() {
    return owner;
  }

  public long getEventTime() {
    return eventTime;
  }

} // class SystemEvent
