package fr.edyp.proline.extraction;

import com.opencsv.CSVWriter;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVParser;
import com.opencsv.ICSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;

public class Output {

  private static Logger logger = LoggerFactory.getLogger(Output.class);
  private ICSVWriter csvWriter;

  public Output() {

  }

  public Output(String filename) {
    setOutputFileName(filename);
  }

  public void setOutputFileName(String filename) {
    try {
      FileWriter writer = new FileWriter(filename);
      csvWriter = new CSVWriterBuilder(writer)
              .withSeparator(';')
              .withQuoteChar(ICSVParser.DEFAULT_QUOTE_CHARACTER)
              .withEscapeChar(ICSVParser.DEFAULT_ESCAPE_CHARACTER)
              .withLineEnd(ICSVWriter.DEFAULT_LINE_END)
              .build();

    } catch (IOException exception) {
      logger.info("cannot create file {}", filename);
    }
  }

  public void writeHeader(String[] header) {
    csvWriter.writeNext(header);
  }

  public void writeValues(String[] values) {
    csvWriter.writeNext(values);
  }

  public void close() throws IOException {
    csvWriter.flush();
    csvWriter.close();
  }
}
