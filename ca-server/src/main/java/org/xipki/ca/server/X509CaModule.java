// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.audit.*;
import org.xipki.ca.api.NameId;
import org.xipki.ca.api.mgmt.RequestorInfo;
import org.xipki.ca.sdk.CaAuditConstants;
import org.xipki.security.X509Cert;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.xipki.util.Args.notNull;

/**
 * X509CA module base class.
 *
 * @author Lijun Liao
 */

public abstract class X509CaModule {

  protected static final long MS_PER_SECOND = 1000L;

  protected static final long MS_PER_MINUTE = 60000L;

  protected static final long MS_PER_HOUR = 60 * MS_PER_MINUTE;

  protected static final int MINUTE_PER_DAY = 24 * 60;

  protected static final long MS_PER_DAY = MINUTE_PER_DAY * MS_PER_MINUTE;

  protected static final long MS_PER_WEEK = 7 * MS_PER_DAY;

  protected final Logger LOG = LoggerFactory.getLogger(getClass());

  protected final NameId caIdent;

  protected final CaInfo caInfo;

  protected final X509Cert caCert;

  protected final List<byte[]> encodedCaCertChain;

  public X509CaModule(CaInfo caInfo) {
    this.caInfo = notNull(caInfo, "caInfo");
    this.caIdent = caInfo.getIdent();
    this.caCert = caInfo.getCert();
    this.encodedCaCertChain = new ArrayList<>(2);
    this.encodedCaCertChain.add(caCert.getEncoded());
    if (caInfo.getCertchain() != null) {
      for (X509Cert c : caInfo.getCertchain()) {
        this.encodedCaCertChain.add(c.getEncoded());
      }
    }
  } // constructor

  protected static AuditService auditService() {
    return Audits.getAuditService();
  }

  protected AuditEvent newAuditEvent(String eventType, RequestorInfo requestor) {
    notNull(eventType, "eventType");
    AuditEvent event = new AuditEvent(new Date());
    event.setApplicationName(CaAuditConstants.APPNAME);
    event.setEventData(CaAuditConstants.NAME_ca, caIdent.getName());
    event.setEventType(eventType);
    if (requestor != null) {
      event.setEventData(CaAuditConstants.NAME_requestor, requestor.getIdent().getName());
    }
    return event;
  }

  protected void setEventStatus(AuditEvent event, boolean successful) {
    event.setLevel(successful ? AuditLevel.INFO : AuditLevel.ERROR);
    event.setStatus(successful ? AuditStatus.SUCCESSFUL : AuditStatus.FAILED);
  }

  protected void finish(AuditEvent event, boolean successful) {
    setEventStatus(event, successful);
    event.finish();
    auditService().logEvent(event);
    event.log(LOG);
  }

  protected boolean verifySignature(X509Cert cert) {
    notNull(cert, "cert");
    PublicKey caPublicKey = caCert.getPublicKey();
    try {
      cert.verify(caPublicKey);
      return true;
    } catch (Exception ex) {
      LOG.debug("{} while verifying signature: {}", ex.getClass().getName(), ex.getMessage());
      return false;
    }
  } // method verifySignature

}
