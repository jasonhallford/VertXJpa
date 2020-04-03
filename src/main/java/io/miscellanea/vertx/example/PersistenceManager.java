package io.miscellanea.vertx.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.List;
import java.util.function.Function;

/**
 * A singleton to manage access to bootstrap the JPA entity manager factory and create entity
 * managers. We use an enum as the JRE makes strong guarantees that only one instance will ever
 * exist at runtime and that it's identity will remain stable.
 *
 * <p><strong>IMPORTANT:</strong> This functionality can only be used from worker verticles! It does
 * blocking I/O with the database which can be problematic. It may also rely on thread- local
 * storage to manage transactions, etc., which means the session must begin and end on the same
 * thread.
 *
 * @author Jason Hallford
 */
public enum PersistenceManager {
  INSTANCE;

  // Fields
  private Logger LOGGER = LoggerFactory.getLogger(PersistenceManager.class);
  private EntityManagerFactory entityManagerFactory;

  // Constructor
  PersistenceManager() {
    LOGGER.debug("Creating new entity manager factory...");

    // We want this to throw an uncaught exception if it fails as it should terminate the
    // JVM--verticles should not deploy if JPA is not initialized.
    this.entityManagerFactory = Persistence.createEntityManagerFactory("vertx-hibernate");

    LOGGER.debug("Factory successfully created.");
  }

  // Entity Manager methods
  /**
   * Tests the persistence manager's initialization state.
   *
   * @return <code>true</code> if the entity manager factory is initialized; otherwise, <code>false
   *     </code>.
   */
  public boolean isInitialized() {
    return this.entityManagerFactory != null;
  }

  /**
   * Create a new <code>EntityManager</code> instance.
   *
   * @return The entity manager.
   */
  public EntityManager getEntityManager() {
    LOGGER.debug("Creating new entity manager.");
    return this.entityManagerFactory.createEntityManager();
  }

  public <T> List<T> find(Function<EntityManager, List<T>> func) {
    List<T> result;
    EntityManager em;

    try {
      em = entityManagerFactory.createEntityManager();

      try {
        LOGGER.debug("Executing find operation with managed entity manager.");
        result = func.apply(em);
      } catch (Exception e) {
        throw new PersistenceException("Unable to execute find operation.", e);
      } finally {
        try {
          em.close();
        } catch (Exception e) {
          LOGGER.error(
              "Unable to close entity manager! This may cause a leak in the connection pool.", e);
        }
      }

    } catch (Exception e) {
      throw new PersistenceException(
          "Unable to retrieve an entity manager from Persistence Manager.", e);
    }

    return result;
  }

  public <T> T persist(T entity) {
    T result;
    EntityManager em;

    try {
      em = entityManagerFactory.createEntityManager();

      try {
        LOGGER.debug("Executing find operation with managed entity manager.");
        em.getTransaction().begin();
        result = em.merge(entity);
        em.getTransaction().commit();
      } catch (Exception e) {
        em.getTransaction().rollback();
        throw new PersistenceException(
            "An error occurred while saving the object to the database; transaction rolled-back.",
            e);
      } finally {
        try {
          em.close();
        } catch (Exception e) {
          LOGGER.error(
              "Unable to close entity manager! This may cause a leak in the connection pool.", e);
        }
      }

    } catch (Exception e) {
      throw new PersistenceException(
          "Unable to retrieve an entity manager from Persistence Manager.", e);
    }

    return result;
  }

  /** Closes the shared entity manager factory. */
  public synchronized void close() {
    try {
      if (this.entityManagerFactory != null) {
        LOGGER.debug("Closing shared entity manager factory...");
        this.entityManagerFactory.close();
        LOGGER.debug("Factory successfully closed.");
      } else {
        LOGGER.debug("Factory already closed.");
      }
    } catch (Exception e) {
      LOGGER.error("Unable to close factory. See cause for details.", e);
    }
  }
}
