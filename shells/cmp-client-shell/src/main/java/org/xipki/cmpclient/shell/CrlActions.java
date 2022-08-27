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

package org.xipki.cmpclient.shell;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.completers.FileCompleter;
import org.bouncycastle.cert.X509CRLHolder;
import org.xipki.cmpclient.CmpClientException;
import org.xipki.cmpclient.PkiErrorException;
import org.xipki.cmpclient.shell.Actions.ClientAction;
import org.xipki.shell.CmdFailure;
import org.xipki.shell.Completers;
import org.xipki.util.ReqRespDebug;

/**
 * CMP client actions related to CRL.
 *
 * @author Lijun Liao
 *
 */
public class CrlActions {

  @Command(scope = "xi", name = "cmp-get-crl", description = "download CRL")
  @Service
  public static class CmpGetCrl extends CrlAction {

    @Override
    protected X509CRLHolder retrieveCrl() throws CmpClientException, PkiErrorException {
      ReqRespDebug debug = getReqRespDebug();
      try {
        return client.downloadCrl(caName, debug);
      } finally {
        saveRequestResponse(debug);
      }
    }

    @Override
    protected Object execute0() throws Exception {
      X509CRLHolder crl;
      try {
        crl = retrieveCrl();
      } catch (PkiErrorException ex) {
        throw new CmdFailure("received no CRL from server: " + ex.getMessage());
      }

      if (crl == null) {
        throw new CmdFailure("received no CRL from server");
      }

      saveVerbose("saved CRL to file", outFile, encodeCrl(crl.getEncoded(), outform));
      return null;
    } // method execute0

  } // class CmpGetCrl

  public abstract static class CrlAction extends ClientAction {

    @Option(name = "--outform", description = "output format of the CRL")
    @Completion(Completers.DerPemCompleter.class)
    protected String outform = "der";

    @Option(name = "--out", aliases = "-o", required = true, description = "where to save the CRL")
    @Completion(FileCompleter.class)
    protected String outFile;

    protected abstract X509CRLHolder retrieveCrl()
        throws CmpClientException, PkiErrorException;

    @Override
    protected Object execute0() throws Exception {
      X509CRLHolder crl;
      try {
        crl = retrieveCrl();
      } catch (PkiErrorException ex) {
        throw new CmdFailure("received no CRL from server: " + ex.getMessage());
      }

      if (crl == null) {
        throw new CmdFailure("received no CRL from server");
      }

      saveVerbose("saved CRL to file", outFile, encodeCrl(crl.getEncoded(), outform));
      return null;
    } // method execute0

  } // class CrlAction

}
