// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.ca.api.CertificateInfo;
import org.xipki.ca.api.NameId;
import org.xipki.ca.server.db.CertStore;
import org.xipki.ca.server.db.CertStore.SerialWithId;
import org.xipki.pki.OperationException;
import org.xipki.security.X509Cert;
import org.xipki.util.Args;
import org.xipki.util.LogUtil;
import org.xipki.util.ProcessLog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publish certificates.
 *
 * @author Lijun Liao (xipki)
 * @since 2.1.0
 */

class CertRepublisher {

  private interface QueueEntry {

    EndOfQueue END_OF_QUEUE = new EndOfQueue();

    class EndOfQueue implements QueueEntry {

      private EndOfQueue() {
      }

    }

  } // class QueueEntry

  private static class SerialWithIdQueueEntry implements QueueEntry {

    private final SerialWithId serialWithId;

    public SerialWithIdQueueEntry(SerialWithId serialWithId) {
      this.serialWithId = Args.notNull(serialWithId, "serialWithId");
    }

    public SerialWithId serialWithId() {
      return serialWithId;
    }

  } // class SerialWithIdQueueEntry

  private class CertRepublishProducer implements Runnable {

    private boolean failed;

    private CertRepublishProducer() {
    }

    @Override
    public void run() {
      final int numEntries = 100;
      long startId = 1;

      try {
        List<SerialWithId> serials;
        do {
          serials = certstore.getSerialNumbers(ca, startId, numEntries, onlyRevokedCerts);
          long maxId = 1;
          for (SerialWithId sid : serials) {
            if (sid.getId() > maxId) {
              maxId = sid.getId();
            }
            queue.put(new SerialWithIdQueueEntry(sid));
          }

          startId = maxId + 1;
        } while (serials.size() >= numEntries && !failed && !stopMe.get());

        queue.put(QueueEntry.END_OF_QUEUE);
      } catch (OperationException | InterruptedException ex) {
        LogUtil.error(LOG, ex, "error in RepublishProducer");
        failed = true;
      }

      if (!queue.contains(QueueEntry.END_OF_QUEUE)) {
        try {
          queue.put(QueueEntry.END_OF_QUEUE);
        } catch (InterruptedException ex) {
          LogUtil.error(LOG, ex, "error in RepublishProducer");
          failed = true;
        }
      }
    } // method run
  } // class CertRepublishProducer

  private class CertRepublishConsumer implements Runnable {

    private boolean failed;

    private CertRepublishConsumer() {
    }

    @Override
    public void run() {
      while (!failed) {
        QueueEntry entry;
        try {
          entry = queue.take();
        } catch (InterruptedException ex) {
          LogUtil.error(LOG, ex, "could not take from queue");
          failed = true;
          break;
        }

        if (entry instanceof QueueEntry.EndOfQueue) {
          // re-add it to queue so that other consumers know it
          try {
            queue.put(entry);
          } catch (InterruptedException ex) {
            LogUtil.warn(LOG, ex, "could not re-add EndOfQueue to queue");
          }
          break;
        }

        SerialWithId sid = ((SerialWithIdQueueEntry) entry).serialWithId();

        CertificateInfo certInfo;

        try {
          certInfo = certstore.getCertForId(ca, caCert, sid.getId(), caIdNameMap);
        } catch (OperationException ex) {
          LogUtil.error(LOG, ex);
          failed = true;
          break;
        }

        boolean allSucc = true;
        for (IdentifiedCertPublisher publisher : publishers) {
          if (!certInfo.isRevoked() && !publisher.publishsGoodCert()) {
            continue;
          }

          boolean successful = publisher.certificateAdded(certInfo);
          if (!successful) {
            LOG.error("republish certificate serial={} to publisher {} failed",
                LogUtil.formatCsn(sid.getSerial()), publisher.getIdent());
            allSucc = false;
          }
        }

        if (!allSucc) {
          break;
        }
        processLog.addNumProcessed(1);
      }
    } // method run

  } // class CertRepublishConsumer

  private static final Logger LOG = LoggerFactory.getLogger(CertRepublisher.class);

  private final NameId ca;

  private final X509Cert caCert;

  private final CaIdNameMap caIdNameMap;

  private final CertStore certstore;

  private final List<IdentifiedCertPublisher> publishers;

  private final boolean onlyRevokedCerts;

  private final int numThreads;

  private final BlockingQueue<QueueEntry> queue = new ArrayBlockingQueue<>(1000);

  private final AtomicBoolean stopMe = new AtomicBoolean(false);

  private ProcessLog processLog;

  CertRepublisher(NameId ca, X509Cert caCert, CaIdNameMap caIdNameMap, CertStore certstore,
      List<IdentifiedCertPublisher> publishers, boolean onlyRevokedCerts, int numThreads) {
    this.ca = Args.notNull(ca, "ca");
    this.caCert = Args.notNull(caCert, "caCert");
    this.caIdNameMap = Args.notNull(caIdNameMap, "caIdNameMap");
    this.certstore = Args.notNull(certstore, "certstore");
    this.publishers = Args.notEmpty(publishers, "publishers");
    this.onlyRevokedCerts = onlyRevokedCerts;
    this.numThreads = Args.positive(numThreads, "numThreads");
  } // constructor

  boolean republish() {
    try {
      return republish0();
    } finally {
      if (processLog != null) {
        processLog.finish();
        processLog.printTrailer();
      }
    }
  } // method republish

  private boolean republish0() {
    long total;
    try {
      total = certstore.getCountOfCerts(ca, onlyRevokedCerts);
    } catch (OperationException ex) {
      LogUtil.error(LOG, ex, "could not getCountOfCerts");
      return false;
    }
    processLog = new ProcessLog(total);
    processLog.printHeader();

    ExecutorService executor = Executors.newFixedThreadPool(numThreads + 1);
    List<CertRepublishConsumer> consumers = new ArrayList<>(numThreads);
    for (int i = 0; i < numThreads; i++) {
      CertRepublishConsumer consumer = new CertRepublishConsumer();
      consumers.add(consumer);
    }

    CertRepublishProducer producer = new CertRepublishProducer();

    executor.execute(producer);
    for (CertRepublishConsumer consumer : consumers) {
      executor.execute(consumer);
    }

    executor.shutdown();
    boolean successful = true;

    while (true) {
      processLog.printStatus();

      if (successful) {
        if (producer.failed) {
          successful = false;
        }

        if (successful) {
          for (CertRepublishConsumer consumer : consumers) {
            if (consumer.failed) {
              successful = false;
              break;
            }
          }
        }

        if (!successful) {
          stopMe.set(true);
          LOG.warn("failed");
        }
      }

      try {
        boolean terminated = executor.awaitTermination(1, TimeUnit.SECONDS);
        if (terminated) {
          break;
        }
      } catch (InterruptedException ex) {
        stopMe.set(true);
        LogUtil.warn(LOG, ex, "interrupted: " + ex.getMessage());
      }
    }

    if (successful) {
      if (producer.failed) {
        successful = false;
      }

      if (successful) {
        for (CertRepublishConsumer consumer : consumers) {
          if (consumer.failed) {
            successful = false;
            break;
          }
        }
      }

      if (!successful) {
        LOG.warn("failed");
      }
    }

    return successful;
  } // method republish0

}
