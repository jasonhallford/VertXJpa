package io.miscellanea.vertx.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.miscellanea.vertx.example.PersistenceManager.INSTANCE;

/**
 * A JPA-based repository for Person objects implemented as Vert.x worker verticle.
 *
 * @author Jason Hallford
 */
public class JpaRepositoryVerticle extends AbstractVerticle {
  // Fields
  private static final Logger LOGGER = LoggerFactory.getLogger(JpaRepositoryVerticle.class);

  // Constructors
  public JpaRepositoryVerticle() {}

  // Verticle life-cycle management
  @Override
  public void start() {
    LOGGER.debug("Registering event handlers...");
    var bus = vertx.eventBus();

    // This is where all the interesting stuff happens: the repository registers
    // and interest in named events that represent its core operations: create,
    // find, and list. The runtime will invoke these handlers when the API
    // verticle requires access to the persistence layer.
    bus.consumer(EventBusAddress.REPOSITORY_PERSON_CREATE, this::createPerson);
    bus.consumer(EventBusAddress.REPOSITORY_PERSON_FIND, this::findPerson);
    bus.consumer(EventBusAddress.REPOSITORY_PERSON_LIST, this::listPeople);
    LOGGER.debug("Handlers registered.");

    LOGGER.info("JPA verticle started.");
  }

  @Override
  public void stop() {
    LOGGER.info("JPA verticle stopped.");
  }

  // Message handlers
  private void createPerson(Message<JsonObject> message) {
    LOGGER.debug("Creating new person.");

    var reply = prepareReply(message);

    try {
      Optional<Person> optionalPerson = this.convertJsonToPerson(message.body());

      if (optionalPerson.isPresent()) {
        Person persistedPerson = INSTANCE.persist(optionalPerson.get());

        // Return the results as a JSON array.
        String jsonString = this.convertPersonToJson(persistedPerson);
        if (jsonString != null) {
          reply.put(MessageField.STATUS, "ok");
          reply.put(MessageField.RESULT, jsonString);
        } else {
          throw new PersistenceException("Unable to convert Person to JSON.");
        }
      } else {
        throw new PersistenceException("Unable to create Person from provided JSON.");
      }
    } catch (PersistenceException e) {
      LOGGER.error("Unable to save person to database; returning error reply.", e);

      // Return an error status to the message's originator.
      reply.put(MessageField.STATUS, "err");
      reply.put(MessageField.ERROR, e.getMessage());
    }

    message.reply(reply);
  }

  private void listPeople(Message<JsonObject> message) {
    LOGGER.debug("Reading all people from the database.");

    var reply = prepareReply(message);

    try {
      List<Person> people =
          INSTANCE.find(em -> em.createQuery("select p from Person p").getResultList());

      // Convert the response to a JSON array.
      var mapper = new ObjectMapper();
      var jsonString = mapper.writeValueAsString(people);
      LOGGER.debug("Query results as JSON = {}", jsonString);

      // Return the results as a JSON array.
      reply.put(MessageField.STATUS, "ok");
      reply.put(MessageField.RESULT, jsonString);
    } catch (PersistenceException | JsonProcessingException e) {
      LOGGER.error(
          "Unable to read from database or marshal results to JSON; returning error reply.", e);

      // Return an error status to the message's originator.
      reply.put(MessageField.STATUS, "err");
      reply.put(MessageField.ERROR, e.getMessage());
    }

    message.reply(reply);
  }

  private void findPerson(Message<JsonObject> message) {
    LOGGER.debug(
        "Finding person with id {} in the database.",
        message.body().getString(MessageField.ENTITY_ID));
    Long entityId = Long.parseLong(message.body().getString(MessageField.ENTITY_ID));

    var reply = prepareReply(message);

    try {
      List<Person> people =
          INSTANCE.find(
              em -> {
                Person person = em.find(Person.class, entityId);
                return person == null ? new ArrayList<>() : Collections.singletonList(person);
              });

      // Convert the response to a JSON string.
      var jsonString = "{}";
      if (people.size() > 0) {
        var mapper = new ObjectMapper();
        jsonString = mapper.writeValueAsString(people.get(0));
      }
      LOGGER.debug("Query results as JSON = {}", jsonString);

      reply.put(MessageField.STATUS, "ok");
      reply.put(MessageField.RESULT, jsonString);
    } catch (PersistenceException | JsonProcessingException e) {
      LOGGER.error(
          "Unable to read from database or marshal results to JSON; returning error reply.", e);

      // Return an error status to the message's originator.
      reply.put(MessageField.STATUS, "err");
      reply.put(MessageField.RESULT, e.getMessage());
    }

    message.reply(reply);
  }

  private JsonObject prepareReply(Message<JsonObject> message) {
    return new JsonObject()
        .put(MessageField.REQUEST_ID, message.body().getValue(MessageField.REQUEST_ID));
  }

  private Optional<Person> convertJsonToPerson(JsonObject json) {
    Optional<Person> optional;
    Person person = null;

    try {
      var mapper = new ObjectMapper();
      person = mapper.readValue(json.toString(), Person.class);
    } catch (Exception e) {
      LOGGER.error("Unable to extract Person from JSON '" + json.toString() + "'.", e);
    }

    return Optional.ofNullable(person);
  }

  private String convertPersonToJson(Person person) {
    String json = null;

    try {
      var mapper = new ObjectMapper();
      json = mapper.writeValueAsString(person);
    } catch (Exception e) {
      LOGGER.error("Unable to convert Person to JSON.", e);
    }

    return json;
  }
}
