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

package org.xipki.ca.server;

import static org.xipki.ca.api.OperationException.ErrorCode.BAD_REQUEST;
import static org.xipki.ca.api.OperationException.ErrorCode.CERT_REVOKED;
import static org.xipki.ca.api.OperationException.ErrorCode.CERT_UNREVOKED;
import static org.xipki.ca.api.OperationException.ErrorCode.CRL_FAILURE;
import static org.xipki.ca.api.OperationException.ErrorCode.DATABASE_FAILURE;
import static org.xipki.ca.api.OperationException.ErrorCode.NOT_PERMITTED;
import static org.xipki.ca.api.OperationException.ErrorCode.SYSTEM_FAILURE;
import static org.xipki.util.Args.notNull;
import static org.xipki.util.Args.positive;

import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.CertificateList;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.TBSCertList.CRLEntry;
import org.bouncycastle.cert.X509CRLHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.ca.api.CertWithDbId;
import org.xipki.ca.api.CertificateInfo;
import org.xipki.ca.api.NameId;
import org.xipki.ca.api.OperationException;
import org.xipki.ca.api.RequestType;
import org.xipki.ca.api.mgmt.CertListInfo;
import org.xipki.ca.api.mgmt.CertListOrderBy;
import org.xipki.ca.api.mgmt.CertWithRevocationInfo;
import org.xipki.ca.api.mgmt.MgmtEntry;
import org.xipki.datasource.DataAccessException;
import org.xipki.datasource.DataSourceWrapper;
import org.xipki.security.CertRevocationInfo;
import org.xipki.security.CrlReason;
import org.xipki.security.HashAlgo;
import org.xipki.security.ObjectIdentifiers;
import org.xipki.security.X509Cert;
import org.xipki.security.util.X509Util;
import org.xipki.util.Base64;
import org.xipki.util.LogUtil;
import org.xipki.util.LruCache;
import org.xipki.util.StringUtil;

