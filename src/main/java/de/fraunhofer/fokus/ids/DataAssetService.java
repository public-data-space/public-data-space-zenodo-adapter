package de.fraunhofer.fokus.ids;

import de.fraunhofer.fokus.ids.messages.DataAssetCreateMessage;
import de.fraunhofer.fokus.ids.persistence.entities.Dataset;
import de.fraunhofer.fokus.ids.persistence.entities.Distribution;
import de.fraunhofer.fokus.ids.persistence.enums.DataAssetStatus;
import de.fraunhofer.fokus.ids.services.database.DatabaseService;
import de.fraunhofer.fokus.ids.services.zenodo.ZenodoService;
import io.vertx.core.*;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class DataAssetService {

    private final Logger LOGGER = LoggerFactory.getLogger(DataAssetService.class.getName());

    private final ZenodoService zenodoService;
    private final DatabaseService databaseService;
    private final FileService fileService;

    public DataAssetService(Vertx vertx) {
        this.zenodoService = ZenodoService.createProxy(vertx, ApplicationConfig.ZENODO_SERVICE);
        this.databaseService = DatabaseService.createProxy(vertx, ApplicationConfig.DATABASE_SERVICE);
        this.fileService = new FileService(vertx);
    }

    public void deleteDataAsset(String id, Handler<AsyncResult<JsonObject>> resultHandler) {
        databaseService.update("DELETE FROM accessinformation WHERE datasetid=?", new JsonArray().add(id), databaseDeleteReply -> {
            if (databaseDeleteReply.succeeded()) {
                LOGGER.info("Data Asset successfully deleted.");
                resultHandler.handle(Future.succeededFuture(new JsonObject().put("status", "success")));
            } else {
                LOGGER.error("Data Asset could not be deleted.");
                resultHandler.handle(Future.failedFuture(databaseDeleteReply.cause()));
            }
        });
    }

    private void saveAccessInformation(Distribution dist, String distributionUrl, String datasetId, Handler<AsyncResult<Void>> resultHandler) {
        Date d = new Date();
        databaseService.update("INSERT INTO accessinformation values(?,?,?,?,?)",
                new JsonArray().add(d.toInstant()).add(d.toInstant())
                        .add(dist.getResourceId())
                        .add(datasetId)
                        .add(distributionUrl), reply -> {
                    if (reply.succeeded()) {
                        LOGGER.info("Saved distribution with id " + dist.getResourceId() + " from dataset " + datasetId);
                        resultHandler.handle(Future.succeededFuture());
                    } else {
                        LOGGER.error("Access information could not be inserted into database.", reply.cause());
                        resultHandler.handle(Future.failedFuture(reply.cause()));
                    }
                });
    }

    public void createDataAsset(DataAssetCreateMessage message, Handler<AsyncResult<JsonObject>> resultHandler) {
        String recordId = message.getData().getString("recordId");
        String accessToken = message.getDataSource().getData().getString("accessToken");

        zenodoService.query(new JsonObject(Json.encode(message.getDataSource())), recordId, accessToken, response -> {
            if (response.succeeded()) {
                LOGGER.info("Received: " + response.result());
                Dataset dataset = new Dataset();
                dataset.setStatus(DataAssetStatus.APPROVED);
                dataset.setResourceId(response.result().getString("doi", UUID.randomUUID().toString()));

                JsonObject metadata = response.result().getJsonObject("metadata");

                if (metadata != null) {
                    if (metadata.containsKey("title"))
                        dataset.setTitle(metadata.getString("title"));

                    if (metadata.containsKey("description"))
                        dataset.setDescription(metadata.getString("description"));

                    if (metadata.containsKey("license") && metadata.getJsonObject("license").containsKey("id"))
                        dataset.setLicense(metadata.getJsonObject("license").getString("id"));

                    if (metadata.containsKey("version"))
                        dataset.setVersion(metadata.getString("version"));

                    if (metadata.containsKey("keywords")) {
                        dataset.setTags(metadata.getJsonArray("keywords")
                                .stream()
                                .map(Object::toString)
                                .collect(Collectors.toSet()));
                    }
                }

                List<Future> distributions = response.result().getJsonArray("files", new JsonArray()).stream()
                        .map(file -> buildDistribution((JsonObject) file, dataset))
                        .collect(Collectors.toList());

                CompositeFuture.all(distributions).onComplete(handler -> {
                    if (handler.succeeded()) {
                        dataset.setDistributions(new HashSet<>(handler.result().list()));
                        resultHandler.handle(Future.succeededFuture(new JsonObject(Json.encode(dataset))));
                    } else {
                        resultHandler.handle(Future.failedFuture(handler.cause()));
                    }
                });
            }
        });
    }

    private Future<Distribution> buildDistribution(JsonObject zenodoDistribution, Dataset dataset) {
        return Future.future(buildDistribution -> {
            Distribution distribution = new Distribution();
            distribution.setResourceId(UUID.randomUUID().toString());

            // TODO determine/generate proper filename
            if (zenodoDistribution.containsKey("type"))
                distribution.setFiletype(zenodoDistribution.getString("type"));

            if (zenodoDistribution.containsKey("links") && zenodoDistribution.getJsonObject("links").containsKey("self")) {
                String downloadUrl = zenodoDistribution.getJsonObject("links").getString("self");

                String fileName = !StringUtils.substringAfterLast(downloadUrl, "/").isEmpty()
                        ? StringUtils.substringAfterLast(downloadUrl, "/")
                        : distribution.getFiletype();

                distribution.setFilename(fileName);


                fileService.tryFile(downloadUrl, downloadFile -> {
                    if (downloadFile.succeeded()) {
                        saveAccessInformation(distribution, downloadUrl, dataset.getResourceId(), saveFile -> {
                            if (saveFile.succeeded()) {
                                buildDistribution.complete(distribution);
                            } else {
                                buildDistribution.fail(saveFile.cause());
                            }
                        });
                    } else {
                        buildDistribution.fail(downloadFile.cause());
                    }
                });
            } else {
                buildDistribution.fail("No downloadURL provided");
            }
        });
    }
}

