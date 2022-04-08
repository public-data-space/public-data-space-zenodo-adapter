package de.fraunhofer.fokus.ids.services.zenodo;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

@ProxyGen
@VertxGen
public interface ZenodoService {

    @Fluent
    ZenodoService query(JsonObject dataSource, String recordId, String accessToken, Handler<AsyncResult<JsonObject>> resultHandler);

    @GenIgnore
    static ZenodoService create(WebClient webClient, Handler<AsyncResult<ZenodoService>> readyHandler) {
        return new ZenodoServiceImpl(webClient, readyHandler);
    }

    @GenIgnore
    static ZenodoService createProxy(Vertx vertx, String address) {
        return new ZenodoServiceVertxEBProxy(vertx, address);
    }

}