/**
 * CA database store.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

public class CertStore {

  static enum CertStatus {

    UNKNOWN,
    REVOKED,
    GOOD

  } // class CertStatus

  private class DbSchemaInfo {
    private final Map<String, String> variables = new HashMap<>();

    public DbSchemaInfo(DataSourceWrapper datasource)
        throws DataAccessException {
      notNull(datasource, "datasource");
      final String sql = "SELECT NAME,VALUE2 FROM DBSCHEMA";

      Statement stmt = null;
      ResultSet rs = null;

      try {
        stmt = datasource.createStatement();
        if (stmt == null) {
          throw new DataAccessException("could not create statement");
        }

        rs = stmt.executeQuery(sql);
        while (rs.next()) {
          String name = rs.getString("NAME");
          String value = rs.getString("VALUE2");
          variables.put(name, value);
        }
      } catch (SQLException ex) {
        throw datasource.translate(sql, ex);
      } finally {
        datasource.releaseResources(stmt, rs);
      }
    } // constructor

    public String variableValue(String variableName) {
      return variables.get(notNull(variableName, "variableName"));
    }

  } // class DbSchemaInfo

  static class KnowCertResult {

    public static final KnowCertResult UNKNOWN = new KnowCertResult(false, null);

    private final boolean known;

    private final Integer userId;

    public KnowCertResult(boolean known, Integer userId) {
      this.known = known;
      this.userId = userId;
    }

    public boolean isKnown() {
      return known;
    }

    public Integer getUserId() {
      return userId;
    }

  } // end class KnowCertResult

  static class SerialWithId {

    private long id;

    private BigInteger serial;

    public SerialWithId(long id, BigInteger serial) {
      this.id = id;
      this.serial = serial;
    }

    public BigInteger getSerial() {
      return serial;
    }

    public long getId() {
      return id;
    }

  } // class SerialWithId

  private static final Logger LOG = LoggerFactory.getLogger(CertStore.class);

  private static final String SQL_ADD_CERT_V4 =
      "INSERT INTO CERT (ID,LUPDATE,SN,SUBJECT,FP_S,FP_RS,NBEFORE,NAFTER,REV,PID,"
      + "CA_ID,RID,UID,EE,RTYPE,TID,SHA1,REQ_SUBJECT,CRL_SCOPE,CERT,FP_K)"
      + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)";

  private static final String SQL_ADD_CERT =
      "INSERT INTO CERT (ID,LUPDATE,SN,SUBJECT,FP_S,FP_RS,NBEFORE,NAFTER,REV,PID,"
      + "CA_ID,RID,UID,EE,RTYPE,TID,SHA1,REQ_SUBJECT,CRL_SCOPE,CERT)"
      + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

  private static final String SQL_REVOKE_CERT =
      "UPDATE CERT SET LUPDATE=?,REV=?,RT=?,RIT=?,RR=? WHERE ID=?";

  private static final String SQL_REVOKE_SUSPENDED_CERT =
      "UPDATE CERT SET LUPDATE=?,RR=? WHERE ID=?";

  private static final String SQL_INSERT_PUBLISHQUEUE =
      "INSERT INTO PUBLISHQUEUE (PID,CA_ID,CID) VALUES (?,?,?)";

  private static final String SQL_REMOVE_PUBLISHQUEUE =
      "DELETE FROM PUBLISHQUEUE WHERE PID=? AND CID=?";

  private static final String SQL_MAX_CRLNO = "SELECT MAX(CRL_NO) FROM CRL WHERE CA_ID=?";

  private static final String SQL_MAX_FULL_CRLNO =
      "SELECT MAX(CRL_NO) FROM CRL WHERE CA_ID=? AND DELTACRL = 0";

  private static final String SQL_MAX_THISUPDAATE_CRL =
      "SELECT MAX(THISUPDATE) FROM CRL WHERE CA_ID=? AND DELTACRL=?";

  private static final String SQL_ADD_CRL =
      "INSERT INTO CRL (ID,CA_ID,CRL_NO,THISUPDATE,NEXTUPDATE,DELTACRL,BASECRL_NO,CRL_SCOPE,CRL)"
      + " VALUES (?,?,?,?,?,?,?,?,?)";

  private static final String SQL_REMOVE_CERT = "DELETE FROM CERT WHERE CA_ID=? AND SN=?";

  private static final String SQL_DELETE_UNREFERENCED_REQUEST =
      "DELETE FROM REQUEST WHERE ID NOT IN (SELECT req.RID FROM REQCERT req)";

  private static final String SQL_ADD_REQUEST =
      "INSERT INTO REQUEST (ID,LUPDATE,DATA) VALUES(?,?,?)";

  private static final String SQL_ADD_REQCERT = "INSERT INTO REQCERT (ID,RID,CID) VALUES(?,?,?)";

  private final String sqlCaHasCrl;

  private final String sqlCertForId;

  private final String sqlCertWithRevInfo;

  private final String sqlCertInfo;

  private final String sqlCertprofileForCertId;

  private final String sqlActiveUserInfoForName;

  private final String sqlActiveUserNameForId;

  private final String sqlCaHasUser;

  private final String sqlKnowsCertForSerial;

  private final String sqlCertStatusForSubjectFp;

  private final String sqlCertforSubjectIssued;

  private final String sqlLatestSerialForSubjectLike;

  private final String sqlCrl;

  private final String sqlCrlWithNo;

  private final String sqlReqIdForSerial;

  private final String sqlReqForId;

  private final String sqlSelectUnrevokedSn100;

  private final String sqlSelectUnrevokedSn;

  private final LruCache<Integer, String> cacheSqlCidFromPublishQueue = new LruCache<>(5);

  private final LruCache<Integer, String> cacheSqlExpiredSerials = new LruCache<>(5);

  private final LruCache<Integer, String> cacheSqlSuspendedSerials = new LruCache<>(5);

  private final LruCache<Integer, String> cacheSqlRevokedCerts = new LruCache<>(5);

  private final LruCache<Integer, String> cacheSqlSerials = new LruCache<>(5);

  private final LruCache<Integer, String> cacheSqlSerialsRevoked = new LruCache<>(5);

  private final DataSourceWrapper datasource;

  private final int dbSchemaVersion;

  private final int maxX500nameLen;

  private final UniqueIdGenerator idGenerator;

  public CertStore(DataSourceWrapper datasource, UniqueIdGenerator idGenerator)
      throws DataAccessException {
    this.datasource = notNull(datasource, "datasource");
    this.idGenerator = notNull(idGenerator, "idGenerator");

    DbSchemaInfo dbSchemaInfo = new DbSchemaInfo(datasource);
    this.dbSchemaVersion = Integer.parseInt(dbSchemaInfo.variableValue("VERSION"));
    this.maxX500nameLen = Integer.parseInt(dbSchemaInfo.variableValue("X500NAME_MAXLEN"));

    this.sqlCaHasCrl = buildSelectFirstSql("ID FROM CRL WHERE CA_ID=?");
    this.sqlCertForId = buildSelectFirstSql("PID,RID,REV,RR,RT,RIT,CERT FROM CERT WHERE ID=?");
    this.sqlCertWithRevInfo = buildSelectFirstSql(
        "ID,REV,RR,RT,RIT,PID,CERT FROM CERT WHERE CA_ID=? AND SN=?");
    this.sqlCertInfo = buildSelectFirstSql(
        "PID,RID,REV,RR,RT,RIT,CERT FROM CERT WHERE CA_ID=? AND SN=?");
    this.sqlCertprofileForCertId = buildSelectFirstSql("PID FROM CERT WHERE ID=? AND CA_ID=?");
    this.sqlActiveUserInfoForName = buildSelectFirstSql(
        "ID,PASSWORD FROM TUSER WHERE NAME=? AND ACTIVE=1");
    this.sqlActiveUserNameForId = buildSelectFirstSql("NAME FROM TUSER WHERE ID=? AND ACTIVE=1");
    this.sqlCaHasUser = buildSelectFirstSql(
        "PERMISSION,PROFILES FROM CA_HAS_USER WHERE CA_ID=? AND USER_ID=?");
    this.sqlKnowsCertForSerial = buildSelectFirstSql("UID FROM CERT WHERE SN=? AND CA_ID=?");
    this.sqlCertStatusForSubjectFp = buildSelectFirstSql("REV FROM CERT WHERE FP_S=? AND CA_ID=?");
    this.sqlCertforSubjectIssued = buildSelectFirstSql("ID FROM CERT WHERE CA_ID=? AND FP_S=?");
    this.sqlReqIdForSerial = buildSelectFirstSql("REQCERT.RID as REQ_ID FROM REQCERT INNER JOIN "
        + "CERT ON CERT.CA_ID=? AND CERT.SN=? AND REQCERT.CID=CERT.ID");
    this.sqlReqForId = buildSelectFirstSql("DATA FROM REQUEST WHERE ID=?");
    this.sqlLatestSerialForSubjectLike = datasource.buildSelectFirstSql(1, "NBEFORE DESC",
        "SUBJECT FROM CERT WHERE SUBJECT LIKE ?");
    this.sqlCrl = datasource.buildSelectFirstSql(1, "THISUPDATE DESC",
        "THISUPDATE,CRL FROM CRL WHERE CA_ID=?");
    this.sqlCrlWithNo = datasource.buildSelectFirstSql(1, "THISUPDATE DESC",
        "THISUPDATE,CRL FROM CRL WHERE CA_ID=? AND CRL_NO=?");

    this.sqlSelectUnrevokedSn = datasource.buildSelectFirstSql(1,
        "LUPDATE FROM CERT WHERE REV=0 AND SN=?");
    final String prefix = "SN,LUPDATE FROM CERT WHERE REV=0 AND SN";
    this.sqlSelectUnrevokedSn100 = buildArraySql(datasource, prefix, 100);
  } // constructor

  private static final String buildArraySql(DataSourceWrapper datasource, String prefix, int num) {
    StringBuilder sb = new StringBuilder(prefix.length() + num * 2);
    sb.append(prefix).append(" IN (?");
    for (int i = 1; i < num; i++) {
      sb.append(",?");
    }
    sb.append(")");
    return datasource.buildSelectFirstSql(num, sb.toString());
  }

  private String buildSelectFirstSql(String coreSql) {
    return datasource.buildSelectFirstSql(1, coreSql);
  }

  public boolean addCert(CertificateInfo certInfo) {
    notNull(certInfo, "certInfo");
    try {
      addCert(certInfo.getIssuer(), certInfo.getCert(),
          certInfo.getProfile(), certInfo.getRequestor(), certInfo.getUser(), certInfo.getReqType(),
          certInfo.getTransactionId(), certInfo.getRequestedSubject());
    } catch (Exception ex) {
      LOG.error("could not save certificate {}: {}. Message: {}",
          new Object[]{certInfo.getCert().getCert().getSubject(),
              Base64.encodeToString(certInfo.getCert().getCert().getEncoded(), true),
              ex.getMessage()});
      LOG.debug("error", ex);
      return false;
    }

    return true;
  } // method addCert

  private void addCert(NameId ca, CertWithDbId certificate,
      NameId certprofile, NameId requestor, Integer userId, RequestType reqType,
      byte[] transactionId, X500Name reqSubject)
          throws DataAccessException, OperationException {
    notNull(ca, "ca");
    notNull(certificate, "certificate");
    notNull(certprofile, "certprofile");
    notNull(requestor, "requestor");

    long certId = idGenerator.nextId();

    String subjectText = X509Util.cutText(
        certificate.getCert().getSubjectRfc4519Text(), maxX500nameLen);
    long fpSubject = X509Util.fpCanonicalizedName(certificate.getCert().getSubject());

    String reqSubjectText = null;
    Long fpReqSubject = null;
    if (reqSubject != null) {
      fpReqSubject = X509Util.fpCanonicalizedName(reqSubject);
      if (fpSubject == fpReqSubject) {
        fpReqSubject = null;
      } else {
        reqSubjectText = X509Util.cutX500Name(CaUtil.sortX509Name(reqSubject), maxX500nameLen);
      }
    }

    byte[] encodedCert = certificate.getCert().getEncoded();
    String b64FpCert = base64Fp(encodedCert);
    String b64Cert = Base64.encodeToString(encodedCert);
    String tid = (transactionId == null) ? null : Base64.encodeToString(transactionId);

    final String sql = dbSchemaVersion < 5 ? SQL_ADD_CERT_V4 : SQL_ADD_CERT;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      // cert
      X509Cert cert = certificate.getCert();
      int idx = 1;
      ps.setLong(idx++, certId);
      ps.setLong(idx++, System.currentTimeMillis() / 1000); // currentTimeSeconds
      ps.setString(idx++, cert.getSerialNumber().toString(16));
      ps.setString(idx++, subjectText);
      ps.setLong(idx++, fpSubject);
      setLong(ps, idx++, fpReqSubject);
      ps.setLong(idx++, cert.getNotBefore().getTime() / 1000); // notBeforeSeconds
      ps.setLong(idx++, cert.getNotAfter().getTime() / 1000); // notAfterSeconds
      setBoolean(ps, idx++, false);
      ps.setInt(idx++, certprofile.getId());
      ps.setInt(idx++, ca.getId());
      setInt(ps, idx++, requestor.getId());
      setInt(ps, idx++, userId);
      boolean isEeCert = cert.getBasicConstraints() == -1;
      ps.setInt(idx++, isEeCert ? 1 : 0);
      ps.setInt(idx++, reqType.getCode());
      ps.setString(idx++, tid);

      ps.setString(idx++, b64FpCert);
      ps.setString(idx++, reqSubjectText);
      // in this version we set CRL_SCOPE to fixed value 0
      ps.setInt(idx++, 0);
      ps.setString(idx++, b64Cert);

      ps.executeUpdate();

      certificate.setCertId(certId);
    } catch (SQLException ex) {
      throw datasource.translate(sql, ex);
    } finally {
      datasource.releaseResources(ps, null);
    }
  } // method addCert

  public void addToPublishQueue(NameId publisher, long certId, NameId ca)
      throws OperationException {
    notNull(ca, "ca");

    final String sql = SQL_INSERT_PUBLISHQUEUE;
    PreparedStatement ps = borrowPreparedStatement(sql);
    try {
      ps.setInt(1, publisher.getId());
      ps.setInt(2, ca.getId());
      ps.setLong(3, certId);
      ps.executeUpdate();
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, null);
    }
  } // method addToPublishQueue

  public void removeFromPublishQueue(NameId publisher, long certId)
      throws OperationException {
    final String sql = SQL_REMOVE_PUBLISHQUEUE;
    PreparedStatement ps = borrowPreparedStatement(sql);
    try {
      ps.setInt(1, publisher.getId());
      ps.setLong(2, certId);
      ps.executeUpdate();
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, null);
    }
  } // method removeFromPublishQueue

  public void clearPublishQueue(NameId ca, NameId publisher)
      throws OperationException {
    StringBuilder sqlBuilder = new StringBuilder(80);
    sqlBuilder.append("DELETE FROM PUBLISHQUEUE");
    if (ca != null || publisher != null) {
      sqlBuilder.append(" WHERE");
      if (ca != null) {
        sqlBuilder.append(" CA_ID=?");
        if (publisher != null) {
          sqlBuilder.append(" AND");
        }
      }
      if (publisher != null) {
        sqlBuilder.append(" PID=?");
      }
    }

    String sql = sqlBuilder.toString();
    PreparedStatement ps = borrowPreparedStatement(sql);
    try {
      int idx = 1;
      if (ca != null) {
        ps.setInt(idx++, ca.getId());
      }

      if (publisher != null) {
        ps.setInt(idx++, publisher.getId());
      }
      ps.executeUpdate();
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, null);
    }
  } // method clearPublishQueue

  public long getMaxFullCrlNumber(NameId ca)
      throws OperationException {
    return getMaxCrlNumber(ca, SQL_MAX_FULL_CRLNO);
  }

  public long getMaxCrlNumber(NameId ca)
      throws OperationException {
    return getMaxCrlNumber(ca, SQL_MAX_CRLNO);
  }

  private long getMaxCrlNumber(NameId ca, String sql)
      throws OperationException {
    notNull(ca, "ca");
    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);
    try {
      ps.setInt(1, ca.getId());
      rs = ps.executeQuery();
      if (!rs.next()) {
        return 0;
      }
      long maxCrlNumber = rs.getLong(1);
      return (maxCrlNumber < 0) ? 0 : maxCrlNumber;
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  } // method getMaxCrlNumber

  public long getThisUpdateOfCurrentCrl(NameId ca, boolean deltaCrl)
      throws OperationException {
    notNull(ca, "ca");

    final String sql = SQL_MAX_THISUPDAATE_CRL;
    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);
    try {
      ps.setInt(1, ca.getId());
      setBoolean(ps, 2, deltaCrl);
      rs = ps.executeQuery();
      if (!rs.next()) {
        return 0L;
      }
      return rs.getLong(1);
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  } // method getThisUpdateOfCurrentCrl

  public boolean hasCrl(NameId ca)
      throws OperationException {
    notNull(ca, "ca");

    final String sql = sqlCaHasCrl;
    PreparedStatement ps = null;
    ResultSet rs = null;
    try {
      ps = borrowPreparedStatement(sql);
      ps.setInt(1, ca.getId());
      rs = ps.executeQuery();
      return rs.next();
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  } // method hasCrl

  public void addCrl(NameId ca, X509CRLHolder crl)
      throws OperationException, CRLException {
    notNull(ca, "ca");
    notNull(crl, "crl");

    Extensions extns = crl.getExtensions();
    byte[] extnValue = X509Util.getCoreExtValue(extns, Extension.cRLNumber);
    Long crlNumber = null;
    if (extnValue != null) {
      crlNumber = ASN1Integer.getInstance(extnValue).getPositiveValue().longValue();
    }

    extnValue = X509Util.getCoreExtValue(extns, Extension.deltaCRLIndicator);
    Long baseCrlNumber = null;
    if (extnValue != null) {
      baseCrlNumber = ASN1Integer.getInstance(extnValue).getPositiveValue().longValue();
    }

    final String sql = SQL_ADD_CRL;
    long currentMaxCrlId;
    try {
      currentMaxCrlId = datasource.getMax(null, "CRL", "ID");
    } catch (DataAccessException ex) {
      throw new OperationException(DATABASE_FAILURE, ex.getMessage());
    }
    long crlId = currentMaxCrlId + 1;

    String b64Crl;
    try {
      b64Crl = Base64.encodeToString(crl.getEncoded());
    } catch (IOException ex) {
      throw new CRLException(ex.getMessage(), ex);
    }

    PreparedStatement ps = null;

    try {
      ps = borrowPreparedStatement(sql);

      int idx = 1;
      ps.setLong(idx++, crlId);
      ps.setInt(idx++, ca.getId());
      setLong(ps, idx++, crlNumber);
      Date date = crl.getThisUpdate();
      ps.setLong(idx++, date.getTime() / 1000);
      setDateSeconds(ps, idx++, crl.getNextUpdate());
      setBoolean(ps, idx++, (baseCrlNumber != null));
      setLong(ps, idx++, baseCrlNumber);
      // in this version we set CRL_SCOPE to fixed value 0
      ps.setInt(idx++, 0);
      ps.setString(idx++, b64Crl);

      ps.executeUpdate();
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, null);
    }
  } // method addCrl

  public CertWithRevocationInfo revokeCert(NameId ca, BigInteger serialNumber,
      CertRevocationInfo revInfo, boolean force, CaIdNameMap idNameMap)
          throws OperationException {
    notNull(ca, "ca");
    notNull(serialNumber, "serialNumber");
    notNull(revInfo, "revInfo");

    CertWithRevocationInfo certWithRevInfo =
        getCertWithRevocationInfo(ca.getId(), serialNumber, idNameMap);
    if (certWithRevInfo == null) {
      LOG.warn("certificate with CA={} and serialNumber={} does not exist",
          ca.getName(), LogUtil.formatCsn(serialNumber));
      return null;
    }

    CertRevocationInfo currentRevInfo = certWithRevInfo.getRevInfo();
    if (currentRevInfo != null) {
      CrlReason currentReason = currentRevInfo.getReason();
      if (currentReason == CrlReason.CERTIFICATE_HOLD) {
        if (revInfo.getReason() == CrlReason.CERTIFICATE_HOLD) {
          throw new OperationException(CERT_REVOKED,
              "certificate already revoked with the requested reason "
              + currentReason.getDescription());
        } else {
          revInfo.setRevocationTime(currentRevInfo.getRevocationTime());
          revInfo.setInvalidityTime(currentRevInfo.getInvalidityTime());
        }
      } else if (!force) {
        throw new OperationException(CERT_REVOKED,
          "certificate already revoked with reason " + currentReason.getDescription());
      }
    }

    Long invTimeSeconds = null;
    if (revInfo.getInvalidityTime() != null) {
      invTimeSeconds = revInfo.getInvalidityTime().getTime() / 1000;
    }

    PreparedStatement ps = borrowPreparedStatement(SQL_REVOKE_CERT);
    try {
      int idx = 1;
      ps.setLong(idx++, System.currentTimeMillis() / 1000);
      setBoolean(ps, idx++, true);
      ps.setLong(idx++, revInfo.getRevocationTime().getTime() / 1000); // revTimeSeconds
      setLong(ps, idx++, invTimeSeconds);
      ps.setInt(idx++, revInfo.getReason().getCode());
      ps.setLong(idx++, certWithRevInfo.getCert().getCertId().longValue()); // certId

      int count = ps.executeUpdate();
      if (count != 1) {
        String message = (count > 1)
            ? count + " rows modified, but exactly one is expected"
            : "no row is modified, but exactly one is expected";
        throw new OperationException(SYSTEM_FAILURE, message);
      }
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE,
          datasource.translate(SQL_REVOKE_CERT, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, null);
    }

    certWithRevInfo.setRevInfo(revInfo);
    return certWithRevInfo;
  } // method revokeCert

  public CertWithRevocationInfo revokeSuspendedCert(NameId ca, BigInteger serialNumber,
      CrlReason reason, CaIdNameMap idNameMap)
      throws OperationException {
    notNull(ca, "ca");
    notNull(serialNumber, "serialNumber");
    notNull(reason, "reason");

    CertWithRevocationInfo certWithRevInfo =
        getCertWithRevocationInfo(ca.getId(), serialNumber, idNameMap);
    if (certWithRevInfo == null) {
      LOG.warn("certificate with CA={} and serialNumber={} does not exist",
          ca.getName(), LogUtil.formatCsn(serialNumber));
      return null;
    }

    CertRevocationInfo currentRevInfo = certWithRevInfo.getRevInfo();
    if (currentRevInfo == null) {
      throw new OperationException(CERT_UNREVOKED, "certificate is not revoked");
    }

    CrlReason currentReason = currentRevInfo.getReason();
    if (currentReason != CrlReason.CERTIFICATE_HOLD) {
      throw new OperationException(CERT_REVOKED, "certificate is revoked but not with reason "
          + CrlReason.CERTIFICATE_HOLD.getDescription());
    }

    PreparedStatement ps = borrowPreparedStatement(SQL_REVOKE_SUSPENDED_CERT);
    try {
      int idx = 1;
      ps.setLong(idx++, System.currentTimeMillis() / 1000);
      ps.setInt(idx++, reason.getCode());
      ps.setLong(idx++, certWithRevInfo.getCert().getCertId().longValue()); // certId

      int count = ps.executeUpdate();
      if (count != 1) {
        String message = (count > 1)
            ? count + " rows modified, but exactly one is expected"
            : "no row is modified, but exactly one is expected";
        throw new OperationException(SYSTEM_FAILURE, message);
      }
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE,
          datasource.translate(SQL_REVOKE_CERT, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, null);
    }

    currentRevInfo.setReason(reason);
    return certWithRevInfo;
  } // method revokeSuspendedCert

  public CertWithDbId unrevokeCert(NameId ca, BigInteger serialNumber, boolean force,
      CaIdNameMap idNamMap)
          throws OperationException {
    notNull(ca, "ca");
    notNull(serialNumber, "serialNumber");

    CertWithRevocationInfo certWithRevInfo =
        getCertWithRevocationInfo(ca.getId(), serialNumber, idNamMap);
    if (certWithRevInfo == null) {
      if (LOG.isWarnEnabled()) {
        LOG.warn("certificate with CA={} and serialNumber={} does not exist",
            ca.getName(), LogUtil.formatCsn(serialNumber));
      }
      return null;
    }

    CertRevocationInfo currentRevInfo = certWithRevInfo.getRevInfo();
    if (currentRevInfo == null) {
      throw new OperationException(CERT_UNREVOKED, "certificate is not revoked");
    }

    CrlReason currentReason = currentRevInfo.getReason();
    if (!force) {
      if (currentReason != CrlReason.CERTIFICATE_HOLD) {
        throw new OperationException(NOT_PERMITTED,
            "could not unrevoke certificate revoked with reason "
            + currentReason.getDescription());
      }
    }

    final String sql = "UPDATE CERT SET LUPDATE=?,REV=?,RT=?,RIT=?,RR=? WHERE ID=?";

    PreparedStatement ps = borrowPreparedStatement(sql);
    try {
      int idx = 1;
      ps.setLong(idx++, System.currentTimeMillis() / 1000); // currentTimeSeconds
      setBoolean(ps, idx++, false);
      ps.setNull(idx++, Types.INTEGER);
      ps.setNull(idx++, Types.INTEGER);
      ps.setNull(idx++, Types.INTEGER);
      ps.setLong(idx++, certWithRevInfo.getCert().getCertId().longValue()); // certId

      int count = ps.executeUpdate();
      if (count != 1) {
        String message = (count > 1)
            ? count + " rows modified, but exactly one is expected"
            : "no row is modified, but exactly one is expected";
        throw new OperationException(SYSTEM_FAILURE, message);
      }
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, null);
    }

    return certWithRevInfo.getCert();
  } // method unrevokeCert

  public void removeCert(NameId ca, BigInteger serialNumber)
      throws OperationException {
    notNull(ca, "ca");
    notNull(serialNumber, "serialNumber");

    final String sql = SQL_REMOVE_CERT;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      ps.setInt(1, ca.getId());
      ps.setString(2, serialNumber.toString(16));

      int count = ps.executeUpdate();
      if (count != 1) {
        String message = (count > 1)
            ? count + " rows modified, but exactly one is expected"
            : "no row is modified, but exactly one is expected";
        throw new OperationException(SYSTEM_FAILURE, message);
      }
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, null);
    }
  } // method removeCert

  public List<Long> getPublishQueueEntries(NameId ca, NameId publisher, int numEntries)
      throws OperationException {
    final String sql = getSqlCidFromPublishQueue(numEntries);
    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      ps.setInt(1, publisher.getId());
      ps.setInt(2, ca.getId());
      rs = ps.executeQuery();
      List<Long> ret = new ArrayList<>();
      while (rs.next() && ret.size() < numEntries) {
        long certId = rs.getLong("CID");
        if (!ret.contains(certId)) {
          ret.add(certId);
        }
      }
      return ret;
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  } // method getPublishQueueEntries

  public long getCountOfCerts(NameId ca, boolean onlyRevoked)
      throws OperationException {
    final String sql = onlyRevoked ? "SELECT COUNT(*) FROM CERT WHERE CA_ID=? AND REV=1"
                    : "SELECT COUNT(*) FROM CERT WHERE CA_ID=?";

    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      ps.setInt(1, ca.getId());
      rs = ps.executeQuery();
      rs.next();
      return rs.getLong(1);
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  } // method getCountOfCerts

  public List<SerialWithId> getSerialNumbers(NameId ca,  long startId, int numEntries,
      boolean onlyRevoked)
          throws OperationException {
    notNull(ca, "ca");
    positive(numEntries, "numEntries");

    final String sql = getSqlSerials(numEntries, onlyRevoked);

    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      ps.setLong(1, startId - 1);
      ps.setInt(2, ca.getId());
      rs = ps.executeQuery();
      List<SerialWithId> ret = new ArrayList<>();
      while (rs.next() && ret.size() < numEntries) {
        long id = rs.getLong("ID");
        String serial = rs.getString("SN");
        ret.add(new SerialWithId(id, new BigInteger(serial, 16)));
      }
      return ret;
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  } // method getSerialNumbers

  public List<SerialWithId> getSerialNumbers(NameId ca, Date notExpiredAt, long startId,
      int numEntries, boolean onlyRevoked, boolean onlyCaCerts, boolean onlyUserCerts)
      throws OperationException {
    notNull(ca, "ca");
    positive(numEntries, "numEntries");

    if (onlyCaCerts && onlyUserCerts) {
      throw new IllegalArgumentException("onlyCaCerts and onlyUserCerts cannot be both of true");
    }
    boolean withEe = onlyCaCerts || onlyUserCerts;
    final String sql = getSqlSerials(numEntries, notExpiredAt, onlyRevoked, withEe);

    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      int idx = 1;
      ps.setLong(idx++, startId - 1);
      ps.setInt(idx++, ca.getId());
      if (notExpiredAt != null) {
        ps.setLong(idx++, notExpiredAt.getTime() / 1000 + 1);
      }
      if (withEe) {
        setBoolean(ps, idx++, onlyUserCerts);
      }
      rs = ps.executeQuery();
      List<SerialWithId> ret = new ArrayList<>();
      while (rs.next() && ret.size() < numEntries) {
        long id = rs.getLong("ID");
        String serial = rs.getString("SN");
        ret.add(new SerialWithId(id, new BigInteger(serial, 16)));
      }
      return ret;
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  } // method getSerialNumbers

  public List<BigInteger> getExpiredSerialNumbers(NameId ca, long expiredAt, int numEntries)
      throws OperationException {
    notNull(ca, "ca");
    positive(numEntries, "numEntries");

    final String sql = getSqlExpiredSerials(numEntries);

    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      ps.setInt(1, ca.getId());
      ps.setLong(2, expiredAt);
      rs = ps.executeQuery();
      List<BigInteger> ret = new ArrayList<>();
      while (rs.next() && ret.size() < numEntries) {
        String serial = rs.getString("SN");
        ret.add(new BigInteger(serial, 16));
      }
      return ret;
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  } // method getExpiredSerialNumbers

  public List<BigInteger> getSuspendedCertSerials(NameId ca, long latestLastUpdate, int numEntries)
      throws OperationException {
    notNull(ca, "ca");
    positive(numEntries, "numEntries");

    final String sql = getSqlSuspendedSerials(numEntries);
    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      ps.setInt(1, ca.getId());
      ps.setLong(2, latestLastUpdate + 1);
      ps.setInt(3, CrlReason.CERTIFICATE_HOLD.getCode());
      rs = ps.executeQuery();
      List<BigInteger> ret = new ArrayList<>();
      while (rs.next() && ret.size() < numEntries) {
        String str = rs.getString("SN");
        ret.add(new BigInteger(str, 16));
      }
      return ret;
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  } // method getSuspendedCertIds

  public byte[] getEncodedCrl(NameId ca)
      throws OperationException {
    notNull(ca, "ca");

    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sqlCrl);

    String b64Crl = null;
    try {
      int idx = 1;
      ps.setInt(idx++, ca.getId());
      rs = ps.executeQuery();
      long currentThisUpdate = 0;
      // iterate all entries to make sure that the latest CRL will be returned
      while (rs.next()) {
        long thisUpdate = rs.getLong("THISUPDATE");
        if (thisUpdate >= currentThisUpdate) {
          b64Crl = rs.getString("CRL");
          currentThisUpdate = thisUpdate;
        }
      }
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sqlCrl, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }

    return (b64Crl == null) ? null : Base64.decodeFast(b64Crl);
  } // method getEncodedCrl

  public byte[] getEncodedCrl(NameId ca, BigInteger crlNumber)
      throws OperationException {
    notNull(ca, "ca");

    if (crlNumber == null) {
      return getEncodedCrl(ca);
    }

    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sqlCrlWithNo);

    String b64Crl = null;
    try {
      int idx = 1;
      ps.setInt(idx++, ca.getId());
      ps.setLong(idx++, crlNumber.longValue());
      rs = ps.executeQuery();

      if (rs.next()) {
        b64Crl = rs.getString("CRL");
      }
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE,
          datasource.translate(sqlCrlWithNo, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }

    return (b64Crl == null) ? null : Base64.decodeFast(b64Crl);
  } // method getEncodedCrl

  public int cleanupCrls(NameId ca, int numCrls)
      throws OperationException {
    notNull(ca, "ca");
    positive(numCrls, "numCrls");

    String sql = "SELECT CRL_NO FROM CRL WHERE CA_ID=? AND DELTACRL=?";
    PreparedStatement ps = borrowPreparedStatement(sql);
    List<Integer> crlNumbers = new LinkedList<>();
    ResultSet rs = null;
    try {
      ps.setInt(1, ca.getId());
      setBoolean(ps, 2, false);
      rs = ps.executeQuery();

      while (rs.next()) {
        int crlNumber = rs.getInt("CRL_NO");
        crlNumbers.add(crlNumber);
      }
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }

    int size = crlNumbers.size();
    Collections.sort(crlNumbers);

    int numCrlsToDelete = size - numCrls;
    if (numCrlsToDelete < 1) {
      return 0;
    }

    int crlNumber = crlNumbers.get(numCrlsToDelete - 1);
    sql = "DELETE FROM CRL WHERE CA_ID=? AND CRL_NO<?";
    ps = borrowPreparedStatement(sql);

    try {
      int idx = 1;
      ps.setInt(idx++, ca.getId());
      ps.setInt(idx++, crlNumber + 1);
      ps.executeUpdate();
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, null);
    }

    return numCrlsToDelete;
  } // method cleanupCrls

  public CertificateInfo getCertForId(NameId ca, X509Cert caCert, long certId,
      CaIdNameMap idNameMap)
          throws OperationException, CertificateException {
    notNull(ca, "ca");
    notNull(caCert, "caCert");
    notNull(idNameMap, "idNameMap");

    final String sql = sqlCertForId;

    String b64Cert;
    int certprofileId;
    int requestorId;
    boolean revoked;
    int revReason = 0;
    long revTime = 0;
    long revInvTime = 0;

    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);
    try {
      ps.setLong(1, certId);
      rs = ps.executeQuery();
      if (!rs.next()) {
        return null;
      }
      b64Cert = rs.getString("CERT");
      certprofileId = rs.getInt("PID");
      requestorId = rs.getInt("RID");
      revoked = rs.getBoolean("REV");
      if (revoked) {
        revReason = rs.getInt("RR");
        revTime = rs.getLong("RT");
        revInvTime = rs.getLong("RIT");
      }
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }

    byte[] encodedCert = Base64.decodeFast(b64Cert);
    X509Cert cert = X509Util.parseCert(encodedCert);
    CertWithDbId certWithMeta = new CertWithDbId(cert);
    certWithMeta.setCertId(certId);
    CertificateInfo certInfo = new CertificateInfo(certWithMeta, null, ca, caCert,
        idNameMap.getCertprofile(certprofileId),
        idNameMap.getRequestor(requestorId));
    if (!revoked) {
      return certInfo;
    }
    Date invalidityTime = (revInvTime == 0 || revInvTime == revTime) ? null
        : new Date(revInvTime * 1000);
    CertRevocationInfo revInfo = new CertRevocationInfo(revReason,
        new Date(revTime * 1000), invalidityTime);
    certInfo.setRevocationInfo(revInfo);
    return certInfo;
  } // method getCertForId

  public CertWithRevocationInfo getCertWithRevocationInfo(int caId, BigInteger serial,
      CaIdNameMap idNameMap)
          throws OperationException {
    notNull(serial, "serial");
    notNull(idNameMap, "idNameMap");

    final String sql = sqlCertWithRevInfo;

    long certId;
    String b64Cert;
    boolean revoked;
    int revReason = 0;
    long revTime = 0;
    long revInvTime = 0;
    int certprofileId = 0;

    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      int idx = 1;
      ps.setInt(idx++, caId);
      ps.setString(idx++, serial.toString(16));
      rs = ps.executeQuery();
      if (!rs.next()) {
        return null;
      }
      certId = rs.getLong("ID");
      b64Cert = rs.getString("CERT");
      certprofileId = rs.getInt("PID");

      revoked = rs.getBoolean("REV");
      if (revoked) {
        revReason = rs.getInt("RR");
        revTime = rs.getLong("RT");
        revInvTime = rs.getLong("RIT");
      }
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, null);
    }

    byte[] encodedCert = Base64.decodeFast(b64Cert);
    X509Cert cert;
    try {
      cert = X509Util.parseCert(encodedCert);
    } catch (CertificateException ex) {
      throw new OperationException(SYSTEM_FAILURE, ex);
    }

    CertRevocationInfo revInfo = null;
    if (revoked) {
      Date invalidityTime = (revInvTime == 0) ? null : new Date(1000 * revInvTime);
      revInfo = new CertRevocationInfo(revReason, new Date(1000 * revTime), invalidityTime);
    }

    CertWithDbId certWithMeta = new CertWithDbId(cert);
    certWithMeta.setCertId(certId);

    String profileName = idNameMap.getCertprofileName(certprofileId);
    CertWithRevocationInfo ret = new CertWithRevocationInfo();
    ret.setCertprofile(profileName);
    ret.setCert(certWithMeta);
    ret.setRevInfo(revInfo);
    return ret;
  } // method getCertWithRevocationInfo

  public CertificateInfo getCertInfo(NameId ca, X509Cert caCert, BigInteger serial,
      CaIdNameMap idNameMap)
          throws OperationException, CertificateException {
    notNull(ca, "ca");
    notNull(caCert, "caCert");
    notNull(idNameMap, "idNameMap");
    notNull(serial, "serial");

    final String sql = sqlCertInfo;

    String b64Cert;
    boolean revoked;
    int revReason = 0;
    long revTime = 0;
    long revInvTime = 0;
    int certprofileId;
    int requestorId;

    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      int idx = 1;
      ps.setInt(idx++, ca.getId());
      ps.setString(idx++, serial.toString(16));
      rs = ps.executeQuery();
      if (!rs.next()) {
        return null;
      }
      b64Cert = rs.getString("CERT");
      certprofileId = rs.getInt("PID");
      requestorId = rs.getInt("RID");
      revoked = rs.getBoolean("REV");
      if (revoked) {
        revReason = rs.getInt("RR");
        revTime = rs.getLong("RT");
        revInvTime = rs.getLong("RIT");
      }
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }

    byte[] encodedCert = Base64.decodeFast(b64Cert);
    X509Cert cert = X509Util.parseCert(encodedCert);
    CertWithDbId certWithMeta = new CertWithDbId(cert);

    CertificateInfo certInfo = new CertificateInfo(certWithMeta, null, ca, caCert,
        idNameMap.getCertprofile(certprofileId),
        idNameMap.getRequestor(requestorId));

    if (!revoked) {
      return certInfo;
    }

    Date invalidityTime = (revInvTime == 0) ? null : new Date(revInvTime * 1000);
    CertRevocationInfo revInfo = new CertRevocationInfo(revReason, new Date(revTime * 1000),
        invalidityTime);
    certInfo.setRevocationInfo(revInfo);
    return certInfo;
  } // method getCertInfo

  public Integer getCertprofileForCertId(NameId ca, long cid)
      throws OperationException {
    notNull(ca, "ca");

    final String sql = sqlCertprofileForCertId;
    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      ps.setLong(1, cid);
      ps.setInt(2, ca.getId());
      rs = ps.executeQuery();
      if (!rs.next()) {
        return null;
      }

      return rs.getInt("PID");
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  } // method getCertprofileForId

  /**
   * Get certificates for given subject and transactionId.
   *
   * @param subjectName Subject of Certificate or requested Subject.
   * @param transactionId will only be considered if there are more than one certificate
   *     matches the subject.
   * @return certificates for given subject and transactionId.
   * @throws OperationException
   *           If error occurs.
   */
  public List<X509Cert> getCert(X500Name subjectName, byte[] transactionId)
      throws OperationException {
    final String sql = (transactionId != null)
        ? "SELECT CERT FROM CERT WHERE TID=? AND (FP_S=? OR FP_RS=?)"
        : "SELECT CERT FROM CERT WHERE FP_S=? OR FP_RS=?";

    long fpSubject = X509Util.fpCanonicalizedName(subjectName);
    List<X509Cert> certs = new LinkedList<>();

    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      int idx = 1;
      if (transactionId != null) {
        ps.setString(idx++, Base64.encodeToString(transactionId));
      }
      ps.setLong(idx++, fpSubject);
      ps.setLong(idx++, fpSubject);
      rs = ps.executeQuery();

      while (rs.next()) {
        String b64Cert = rs.getString("CERT");
        byte[] encodedCert = Base64.decodeFast(b64Cert);

        X509Cert cert;
        try {
          cert = X509Util.parseCert(encodedCert);
        } catch (CertificateException ex) {
          throw new OperationException(SYSTEM_FAILURE, ex);
        }
        certs.add(cert);
      }
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }

    return certs;
  } // method getCert

  public byte[] getCertRequest(NameId ca, BigInteger serialNumber)
      throws OperationException {
    notNull(ca, "ca");
    notNull(serialNumber, "serialNumber");

    String sql = sqlReqIdForSerial;
    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    Long reqId = null;
    try {
      ps.setInt(1, ca.getId());
      ps.setString(2, serialNumber.toString(16));
      rs = ps.executeQuery();

      if (rs.next()) {
        reqId = rs.getLong("REQ_ID");
      }
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }

    if (reqId == null) {
      return null;
    }

    String b64Req = null;
    sql = sqlReqForId;
    ps = borrowPreparedStatement(sql);
    try {
      ps.setLong(1, reqId);
      rs = ps.executeQuery();
      if (rs.next()) {
        b64Req = rs.getString("DATA");
      }
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }

    return (b64Req == null) ? null : Base64.decodeFast(b64Req);
  } // method getCertRequest

  public List<CertListInfo> listCerts(NameId ca, X500Name subjectPattern, Date validFrom,
      Date validTo, CertListOrderBy orderBy, int numEntries)
          throws OperationException {
    notNull(ca, "ca");
    positive(numEntries, "numEntries");

    StringBuilder sb = new StringBuilder(200);
    sb.append("SN,NBEFORE,NAFTER,SUBJECT FROM CERT WHERE CA_ID=?");

    Integer idxNotBefore = null;
    Integer idxNotAfter = null;
    Integer idxSubject = null;

    int idx = 2;
    if (validFrom != null) {
      idxNotBefore = idx++;
      sb.append(" AND NBEFORE<?");
    }
    if (validTo != null) {
      idxNotAfter = idx++;
      sb.append(" AND NAFTER>?");
    }

    String subjectLike = null;
    if (subjectPattern != null) {
      idxSubject = idx++;
      sb.append(" AND SUBJECT LIKE ?");

      StringBuilder buffer = new StringBuilder(100);
      buffer.append("%");
      RDN[] rdns = subjectPattern.getRDNs();
      for (int i = 0; i < rdns.length; i++) {
        X500Name rdnName = new X500Name(new RDN[]{rdns[i]});
        String rdnStr = X509Util.getRfc4519Name(rdnName);
        if (rdnStr.indexOf('%') != -1) {
          throw new OperationException(BAD_REQUEST,
              "the character '%' is not allowed in subjectPattern");
        }
        if (rdnStr.indexOf('*') != -1) {
          rdnStr = rdnStr.replace('*', '%');
        }
        buffer.append(rdnStr);
        buffer.append("%");
      }
      subjectLike = buffer.toString();
    }

    String sortByStr = null;
    if (orderBy != null) {
      switch (orderBy) {
        case NOT_BEFORE:
          sortByStr = "NBEFORE";
          break;
        case NOT_BEFORE_DESC:
          sortByStr = "NBEFORE DESC";
          break;
        case NOT_AFTER:
          sortByStr = "NAFTER";
          break;
        case NOT_AFTER_DESC:
          sortByStr = "NAFTER DESC";
          break;
        case SUBJECT:
          sortByStr = "SUBJECT";
          break;
        case SUBJECT_DESC:
          sortByStr = "SUBJECT DESC";
          break;
        default:
          throw new IllegalStateException("unknown CertListOrderBy " + orderBy);
      }
    }

    final String sql = datasource.buildSelectFirstSql(numEntries, sortByStr, sb.toString());
    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      ps.setInt(1, ca.getId());

      if (idxNotBefore != null) {
        long time = validFrom.getTime() / 1000;
        ps.setLong(idxNotBefore, time - 1);
      }

      if (idxNotAfter != null) {
        long time = validTo.getTime() / 1000;
        ps.setLong(idxNotAfter, time);
      }

      if (idxSubject != null) {
        ps.setString(idxSubject, subjectLike);
      }

      rs = ps.executeQuery();
      List<CertListInfo> ret = new LinkedList<>();
      while (rs.next()) {
        CertListInfo info = new CertListInfo(new BigInteger(rs.getString("SN"), 16),
            rs.getString("SUBJECT"), new Date(rs.getLong("NBEFORE") * 1000),
            new Date(rs.getLong("NAFTER") * 1000));
        ret.add(info);
      }
      return ret;
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  } // method listCerts

  public NameId authenticateUser(String user, byte[] password)
      throws OperationException {
    final String sql = sqlActiveUserInfoForName;

    int id;
    String expPasswordText;

    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      ps.setString(1, user);
      rs = ps.executeQuery();

      if (!rs.next()) {
        return null;
      }

      id = rs.getInt("ID");
      expPasswordText = rs.getString("PASSWORD");
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }

    if (StringUtil.isBlank(expPasswordText)) {
      return null;
    }

    boolean valid = PasswordHash.validatePassword(password, expPasswordText);
    return valid ? new NameId(id, user) : null;
  } // method authenticateUser

  public String getUsername(int id)
      throws OperationException {
    final String sql = sqlActiveUserNameForId;

    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      ps.setInt(1, id);
      rs = ps.executeQuery();

      if (!rs.next()) {
        return null;
      }

      return rs.getString("NAME");
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  } // method getUsername

  public MgmtEntry.CaHasUser getCaHasUser(NameId ca, NameId user)
      throws OperationException {
    final String sql = sqlCaHasUser;
    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      ps.setInt(1, ca.getId());
      ps.setInt(2, user.getId());
      rs = ps.executeQuery();

      if (!rs.next()) {
        return null;
      }

      List<String> list = StringUtil.split(rs.getString("PROFILES"), ",");
      Set<String> profiles = (list == null) ? null : new HashSet<>(list);

      MgmtEntry.CaHasUser entry = new MgmtEntry.CaHasUser(user);
      entry.setPermission(rs.getInt("PERMISSION"));
      entry.setProfiles(profiles);
      return entry;
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  } // method getCaHasUser

  public KnowCertResult knowsCertForSerial(NameId ca, BigInteger serial)
      throws OperationException {
    notNull(serial, "serial");
    final String sql = sqlKnowsCertForSerial;

    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      ps.setString(1, serial.toString(16));
      ps.setInt(2, ca.getId());
      rs = ps.executeQuery();

      if (!rs.next()) {
        return KnowCertResult.UNKNOWN;
      }

      int userId = rs.getInt("UID");
      return new KnowCertResult(true, userId);
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  } // method knowsCertForSerial

  public List<CertRevInfoWithSerial> getRevokedCerts(NameId ca, Date notExpiredAt, long startId,
      int numEntries)
          throws OperationException {
    notNull(ca, "ca");
    notNull(notExpiredAt, "notExpiredAt");
    positive(numEntries, "numEntries");

    String sql = getSqlRevokedCerts(numEntries);

    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      int idx = 1;
      ps.setLong(idx++, startId - 1);
      ps.setInt(idx++, ca.getId());
      ps.setLong(idx++, notExpiredAt.getTime() / 1000 + 1);
      rs = ps.executeQuery();

      List<CertRevInfoWithSerial> ret = new LinkedList<>();
      while (rs.next()) {
        long revInvalidityTime = rs.getLong("RIT");
        Date invalidityTime = (revInvalidityTime == 0) ? null : new Date(1000 * revInvalidityTime);
        CertRevInfoWithSerial revInfo = new CertRevInfoWithSerial(rs.getLong("ID"),
            new BigInteger(rs.getString("SN"), 16), rs.getInt("RR"), // revReason
            new Date(1000 * rs.getLong("RT")), invalidityTime);
        ret.add(revInfo);
      }

      return ret;
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  } // method getRevokedCerts

  public List<CertRevInfoWithSerial> getCertsForDeltaCrl(NameId ca, BigInteger baseCrlNumber,
      Date notExpiredAt)
          throws OperationException {
    notNull(ca, "ca");
    notNull(notExpiredAt, "notExpiredAt");
    notNull(baseCrlNumber, "baseCrlNumber");

    // Get the Base FullCRL
    byte[] encodedCrl = getEncodedCrl(ca, baseCrlNumber);
    CertificateList crl = CertificateList.getInstance(encodedCrl);
    // Get revoked certs in CRL
    Enumeration<?> revokedCertsInCrl = crl.getRevokedCertificateEnumeration();

    Set<BigInteger> allSnSet = null;

    boolean supportInSql = datasource.getDatabaseType().supportsInArray();
    List<BigInteger> snList = new LinkedList<>();

    List<CertRevInfoWithSerial> ret = new LinkedList<>();

    PreparedStatement ps = null;
    try {
      while (revokedCertsInCrl.hasMoreElements()) {
        CRLEntry crlEntry = (CRLEntry) revokedCertsInCrl.nextElement();
        if (allSnSet == null) {
          // guess the size of revoked certificate, very rough
          int averageSize = encodedCrl.length / crlEntry.getEncoded().length;
          allSnSet = new HashSet<>((int) (1.1 * averageSize));
        }

        BigInteger sn = crlEntry.getUserCertificate().getPositiveValue();
        snList.add(sn);
        allSnSet.add(sn);

        if (!supportInSql) {
          continue;
        }

        if (snList.size() == 100) {
          // check whether revoked certificates have been unrevoked.

          if (ps == null) {
            ps = borrowPreparedStatement(sqlSelectUnrevokedSn100);
          }

          for (int i = 1; i < 101; i++) {
            ps.setString(i, snList.get(i - 1).toString(16));
          }
          snList.clear();

          ResultSet rs = ps.executeQuery();
          try {
            while (rs.next()) {
              ret.add(new CertRevInfoWithSerial(0L,
                  new BigInteger(rs.getString("SN"), 16), // serial
                  CrlReason.REMOVE_FROM_CRL, // reason
                  new Date(100L * rs.getLong("LUPDATE")), //revocationTime,
                  null)); // invalidityTime
            }
          } finally {
            datasource.releaseResources(null, rs);
          }
        }
      }
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE,
          datasource.translate(sqlSelectUnrevokedSn100, ex).getMessage());
    } catch (IOException ex) {
      throw new OperationException(CRL_FAILURE, ex.getMessage());
    } finally {
      datasource.releaseResources(ps, null);
    }

    if (!snList.isEmpty()) {
      // check whether revoked certificates have been unrevoked.
      ps = borrowPreparedStatement(sqlSelectUnrevokedSn);
      try {
        for (BigInteger sn : snList) {
          ps.setString(1, sn.toString(16));
          ResultSet rs = ps.executeQuery();
          try {
            if (rs.next()) {
              ret.add(new CertRevInfoWithSerial(0L,
                  sn, // serial
                  CrlReason.REMOVE_FROM_CRL, // reason
                  new Date(100L * rs.getLong("LUPDATE")), //revocationTime,
                  null)); // invalidityTime
            }
          } finally {
            datasource.releaseResources(null, rs);
          }
        }
      } catch (SQLException ex) {
        throw new OperationException(DATABASE_FAILURE,
            datasource.translate(sqlSelectUnrevokedSn, ex).getMessage());
      } finally {
        datasource.releaseResources(ps, null);
      }
    }

    // get list of certificates revoked after the generation of Base FullCRL
    // we check all revoked certificates with LUPDATE field (last update) > THISUPDATE - 1.
    final int numEntries = 1000;

    String coreSql =
        "ID,SN,RR,RT,RIT FROM CERT WHERE ID>? AND CA_ID=? AND REV=1 AND NAFTER>? AND LUPDATE>?";
    String sql = datasource.buildSelectFirstSql(numEntries, "ID ASC", coreSql);
    ps = borrowPreparedStatement(sql);
    long startId = 1;

    // -1: so that no entry is ignored: consider all revoked certificates with
    // Database.lastUpdate >= CRL.thisUpdate
    final long updatedSince = crl.getThisUpdate().getDate().getTime() / 1000 - 1;

    try {
      ResultSet rs = null;
      while (true) {
        ps.setLong(1, startId - 1);
        ps.setInt(2, ca.getId());
        ps.setLong(3, notExpiredAt.getTime() / 1000 + 1);
        ps.setLong(4, updatedSince);
        rs = ps.executeQuery();

        try {
          int num = 0;
          while (rs.next()) {
            num++;
            long id = rs.getLong("ID");
            if (id > startId) {
              startId = id;
            }

            BigInteger sn = new BigInteger(rs.getString("SN"), 16);
            if (allSnSet.contains(sn)) {
              // already contained in CRL
              continue;
            }

            long revInvalidityTime = rs.getLong("RIT");
            Date invalidityTime = (revInvalidityTime == 0)
                                    ? null : new Date(1000 * revInvalidityTime);
            CertRevInfoWithSerial revInfo = new CertRevInfoWithSerial(id,
                sn, rs.getInt("RR"), // revReason
                new Date(1000 * rs.getLong("RT")), invalidityTime);
            ret.add(revInfo);
          }

          if (num < numEntries) {
            // no more entries
            break;
          }
        } finally {
          datasource.releaseResources(null,rs);
        }
      }
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, null);
    }

    return ret;
  } // method getCertsForDeltaCrl

  public CertStatus getCertStatusForSubject(NameId ca, X500Name subject)
      throws OperationException {
    long subjectFp = X509Util.fpCanonicalizedName(subject);
    return getCertStatusForSubjectFp(ca, subjectFp);
  } // method getCertStatusForSubject

  private CertStatus getCertStatusForSubjectFp(NameId ca, long subjectFp)
      throws OperationException {
    notNull(ca, "ca");

    final String sql = sqlCertStatusForSubjectFp;
    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      ps.setLong(1, subjectFp);
      ps.setInt(2, ca.getId());
      rs = ps.executeQuery();
      if (!rs.next()) {
        return CertStatus.UNKNOWN;
      }
      return rs.getBoolean("REV") ? CertStatus.REVOKED : CertStatus.GOOD;
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  } // method getCertStatusForSubjectFp

  public boolean isCertForSubjectIssued(NameId ca, long subjectFp)
      throws OperationException {
    notNull(ca, "ca");
    String sql = sqlCertforSubjectIssued;
    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    try {
      ps.setInt(1, ca.getId());
      ps.setLong(2, subjectFp);
      rs = ps.executeQuery();
      return rs.next();
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  }

  private String base64Fp(byte[] data) {
    return HashAlgo.SHA1.base64Hash(data);
  } // method base64Fp

  private PreparedStatement borrowPreparedStatement(String sqlQuery)
      throws OperationException {
    try {
      return datasource.prepareStatement(sqlQuery);
    } catch (DataAccessException ex) {
      LOG.debug("DataAccessException", ex);
      throw new OperationException(DATABASE_FAILURE, ex.getMessage());
    }
  } // method borrowPreparedStatement

  public boolean isHealthy() {
    final String sql = "SELECT ID FROM CA";

    try {
      PreparedStatement ps = borrowPreparedStatement(sql);

      ResultSet rs = null;
      try {
        rs = ps.executeQuery();
      } finally {
        datasource.releaseResources(ps, rs);
      }
      return true;
    } catch (Exception ex) {
      LOG.error("isHealthy(). {}: {}", ex.getClass().getName(), ex.getMessage());
      LOG.debug("isHealthy()", ex);
      return false;
    }
  } // method isHealthy

  public String getLatestSerialNumber(X500Name nameWithSn)
      throws OperationException {
    RDN[] rdns1 = nameWithSn.getRDNs();
    RDN[] rdns2 = new RDN[rdns1.length];
    for (int i = 0; i < rdns1.length; i++) {
      RDN rdn = rdns1[i];
      rdns2[i] =  rdn.getFirst().getType().equals(ObjectIdentifiers.DN.serialNumber)
          ? new RDN(ObjectIdentifiers.DN.serialNumber, new DERPrintableString("%")) : rdn;
    }

    String namePattern = X509Util.getRfc4519Name(new X500Name(rdns2));

    final String sql = sqlLatestSerialForSubjectLike;
    ResultSet rs = null;
    PreparedStatement ps = borrowPreparedStatement(sql);

    String subjectStr;

    try {
      ps.setString(1, namePattern);
      rs = ps.executeQuery();
      if (!rs.next()) {
        return null;
      }

      subjectStr = rs.getString("SUBJECT");
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, ex.getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }

    X500Name lastName = new X500Name(subjectStr);
    RDN[] rdns = lastName.getRDNs(ObjectIdentifiers.DN.serialNumber);
    if (rdns == null || rdns.length == 0) {
      return null;
    }

    return X509Util.rdnValueToString(rdns[0].getFirst().getValue());
  } // method getLatestSerialNumber

  public void deleteUnreferencedRequests()
      throws OperationException {
    final String sql = SQL_DELETE_UNREFERENCED_REQUEST;
    PreparedStatement ps = borrowPreparedStatement(sql);
    ResultSet rs = null;
    try {
      ps.executeUpdate();
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, rs);
    }
  } // method deleteUnreferencedRequests

  public long addRequest(byte[] request)
      throws OperationException {
    notNull(request, "request");

    long id = idGenerator.nextId();
    long currentTimeSeconds = System.currentTimeMillis() / 1000;
    String b64Request = Base64.encodeToString(request);
    final String sql = SQL_ADD_REQUEST;
    PreparedStatement ps = borrowPreparedStatement(sql);
    try {
      ps.setLong(1, id);
      ps.setLong(2, currentTimeSeconds);
      ps.setString(3, b64Request);
      ps.executeUpdate();
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, null);
    }

    return id;
  } // method addRequest

  public void addRequestCert(long requestId, long certId)
      throws OperationException {
    final String sql = SQL_ADD_REQCERT;
    long id = idGenerator.nextId();
    PreparedStatement ps = borrowPreparedStatement(sql);
    try {
      ps.setLong(1, id);
      ps.setLong(2, requestId);
      ps.setLong(3, certId);
      ps.executeUpdate();
    } catch (SQLException ex) {
      throw new OperationException(DATABASE_FAILURE, datasource.translate(sql, ex).getMessage());
    } finally {
      datasource.releaseResources(ps, null);
    }
  } // method addRequestCert

  private String getSqlCidFromPublishQueue(int numEntries) {
    String sql = cacheSqlCidFromPublishQueue.get(numEntries);
    if (sql == null) {
      sql = datasource.buildSelectFirstSql(numEntries, "CID ASC",
          "CID FROM PUBLISHQUEUE WHERE PID=? AND CA_ID=?");
      cacheSqlCidFromPublishQueue.put(numEntries, sql);
    }
    return sql;
  } // method getSqlCidFromPublishQueue

  private String getSqlExpiredSerials(int numEntries) {
    String sql = cacheSqlExpiredSerials.get(numEntries);
    if (sql == null) {
      sql = datasource.buildSelectFirstSql(numEntries, "SN FROM CERT WHERE CA_ID=? AND NAFTER<?");
      cacheSqlExpiredSerials.put(numEntries, sql);
    }
    return sql;
  } // method getSqlExpiredSerials

  private String getSqlSuspendedSerials(int numEntries) {
    String sql = cacheSqlSuspendedSerials.get(numEntries);
    if (sql == null) {
      sql = datasource.buildSelectFirstSql(numEntries,
          "SN FROM CERT WHERE CA_ID=? AND LUPDATE<? AND RR=?");
      cacheSqlSuspendedSerials.put(numEntries, sql);
    }
    return sql;
  } // method getSqlSuspendedSerials

  private String getSqlRevokedCerts(int numEntries) {
    String sql = cacheSqlRevokedCerts.get(numEntries);
    if (sql == null) {
      String coreSql =
          "ID,SN,RR,RT,RIT FROM CERT WHERE ID>? AND CA_ID=? AND REV=1 AND NAFTER>?";
      sql = datasource.buildSelectFirstSql(numEntries, "ID ASC", coreSql);
      cacheSqlRevokedCerts.put(numEntries, sql);
    }
    return sql;
  } // method getSqlRevokedCerts

  private String getSqlSerials(int numEntries, boolean onlyRevoked) {
    LruCache<Integer, String> cache = onlyRevoked ? cacheSqlSerialsRevoked : cacheSqlSerials;
    String sql = cache.get(numEntries);
    if (sql == null) {
      String coreSql = "ID,SN FROM CERT WHERE ID>? AND CA_ID=?";
      if (onlyRevoked) {
        coreSql += "AND REV=1";
      }
      sql = datasource.buildSelectFirstSql(numEntries, "ID ASC", coreSql);
      cache.put(numEntries, sql);
    }
    return sql;
  } // method getSqlSerials

  private String getSqlSerials(int numEntries, Date notExpiredAt, boolean onlyRevoked,
      boolean withEe) {
    String sql = StringUtil.concat("ID,SN FROM CERT WHERE ID>? AND CA_ID=?",
        (notExpiredAt != null ? " AND NAFTER>?" : ""),
        (onlyRevoked ? " AND REV=1" : ""), (withEe ? " AND EE=?" : ""));
    return datasource.buildSelectFirstSql(numEntries, "ID ASC", sql);
  } // method getSqlSerials

  private static void setBoolean(PreparedStatement ps, int index, boolean value)
      throws SQLException {
    ps.setInt(index, value ? 1 : 0);
  } // method setBoolean

  private static void setLong(PreparedStatement ps, int index, Long value)
      throws SQLException {
    if (value != null) {
      ps.setLong(index, value.longValue());
    } else {
      ps.setNull(index, Types.BIGINT);
    }
  } // method setLong

  private static void setInt(PreparedStatement ps, int index, Integer value)
      throws SQLException {
    if (value != null) {
      ps.setInt(index, value.intValue());
    } else {
      ps.setNull(index, Types.INTEGER);
    }
  } // method setInt

  private static void setDateSeconds(PreparedStatement ps, int index, Date date)
      throws SQLException {
    if (date != null) {
      ps.setLong(index, date.getTime() / 1000);
    } else {
      ps.setNull(index, Types.BIGINT);
    }
  } // method setDateSeconds

}
