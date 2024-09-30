package fr.edyp.proline.extraction.commands;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import fr.edyp.proline.extraction.IsobaricEnumeration;
import fr.edyp.proline.extraction.Output;
import fr.edyp.proline.extraction.PeaksFWHMStatistics;
import fr.proline.core.orm.util.DataStoreConnectorFactory;
import fr.proline.repository.IDatabaseConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Parameters(
        commandNames = { "isobaric" },
        commandDescription = "Compute isobaric enumerations"
)
public class IsobariqueEnumerationCommand {

  private static Logger logger = LoggerFactory.getLogger(PeaksFWHMStatistics.class);

  @Parameter(
          names = { "--project", "-p" },
          description = "Id of the project ",
          required = true
  )
  Long projectId;

  @Parameter(names = "--help", help = true)
  public boolean help;

  public void run() {
    try {
      DataStoreConnectorFactory connectorFactory = DataStoreConnectorFactory.getInstance();
      connectorFactory.initialize("db_uds.properties");

      IDatabaseConnector udsConnector = connectorFactory.getUdsDbConnector();
      IDatabaseConnector msiConnector = connectorFactory.getMsiDbConnector(projectId);
      IsobaricEnumeration command = new IsobaricEnumeration(udsConnector, msiConnector);
      Output output = new Output();
      command.run(output);
      output.close();
    } catch (Exception e) {
      logger.error("Error in ConnectDatastore", e);
      e.printStackTrace();
    }

  }
}
