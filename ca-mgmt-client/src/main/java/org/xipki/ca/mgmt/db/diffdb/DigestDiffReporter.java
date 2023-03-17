// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.mgmt.db.diffdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.util.Args;
import org.xipki.util.IoUtil;
import org.xipki.util.LogUtil;
import org.xipki.util.StringUtil;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Report of the comparison result of two databases.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

class DigestDiffReporter implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(DigestDiffReporter.class);

  private final String reportDirname;

  private final BufferedWriter goodWriter;

  private final BufferedWriter diffWriter;

  private final BufferedWriter missingWriter;

  private final BufferedWriter unexpectedWriter;

  private final BufferedWriter errorWriter;

  private Date startTime;

  private final AtomicInteger numGood = new AtomicInteger(0);

  private final AtomicInteger numDiff = new AtomicInteger(0);

  private final AtomicInteger numMissing = new AtomicInteger(0);

  private final AtomicInteger numUnexpected = new AtomicInteger(0);

  private final AtomicInteger numError = new AtomicInteger(0);

  public DigestDiffReporter(String reportDirname, byte[] caCertBytes)
      throws IOException {
    this.reportDirname = Args.notBlank(reportDirname, "reportDirname");
    File dir = new File(reportDirname);
    dir.mkdirs();
    IoUtil.save(new File(dir, "ca.der"), caCertBytes);

    String dirPath = dir.getPath();

    this.missingWriter = Files.newBufferedWriter(Paths.get(dirPath, "missing"));
    this.unexpectedWriter = Files.newBufferedWriter(Paths.get(dirPath, "unexpected"));
    this.diffWriter = Files.newBufferedWriter(Paths.get(dirPath, "diff"));
    this.goodWriter = Files.newBufferedWriter(Paths.get(dirPath, "good"));
    this.errorWriter = Files.newBufferedWriter(Paths.get(dirPath, "error"));

    start();
  } // constructor

  public final void start() {
    startTime = new Date();
  }

  public String getReportDirname() {
    return reportDirname;
  }

  public void addMissing(BigInteger serialNumber) throws IOException {
    numMissing.incrementAndGet();
    writeSerialNumberLine(missingWriter, serialNumber);
  }

  public void addGood(BigInteger serialNumber) throws IOException {
    numGood.incrementAndGet();
    writeSerialNumberLine(goodWriter, serialNumber);
  }

  public void addUnexpected(BigInteger serialNumber) throws IOException {
    numUnexpected.incrementAndGet();
    writeSerialNumberLine(unexpectedWriter, serialNumber);
  }

  public void addDiff(DigestEntry refCert, DigestEntry targetCert) throws IOException {
    if (Args.notNull(refCert, "refCert").getSerialNumber().equals(
        Args.notNull(targetCert, "targetCert").getSerialNumber())) {
      throw new IllegalArgumentException("refCert and targetCert are not of the same serialNumber");
    }

    numDiff.incrementAndGet();
    String msg = StringUtil.concat(refCert.getSerialNumber().toString(16),
        "\t", refCert.encodedOmitSeriaNumber(), "\t", targetCert.encodedOmitSeriaNumber(), "\n");
    synchronized (diffWriter) {
      diffWriter.write(msg);
    }
  } // method addDiff

  public void addError(String errorMessage) throws IOException {
    String msg = StringUtil.concat(Args.notNull(errorMessage, "errorMessage"), "\n");
    numError.incrementAndGet();
    synchronized (errorWriter) {
      errorWriter.write(msg);
    }
  } // method addError

  public void addNoCaMatch() throws IOException {
    synchronized (errorWriter) {
      errorWriter.write("could not find corresponding CA in target to diff\n");
    }
  }

  @Override
  public void close() {
    closeWriter(missingWriter, unexpectedWriter, diffWriter, goodWriter, errorWriter);

    int sum = numGood.get() + numDiff.get() + numMissing.get() + numUnexpected.get() + numError.get();
    Date now = new Date();
    int durationSec = (int) ((now.getTime() - startTime.getTime()) / 1000);

    String speedStr = (durationSec > 0)
        ? StringUtil.formatAccount(sum / durationSec, false) + " /s" : "--";

    String str = StringUtil.concatObjectsCap(200,
        "      sum : ", StringUtil.formatAccount(sum, false),
        "\n      good: ", StringUtil.formatAccount(numGood.get(), false),
        "\n      diff: ", StringUtil.formatAccount(numDiff.get(), false),
        "\n   missing: ", StringUtil.formatAccount(numMissing.get(), false),
        "\nunexpected: ", StringUtil.formatAccount(numUnexpected.get(), false),
        "\n     error: ", StringUtil.formatAccount(numError.get(), false),
        "\n  duration: ", StringUtil.formatTime(durationSec, false),
        "\nstart time: ", startTime,
        "\n  end time: ", now,
        "\n     speed: ", speedStr, "\n");

    try {
      IoUtil.save(reportDirname + File.separator + "overview.txt", StringUtil.toUtf8Bytes(str));
    } catch (IOException ex) {
      System.out.println("Could not write overview.txt with following content\n" + str);
    }
  } // method close

  private static void writeSerialNumberLine(BufferedWriter writer, BigInteger serialNumber)
      throws IOException {
    synchronized (writer) {
      writer.write(StringUtil.concat(serialNumber.toString(16), "\n"));
    }
  } // method writeSerialNumberLine

  private static void closeWriter(Writer... writers) {
    for (Writer writer : writers) {
      try {
        writer.close();
      } catch (Exception ex) {
        LogUtil.warn(LOG, ex, "could not close writer " + writer);
      }
    }
  } // method closeWriter

}
