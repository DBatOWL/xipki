// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.gateway.scep.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.ca.gateway.GatewayUtil;
import org.xipki.ca.gateway.ProtocolProxyConfWrapper;
import org.xipki.ca.gateway.scep.CaNameScepSigners;
import org.xipki.ca.gateway.scep.ScepProxyConf;
import org.xipki.ca.gateway.scep.ScepResponder;
import org.xipki.util.IoUtil;
import org.xipki.util.XipkiBaseDir;
import org.xipki.util.exception.InvalidConfException;
import org.xipki.util.exception.ServletException0;

import java.io.IOException;

/**
 * SCEP Gateway ServletFilter.
 *
 * @author Lijun Liao (xipki)
 * @since 6.0.0
 */
public class ProtocolServletFilter0 {

  private static final Logger LOG = LoggerFactory.getLogger(ProtocolServletFilter0.class);

  private static final String DFLT_CFG = "etc/scep-gateway.json";

  private HttpScepServlet0 servlet;

  private ProtocolProxyConfWrapper conf;

  public ProtocolServletFilter0() throws ServletException0 {
    try {
      XipkiBaseDir.init();

      ScepProxyConf conf0;
      try {
        conf0 = ScepProxyConf.readConfFromFile(IoUtil.expandFilepath(DFLT_CFG, true));
      } catch (IOException | InvalidConfException ex) {
        throw new IllegalArgumentException("could not parse configuration file " + DFLT_CFG, ex);
      }

      conf = new ProtocolProxyConfWrapper(conf0);

      CaNameScepSigners signers = new CaNameScepSigners(conf.getSigners());
      ScepResponder responder = new ScepResponder(conf0.getScep(), conf.getSdkClient(),
          conf.getSecurities().getSecurityFactory(), signers, conf.getAuthenticator(),
          conf.getPopControl(), conf.getCaProfiles());

      servlet = new HttpScepServlet0();
      servlet.setLogReqResp(conf.isLogReqResp());
      servlet.setResponder(responder);

      GatewayUtil.auditLogPciEvent("SCEP-Gateway", true, "START");
    } catch (Exception e) {
      String msg = "error initializing ServletFilter";
      LOG.error(msg, e);
      GatewayUtil.auditLogPciEvent("SCEP-Gateway", false, "START");
      throw new ServletException0(msg);
    }
  }

  public void destroy() {
    try {
      if (conf != null) {
        conf.destroy();
        conf = null;
      }
      GatewayUtil.auditLogPciEvent("SCEP-Gateway", true, "SHUTDOWN");
    } catch (Exception e) {
      //LOG.error("error closing audit service", e);
    }
  }

  public HttpScepServlet0 getServlet() {
    return servlet;
  }

}
