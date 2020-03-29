package io.miscellanea.vertx.example;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * Main class used to initialize the JPA runtime (Hibernate, in this case) and
 * deploy the example's verticles, of which there are two:
 * <ol>
 *     <li><code>PeopleApiVertical</code>, which presents a simple RESTful API
 *     for creating, listing, and finding instances of <code>Person</code></li>
 *     <li><code>PersonJpaRepositoryVerticle</code>, which executes as a worker
 *     verticle and handles the JPA interface with the database (H2, in this case)</li>
 * </ol>
 * This is example code, and as such is light on error handling, etc. It's primary
 * purpose is to demonstrate how one might integrate JPA with Vert.x to implement
 * a database-backed API.
 */
public class VertXHibernateDeployer {
  // Fields
  private static final Logger LOGGER = LoggerFactory.getLogger(VertXHibernateDeployer.class);

  public static void main(String[] args) {
    LOGGER.debug("Bootstrapping the Vert.x runtime...");
    var vertx = Vertx.vertx();
    LOGGER.debug("Vert.x runtime initialized.");

    // Intitialize the JPA entity manager. We must only have one of these per
    // JVM. We terminate at this point in the bootstrap if initialization
    // fails.
    try {
      var pm = PersistenceManager.INSTANCE;
      LOGGER.debug("Pesistence Manager initialized = {}", pm.isInitialized());
    } catch (Exception e) {
      LOGGER.error("Unable to initialize persistence manager.", e);
      return;
    }

    // Deploy the JPA verticle.
    var opts = new DeploymentOptions().setWorker(true);
    vertx.deployVerticle(new PersonJpaRepositoryVerticle(), opts);

    // Deploy the REST API verticle
    vertx.deployVerticle(new PeopleApiVerticle());
  }
}
