package de.fraunhofer.fokus.ids.services.zenodo;

import de.fraunhofer.fokus.ids.ApplicationConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.serviceproxy.ServiceBinder;


public class ZenodoServiceVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        WebClientOptions options = new WebClientOptions().setTrustAll(true);
        WebClient webClient = WebClient.create(vertx, options);
        ZenodoService.create(webClient, ready -> {
            if (ready.succeeded()) {
                ServiceBinder binder = new ServiceBinder(vertx);
                binder
                        .setAddress(ApplicationConfig.ZENODO_SERVICE)
                        .register(ZenodoService.class, ready.result());
                startPromise.complete();
            } else {
                startPromise.fail(ready.cause());
            }
        });
    }

}
