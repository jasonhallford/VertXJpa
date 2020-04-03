package io.miscellanea.vertx.example;

/**
 * Constants naming Vert.x event bus addresses.
 *
 * @author Jason Hallford
 */
public final class EventBusAddress {
  public static final String REPOSITORY_PERSON_CREATE = "repo.person.create";
  public static final String REPOSITORY_PERSON_FIND = "repo.person.find";
  public static final String REPOSITORY_PERSON_LIST = "repo.person.list";

  private EventBusAddress() {}
}
