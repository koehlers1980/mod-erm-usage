package org.folio.mod_erm_usage_test;

import static com.jayway.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.parsing.Parser;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.UnsupportedEncodingException;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class CounterReportIT {

  public static final String APPLICATION_JSON = "application/json";
  public static final String BASE_URI = "/counter-reports";
  private static final String TENANT = "diku";
  private static Vertx vertx;
  private static Context vertxContext;
  private static int port;
  @Rule
  public Timeout timeout = Timeout.seconds(10);

  @BeforeClass
  public static void setUp(TestContext context) {
    vertx = Vertx.vertx();
    vertxContext = vertx.getOrCreateContext();
    try {
      PostgresClient.setIsEmbedded(true);
      PostgresClient instance = PostgresClient.getInstance(vertx);
      instance.startEmbeddedPostgres();
    } catch (Exception e) {
      e.printStackTrace();
      context.fail(e);
      return;
    }
    Async async = context.async();
    port = NetworkUtils.nextFreePort();

    RestAssured.baseURI = "http://localhost";
    RestAssured.port = port;
    RestAssured.defaultParser = Parser.JSON;

    TenantClient tenantClient = new TenantClient("localhost", port, "diku", "diku");
    DeploymentOptions options = new DeploymentOptions()
        .setConfig(new JsonObject().put("http.port", port))
        .setWorker(true);

    vertx.deployVerticle(RestVerticle.class.getName(), options, res -> {
      try {
        tenantClient.post(null, res2 -> {
          async.complete();
        });
      } catch (Exception e) {
        context.fail(e);
      }
    });
  }

  @AfterClass
  public static void teardown(TestContext context) {
    Async async = context.async();
    vertx.close(context.asyncAssertSuccess(res -> {
      PostgresClient.stopEmbeddedPostgres();
      async.complete();
    }));
  }

  @Test
  public void checkThatWeCanAddGetPutAndDeleteCounterReports() {
    CounterReport counterReport = given()
        .body("{\n"
            + "\t\"id\": \"8c6b1c02-e153-4413-970b-1b1a3eca1325\",\n"
            + "\t\"downloadTime\": \"2018-07-18T14:12:00+02:00\",\n"
            + "\t\"creationTime\": \"2018-07-18T14:12:00+02:00\",\n"
            + "\t\"release\": \"4\",\n"
            + "\t\"reportName\": \"JR1\",\n"
            + "\t\"beginDate\": \"2018-07-18\",\n"
            + "\t\"endDate\": \"2018-07-18\",\n"
            + "\t\"customerId\": \"CUSTOMER_ID\",\n"
            + "\t\"vendorId\": \"5fb03fb5-56c0-4a6e-9f4e-194d1b540140\",\n"
            + "\t\"platformId\": \"19b535aa-7007-435a-96da-a390853e6666\",\n"
            + "\t\"format\": \"xml\",\n"
            + "\t\"report\": \"<?xml version='1.0' encoding='UTF-8'?><cs:ReportResponse xmlns:cs='http://www.niso.org/schemas/sushi/counter' xmlns='http://www.niso.org/schemas/counter'>...</cs:ReportResponse>\"\n"
            + "}")
        .header("X-Okapi-Tenant", TENANT)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .request()
        .post(BASE_URI)
        .thenReturn()
        .as(CounterReport.class);
    assertThat(counterReport.getRelease()).isEqualTo("4");
    assertThat(counterReport.getCustomerId()).isEqualTo("CUSTOMER_ID");
    assertThat(counterReport.getId()).isNotEmpty();

    given()
        .header("X-Okapi-Tenant", TENANT)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .when()
        .get(BASE_URI + "/" + counterReport.getId())
        .then()
        .contentType(ContentType.JSON)
        .statusCode(200)
        .body("id", equalTo(counterReport.getId()))
        .body("customerId", equalTo(counterReport.getCustomerId()));

    given()
        .body("{\n"
            + "\t\"id\": \"8c6b1c02-e153-4413-970b-1b1a3eca1325\",\n"
            + "\t\"downloadTime\": \"2018-07-18T14:12:00+02:00\",\n"
            + "\t\"creationTime\": \"2018-07-18T14:12:00+02:00\",\n"
            + "\t\"release\": \"4\",\n"
            + "\t\"reportName\": \"JR1\",\n"
            + "\t\"beginDate\": \"2018-07-18\",\n"
            + "\t\"endDate\": \"2018-07-18\",\n"
            + "\t\"customerId\": \"CUSTOMER_ID_CHANGED\",\n"
            + "\t\"vendorId\": \"5fb03fb5-56c0-4a6e-9f4e-194d1b540140\",\n"
            + "\t\"platformId\": \"19b535aa-7007-435a-96da-a390853e6666\",\n"
            + "\t\"format\": \"xml\",\n"
            + "\t\"report\": \"<?xml version='1.0' encoding='UTF-8'?><cs:ReportResponse xmlns:cs='http://www.niso.org/schemas/sushi/counter' xmlns:s='http://www.niso.org/schemas/sushi' xmlns='http://www.niso.org/schemas/counter'>...</cs:ReportResponse>\"\n"
            + "}")
        .header("X-Okapi-Tenant", TENANT)
        .header("content-type", APPLICATION_JSON)
        .header("accept", "text/plain")
        .request()
        .put(BASE_URI + "/" + counterReport.getId())
        .then()
        .statusCode(204);

    CounterReport changed = given()
        .header("X-Okapi-Tenant", TENANT)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .request()
        .get(BASE_URI + "/" + counterReport.getId())
        .thenReturn()
        .as(CounterReport.class);
    assertThat(changed.getId()).isEqualTo(counterReport.getId());
    assertThat(changed.getCustomerId()).isEqualTo("CUSTOMER_ID_CHANGED");

    given()
        .header("X-Okapi-Tenant", TENANT)
        .header("content-type", APPLICATION_JSON)
        .header("accept", "text/plain")
        .when()
        .delete(BASE_URI + "/" + counterReport.getId())
        .then()
        .statusCode(204);

    given()
        .header("X-Okapi-Tenant", TENANT)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .when()
        .get(BASE_URI + "/" + counterReport.getId())
        .then()
        .statusCode(404);
  }

  @Test
  public void checkThatWeCanSearchByCQL() throws UnsupportedEncodingException {
    CounterReport counterReport = given()
        .body("{\n"
            + "\t\"id\": \"e4c83024-fb81-45c2-ab01-bf2dc87bd905\",\n"
            + "\t\"downloadTime\": \"2018-07-18T14:12:00+02:00\",\n"
            + "\t\"creationTime\": \"2018-07-18T14:12:00+02:00\",\n"
            + "\t\"release\": \"4\",\n"
            + "\t\"reportName\": \"JR1\",\n"
            + "\t\"beginDate\": \"2018-07-18\",\n"
            + "\t\"endDate\": \"2018-07-18\",\n"
            + "\t\"customerId\": \"CUSTOMER_ID\",\n"
            + "\t\"vendorId\": \"5fb03fb5-56c0-4a6e-9f4e-194d1b540140\",\n"
            + "\t\"platformId\": \"19b535aa-7007-435a-96da-a390853e6666\",\n"
            + "\t\"format\": \"xml\",\n"
            + "\t\"report\": \"<?xml version='1.0' encoding='UTF-8'?><cs:ReportResponse xmlns:cs='http://www.niso.org/schemas/sushi/counter' xmlns='http://www.niso.org/schemas/counter'>Search Me!</cs:ReportResponse>\"\n"
            + "}")
        .header("X-Okapi-Tenant", TENANT)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .request()
        .post(BASE_URI)
        .thenReturn()
        .as(CounterReport.class);
    assertThat(counterReport.getRelease()).isEqualTo("4");
    assertThat(counterReport.getCustomerId()).isEqualTo("CUSTOMER_ID");
    assertThat(counterReport.getId()).isNotEmpty();

    String cqlReport = "?query=(report=\"Search*\")";
    given()
        .header("X-Okapi-Tenant", TENANT)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .when()
        .get(BASE_URI + cqlReport)
        .then()
        .contentType(ContentType.JSON)
        .statusCode(200)
        .body("counterReports.size()", equalTo(1))
        .body("counterReports[0].id", equalTo(counterReport.getId()))
        .body("counterReports[0].customerId", equalTo(counterReport.getCustomerId()))
        .body("counterReports[0].release", equalTo(counterReport.getRelease()));

    String cqlReportName = "?query=(reportName==\"JR1\")";
    given()
        .header("X-Okapi-Tenant", TENANT)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .when()
        .get(BASE_URI + cqlReportName)
        .then()
        .contentType(ContentType.JSON)
        .statusCode(200)
        .body("counterReports.size()", equalTo(1))
        .body("counterReports[0].id", equalTo(counterReport.getId()))
        .body("counterReports[0].customerId", equalTo(counterReport.getCustomerId()))
        .body("counterReports[0].release", equalTo(counterReport.getRelease()));
  }

  @Test
  public void checkThatInvalidCounterReportIsNotPosted() {
    given()
        .body("{\n"
            + "\t\"id\": \"8c6b1c02-e153-4413-970b-1b1a3eca1325\",\n"
            + "\t\"downloadTime\": \"2018-07-18T14:12:00+02:00\",\n"
            + "\t\"creationTime\": \"2018-07-18T14:12:00+02:00\",\n"
            + "\t\"beginDate\": \"2018-07-18\",\n"
            + "\t\"endDate\": \"2018-07-18\",\n"
            + "\t\"customerId\": \"CUSTOMER_ID_CHANGED\",\n"
            + "\t\"vendorId\": \"5fb03fb5-56c0-4a6e-9f4e-194d1b540140\",\n"
            + "\t\"platformId\": \"19b535aa-7007-435a-96da-a390853e6666\",\n"
            + "\t\"format\": \"xml\",\n"
            + "\t\"report\": \"<?xml version='1.0' encoding='UTF-8'?><cs:ReportResponse xmlns:cs='http://www.niso.org/schemas/sushi/counter' xmlns:s='http://www.niso.org/schemas/sushi' xmlns='http://www.niso.org/schemas/counter'>...</cs:ReportResponse>\"\n"
            + "}")
        .header("X-Okapi-Tenant", TENANT)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .request()
        .post(BASE_URI)
        .then()
        .statusCode(422);
  }

}