// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.sdk;

import org.xipki.pki.ErrorCode;

/**
 * Exception wraps the error response.
 * @author Lijun Liao (xipki)
 */
public class SdkErrorResponseException extends Exception {

  private final ErrorResponse errorResponse;

  public SdkErrorResponseException(ErrorCode errorCode, String message) {
    this(new ErrorResponse(null, errorCode, message));
  }

  public SdkErrorResponseException(ErrorResponse errorResponse) {
    super(errorResponse.toString());
    this.errorResponse = errorResponse;
  }

  public ErrorResponse getErrorResponse() {
    return errorResponse;
  }

}
