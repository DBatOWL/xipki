package org.xipki.qa.shell;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.completers.FileCompleter;
import org.xipki.datasource.DataSourceFactory;
import org.xipki.password.PasswordResolver;
import org.xipki.qa.ca.FillKeytool;
import org.xipki.shell.IllegalCmdParamException;
import org.xipki.shell.XiAction;

/**
 * Shell to fill the keypool with keypairs.
 *
 * @since 5.4.0
 * @author Lijun Liao
 */

@Command(scope = "qa", name = "fill-keypool", description = "Fill the keypool")
@Service
public class QaFillKeypoolAction extends XiAction {

  @Option(name = "--db-conf", required = true,
      description = "database configuration file of the keypool")
  @Completion(FileCompleter.class)
  private String dbconfFile;

  @Option(name = "--num",
      description = "number of keypairs for each keyspec")
  private int num = 10;

  private DataSourceFactory datasourceFactory;

  @Reference
  private PasswordResolver passwordResolver;

  public QaFillKeypoolAction() {
    datasourceFactory = new DataSourceFactory();
  }

  @Override
  protected Object execute0() throws Exception {
    if (num < 1) {
      throw new IllegalCmdParamException("invalid num " + num);
    }

    FillKeytool fillKeytool = new FillKeytool(datasourceFactory, passwordResolver, dbconfFile);
    fillKeytool.execute(num);
    return null;
  }
}
