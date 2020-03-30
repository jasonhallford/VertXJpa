package io.miscellanea.vertx.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static io.miscellanea.vertx.example.BusAddress.*;

/**
 * A Vert.x verticle that implements the People resource for our example API.
 *
 * @author Jason Hallford
 */
public class PeopleApiVerticle extends AbstractVerticle {
  // Fields
  private static final Logger LOGGER = LoggerFactory.getLogger(PeopleApiVerticle.class);

  // Constructors
  public PeopleApiVerticle() {}

  // Vert.x life-cycle management
  @Override
  public void start(Promise<Void> startPromise) {
    LOGGER.debug("Starting HTTP server...");

    // Create and initialize the router. This object directs web
    // requests to specific handlers based on URL pattern matching.
    var router = Router.router(vertx);

    // Add a body handler to all routes. If we forget to do this,
    // we won't be able to access the content of any POST methods!
    router.route("/api/people*").handler(BodyHandler.create());

    // Add handlers for supported HTTP methods
    router.get("/api/people").handler(this::getPeople);
    router.get("/api/people/:id").handler(this::getPerson);
    router.post("/api/people").handler(this::createPerson);

    LOGGER.debug(
        "Will bind API verticle to TCP port {}.",
        config().getInteger(ConfigProps.ApiBindPort.toString()));

    // Create the HTTP server. Since this may take a while, we're
    // using the Promise passed to this method to tell Vert.x when
    // this verticle is fully deployed.
    vertx
        .createHttpServer()
        .requestHandler(router)
        .listen(
            config().getInteger(ConfigProps.ApiBindPort.toString()),
            result -> {
              if (result.succeeded()) {
                LOGGER.debug("HTTP server started successfully.");
                startPromise.complete();
              } else {
                LOGGER.error(
                    "Unable to start HTTP server. Reason: {}", result.cause().getMessage());
                startPromise.fail(result.cause());
              }
            });

    LOGGER.info("Person API verticle started.");
  }

  @Override
  public void stop() {
    LOGGER.info("Person API verticle stopped.");
  }

  // API Resource Handlers
  private void getPeople(RoutingContext routingContext) {
    LOGGER.debug("getPeople() called. Dispatching event to JPA verticle.");

    var payload = new JsonObject();
    payload.put("request-id", UUID.randomUUID().toString());

    // We use the event bus' request-reply pattern to ensure that:
    // 1. If we have more than one JPA verticle that only one will process
    //    the event, and
    // 2. The JPA verticle can send the response back to the requesting
    //    verticle so that it may be returned to the caller
    vertx
        .eventBus()
        .request(
            RepositoryPersonList.toString(),
            payload,
            reply -> this.sendGetResponse(routingContext, reply.result()));
  }

  private void getPerson(RoutingContext routingContext) {
    LOGGER.debug("getPeople() called. Dispatching event to JPA verticle.");

    var id = routingContext.request().getParam("id");
    LOGGER.debug("Requested person is is {}.", id);

    var payload = new JsonObject();
    payload.put("request-id", UUID.randomUUID().toString());
    payload.put("entity-id", id);

    // We use the event bus' request-reply pattern to ensure that:
    // 1. If we have more than one JPA verticle that only one will process
    //    the event, and
    // 2. The JPA verticle can send the response back to the requesting
    //    verticle so that it may be returned to the caller
    vertx
        .eventBus()
        .request(
            RepositoryPersonFind.toString(),
            payload,
            reply -> this.sendGetResponse(routingContext, reply.result()));
  }

  private void createPerson(RoutingContext routingContext) {
    LOGGER.debug(
        "create person handler called with HTTP body = {}", routingContext.getBodyAsString());

    if (!"application/json".equalsIgnoreCase(routingContext.request().getHeader("content-type"))) {
      routingContext.response().setStatusCode(400).end();
    } else {
      var payload = routingContext.getBodyAsJson();

      // The 'request-id' is a correlation ID that follows the request between verticles. It
      // facilitates debugging through logs and is presented to the user in each response.
      payload.put("request-id", UUID.randomUUID().toString());

      vertx
          .eventBus()
          .request(
              RepositoryPersonCreate.toString(),
              payload,
              reply -> this.sendPostResponse(routingContext, reply.result()));
    }
  }

  // Utility methods
  private void sendGetResponse(RoutingContext routingContext, Message<Object> message) {
    LOGGER.debug("Sending GET response.");

    var result = (JsonObject) message.body();

    if (result == null
        || ("{}".equals(result.getString("result")) || "[]".equals(result.getString("result")))) {
      var reqId = result == null ? UUID.randomUUID().toString() : result.getString("request-id");
      routingContext
          .response()
          .putHeader("X-request-id", reqId)
          .setStatusCode(404)
          .end();
    } else {
      var response = routingContext.response().putHeader("content-type", "application/json");

      switch (result.getString("status")) {
        case "ok":
          response.setStatusCode(200).end(result.getString("result"));
          break;
        case "err":
          response.setStatusCode(500).end(result.getString("message"));
          break;
        default:
          response.setStatusCode(500).end("An unknown error occurred.");
          break;
      }
    }
  }

  private void sendPostResponse(RoutingContext routingContext, Message<Object> message) {
    LOGGER.debug("Sending POST response.");

    var result = (JsonObject) message.body();

    int statusCode = "ok".equalsIgnoreCase(result.getString("status")) ? 201 : 500;

    var response =
        routingContext
            .response()
            .putHeader("X-request-id", result.getString("request-id"))
            .setStatusCode(statusCode);

    if (statusCode == 201) {
      var persisted = (JsonObject) Json.decodeValue(result.getString("result"));
      var location = "/api/people/" + persisted.getInteger("id");

      LOGGER.debug("Setting HTTP location header to '{}'", location);

      response = response.putHeader("location", location);
    }

    response.end();
  }
}
