package fr.edyp.proline.extraction;

import fr.proline.repository.IDataStoreConnectorFactory;
import fr.proline.repository.IDatabaseConnector;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class ProjectStatistic {

  private final Logger logger = LoggerFactory.getLogger(ProjectStatistic.class);

  private final IDatabaseConnector udsConnector;
  private final IDataStoreConnectorFactory msiConnectorFactory;
  public ProjectStatistic(IDatabaseConnector udsConnector, IDataStoreConnectorFactory connFactory) {
    this.udsConnector = udsConnector;
    msiConnectorFactory = connFactory;
  }

  public void run(Output output, Long projectId, boolean validated) {

    logger.info(" -- Start ProjectStatistic on project "+projectId+" (null = All Projects)");
    String[] rsHeaders = {"Project Id","Project Name", "Project Prop","Owner", "RS Id","RS Created at","RS Dat File", "Queries count","Peaklist Path"};
    String[] validatedHeaders = {"Project Id","Project Name", "Project Prop","Owner","RS Id", "RS Created at","RS Dat File", "RSM Id", "Queries count", "Validated Queries count","Peaklist Path"};
    String[] values =new String[4];
    String[] headers = validated ? validatedHeaders : rsHeaders;
    output.writeHeader(headers);

    String query = " SELECT project.id, project.name,  project.serialized_properties, user_account.login " +
            "  FROM  public.project_db_map,  public.external_db, public.user_account, public.project " +
            "  WHERE project.owner_id = user_account.id AND project_db_map.project_id = project.id AND project_db_map.external_db_id = external_db.id AND " +
            "  external_db.name like 'msi_db%'  ";
    if(projectId!=null)
      query =query+" AND project.id = "+projectId;


//    EntityManager udsEm = udsConnector.createEntityManager();
//    Query queryPrj = udsEm.createNativeQuery(query);
//    queryPrj.getResultList();
    List<Object[]> results = runQueryInConnectorEM(udsConnector, query);

    for (Object[] resCur : results) {
      long prjId = ((BigInteger) resCur[0]).longValue();
      values[0] = String.valueOf(prjId);
      values[1] = (String) resCur[1];
      values[2] =  (String) resCur[2];
      values[3] = (String) resCur[3];
      logger.info("Get Information from "+prjId);
      boolean isInactive = StringUtils.isNotEmpty(values[2]) && values[2].contains("\"is_active\":false");
      if(isInactive)
        output.writeValues(values);
      else {
        // Go in MSI to get ResultSet infos
        IDatabaseConnector msiConn = msiConnectorFactory.getMsiDbConnector(prjId);
        if(validated)
          getMsiValidInfo(msiConn, values, output);
        else
          getMsiInfo(msiConn, values, output);
        msiConnectorFactory.closeMsiDbConnector(prjId);
      }
    }
    udsConnector.close();
  }

  private void getMsiInfo(IDatabaseConnector msiConnector, String[] projectValue, Output output){
    // "RS Id","RS Created at","RS Dat File", "Queries count","Peaklist Path"
    String q = "SELECT rs.id , rs.creation_timestamp , ms.result_file_name, ms.queries_count , p.\"path\" "  +
            "FROM result_set rs left join msi_search ms on rs.msi_search_id  = ms.id  left outer join peaklist p on p.id = ms.peaklist_id " +
            "WHERE rs.\"type\"= 'SEARCH'";

    List<Object[]> results = runQueryInConnectorEM(msiConnector, q);

    String[] values =new String[projectValue.length+5];
    System.arraycopy(projectValue, 0, values,0,projectValue.length);
    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH);
    for (Object[] resCur : results) {
      int rsIndex = projectValue.length;
      values[rsIndex++]  = resCur[0].toString();
      Timestamp ts = (Timestamp) resCur[1];
      values[rsIndex++]  = ts.toLocalDateTime().format(dateFormat);
      values[rsIndex++]  = (String) resCur[2];
      values[rsIndex++]  = resCur[3].toString();
      values[rsIndex]  =  (String) resCur[4];
      output.writeValues(values);
    }

  }

  private void getMsiValidInfo(IDatabaseConnector msiConnector, String[] projectValue, Output output){
    //"RS Id", "RS Created at","RS Dat File", "RSM Id", "Queries count", "Validated Queries count","Peaklist Path"
    String q= "select rsm.result_set_id, rs.creation_timestamp , ms.result_file_name, pi2.result_summary_id, ms.queries_count, sum(pi2.total_leaves_match_count), p.\"path\" " +
            " from peptide_instance pi2, result_summary rsm left join result_set rs on rsm.result_set_id = rs.id left join msi_search ms on rs.msi_search_id = ms.id left outer join peaklist p on p.id = ms.peaklist_id " +
            " where rsm.id  = pi2.result_summary_id and pi2.result_summary_id in " +
            "(select rsm.id from result_summary rsm , result_set rs where rs.\"type\" = 'SEARCH' and rsm.result_set_id  = rs.id " +
            " and rsm.id in (select max(id) from result_summary group by result_set_id) ) group by (rsm.result_set_id,rs.creation_timestamp , ms.result_file_name, pi2.result_summary_id,p.\"path\", ms.queries_count)";
    List<Object[]> results = runQueryInConnectorEM(msiConnector, q);

    String[] values =new String[projectValue.length+7];
    System.arraycopy(projectValue, 0, values,0,projectValue.length);
    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH);
    for (Object[] resCur : results) {
      int rsIndex = projectValue.length;
      values[rsIndex++]  = resCur[0].toString();
      Timestamp ts = (Timestamp) resCur[1];
      values[rsIndex++]  =ts.toLocalDateTime().format(dateFormat);
      values[rsIndex++]  = (String) resCur[2];
      values[rsIndex++]  = resCur[3].toString();
      values[rsIndex++]  = resCur[4].toString();
      values[rsIndex++]  = resCur[5].toString();
      values[rsIndex]  = (String) resCur[6];
      output.writeValues(values);
    }
  }

  private List<Object[]> runQueryInConnectorEM(IDatabaseConnector msiConnector, String query){
    EntityManager msiEm = msiConnector.createEntityManager();
    logger.debug(" -- Run Query  "+query);
    Query queryPrj = msiEm.createNativeQuery(query);
    List<Object[]> result = queryPrj.getResultList();
//    msiEm.close();
    return result;
  }


}
