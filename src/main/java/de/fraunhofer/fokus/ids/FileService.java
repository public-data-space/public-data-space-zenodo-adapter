package de.fraunhofer.fokus.ids;

import de.fraunhofer.fokus.ids.messages.ResourceRequest;
import de.fraunhofer.fokus.ids.persistence.entities.Distribution;
import de.fraunhofer.fokus.ids.services.database.DatabaseService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 	@newest_changeses_and_notes_of_Zead:
 * 		@Properties:
 * 		@methods: (#some_key is a key of the adjustment that you can search for.)
 * 			@getURL: (edited)
 * 		        #AddAditioalDataToDatasetObject:
 * 		            I think streamFile() is not needed.
 * 		            return jsonObject <link, file_path> and pass dataAssetId and distributionId to get the url
 * 		    @SELECTurl: (edited)
 * 		        #AddAditioalDataToDistibutionObject:
 * 		            get one url based on dataAssetId and distributionId
 *
 */

public class FileService {

    private final Logger LOGGER = LoggerFactory.getLogger(DataAssetService.class.getName());

    private final DatabaseService databaseService;
    private final WebClient webClient;

    public FileService(Vertx vertx) {
        WebClientOptions options = new WebClientOptions().setTrustAll(true);
        this.webClient = WebClient.create(vertx, options);
        this.databaseService = DatabaseService.createProxy(vertx, ApplicationConfig.DATABASE_SERVICE);
    }

    //#getURL
    public void getFile(JsonObject requestInfo, HttpServerResponse httpServerResponse, Handler<AsyncResult<JsonObject>> resultHandlerP) {
        getAccessInformation(resultHandler -> {
            if (resultHandler.succeeded()) {
                if (resultHandler.result() != null) {
                    resultHandlerP.handle(Future.succeededFuture(new JsonObject().put("link", resultHandler.result())));
                    //streamFile(resultHandler.result(), httpServerResponse);
                } else {
                    LOGGER.error("File is null");
                    httpServerResponse.setStatusCode(404).end();
                }
            } else {
                LOGGER.error(resultHandler.cause());
                httpServerResponse.setStatusCode(404).end();
            }
        }, requestInfo.getString("dataAssetId"), requestInfo.getInteger("distributionId"));
    }

    private void getAccessInformation(Handler<AsyncResult<String>> resultHandler, String dataAssetId, int distributionId) {
        //#SELECTurl
        databaseService.query("SELECT url from accessinformation WHERE rowid = ? AND datasetid = ?", new JsonArray().add(distributionId).add(dataAssetId), handler -> {
            if (handler.succeeded()) {
                if (handler.result().size() == 1) {
                    resultHandler.handle(Future.succeededFuture(handler.result().get(0).getString("url")));
                } else {
                    resultHandler.handle(Future.failedFuture("Retrieved either none or multiple results for distribution with id " + distributionId));
                    //resultHandler.handle(Future.failedFuture("Retrieved either none or multiple results for distribution with id " + distributionId));
                }
            } else {
                LOGGER.error("File information could not be retrieved.", handler.cause());
                resultHandler.handle(Future.failedFuture(handler.cause()));
            }
        });
    }

    public void tryFile(String urlString, Handler<AsyncResult<String>> resultHandler) {
        try {
            URL url = new URL(urlString);
            webClient
                    .headAbs(url.toString())
                    .send(ar -> {
                        if (ar.succeeded()) {
                            String contentDisposition = ar.result().getHeader(HttpHeaders.CONTENT_DISPOSITION.toString());

                            if (contentDisposition != null && contentDisposition.contains("filename")) {
                                String[] dispSplit = contentDisposition.substring(contentDisposition.indexOf("filename")).split("\"");

                                if (dispSplit.length > 1) {
                                    String filename = dispSplit[1].trim();
                                    resultHandler.handle(Future.succeededFuture(filename));
                                } else {
                                    resultHandler.handle(Future.succeededFuture(resolvePath(urlString)));
                                }
                            } else {
                                resultHandler.handle(Future.succeededFuture(resolvePath(urlString)));
                            }
                        } else {
                            resultHandler.handle(Future.succeededFuture(resolvePath(urlString)));
                        }
                    });
        } catch (MalformedURLException e) {
            LOGGER.error(e);
            resultHandler.handle(Future.succeededFuture(resolvePath(urlString)));
        }
    }


    private String resolvePath(String url) {
        try {
            return Paths.get(new URI(url).getPath()).getFileName().toString();
        } catch (URISyntaxException e) {
            return UUID.randomUUID().toString();
        }
    }

    public void streamFile(String urlString, HttpServerResponse response) {
        try {
            URL url = new URL(urlString);

            LOGGER.info("Piping file from " + urlString);
            response.putHeader("Transfer-Encoding", "chunked");

            webClient
                    .getAbs(url.toString())
                    .as(BodyCodec.pipe(response))
                    .send(ar -> {
                        if (ar.succeeded()) {
                            LOGGER.info("Received response with status code " + ar.result().statusCode());
                        } else {
                            LOGGER.error("Something went wrong " + ar.cause().getMessage());
                        }
                    });
        } catch (MalformedURLException e) {
            LOGGER.error(e);
            response.setStatusCode(404).end();
        }
    }
}
