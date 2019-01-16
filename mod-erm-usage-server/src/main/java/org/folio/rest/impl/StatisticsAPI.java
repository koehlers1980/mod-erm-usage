package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.CumulativeAccess;
import org.folio.rest.jaxrs.resource.Statistics;

public class StatisticsAPI implements Statistics {

  private final static String STATISTIC_PREFIX = "statistics/";
  private final Logger logger = LoggerFactory.getLogger(StatisticsAPI.class);

  /**
   * ATTENTION: This is just a mock!
   *
   * @param agreementId@param udpId@param start@param end@param asyncResultHandler An AsyncResult<Response> Handler  {@link Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
   * @param udpId
   * @param start
   * @param end
   * @param okapiHeaders
   * @param asyncResultHandler
   * @param vertxContext
   *  The Vertx Context Object <code>io.vertx.core.Context</code>
   */
  @Override
  @Validate
  public void getStatisticsCumulativeAccess(String agreementId, String udpId, String start,
    String end, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    logger.debug("Getting statistics");
    try {
      CumulativeAccess cumulativeAccess = getCumulativeAccess(agreementId, udpId, start, end);
      asyncResultHandler.handle(
        Future.succeededFuture(
          GetStatisticsCumulativeAccessResponse.respond200WithApplicationJson(cumulativeAccess))
      );
    } catch (IOException e) {
      asyncResultHandler.handle(Future.succeededFuture(
        GetStatisticsCumulativeAccessResponse.respond500WithTextPlain(e.getCause())));
    }
  }

  private CumulativeAccess getCumulativeAccess(String agreementId, String udpId, String start,
    String end) throws IOException {
    String statsStr = null;
    if (agreementId.equals("annualre-view-s170-0000-000000000000") && udpId
      .equals("annualre-view-scou-nter-000000000000") && start.equals("2017-01") && end
      .equals("2017-12")) {
      statsStr = getFileContent("annualreview.json");
    } else if (agreementId.equals("cambridg-e170-0000-0000-000000000000") && udpId
      .equals("cambridg-ecou-nter-0000-000000000000")) {
      if (start.equals("2017-01") && end.equals("2017-12")) {
        statsStr = getFileContent("cambridge_jan_dec.json");
      } else if (start.equals("2017-01") && end.equals("2017-05")) {
        statsStr = getFileContent("cambridge_jan_may.json");
      }
    } else if (agreementId.equals("jstor170-0000-0000-0000-000000000000") && udpId
      .equals("jstorcou-nter-0000-0000-000000000000")) {
      if (start.equals("2017-01") && end.equals("2017-12")) {
        statsStr = getFileContent("jstor_jan_dec.json");
      } else if (start.equals("2017-03") && end.equals("2017-04")) {
        statsStr = getFileContent("jstor_mar_apr.json");
      }
    }
    return Json.decodeValue(statsStr, CumulativeAccess.class);
  }

  private String getFileContent(String fileName) throws IOException {
    InputStream resourceAsStream = this.getClass().getClassLoader()
      .getResourceAsStream(STATISTIC_PREFIX + fileName);
    return readFromInputStream(resourceAsStream);
  }

  private String readFromInputStream(InputStream inputStream)
    throws IOException {
    StringBuilder resultStringBuilder = new StringBuilder();
    try (BufferedReader br
      = new BufferedReader(new InputStreamReader(inputStream))) {
      String line;
      while ((line = br.readLine()) != null) {
        resultStringBuilder.append(line).append("\n");
      }
    }
    return resultStringBuilder.toString();
  }

}
