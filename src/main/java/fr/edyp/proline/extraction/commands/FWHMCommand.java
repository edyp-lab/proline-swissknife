package fr.edyp.proline.extraction.commands;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import fr.edyp.proline.extraction.Output;
import fr.edyp.proline.extraction.PeaksFWHMStatistics;
import fr.proline.core.orm.util.DataStoreConnectorFactory;
import fr.proline.repository.IDatabaseConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Parameters(
        commandNames = { "fwhm" },
        commandDescription = "Extract full width half middle information from quantified elution peaks"
)
public class FWHMCommand {

  private static Logger logger = LoggerFactory.getLogger(PeaksFWHMStatistics.class);

  @Parameter(
          names = { "--project", "-p" },
          description = "Id of the project ",
          required = true
  )
  Long projectId;

  @Parameter(
          names = { "--dataset", "-d" },
          description = "Id of the quantification dataset containing the elution peaks to extract ",
          required = true
  )
  Long datasetId;

  @Parameter(names = "--help", help = true)
  public boolean help;

  public void run() {
    try {
      DataStoreConnectorFactory connectorFactory = DataStoreConnectorFactory.getInstance();
      connectorFactory.initialize("db_uds.properties");

      IDatabaseConnector udsConnector = connectorFactory.getUdsDbConnector();
      IDatabaseConnector lcmsConnector = connectorFactory.getLcMsDbConnector(projectId);

      PeaksFWHMStatistics command = new PeaksFWHMStatistics(udsConnector, lcmsConnector, datasetId);
      Output output = new Output();
      command.run(output);
      output.close();
    } catch (Exception e) {
      logger.error("Error in ConnectDatastore", e);
      e.printStackTrace();
    }

  }
}
