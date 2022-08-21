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

import org.xipki.util.Args;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Control of the certificate's NotBefore field.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */
public class NotBeforeOption {

  private final TimeZone midNightTimeZone;

  private final Long offsetMillis;

  private NotBeforeOption(TimeZone midNightTimeZone, Long offsetSeconds) {
    this.midNightTimeZone = midNightTimeZone;
    this.offsetMillis = (offsetSeconds == null) ? null : offsetSeconds * 1000;
  }

  static NotBeforeOption getMidNightOption(TimeZone timeZone) {
    return new NotBeforeOption(timeZone, null);
  }

  static NotBeforeOption getOffsetOption(long offsetSeconds) {
    Args.min(offsetSeconds, "offsetSeconds", -600);
    return new NotBeforeOption(null, offsetSeconds);
  }

  Date getNotBefore(Date requestedNotBefore) {
    long now = System.currentTimeMillis();
    if (requestedNotBefore != null) {
      long notOlderThan = (offsetMillis != null && offsetMillis < 0) ? now + offsetMillis : now;

      long notBefore = Math.max(requestedNotBefore.getTime(), notOlderThan);

      return (midNightTimeZone == null) ? new Date(notBefore) : setToMidnight(notBefore);
    } else {
      if (midNightTimeZone != null) {
        return setToMidnight(now);
      } else {
        return new Date(System.currentTimeMillis() + offsetMillis);
      }
    }
  } // method getNotBefore

  private Date setToMidnight(long date) {
    Calendar cal = Calendar.getInstance(midNightTimeZone);
    // the next midnight time
    final long dayInMs = 24L * 60 * 60 * 1000;
    cal.setTime(new Date(date + dayInMs - 1));
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    return cal.getTime();
  } // method setToMidnight

  public TimeZone getMidNightTimeZone() {
    return midNightTimeZone;
  }

  public Long getOffsetMillis() {
    return offsetMillis;
  }

}
