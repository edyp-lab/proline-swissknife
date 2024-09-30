package fr.edyp.proline.extraction;

import fr.proline.context.MsiDbConnectionContext;
import fr.proline.core.om.model.msi.LocatedPtm;
import fr.proline.core.om.model.msi.Peptide;
import fr.proline.core.om.model.msi.PtmDefinition;
import fr.proline.core.om.model.msi.PtmLocation;
import fr.proline.core.om.provider.msi.impl.SQLPTMProvider;
import fr.proline.repository.IDatabaseConnector;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.FastMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Collectors;

public class IsobaricEnumeration {

  private static Logger logger = LoggerFactory.getLogger(IsobaricEnumeration.class);
  private static final DecimalFormat DF = new DecimalFormat("#.00000", new DecimalFormatSymbols(Locale.ENGLISH));

  public static final String HIBERNATE_DIALECT_KEY = "hibernate.dialect";
  private static final String HIBERNATE_CONNECTION_KEEPALIVE_KEY = "hibernate.connection.tcpKeepAlive";

  private static double PROTON_MASS = 1.007276466812;

  private IDatabaseConnector udsConnector;
  private IDatabaseConnector msiConnector;
  private List<PtmDefinition> ptmDefinitions;
  private PtmDefinition propionylNter;

  private Map<String, String> variants = new HashMap<>();

  public IsobaricEnumeration(IDatabaseConnector udsConnector, IDatabaseConnector msiConnector) {

    this.udsConnector = udsConnector;
    this.msiConnector = msiConnector;
  }

  public void run(Output output) {

    SQLPTMProvider ptmProvider = new SQLPTMProvider(new MsiDbConnectionContext(msiConnector));

    variants.put("h3", "KSAPATGGVKKPHR");
    variants.put("h33", "KSAPSTGGVKKPHR");
    variants.put("h3mm13", "KSVPSTGGVKKPHR");
    variants.put("h3mm7", "KSAPSIGGVKKPHR");
    variants.put("h3mm6", "KSAPTTGGVKKPHR");

    PtmDefinition acetyl  = ptmProvider.getPtmDefinition("Acetyl", 'K', PtmLocation.ANYWHERE()).get();
    PtmDefinition methyl  = ptmProvider.getPtmDefinition("Methyl", 'K', PtmLocation.ANYWHERE()).get();
    PtmDefinition dimethyl  = ptmProvider.getPtmDefinition("Dimethyl", 'K', PtmLocation.ANYWHERE()).get();
    PtmDefinition trimethyl  = ptmProvider.getPtmDefinition("Trimethyl", 'K', PtmLocation.ANYWHERE()).get();
    PtmDefinition propionyl  = ptmProvider.getPtmDefinition("Propionyl", 'K', PtmLocation.ANYWHERE()).get();
    PtmDefinition butyryl  = ptmProvider.getPtmDefinition("Butyryl", 'K', PtmLocation.ANYWHERE()).get();
    PtmDefinition crotonyl  = ptmProvider.getPtmDefinition("Crotonyl", 'K', PtmLocation.ANYWHERE()).get();
    PtmDefinition lactyl  = ptmProvider.getPtmDefinition("Lactyl_DP", 'K', PtmLocation.ANYWHERE()).get();
    PtmDefinition hydroxy  = ptmProvider.getPtmDefinition("Hydroxybutyrylation", 'K', PtmLocation.ANYWHERE()).get();
    PtmDefinition formyl  = ptmProvider.getPtmDefinition("Formyl", 'K', PtmLocation.ANYWHERE()).get();

    PtmDefinition propionyl_S  = ptmProvider.getPtmDefinition("Propionyl", 'S', PtmLocation.ANYWHERE()).get();
    PtmDefinition propionyl_T  = ptmProvider.getPtmDefinition("Propionyl", 'T', PtmLocation.ANYWHERE()).get();
    PtmDefinition formyl_S  = ptmProvider.getPtmDefinition("Formyl", 'S', PtmLocation.ANYWHERE()).get();
    PtmDefinition formyl_T  = ptmProvider.getPtmDefinition("Formyl", 'T', PtmLocation.ANYWHERE()).get();

    propionylNter  = ptmProvider.getPtmDefinition("Propionyl", Character.MIN_VALUE, PtmLocation.ANY_N_TERM()).get();

    PtmDefinition[] definitions = {acetyl, methyl, dimethyl, trimethyl, propionyl, butyryl, crotonyl, lactyl, hydroxy, formyl, propionyl_S, propionyl_T, formyl_S, formyl_T};
    ptmDefinitions = Arrays.asList(definitions);

    int[] charges = { 2, 3, 4 };

// Test ptm application and mass calculation
//    LocatedPtm[] lptms = { LocatedPtm.apply(acetyl, 1) };
//    double mass = Peptide.calcMass(variants.get("h3"), lptms);

    output.setOutputFileName("isobaricPeptides.tsv");

    long start = System.currentTimeMillis();

    int count = 0;
    int columnCount = 8;
    int columnIndex = 0;

    String[] header = new String[columnCount];
    header[columnIndex++] = "id";
    header[columnIndex++] = "mass";
    header[columnIndex++] = "moz";
    header[columnIndex++] = "charge";
    header[columnIndex++] = "variant_name";
    header[columnIndex++] = "variant_sequence";
    header[columnIndex++] = "nb_modifications";
    header[columnIndex++] = "located_modifications";
    output.writeHeader(header);

    Map<String, List<String[]> > groups = new HashMap<>();

    for (String variantName : variants.keySet()) {
      final List<Peptide> peptides = enumerateAll(variantName, new LocatedPtm[0], new HashSet<String>());
      logger.info("Variant {} generates {} peptides", variantName, peptides.size());
      for (Peptide peptide : peptides) {
        String mozStr = DF.format(peptide.calculatedMass());
        String[] values = {String.valueOf(-peptide.id()),
                DF.format(peptide.calculatedMass()),
                mozStr,
                "0",
                variantName,
                variants.get(variantName),
                String.valueOf(peptide.ptms().length),
                peptide.readablePtmString() };
        count++;
        output.writeValues(values);

        if (!groups.containsKey(mozStr)) {
          List<String[]> list = new ArrayList<>();
          groups.put(mozStr, list);
        }
        groups.get(mozStr).add(values);

        for(int charge : charges) {
          mozStr = DF.format(massToMoz(peptide.calculatedMass(), charge));
          values = new String[]{String.valueOf(-peptide.id()),
                  DF.format(peptide.calculatedMass()),
                  mozStr,
                  String.valueOf(charge),
                  variantName,
                  variants.get(variantName),
                  String.valueOf(peptide.ptms().length),
                  peptide.readablePtmString()};
          count++;
          output.writeValues(values);

          if (!groups.containsKey(mozStr)) {
            List<String[]> list = new ArrayList<>();
            groups.put(mozStr, list);
          }
          groups.get(mozStr).add(values);

        }

      }
    }

    logger.info("rows count = {}", count);
    logger.info("Compute isobaric combinations = {} ms", (System.currentTimeMillis() - start));

    logger.info("groups = {}", groups.size());
    for ( Map.Entry<String, List<String[]>> e :  groups.entrySet()) {
        logger.info("{} ; {}", e.getKey(), e.getValue().size());
    }

  }


