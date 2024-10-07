package fr.edyp.proline.extraction;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import fr.edyp.proline.extraction.commands.FWHMCommand;
import fr.edyp.proline.extraction.commands.IsobariqueEnumerationCommand;
import fr.edyp.proline.extraction.commands.ProjectsStatisticCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static Logger logger = LoggerFactory.getLogger(Main.class);

  public static final String HIBERNATE_DIALECT_KEY = "hibernate.dialect";
  private static final String HIBERNATE_CONNECTION_KEEPALIVE_KEY = "hibernate.connection.tcpKeepAlive";


  public static void main(String[] args) {


    FWHMCommand fwhmCommand = new FWHMCommand();
    IsobariqueEnumerationCommand isobaricCommand = new IsobariqueEnumerationCommand();
    ProjectsStatisticCommand prjStatCommand = new ProjectsStatisticCommand();

    JCommander jc = JCommander.newBuilder().addCommand(fwhmCommand).addCommand(isobaricCommand).addCommand(prjStatCommand).build();

    try {
      jc.parse(args);
      String parsedCommandStr = jc.getParsedCommand();

      switch (parsedCommandStr) {
        case "fwhm":
          if (fwhmCommand.help) jc.usage(); else fwhmCommand.run();
          break;
        case "isobaric":
          if (isobaricCommand.help) jc.usage(); else isobaricCommand.run();
          break;
        case "prj_stats":
          if (prjStatCommand.help) jc.usage(); else prjStatCommand.run();
          break;
        default:
          logger.error("Invalid command: " + parsedCommandStr);
          jc.usage();
      }
    } catch (ParameterException pe) {
      logger.error("Invalid command parameter(s): {}", pe.getMessage());
      jc.usage();
    }
  }

}
