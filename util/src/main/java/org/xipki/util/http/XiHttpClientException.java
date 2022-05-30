/*
 *
 * Copyright (c) 2013 - 2022 Lijun Liao
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

package org.xipki.util.http;

/**
 * HTTP client exception.
 *
 * @author Lijun Liao
 */

public class XiHttpClientException extends Exception {

  public XiHttpClientException(String message, Throwable cause) {
    super(message, cause);
  }

  public XiHttpClientException(String message) {
    super(message);
  }

  public XiHttpClientException(Throwable cause) {
    super(cause.getMessage(), cause);
  }

}