  private List<Peptide> enumerate(String variantName, LocatedPtm[] locatedPtms, Set<String> ptmsCombinations) {

    String sequence = variants.get(variantName);
    String key = Arrays.stream(locatedPtms).map(locatedPtm -> {
      return locatedPtm.definition().residue() == 'K' ? locatedPtm.definition().toReadableString() : locatedPtm.definition().names().shortName();
    }).sorted().collect(Collectors.joining());
    List<Peptide> peptides = new ArrayList<>();

    if (!ptmsCombinations.contains(key)) {

      ptmsCombinations.add(key);

      final int lysineCount = StringUtils.countMatches(sequence, 'K');
      long freeLysineCount = lysineCount - Arrays.stream(locatedPtms).filter(lp -> lp.definition().residue() == 'K').count();
      long propionylCount = Arrays.stream(locatedPtms).filter(lp -> lp.definition().names().shortName().equals("Propionyl")).filter(lp -> lp.definition().residue() != 'K').count();
      long formylSTCount = Arrays.stream(locatedPtms).filter(lp -> lp.definition().names().shortName().equals("Formyl")).filter(lp -> lp.definition().residue() != 'K').count();

      if ((freeLysineCount + propionylCount + formylSTCount) <= 1) {

        LocatedPtm[] newPtms = new LocatedPtm[locatedPtms.length + 1];
        System.arraycopy(locatedPtms, 0, newPtms, 0, locatedPtms.length);
        newPtms[locatedPtms.length] = LocatedPtm.apply(propionylNter, 0);
        Peptide peptide = new Peptide(sequence, newPtms, Peptide.calcMass(sequence, newPtms));
        peptides.add(peptide);

        if ((freeLysineCount + propionylCount + formylSTCount) == 0) {
          peptide = new Peptide(sequence, locatedPtms, Peptide.calcMass(sequence, locatedPtms));
          peptides.add(peptide);
        }

      }

//      if (lptms.length >= 1) {
//        LocatedPtm[] newLptms = new LocatedPtm[locatedPtms.length + 1];
//        System.arraycopy(locatedPtms, 0, newLptms, 0, locatedPtms.length);
//        newLptms[locatedPtms.length] = LocatedPtm.apply(propionylNter, 0);
//        double mass = Peptide.calcMass(sequence, newLptms);
//        Peptide peptide = new Peptide(sequence, newLptms, mass);
//        peptides.add(peptide);
//      }
    }

    // Recursive iteration
      for (PtmDefinition ptmDefinition : ptmDefinitions) {
        LocatedPtm[] newLocatedPtms = new LocatedPtm[locatedPtms.length + 1];
        System.arraycopy(locatedPtms, 0, newLocatedPtms, 0, locatedPtms.length);
        final OptionalInt optionalMaxPosition = Arrays.stream(locatedPtms).filter(lp -> lp.definition().residue() == ptmDefinition.residue()).mapToInt(lp -> lp.seqPosition()).max();
        int position = sequence.indexOf(ptmDefinition.residue(), optionalMaxPosition.isPresent() ? optionalMaxPosition.getAsInt() : 0);
        if (position >= 0) {
          newLocatedPtms[locatedPtms.length] = LocatedPtm.apply(ptmDefinition, position + 1);
//          String ptmString = Arrays.stream(newLocatedPtms).map(locatedPtm -> locatedPtm.toReadableString()).collect(Collectors.joining(";"));
//          logger.info("Generated ptms combination = {}", ptmString);
          peptides.addAll(enumerate(variantName, newLocatedPtms, ptmsCombinations));
        }
      }

    return peptides;
  }

