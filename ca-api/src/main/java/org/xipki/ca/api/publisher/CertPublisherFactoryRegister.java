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

package org.xipki.ca.api.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.util.Args;
import org.xipki.util.exception.ObjectCreationException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Register of CertPublisherFacotries.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

public class CertPublisherFactoryRegister {

  private static final Logger LOG = LoggerFactory.getLogger(CertPublisherFactoryRegister.class);

  private final ConcurrentLinkedDeque<CertPublisherFactory> factories = new ConcurrentLinkedDeque<>();

  /**
   * Whether publisher of given type can be created.
   *
   * @param type
   *          Type of the publisher. Must not be {@code null}.
   * @return whether publisher of this type can be created.
   */
  public boolean canCreatePublisher(String type) {
    for (CertPublisherFactory service : factories) {
      if (service.canCreatePublisher(type)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Create new publisher of given type.
   *
   * @param type
   *          Type of the publisher. Must not be {@code null}.
   * @return new publisher.
   * @throws ObjectCreationException
   *           if publisher could not be created.
   */
  public CertPublisher newPublisher(String type) throws ObjectCreationException {
    Args.notBlank(type, "type");

    for (CertPublisherFactory service : factories) {
      if (service.canCreatePublisher(type)) {
        return service.newPublisher(type);
      }
    }

    throw new ObjectCreationException("could not find factory to create Publisher of type " + type);
  } // method newPublisher

  /**
   * Retrieves the types of supported publishers.
   * @return lower-case types of supported publishers, never {@code null}.
   */
  public Set<String> getSupportedTypes() {
    Set<String> types = new HashSet<>();
    for (CertPublisherFactory service : factories) {
      types.addAll(service.getSupportedTypes());
    }
    return Collections.unmodifiableSet(types);
  }

  public void bindService(CertPublisherFactory service) {
    registFactory(service);
  }

  public void registFactory(CertPublisherFactory factory) {
    //might be null if dependency is optional
    if (factory == null) {
      LOG.info("registFactory invoked with null.");
      return;
    }

    boolean replaced = factories.remove(factory);
    factories.add(factory);

    String action = replaced ? "replaced" : "added";
    LOG.info("{} CertPublisherFactory binding for {}", action, factory);
  }

  public void unbindService(CertPublisherFactory service) {
    unregistFactory(service);
  }

  public void unregistFactory(CertPublisherFactory factory) {
    //might be null if dependency is optional
    if (factory == null) {
      LOG.info("unregistFactory invoked with null.");
      return;
    }

    if (factories.remove(factory)) {
      LOG.info("removed CertPublisherFactory binding for {}", factory);
    } else {
      LOG.info("no CertPublisherFactory binding found to remove for {}", factory);
    }
  } // method unregistFactory

}
