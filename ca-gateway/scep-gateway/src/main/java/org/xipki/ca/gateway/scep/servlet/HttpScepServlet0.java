// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.gateway.scep.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.ca.gateway.scep.ScepResponder;
import org.xipki.security.util.HttpRequestMetadataRetriever;
import org.xipki.util.Args;
import org.xipki.util.Base64;
import org.xipki.util.IoUtil;
import org.xipki.util.LogUtil;
import org.xipki.util.http.RestResponse;

import java.io.IOException;
import java.io.InputStream;

/**
 * SCEP servlet.
 *
 * <p>URL http://host:port/scep/&lt;name&gt;/&lt;profile-alias&gt;/pkiclient.exe
 *
 * @author Lijun Liao (xipki)
 * @since 6.0.0
 */

public class HttpScepServlet0 {

  private static final Logger LOG = LoggerFactory.getLogger(HttpScepServlet0.class);

  private boolean logReqResp;

  private ScepResponder responder;

  public void setLogReqResp(boolean logReqResp) {
    this.logReqResp = logReqResp;
  }

  public void setResponder(ScepResponder responder) {
    this.responder = Args.notNull(responder, "responder");
  }

  public RestResponse doGet(HttpRequestMetadataRetriever req) throws IOException {
    return service0(req, null, false);
  }

  public RestResponse doPost(HttpRequestMetadataRetriever req, InputStream reqStream) throws IOException {
    return service0(req, reqStream, true);
  }

  private RestResponse service0(HttpRequestMetadataRetriever req, InputStream reqStream, boolean viaPost)
      throws IOException {
    String path = req.getServletPath();

    byte[] requestBytes = null;
    RestResponse restResp = null;
    try {
      requestBytes = viaPost ? IoUtil.readAllBytesAndClose(reqStream)
          : Base64.decode(req.getParameter("message"));
      restResp = responder.service(path, requestBytes, req);
      return restResp;
    } finally {
      LogUtil.logReqResp("SCEP Gateway", LOG, logReqResp, viaPost, req.getRequestURI(),
          requestBytes, restResp == null ? null : restResp.getBody());
    }
  }

}
