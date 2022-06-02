package de.fraunhofer.fokus.ids;

import de.fraunhofer.fokus.ids.services.database.DatabaseService;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;

import static de.fraunhofer.fokus.ids.ApplicationConfig.*;

public class InitService {

    private final Logger LOGGER = LoggerFactory.getLogger(InitService.class.getName());
    private final DatabaseService databaseService;

    public InitService(Vertx vertx, Handler<AsyncResult<Void>> resultHandler) {
        this.databaseService = DatabaseService.createProxy(vertx, DATABASE_SERVICE);

        Promise<Void> dbPromise = Promise.promise();
        Future<Void> dbFuture = dbPromise.future();
        initDB(dbFuture);

        Promise<Void> configPromise = Promise.promise();
        Future<Void> configFuture = configPromise.future();
        register(vertx, configFuture);

        CompositeFuture.all(dbFuture, configFuture).onComplete(handler -> {
            if (handler.succeeded()) {
                resultHandler.handle(Future.succeededFuture());
            } else {
                LOGGER.error("Table creation failed.", handler.cause());
                resultHandler.handle(Future.failedFuture(handler.cause()));
            }
        });
    }

    private void initDB(Handler<AsyncResult<Void>> resultHandler) {
        databaseService.update("CREATE TABLE IF NOT EXISTS accessinformation (created_at, updated_at, datasetid, url)", new JsonArray(), handler -> {
            if (handler.succeeded()) {
                resultHandler.handle(Future.succeededFuture());
            } else {
                LOGGER.error("Table creation failed.", handler.cause());
                resultHandler.handle(Future.failedFuture(handler.cause()));
            }
        });
    }

    private void register(Vertx vertx, Handler<AsyncResult<Void>> resultHandler) {
        ConfigRetrieverOptions options = new ConfigRetrieverOptions()
                .addStore(new ConfigStoreOptions()
                        .setType("env"));

        ConfigRetriever.create(vertx, options).getConfig(ar -> {
            if (ar.succeeded()) {
                JsonObject registration = new JsonObject()
                        .put("name", ar.result().getString(ENV_ROUTE_ALIAS, DEFAULT_ADAPTER_NAME))
                        .put("address", new JsonObject()
                                .put("host", ar.result().getString(ENV_ROUTE_ALIAS, DEFAULT_ROUTE_ALIAS))
                                .put("port", ar.result().getInteger(ENV_MANAGER_PORT, DEFAULT_ZENODO_PORT)));

                WebClient webClient = WebClient.create(vertx);
                establishConnection(3, webClient,
                        ar.result().getInteger(ENV_MANAGER_PORT, DEFAULT_MANAGER_PORT),
                        ar.result().getString(ENV_MANAGER_HOST, DEFAULT_MANAGER_HOST),
                        registration, resultHandler);
            } else {
                resultHandler.handle(Future.failedFuture(ar.cause()));
            }
        });
    }

    private void establishConnection(int i, WebClient webClient, int port, String host, JsonObject registration, Handler<AsyncResult<Void>> resultHandler) {
        if (i == 0) {
            resultHandler.handle(Future.failedFuture("Connection refused"));
        } else {
            webClient
                    .post(port, host, "/register")
                    .sendJsonObject(registration, ar -> {
                        if (ar.succeeded()) {
                            resultHandler.handle(Future.succeededFuture());
                        } else {
                            establishConnection(i - 1, webClient, port, host, registration, resultHandler);
                        }
                    });
        }
    }
}
