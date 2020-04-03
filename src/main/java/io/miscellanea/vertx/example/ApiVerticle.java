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

/**
 * A Vert.x verticle that implements the People resource for our example API.
 *
 * @author Jason Hallford
 */
public class ApiVerticle extends AbstractVerticle {
  // Fields
  private static final Logger LOGGER = LoggerFactory.getLogger(ApiVerticle.class);

  // Constructors
  public ApiVerticle() {}

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
        "Will bind API verticle to TCP port {}.", config().getInteger(ConfigProp.BIND_PORT));

    // Create the HTTP server. Since this may take a while, we're
    // using the Promise passed to this method to tell Vert.x when
    // this verticle is fully deployed.
    vertx
        .createHttpServer()
        .requestHandler(router)
        .listen(
            config().getInteger(ConfigProp.BIND_PORT),
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
    payload.put(MessageField.REQUEST_ID, UUID.randomUUID().toString());

    // We use the event bus' request-reply pattern to ensure that:
    // 1. If we have more than one JPA verticle that only one will process
    //    the event, and
    // 2. The JPA verticle can send the response back to the requesting
    //    verticle so that it may be returned to the caller
    vertx
        .eventBus()
        .request(
            EventBusAddress.REPOSITORY_PERSON_LIST,
            payload,
            reply -> this.sendGetResponse(routingContext, reply.result()));
  }

  private void getPerson(RoutingContext routingContext) {
    LOGGER.debug("getPeople() called. Dispatching event to JPA verticle.");

    var id = routingContext.request().getParam("id");
    LOGGER.debug("Requested person is is {}.", id);

    var payload = new JsonObject();
    payload.put(MessageField.REQUEST_ID, UUID.randomUUID().toString());
    payload.put(MessageField.ENTITY_ID, id);

    // We use the event bus' request-reply pattern to ensure that:
    // 1. If we have more than one JPA verticle that only one will process
    //    the event, and
    // 2. The JPA verticle can send the response back to the requesting
    //    verticle so that it may be returned to the caller
    vertx
        .eventBus()
        .request(
            EventBusAddress.REPOSITORY_PERSON_FIND,
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
      payload.put(MessageField.REQUEST_ID, UUID.randomUUID().toString());

      vertx
          .eventBus()
          .request(
              EventBusAddress.REPOSITORY_PERSON_CREATE,
              payload,
              reply -> this.sendPostResponse(routingContext, reply.result()));
    }
  }

  // Utility methods
  private void sendGetResponse(RoutingContext routingContext, Message<Object> message) {
    LOGGER.debug("Sending GET response.");

    var result = (JsonObject) message.body();

    if (result == null
        || ("{}".equals(result.getString(MessageField.RESULT))
            || "[]".equals(result.getString(MessageField.RESULT)))) {
      var reqId =
          result == null ? UUID.randomUUID().toString() : result.getString(MessageField.REQUEST_ID);
      routingContext.response().putHeader("X-request-id", reqId).setStatusCode(404).end();
    } else {
      var response = routingContext.response().putHeader("content-type", "application/json");

      switch (result.getString(MessageField.STATUS)) {
        case "ok":
          response.setStatusCode(200).end(result.getString(MessageField.RESULT));
          break;
        case "err":
          response.setStatusCode(500).end(result.getString(MessageField.ERROR));
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

    int statusCode = "ok".equalsIgnoreCase(result.getString(MessageField.STATUS)) ? 201 : 500;

    var response =
        routingContext
            .response()
            .putHeader("X-request-id", result.getString("request-id"))
            .setStatusCode(statusCode);

    if (statusCode == 201) {
      var persisted = (JsonObject) Json.decodeValue(result.getString(MessageField.RESULT));
      var location = "/api/people/" + persisted.getInteger("id");

      LOGGER.debug("Setting HTTP location header to '{}'", location);

      response = response.putHeader("location", location);
    }

    response.end();
  }
}
