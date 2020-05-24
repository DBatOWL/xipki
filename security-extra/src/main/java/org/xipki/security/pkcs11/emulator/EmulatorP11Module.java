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

package org.xipki.security.pkcs11.emulator;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.password.PasswordResolverException;
import org.xipki.security.pkcs11.P11Module;
import org.xipki.security.pkcs11.P11ModuleConf;
import org.xipki.security.pkcs11.P11Slot;
import org.xipki.security.pkcs11.P11SlotIdentifier;
import org.xipki.security.pkcs11.P11TokenException;
import org.xipki.util.Args;
import org.xipki.util.IoUtil;
import org.xipki.util.StringUtil;

/**
 * {@link P11Module} for PKCS#11 emulator.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

public class EmulatorP11Module extends P11Module {

  public static enum Vendor {

    YUBIKEY,
    GENERAL

  } // class Vendor

  public static final String TYPE = "emulator";

  public static final String DFLT_BASEDIR =
      System.getProperty("java.io.tmpdir") + File.separator + "pkcs11-emulator";

  private static final Logger LOG = LoggerFactory.getLogger(EmulatorP11Module.class);

  private final String description;

  private EmulatorP11Module(P11ModuleConf moduleConf) throws P11TokenException {
    super(moduleConf);

    Vendor vendor = null;
    File baseDir;
    String modulePath = moduleConf.getNativeLibrary().trim();
    String parametersStr = "";
    if (!modulePath.isEmpty()) {
      int idx = modulePath.indexOf('?');
      if (idx != -1) {
        parametersStr = modulePath.substring(idx);
        modulePath = modulePath.substring(0, idx);

        StringTokenizer tokens = new StringTokenizer(parametersStr, "?");
        while (tokens.hasMoreTokens()) {
          String token = tokens.nextToken();
          List<String> strs = StringUtil.split(token, "=");
          if (strs.size() != 2) {
            continue;
          }

          if (strs.get(0).equalsIgnoreCase("vendor")) {
            vendor = Vendor.valueOf(strs.get(1).toUpperCase());
          }
        }
      }
    }

    if (modulePath.isEmpty()) {
      baseDir = new File(DFLT_BASEDIR);
      LOG.info("Use existing default base directory: " + DFLT_BASEDIR);
    } else {
      baseDir = new File(IoUtil.expandFilepath(modulePath));
      LOG.info("Use explicit base directory: " + baseDir.getPath());
    }

    if (!baseDir.exists()) {
      try {
        createExampleRepository(baseDir, 2);
      } catch (IOException ex) {
        throw new P11TokenException(
            "could not initialize the base direcotry: " + baseDir.getPath(), ex);
      }

      LOG.info("create and initialize the base directory: " + baseDir.getPath());
    }

    this.description = StringUtil.concat("PKCS#11 emulator", "\nPath: ",
        baseDir.getAbsolutePath() + parametersStr);

    File[] children = baseDir.listFiles();

    if (children == null || children.length == 0) {
      LOG.error("found no slots");
      setSlots(Collections.emptySet());
      return;
    }

    Set<Integer> allSlotIndexes = new HashSet<>();
    Set<Long> allSlotIdentifiers = new HashSet<>();

    List<P11SlotIdentifier> slotIds = new LinkedList<>();

    for (File child : children) {
      if ((child.isDirectory() && child.canRead() && !child.exists())) {
        LOG.warn("ignore path {}, it does not point to a readable exist directory",
            child.getPath());
        continue;
      }

      String filename = child.getName();
      String[] tokens = filename.split("-");
      if (tokens == null || tokens.length != 2) {
        LOG.warn("ignore dir {}, invalid filename syntax", child.getPath());
        continue;
      }

      int slotIndex;
      long slotId;
      try {
        slotIndex = Integer.parseInt(tokens[0]);
        slotId = Long.parseLong(tokens[1]);
      } catch (NumberFormatException ex) {
        LOG.warn("ignore dir {}, invalid filename syntax", child.getPath());
        continue;
      }

      if (allSlotIndexes.contains(slotIndex)) {
        LOG.error("ignore slot dir, the same slot index has been assigned", filename);
        continue;
      }

      if (allSlotIdentifiers.contains(slotId)) {
        LOG.error("ignore slot dir, the same slot identifier has been assigned", filename);
        continue;
      }

      allSlotIndexes.add(slotIndex);
      allSlotIdentifiers.add(slotId);

      P11SlotIdentifier slotIdentifier = new P11SlotIdentifier(slotIndex, slotId);
      if (!moduleConf.isSlotIncluded(slotIdentifier)) {
        LOG.info("skipped slot {}", slotId);
        continue;
      }

      slotIds.add(slotIdentifier);
    } // end for

    Set<P11Slot> slots = new HashSet<>();
    for (P11SlotIdentifier slotId : slotIds) {
      List<char[]> pwd;
      try {
        pwd = moduleConf.getPasswordRetriever().getPassword(slotId);
      } catch (PasswordResolverException ex) {
        throw new P11TokenException("PasswordResolverException: " + ex.getMessage(), ex);
      }

      File slotDir = new File(baseDir, slotId.getIndex() + "-" + slotId.getId());

      if (pwd == null) {
        throw new P11TokenException("no password is configured");
      }

      if (pwd.size() != 1) {
        throw new P11TokenException(pwd.size() + " passwords are configured, but 1 is permitted");
      }

      char[] firstPwd = pwd.get(0);
      PrivateKeyCryptor privateKeyCryptor = new PrivateKeyCryptor(firstPwd);

      int maxSessions = 20;
      P11Slot slot = new EmulatorP11Slot(moduleConf.getName(), slotDir, slotId,
          moduleConf.isReadOnly(), firstPwd, privateKeyCryptor, moduleConf.getP11MechanismFilter(),
          moduleConf.getP11NewObjectConf(), maxSessions, vendor);
      slots.add(slot);
    }

    setSlots(slots);
  } // constructor

  public static P11Module getInstance(P11ModuleConf moduleConf) throws P11TokenException {
    Args.notNull(moduleConf, "moduleConf");
    return new EmulatorP11Module(moduleConf);
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public void close() {
    LOG.info("close", "close pkcs11 module: {}", getName());
  }

  private void createExampleRepository(File dir, int numSlots) throws IOException {
    for (int i = 0; i < numSlots; i++) {
      File slotDir = new File(dir, i + "-" + (800000 + i));
      slotDir.mkdirs();

      File slotInfoFile = new File(slotDir, "slot.info");
      IoUtil.save(slotInfoFile, StringUtil.toUtf8Bytes("namedCurveSupported=true\n"));
    }
  }

}