  private List<Peptide> enumerateAll(String variantName, LocatedPtm[] locatedPtms, Set<String> ptmsCombinations) {

    String sequence = variants.get(variantName);
    String key = Arrays.stream(locatedPtms).map(locatedPtm -> {
      return locatedPtm.definition().residue() == 'K' ? locatedPtm.definition().toReadableString() : locatedPtm.definition().names().shortName();
    }).sorted().collect(Collectors.joining());
    List<Peptide> peptides = new ArrayList<>();

    if (!ptmsCombinations.contains(key)) {

      ptmsCombinations.add(key);

      final int lysineCount = StringUtils.countMatches(sequence, 'K');

      LocatedPtm[] newPtms = new LocatedPtm[locatedPtms.length + 1];
      System.arraycopy(locatedPtms, 0, newPtms, 0, locatedPtms.length);
      newPtms[locatedPtms.length] = LocatedPtm.apply(propionylNter, 0);
      Peptide peptide = new Peptide(sequence, newPtms, Peptide.calcMass(sequence, newPtms));
      peptides.add(peptide);

      peptide = new Peptide(sequence, locatedPtms, Peptide.calcMass(sequence, locatedPtms));
      peptides.add(peptide);

    }

    // Recursive iteration
    for (PtmDefinition ptmDefinition : ptmDefinitions) {
      LocatedPtm[] newLocatedPtms = new LocatedPtm[locatedPtms.length + 1];
      System.arraycopy(locatedPtms, 0, newLocatedPtms, 0, locatedPtms.length);
      final OptionalInt optionalMaxPosition = Arrays.stream(locatedPtms).filter(lp -> lp.definition().residue() == ptmDefinition.residue()).mapToInt(lp -> lp.seqPosition()).max();
      int position = sequence.indexOf(ptmDefinition.residue(), optionalMaxPosition.isPresent() ? optionalMaxPosition.getAsInt() : 0);
      if (position >= 0) {
        newLocatedPtms[locatedPtms.length] = LocatedPtm.apply(ptmDefinition, position + 1);
        peptides.addAll(enumerateAll(variantName, newLocatedPtms, ptmsCombinations));
      }
    }

    return peptides;
  }

  private void print(String variantName, Peptide peptide) {
    final LocatedPtm[] lptms = peptide.ptms();
    logger.info("\t {} \t {} \t {} \t {} \t {} ", DF.format(peptide.calculatedMass()), variantName,  variants.get(variantName), lptms.length, peptide.readablePtmString());
  }

  private void print(double mass, String variantName, LocatedPtm[] lptms) {
    String ptmString = Arrays.stream(lptms).map(locatedPtm -> locatedPtm.toReadableString()).collect(Collectors.joining(";"));
    logger.info("\t {} \t {} \t {} \t {} \t {}", DF.format(mass), variantName,  variants.get(variantName), lptms.length, ptmString);
  }


  private static double massToMoz(double mass, int charge) {
    return (mass + charge * PROTON_MASS) / FastMath.abs(charge);
  }
}
