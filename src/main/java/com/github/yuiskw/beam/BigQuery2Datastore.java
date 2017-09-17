package com.github.yuiskw.beam;

import java.util.LinkedHashMap;

import com.google.api.services.bigquery.model.TableReference;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.datastore.DatastoreIO;
import org.apache.beam.sdk.io.gcp.datastore.DatastoreV1;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.Validation;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;

/**
 * This class is used for a Dataflow job which write parsed Laplace logs to BigQuery.
 */
public class BigQuery2Datastore {

  /** command line options interface */
  public interface Optoins extends DataflowPipelineOptions {
    @Description("Input BigQuery dataset name")
    @Validation.Required
    String getInputBigQueryDataset();
    void setInputBigQueryDataset(String inputBigQueryDataset);

    @Description("Input BigQuery table name")
    @Validation.Required
    String getInputBigQueryTable();
    void setInputBigQueryTable(String inputBigQueryTable);

    @Description("Output Google Datastore namespace")
    @Validation.Required
    String getOutputDatastoreNamespace();
    void setOutputDatastoreNamespace(String outputDatastoreNamespace);

    @Description("Output Google Datastore kind")
    @Validation.Required
    String getOutputDatastoreKind();
    void setOutputDatastoreKind(String outputDatastoreKind);

    @Description("BigQuery column for Datastore key")
    @Validation.Required
    String getKeyColumn();
    void setKeyColumn(String keyColumn);

    @Description("Datastore parent path(s) (format: 'Parent1:p1,Parent2:p2')")
    String getParentPaths();
    void setparentPaths(String parentPaths);
  }

  public static void main(String[] args) {
    Optoins options = getOptions(args);

    String projectId = options.getProject();
    String datasetId = options.getInputBigQueryDataset();
    String tableId = options.getInputBigQueryTable();
    String namespace = options.getOutputDatastoreNamespace();
    String kind = options.getOutputDatastoreKind();
    String keyColumn = options.getKeyColumn();
    LinkedHashMap<String, String> parents = parseParentPaths(options.getParentPaths());

    // Input
    TableReference tableRef = new TableReference().setDatasetId(datasetId).setTableId(tableId);
    BigQueryIO.Read reader = BigQueryIO.read().from(tableRef);

    // Output
    DatastoreV1.Write writer = DatastoreIO.v1().write().withProjectId(projectId);

    // Build and run pipeline
    Pipeline pipeline = Pipeline.create(options);
    pipeline
        .apply(reader)
        .apply(ParDo.of(new TableRow2EntityFn(projectId, namespace, parents, kind, keyColumn)))
        .apply(writer);
    pipeline.run();
  }

  /**
   * Get command line options
   */
  public static Optoins getOptions(String[] args) {
    Optoins options = PipelineOptionsFactory.fromArgs(args)
        .withValidation()
        .as(Optoins.class);
    return options;
  }

  /**
   * Get a parent path map
   *
   * e.g.) "Parent1:p1,Parent2:p2"
   */
  public static LinkedHashMap<String, String> parseParentPaths(String parentPaths) {
    LinkedHashMap<String, String> pathMap = new LinkedHashMap<String, String>();
    if (parentPaths != null) {
      // TODO validation
      for (String path : parentPaths.split(",")) {
        String[] elements = path.split(":");
        pathMap.put(elements[0], elements[1]);
      }
    }
    return pathMap;
  }
}
