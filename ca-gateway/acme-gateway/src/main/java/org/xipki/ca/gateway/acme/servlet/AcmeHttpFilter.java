// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.gateway.acme.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.ca.gateway.GatewayUtil;
import org.xipki.ca.gateway.ProtocolProxyConfWrapper;
import org.xipki.ca.gateway.acme.AcmeProxyConf;
import org.xipki.ca.gateway.acme.AcmeResponder;
import org.xipki.util.IoUtil;
import org.xipki.util.XipkiBaseDir;
import org.xipki.util.exception.InvalidConfException;
import org.xipki.util.exception.ServletException0;
import org.xipki.util.http.XiHttpFilter;
import org.xipki.util.http.XiHttpRequest;
import org.xipki.util.http.XiHttpResponse;

import java.io.IOException;

/**
 * ACME Gateway ServletFilter.
 *
 * @author Lijun Liao (xipki)
 * @since 6.0.0
 */
public class AcmeHttpFilter implements XiHttpFilter {

  private static final Logger LOG = LoggerFactory.getLogger(AcmeHttpFilter.class);

  private static final String DFLT_CFG = "etc/acme-gateway.json";

  private final AcmeHttpServlet servlet;

  private ProtocolProxyConfWrapper conf;

  public AcmeHttpFilter() throws ServletException0 {
    try {
      XipkiBaseDir.init();

      AcmeProxyConf conf0;
      try {
        conf0 = AcmeProxyConf.readConfFromFile(IoUtil.expandFilepath(DFLT_CFG, true));
      } catch (IOException | InvalidConfException ex) {
        throw new ServletException0("could not parse configuration file " + DFLT_CFG, ex);
      }

      conf = new ProtocolProxyConfWrapper(conf0);

      AcmeResponder responder = new AcmeResponder(
          conf.getSdkClient(), conf.getSecurities().getSecurityFactory(), conf.getPopControl(), conf0.getAcme());
      responder.start();

      servlet = new AcmeHttpServlet(conf.isLogReqResp(), responder);

      GatewayUtil.auditLogPciEvent(LOG, "ACME-Gateway", true, "START");
    } catch (Exception e) {
      String msg = "error initializing ServletFilter";
      LOG.error(msg, e);
      GatewayUtil.auditLogPciEvent(LOG, "ACME-Gateway", false, "START");
      throw new ServletException0(msg);
    }
  }

  @Override
  public void destroy() {
    try {
      servlet.getResponder().close();
      if (conf != null) {
        conf.destroy();
        conf = null;
      }
      GatewayUtil.auditLogPciEvent(LOG, "ACME-Gateway", true, "SHUTDOWN");
      GatewayUtil.closeAudits(LOG);
    } catch (Exception e) {
      //LOG.error("error closing audit service", e);
    }
  }

  @Override
  public void doFilter(XiHttpRequest req, XiHttpResponse resp) throws IOException {
    servlet.service(req, resp);
  }

}