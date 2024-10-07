package fr.edyp.proline.extraction.commands;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import fr.edyp.proline.extraction.Output;
import fr.edyp.proline.extraction.ProjectStatistic;
import fr.proline.core.orm.util.DataStoreConnectorFactory;
import fr.proline.repository.IDatabaseConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Parameters(
        commandNames = { "prj_stats" },
        commandDescription = "get projects statistic"
)
public class ProjectsStatisticCommand {

  private static Logger logger = LoggerFactory.getLogger(ProjectsStatisticCommand.class);


  @Parameter(
          names = { "--output", "-o" },
          description = "Output CSV file path to save info to ",
          required = true
  )
  String outputFilepath;

  @Parameter(
          names = { "--project", "-p" },
          description ="Id of the project to get stat from. If not specified get all projects stats",
          required = false
  )
  Long projectId;

  @Parameter(
          names = { "--valid", "-v" },
          description ="Get statistic for Validated data. If not specified get stats from all data",
          required = false
  )
  boolean validated;


  @Parameter(names = "--help", help = true)
  public boolean help;

  public void run() {
    try {
      logger.info("\n-- You are about to run \"Projects Statistic Command\" using parameter from db_uds.properties.\nBe sure to configure db connection correctly." );
//              "\nType any key to continue, <Ctl>C to stop");
//      System.in.read();
      DataStoreConnectorFactory connectorFactory = DataStoreConnectorFactory.getInstance();
      connectorFactory.initialize("db_uds.properties");

      IDatabaseConnector udsConnector = connectorFactory.getUdsDbConnector();
      ProjectStatistic command = new ProjectStatistic(udsConnector,connectorFactory);
      Output output = new Output();
      output.setOutputFileName(outputFilepath);
      command.run(output,projectId, validated);
      output.close();
    } catch (Exception e) {
      logger.error("Error running command prj_stats : ", e);
      throw new RuntimeException(e);
    }
  }

}
