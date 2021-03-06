package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.io.InputStream;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.IOUtils;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.CounterReports;
import org.folio.rest.jaxrs.model.CounterReportsGetOrder;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.niso.schemas.counter.Report;
import org.olf.erm.usage.counter41.Counter4Utils;
import org.olf.erm.usage.counter50.Counter5Utils;
import org.openapitools.client.model.SUSHIReportHeader;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

public class CounterReportAPI implements org.folio.rest.jaxrs.resource.CounterReports {

  private static final String TABLE_NAME_COUNTER_REPORTS = "counter_reports";
  private static final String TABLE_NAME_UDP = "usage_data_providers";
  private static final String MSG_EXACTLY_ONE_MONTH =
      "Provided report must cover a period of exactly one month";
  private static final String MSG_WRONG_FORMAT = "Wrong format supplied";
  private final Messages messages = Messages.getInstance();
  private final Logger logger = LoggerFactory.getLogger(CounterReportAPI.class);

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2PgJSON = new CQL2PgJSON(Arrays.asList(TABLE_NAME_COUNTER_REPORTS + ".jsonb"));
    return new CQLWrapper(cql2PgJSON, query)
        .setLimit(new Limit(limit))
        .setOffset(new Offset(offset));
  }

  @Validate
  @Override
  public void getCounterReports(
      boolean tiny,
      String query,
      String orderBy,
      CounterReportsGetOrder order,
      int offset,
      int limit,
      String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    logger.debug("Getting counter reports");
    try {
      CQLWrapper cql = getCQL(query, limit, offset);
      vertxContext.runOnContext(
          v -> {
            String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(XOkapiHeaders.TENANT));
            logger.debug("Headers present are: " + okapiHeaders.keySet().toString());
            logger.debug("tenantId = " + tenantId);

            String field = (tiny) ? "jsonb - 'report' AS jsonb" : "*";
            String[] fieldList = {field};
            try {
              PostgresClient.getInstance(vertxContext.owner(), tenantId)
                  .get(
                      TABLE_NAME_COUNTER_REPORTS,
                      CounterReport.class,
                      fieldList,
                      cql,
                      true,
                      false,
                      reply -> {
                        try {
                          if (reply.succeeded()) {
                            CounterReports counterReportDataDataCollection = new CounterReports();
                            List<CounterReport> reports = reply.result().getResults();
                            counterReportDataDataCollection.setCounterReports(reports);
                            counterReportDataDataCollection.setTotalRecords(
                                reply.result().getResultInfo().getTotalRecords());
                            asyncResultHandler.handle(
                                Future.succeededFuture(
                                    GetCounterReportsResponse.respond200WithApplicationJson(
                                        counterReportDataDataCollection)));
                          } else {
                            asyncResultHandler.handle(
                                Future.succeededFuture(
                                    GetCounterReportsResponse.respond500WithTextPlain(
                                        reply.cause().getMessage())));
                          }
                        } catch (Exception e) {
                          logger.debug(e.getLocalizedMessage());
                          asyncResultHandler.handle(
                              Future.succeededFuture(
                                  GetCounterReportsResponse.respond500WithTextPlain(
                                      reply.cause().getMessage())));
                        }
                      });
            } catch (IllegalStateException e) {
              logger.debug("IllegalStateException: " + e.getLocalizedMessage());
              asyncResultHandler.handle(
                  Future.succeededFuture(
                      GetCounterReportsResponse.respond400WithTextPlain(
                          "CQL Illegal State Error for '" + "" + "': " + e.getLocalizedMessage())));
            } catch (Exception e) {
              Throwable cause = e;
              while (cause.getCause() != null) {
                cause = cause.getCause();
              }
              logger.debug(
                  "Got error " + cause.getClass().getSimpleName() + ": " + e.getLocalizedMessage());
              if (cause.getClass().getSimpleName().contains("CQLParseException")) {
                logger.debug("BAD CQL");
                asyncResultHandler.handle(
                    Future.succeededFuture(
                        GetCounterReportsResponse.respond400WithTextPlain(
                            "CQL Parsing Error for '" + "" + "': " + cause.getLocalizedMessage())));
              } else {
                asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(
                        GetCounterReportsResponse.respond500WithTextPlain(
                            messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            }
          });
    } catch (Exception e) {
      logger.error(e.getLocalizedMessage(), e);
      if (e.getCause() != null
          && e.getCause().getClass().getSimpleName().contains("CQLParseException")) {
        logger.debug("BAD CQL");
        asyncResultHandler.handle(
            Future.succeededFuture(
                GetCounterReportsResponse.respond400WithTextPlain(
                    "CQL Parsing Error for '" + "" + "': " + e.getLocalizedMessage())));
      } else {
        asyncResultHandler.handle(
            io.vertx.core.Future.succeededFuture(
                GetCounterReportsResponse.respond500WithTextPlain(
                    messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    }
  }

  @Override
  @Validate
  public void postCounterReports(
      String lang,
      CounterReport entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.post(
        TABLE_NAME_COUNTER_REPORTS,
        entity,
        okapiHeaders,
        vertxContext,
        PostCounterReportsResponse.class,
        asyncResultHandler);
  }

  @Override
  @Validate
  public void getCounterReportsById(
      String id,
      String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.getById(
        TABLE_NAME_COUNTER_REPORTS,
        CounterReport.class,
        id,
        okapiHeaders,
        vertxContext,
        GetCounterReportsByIdResponse.class,
        asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteCounterReportsById(
      String id,
      String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.deleteById(
        TABLE_NAME_COUNTER_REPORTS,
        id,
        okapiHeaders,
        vertxContext,
        DeleteCounterReportsByIdResponse.class,
        asyncResultHandler);
  }

  @Override
  @Validate
  public void putCounterReportsById(
      String id,
      String lang,
      CounterReport entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PgUtil.put(
        TABLE_NAME_COUNTER_REPORTS,
        entity,
        id,
        okapiHeaders,
        vertxContext,
        PutCounterReportsByIdResponse.class,
        asyncResultHandler);
  }

  private Optional<String> csvMapper(CounterReport cr) {
    if (cr.getRelease().equals("4") && cr.getReport() != null) {
      return Optional.of(Counter4Utils.toCSV(Counter4Utils.fromJSON(Json.encode(cr.getReport()))));
    }
    return Optional.empty();
  }

  @Override
  public void getCounterReportsCsvById(
      String id,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    PostgresClient.getInstance(vertxContext.owner(), okapiHeaders.get(XOkapiHeaders.TENANT))
        .getById(
            TABLE_NAME_COUNTER_REPORTS,
            id,
            CounterReport.class,
            ar -> {
              if (ar.succeeded()) {
                Optional<String> csvResult = csvMapper(ar.result());
                if (csvResult.isPresent()) {
                  if (csvResult.get() != null) {
                    asyncResultHandler.handle(
                        Future.succeededFuture(
                            GetCounterReportsCsvByIdResponse.respond200WithTextCsv(
                                csvResult.get())));
                  } else {
                    asyncResultHandler.handle(
                        Future.succeededFuture(
                            GetCounterReportsCsvByIdResponse.respond500WithTextPlain(
                                "Error while tranforming report to CSV")));
                  }
                } else {
                  asyncResultHandler.handle(
                      Future.succeededFuture(
                          GetCounterReportsCsvByIdResponse.respond500WithTextPlain(
                              "No report data or no mapper available")));
                }
              } else {
                asyncResultHandler.handle(
                    Future.succeededFuture(
                        GetCounterReportsCsvByIdResponse.respond500WithTextPlain(
                            ar.cause().getMessage())));
              }
            });
  }

  @Override
  public void postCounterReportsUploadProviderById(
      String id,
      boolean overwrite,
      InputStream entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    Vertx vertx = vertxContext.owner();
    String tenantId = okapiHeaders.get(XOkapiHeaders.TENANT);

    CounterReport counterReport;
    try {
      counterReport = getCounterReportFromInputStream(entity);
    } catch (Exception e) {
      asyncResultHandler.handle(
          Future.succeededFuture(
              PostCounterReportsUploadProviderByIdResponse.respond500WithTextPlain(
                  String.format("Error uploading file: %s", e.getMessage()))));
      return;
    }

    getUDPfromDbById(vertx, tenantId, id)
        .compose(
            udp ->
                Future.succeededFuture(
                    counterReport
                        .withProviderId(udp.getId())
                        .withDownloadTime(Date.from(Instant.now()))))
        .compose(cr -> saveCounterReportToDb(vertx, tenantId, cr, overwrite))
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                asyncResultHandler.handle(
                    Future.succeededFuture(
                        PostCounterReportsUploadProviderByIdResponse.respond200WithTextPlain(
                            String.format("Saved report with id %s", ar.result()))));
              } else {
                asyncResultHandler.handle(
                    Future.succeededFuture(
                        PostCounterReportsUploadProviderByIdResponse.respond500WithTextPlain(
                            String.format("Error saving report: %s", ar.cause()))));
              }
            });
  }

  class FileUploadException extends Exception {

    private static final long serialVersionUID = -3795351043189447151L;

    public FileUploadException(String message) {
      super(message);
    }

    public FileUploadException(Exception e) {
      super(e);
    }
  }

  private CounterReport getCounterReportFromInputStream(InputStream entity)
      throws FileUploadException {
    String content;
    try {
      content = IOUtils.toString(entity, Charsets.UTF_8);
    } catch (Exception e) {
      throw new FileUploadException(e);
    }

    // Counter 5
    SUSHIReportHeader header = Counter5Utils.getReportHeader(content);
    if (Counter5Utils.isValidReportHeader(header)) {
      List<YearMonth> yearMonths = Counter5Utils.getYearMonthsFromReportHeader(header);
      if (yearMonths.size() != 1) {
        throw new FileUploadException(MSG_EXACTLY_ONE_MONTH);
      }
      return new CounterReport()
          .withRelease("5")
          .withReportName(header.getReportID())
          .withReport(Json.decodeValue(content, org.folio.rest.jaxrs.model.Report.class))
          .withYearMonth(yearMonths.get(0).toString());
    }

    // Counter 4
    Report report = Counter4Utils.fromString(content);
    if (report != null) {
      List<YearMonth> yearMonthsFromReport = Counter4Utils.getYearMonthsFromReport(report);
      if (yearMonthsFromReport.size() != 1) {
        throw new FileUploadException(MSG_EXACTLY_ONE_MONTH);
      }
      return new CounterReport()
          .withRelease(report.getVersion())
          .withReportName(Counter4Utils.getNameForReportTitle(report.getName()))
          .withReport(
              Json.decodeValue(
                  Counter4Utils.toJSON(report), org.folio.rest.jaxrs.model.Report.class))
          .withYearMonth(yearMonthsFromReport.get(0).toString());
    }

    throw new FileUploadException(MSG_WRONG_FORMAT);
  }

  private Future<UsageDataProvider> getUDPfromDbById(Vertx vertx, String tenantId, String id) {
    Future<UsageDataProvider> udpFuture = Future.future();
    PostgresClient.getInstance(vertx, tenantId)
        .getById(
            TABLE_NAME_UDP,
            id,
            UsageDataProvider.class,
            ar -> {
              if (ar.succeeded()) {
                if (ar.result() != null) {
                  udpFuture.complete(ar.result());
                } else {
                  udpFuture.fail(String.format("Provider with id %s not found", id));
                }
              } else {
                udpFuture.fail(
                    String.format(
                        "Unable to get usage data provider with id %s: %s", id, ar.cause()));
              }
            });
    return udpFuture;
  }

  private Future<String> saveCounterReportToDb(
      Vertx vertx, String tenantId, CounterReport counterReport, boolean overwrite) {

    // check if CounterReport already exists
    Future<String> idFuture = Future.future();
    PostgresClient.getInstance(vertx, tenantId)
        .get(
            TABLE_NAME_COUNTER_REPORTS,
            // select the properties we want to check for
            new CounterReport()
                .withProviderId(counterReport.getProviderId())
                .withReportName(counterReport.getReportName())
                .withRelease(counterReport.getRelease())
                .withYearMonth(counterReport.getYearMonth()),
            false,
            h -> {
              if (h.succeeded()) {
                int resultSize = h.result().getResults().size();
                if (resultSize == 1) {
                  idFuture.complete(h.result().getResults().get(0).getId());
                } else if (resultSize > 1) {
                  idFuture.fail("Too many results");
                } else {
                  idFuture.complete(null);
                }
              } else {
                idFuture.fail(h.cause());
              }
            });

    // save report
    return idFuture.compose(
        id -> {
          if (id != null && !overwrite) {
            return Future.failedFuture("Report already exists");
          }

          Future<String> upsertFuture = Future.future();
          PostgresClient.getInstance(vertx, tenantId)
              .upsert(TABLE_NAME_COUNTER_REPORTS, id, counterReport, upsertFuture::handle);
          return upsertFuture;
        });
  }

  @Override
  public void getCounterReportsCsvProviderReportVersionFromToByIdAndNameAndVersionAndBeginAndEnd(
      String id,
      String name,
      String version,
      String begin,
      String end,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    String where =
        String.format(
            " WHERE (jsonb->>'providerId' = '%s') AND "
                + "(jsonb->>'reportName' = '%s') AND "
                + "(jsonb->>'release' = '%s') AND "
                + "(jsonb->'report' IS NOT NULL) AND"
                + "(jsonb->>'yearMonth' >= '%s') AND "
                + "(jsonb->>'yearMonth' <= '%s')",
            id, name, version, begin, end);

    PostgresClient.getInstance(vertxContext.owner(), okapiHeaders.get(XOkapiHeaders.TENANT))
        .get(
            TABLE_NAME_COUNTER_REPORTS,
            CounterReport.class,
            where,
            true,
            true,
            ar -> {
              if (ar.succeeded()) {
                List<Report> reports =
                    ar.result().getResults().stream()
                        .map(
                            cr -> {
                              if (version.equals("4")) {
                                return Counter4Utils.fromJSON(Json.encode(cr.getReport()));
                              } else {
                                return null;
                              }
                            })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                if (reports.isEmpty()) {
                  asyncResultHandler.handle(
                      Future.succeededFuture(
                          GetCounterReportsCsvProviderReportVersionFromToByIdAndNameAndVersionAndBeginAndEndResponse
                              .respond500WithTextPlain("No valid reports found in period")));
                  return;
                }

                String csv = null;
                try {
                  Report merge = Counter4Utils.merge(reports);
                  csv = Counter4Utils.toCSV(merge);
                } catch (Exception e) {
                  asyncResultHandler.handle(
                      Future.succeededFuture(
                          GetCounterReportsCsvProviderReportVersionFromToByIdAndNameAndVersionAndBeginAndEndResponse
                              .respond500WithTextPlain(e.getMessage())));
                  return;
                }

                asyncResultHandler.handle(
                    Future.succeededFuture(
                        GetCounterReportsCsvProviderReportVersionFromToByIdAndNameAndVersionAndBeginAndEndResponse
                            .respond200WithTextCsv(csv)));
              } else {
                asyncResultHandler.handle(
                    Future.succeededFuture(
                        GetCounterReportsCsvProviderReportVersionFromToByIdAndNameAndVersionAndBeginAndEndResponse
                            .respond500WithTextPlain("Query Error: " + ar.cause())));
              }
            });
  }
}
