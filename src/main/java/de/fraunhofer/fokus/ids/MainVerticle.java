package de.fraunhofer.fokus.ids;

import de.fraunhofer.fokus.ids.enums.FileType;
import de.fraunhofer.fokus.ids.messages.DataAssetCreateMessage;
import de.fraunhofer.fokus.ids.messages.ResourceRequest;
import de.fraunhofer.fokus.ids.services.database.DatabaseServiceVerticle;
import de.fraunhofer.fokus.ids.services.zenodo.ZenodoServiceVerticle;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.http.entity.ContentType;

import java.util.Arrays;

public class MainVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class.getName());
    private Router router;
    private DataAssetService dataAssetService;
    private FileService fileService;
    private int zenodoPort;

    @Override
    public void start(Promise<Void> startPromise) {
        this.router = Router.router(vertx);
        this.dataAssetService = new DataAssetService(vertx);
        this.fileService = new FileService(vertx);

        startVerticle(DatabaseServiceVerticle.class)
                .compose(ar -> startVerticle(ZenodoServiceVerticle.class))
                .compose(ar -> Future.future(initService -> new InitService(vertx, init -> {
                    if (init.succeeded()) {
                        LOGGER.info("Initialization complete.");
                        initService.complete();
                    } else {
                        LOGGER.error("Initialization failed.", init.cause());
                        initService.complete();
                    }
                })))
                .compose(ar -> createHttpServer())
                .onSuccess(success -> startPromise.complete())
                .onFailure(startPromise::fail);
    }

    private Future<Void> createHttpServer() {
    	

    	ConfigRetriever retriever = ConfigRetriever.create(vertx);
        retriever.getConfig(ar -> {
            if (ar.succeeded()) {
                JsonObject env = ar.result();
                zenodoPort = env.getInteger(ApplicationConfig.ENV_MANAGER_PORT, ApplicationConfig.DEFAULT_ZENODO_PORT);
                
            } else {
                LOGGER.error("Config could not be retrieved.");
            }
        });
    	
        return Future.future(spawnServer -> {
            HttpServer server = vertx.createHttpServer();
            router.route().handler(BodyHandler.create());

            router.post("/create").handler(routingContext ->
                    dataAssetService.createDataAsset(Json.decodeValue(routingContext.getBodyAsJson().toString(), DataAssetCreateMessage.class), reply ->
                            reply(reply, routingContext.response())));

            router.get("/delete/:id").handler(routingContext ->
                    dataAssetService.deleteDataAsset(routingContext.request().getParam("id"), reply ->
                            reply(reply, routingContext.response())));

            router.post("/getFile").handler(routingContext ->
                    fileService.getFile(routingContext.getBodyAsJson(), routingContext.response(), fileLink -> reply(fileLink, routingContext.response())));

            router.route("/supported")
                    .handler(routingContext ->
                            supported(result -> reply(result, routingContext.response()))
                    );

            router.route("/getDataAssetFormSchema")
                    .handler(routingContext ->
                            getDataAssetFormSchema(result -> reply(result, routingContext.response()))
                    );

            router.route("/getDataSourceFormSchema")
                    .handler(routingContext ->
                            getDataSourceFormSchema(result -> reply(result, routingContext.response()))
                    );


            server.requestHandler(router)
                    .listen(zenodoPort, listen -> {
                        if (listen.succeeded()) {
                            spawnServer.complete();
                        } else {
                            spawnServer.fail(listen.cause());
                        }
                    });
            LOGGER.info("Zenodo adapter successfully started.");
        });
    }

    private void supported(Handler<AsyncResult<String>> handler) {
        handler.handle(Future.succeededFuture(new JsonObject()
                .put("supported", new JsonArray().add(FileType.JSON))
                .toString()
        ));
    }

    private void getDataAssetFormSchema(Handler<AsyncResult<String>> handler) {
        handler.handle(Future.succeededFuture(new JsonObject()
                .put("type", "object")
                .put("properties", new JsonObject()
                        .put("recordId", new JsonObject()
                                .put("type", "string")
                                .put("ui", new JsonObject()
                                        .put("label", "Record ID")
                                        .put("placeholder", "1234567"))))
                .toString()));
    }

    private void getDataSourceFormSchema(Handler<AsyncResult<String>> handler) {
        handler.handle(Future.succeededFuture(new JsonObject()
                .put("type", "object")
                .put("properties", new JsonObject()
                        .put("zenodoApiUrl", new JsonObject()
                                .put("type", "string")
                                .put("ui", new JsonObject()
                                        .put("label", "Zenodo API URL")
                                        .put("placeholder", "https://zenodo.org/api/records")))
                        .put("accessToken", new JsonObject()
                                .put("type", "string")
                                .put("ui", new JsonObject()
                                        .put("label", "Access Token")
                                        .put("placeholder", "myToken"))))
                .toString()));
    }

    private void reply(AsyncResult result, HttpServerResponse response) {
        if (result.succeeded()) {
            if (result.result() != null) {
                String entity = result.result().toString();
                response.putHeader("content-type", ContentType.APPLICATION_JSON.toString());
                response.end(entity);
            } else {
                response.setStatusCode(404).end();
            }
        } else {
            response.setStatusCode(404).end();
        }
    }

    private Future<Void> startVerticle(Class<? extends AbstractVerticle> clazz) {
        DeploymentOptions deploymentOptions = new DeploymentOptions()
                .setWorker(true);

        return Future.future(startVerticle -> vertx.deployVerticle(clazz.getName(), deploymentOptions, handler -> {
            if (handler.succeeded()) {
                startVerticle.complete();
            } else {
                startVerticle.fail("Failed to deploy [" + clazz.getName() + "] : " + handler.cause());
            }
        }));
    }

    public static void main(String[] args) {
        String[] params = Arrays.copyOf(args, args.length + 1);
        params[params.length - 1] = MainVerticle.class.getName();
        Launcher.executeCommand("run", params);
    }
}
