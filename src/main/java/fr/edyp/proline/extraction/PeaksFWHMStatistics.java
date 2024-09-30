package fr.edyp.proline.extraction;

import fr.proline.core.orm.lcms.*;
import fr.proline.core.orm.uds.MasterQuantitationChannel;
import fr.proline.core.orm.uds.QuantitationChannel;
import fr.proline.repository.IDatabaseConnector;
import org.apache.commons.math3.fitting.GaussianCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PeaksFWHMStatistics {

  private static Logger logger = LoggerFactory.getLogger(PeaksFWHMStatistics.class);

  public static final String HIBERNATE_DIALECT_KEY = "hibernate.dialect";
  private static final String HIBERNATE_CONNECTION_KEEPALIVE_KEY = "hibernate.connection.tcpKeepAlive";
  private final long datasetId;
  private IDatabaseConnector udsConnector;
  private IDatabaseConnector lcmsConnector;

  public PeaksFWHMStatistics(IDatabaseConnector udsConnector, IDatabaseConnector lcmsConnector, long datasetId) {

    this.udsConnector = udsConnector;
    this.lcmsConnector = lcmsConnector;
    this.datasetId = datasetId;
  }

  public void run(Output output) {


//      EntityManager em = lcmsConnector.createEntityManager();
//      Feature f = em.find(Feature.class, 303806L);
//      FeaturePeakelItem fpi = f.getFeaturePeakelItems().stream().filter(i -> (i != null) && i.getIsBasePeakel()).findFirst().orElse(null);
//      if (fpi != null) {
//        Peakel basePeakel = fpi.getPeakel();
//        float w = extractFWHM(basePeakel.getPeakList(), basePeakel.getApexIntensity());
//        logger.info("fwhm = {}", w);
//      }

    MasterQuantitationChannel mqc = getMasterQC(datasetId);
    logger.info("mapset_id = " + mqc.getLcmsMapSetId());
    mqc.getQuantitationChannels().forEach(qc -> logger.debug("qc.number = {},  qc.name = {}, map_id = {}", qc.getNumber(), qc.getName(), qc.getLcmsMapId()));

    MapSet mapset = getMapSet(mqc.getLcmsMapSetId());
    List<ProcessedMap> maps = mapset.getProcessedMaps();
    Map<Long, Long> rawMapByProcessMapId = maps.stream().filter(pm -> !pm.getIsMaster()).collect(Collectors.toMap(pm -> pm.getId(), pm -> pm.getRawMap().getId()));
    Map<Long, Long> processedMapIdByRawMapId = maps.stream().filter(pm -> !pm.getIsMaster()).collect(Collectors.toMap(pm -> pm.getRawMap().getId(), pm -> pm.getId()));
    output.setOutputFileName(mapset.getName() + ".tsv");

    List<Long> qcRawMapIds = mqc.getQuantitationChannels().stream().sorted(Comparator.comparingInt(QuantitationChannel::getNumber)).map(qc -> rawMapByProcessMapId.get(qc.getLcmsMapId())).collect(Collectors.toList());
    List<String> mapNames = mqc.getQuantitationChannels().stream().sorted(Comparator.comparingInt(QuantitationChannel::getNumber)).map(qc -> qc.getName()).collect(Collectors.toList());

    long start = System.currentTimeMillis();

    List<MasterFeatureItem> masterFeatures = getMasterFeatures(mapset.getMasterMap().getId());
    logger.info("Retrieve master ft = {} ms", (System.currentTimeMillis() - start));
    start = System.currentTimeMillis();

    Map<Feature, Map<Long, Feature>> childsFtByMasterFt = masterFeatures.stream().collect(
            Collectors.groupingBy(MasterFeatureItem::getMasterFeature,
                    Collectors.mapping(MasterFeatureItem::getChildFeature, Collectors.toMap(ft -> ft.getMap().getId(), Function.identity(), (first, second) -> first))));

    logger.info("nb of master features = {} ", childsFtByMasterFt.size());
    logger.info("grouping = {} ms", (System.currentTimeMillis() - start));
    start = System.currentTimeMillis();


    Map<Long, Peakel> basePeakelbyFeatureId = new HashMap<>();

    mqc.getQuantitationChannels().forEach(qc -> getBasePeakels(qc.getLcmsMapId(), basePeakelbyFeatureId));

    logger.info("nb of base peakels = {} ", basePeakelbyFeatureId.size());
    logger.info("Retrieve base peakels from all maps = {} ms", (System.currentTimeMillis() - start));

    int count = 0;
    int columnCount = 4 + qcRawMapIds.size() * 3;
    int columnIndex = 0;

    String[] header = new String[columnCount];
    header[columnIndex++] = "masterFt.id";
    header[columnIndex++] = "moz";
    header[columnIndex++] = "charge";
    header[columnIndex++] = "masterFt.intensity";
    for (String mapName : mapNames) {
      header[columnIndex++] = "intensity." + mapName;
      header[columnIndex++] = "duration." + mapName;
      header[columnIndex++] = "fwhm." + mapName;
    }

    output.writeHeader(header);
    start = System.currentTimeMillis();
    for (Map.Entry<Feature, Map<Long, Feature>> entry : childsFtByMasterFt.entrySet()) {
      Feature masterFt = entry.getKey();
      String[] values = new String[columnCount];
      columnIndex = 0;
      values[columnIndex++] = masterFt.getId().toString();
      values[columnIndex++] = Double.toString(masterFt.getMoz());
      values[columnIndex++] = masterFt.getCharge().toString();
      values[columnIndex++] = Float.toString(masterFt.getApexIntensity());

      Map<Long, Feature> childFtByMap = entry.getValue();
      for (Long rawMapId : qcRawMapIds) {
        if (childFtByMap.containsKey(rawMapId)) {
          Feature childFt = childFtByMap.get(rawMapId);
          if (basePeakelbyFeatureId.containsKey(childFt.getId())) {
            Peakel peakel = basePeakelbyFeatureId.get(childFt.getId());
            List<Peak> peaklist = peakel.getPeakList();
            fitPeak(peaklist);
            float fwhm = extractFWHM(peaklist, peakel.getApexIntensity());
            values[columnIndex++] = Float.toString(peakel.getApexIntensity());
            values[columnIndex++] = Float.toString(peakel.getDuration());
            values[columnIndex++] = Float.toString(fwhm);
          } else {
            columnIndex += 3;
          }
        } else {
          if (childFtByMap.containsKey(processedMapIdByRawMapId.get(rawMapId))) {
            Feature childFt = childFtByMap.get(processedMapIdByRawMapId.get(rawMapId));
            childFt = childFt.getFeatureClusterItems().stream().map(fci -> fci.getSubFeature()).max((f1, f2) -> Float.compare(f1.getApexIntensity(), f2.getApexIntensity())).orElse(null);
            if (childFt != null) {
              Peakel peakel = basePeakelbyFeatureId.get(childFt.getId());
              List<Peak> peaklist = peakel.getPeakList();
              float fwhm = extractFWHM(peaklist, peakel.getApexIntensity());
              values[columnIndex++] = Float.toString(peakel.getApexIntensity());
              values[columnIndex++] = Float.toString(peakel.getDuration());
              values[columnIndex++] = Float.toString(fwhm);
            } else {
              columnIndex += 3;
            }
          } else {
            columnIndex += 3;
          }
        }
      }
      count++;
      output.writeValues(values);
    }
    logger.info("rows count = {}", count);
    logger.info("Compute fwhm for all peakels = {} ms", (System.currentTimeMillis() - start));

  }

  private void fitPeak(List<Peak> peaklist) {

    GaussianCurveFitter fitter = GaussianCurveFitter.create();

    WeightedObservedPoints obs = new WeightedObservedPoints();

    for (int index = 0; index < peaklist.size(); index++) {
      Peak p = peaklist.get(index);
      obs.add(p.getElutionTime(), p.getIntensity());
    }

    double[] bestFit = fitter.fit(obs.toList());
    logger.info("best fit :: m = {}, s = {}, norm = {}", bestFit[1]/60.0, bestFit[2], bestFit[0]);

  }

  private float extractFWHM(List<Peak> peaklist, float maxIntensity) {

    int startIdx = 0;

    while (startIdx < peaklist.size() && peaklist.get(startIdx).getIntensity() < maxIntensity / 2.0) {
      startIdx++;
    }
    if (startIdx < peaklist.size()) {
      double start = peaklist.get(startIdx).getElutionTime();
      if (startIdx > 0) {
        Peak p0 = peaklist.get(startIdx - 1);
        Peak p1 = peaklist.get(startIdx);
        start = (maxIntensity / 2.0 - p0.getIntensity()) * ((p1.getElutionTime() - p0.getElutionTime()) / (p1.getIntensity() - p0.getIntensity())) + p0.getElutionTime();
      }

      int endIdx = peaklist.size() - 1;
      while (endIdx >= 0 && peaklist.get(endIdx).getIntensity() < maxIntensity / 2.0) {
        endIdx--;
      }

      if (endIdx > startIdx) {
        double end = peaklist.get(endIdx).getElutionTime();
        if (endIdx < (peaklist.size() - 1)) {
          Peak p0 = peaklist.get(endIdx);
          Peak p1 = peaklist.get(endIdx + 1);
          end = (maxIntensity / 2.0 - p0.getIntensity()) * ((p1.getElutionTime() - p0.getElutionTime()) / (p1.getIntensity() - p0.getIntensity())) + p0.getElutionTime();
        }

        return (float) (end - start);
      }
    }

    return -1.0f;
  }

  public MasterQuantitationChannel getMasterQC(long datasetId) {
    try {
      EntityManager em = udsConnector.createEntityManager();
      TypedQuery<MasterQuantitationChannel> query = em.createQuery("select mqc from MasterQuantitationChannel mqc where mqc.quantDataset.id=:id", MasterQuantitationChannel.class);
      query.setParameter("id", datasetId);
      MasterQuantitationChannel mqc = query.getSingleResult();
      return mqc;
    } catch (Exception e) {
      logger.error("Cannot retrieve MasterQuantitationChannel", e);
      e.printStackTrace();
    }
    return null;
  }


  public MapSet getMapSet(long id) {
    try {
      EntityManager em = lcmsConnector.createEntityManager();
      MapSet mapset = em.find(MapSet.class, id);
      return mapset;
    } catch (Exception e) {
      logger.error("Cannot retrieve MapSet id:" + id, e);
      e.printStackTrace();
    }
    return null;
  }

  public List<MasterFeatureItem> getMasterFeatures(long masterMapId) {
    try {
      EntityManager em = lcmsConnector.createEntityManager();
      TypedQuery<MasterFeatureItem> query = em.createQuery("select mft from MasterFeatureItem mft where mft.masterMap.id=:masterMapId", MasterFeatureItem.class);
      query.setParameter("masterMapId", masterMapId);
      return query.getResultList();
    } catch (Exception e) {
      logger.error("Cannot retrieve master features from map id:" + masterMapId, e);
      e.printStackTrace();
    }
    return null;
  }

  public boolean getBasePeakels(long processedMapId, Map<Long, Peakel> peakelsByFeatureId) {
    long start = System.currentTimeMillis();
    try {
      EntityManager em = lcmsConnector.createEntityManager();
      ProcessedMap map = em.find(ProcessedMap.class, processedMapId);

      Long rawMapId = map.getRawMap().getId();
      TypedQuery<FeaturePeakelItem> query = em.createQuery("select fpi from FeaturePeakelItem fpi where fpi.isBasePeakel=true and fpi.map.id=:mapId", FeaturePeakelItem.class);
      query.setParameter("mapId", rawMapId);
      List<FeaturePeakelItem> result = query.getResultList();

      for (FeaturePeakelItem fpi : result) {
        peakelsByFeatureId.put(fpi.getFeature().getId(), fpi.getPeakel());
      }
      logger.info("base peakels from processed map {} (raw {}) retrieved in {} ms", processedMapId, rawMapId, (System.currentTimeMillis() - start));
      return true;

    } catch (Exception e) {
      logger.error("Cannot retrieve base peakels from map id:" + processedMapId, e);
      e.printStackTrace();
    }
    return false;

  }
}
