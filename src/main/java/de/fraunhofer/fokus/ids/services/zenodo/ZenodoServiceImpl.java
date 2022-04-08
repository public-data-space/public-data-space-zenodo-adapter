package de.fraunhofer.fokus.ids.services.zenodo;

import de.fraunhofer.fokus.ids.persistence.entities.DataSource;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZenodoServiceImpl implements ZenodoService {

    private static final Logger log = LoggerFactory.getLogger(ZenodoServiceImpl.class);
    private final WebClient webClient;

    public ZenodoServiceImpl(WebClient webClient, Handler<AsyncResult<ZenodoService>> readyHandler) {
        this.webClient = webClient;
        readyHandler.handle(Future.succeededFuture(this));
    }

    @Override
    public ZenodoService query(JsonObject dataSource, String recordId, String accessToken, Handler<AsyncResult<JsonObject>> resultHandler) {
        String url = Json.decodeValue(dataSource.toString(), DataSource.class)
                .getData()
                .getString("zenodoApiUrl", "https://zenodo.org/api/records");

        url = url.endsWith("/")
                ? url
                : url + "/";

        webClient.getAbs(url + recordId)
                .addQueryParam("access_token", accessToken)
                .expect(ResponsePredicate.SC_SUCCESS)
                .send(response -> {
                    if (response.succeeded()) {
                        resultHandler.handle(Future.succeededFuture(response.result().bodyAsJsonObject()));
                    } else {
                        resultHandler.handle(Future.failedFuture(response.cause()));
                    }
                });

        return this;
    }
}
