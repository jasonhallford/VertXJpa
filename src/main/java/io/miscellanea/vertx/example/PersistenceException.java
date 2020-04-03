package io.miscellanea.vertx.example;

/**
 * Thrown when an error occurs at the persistence layer.
 *
 * @author Jason Hallford
 */
public class PersistenceException extends RuntimeException {
  // Constructors
  public PersistenceException(String message) {
    super(message);
  }

  public PersistenceException(String message, Throwable cause) {
    super(message, cause);
  }
}
