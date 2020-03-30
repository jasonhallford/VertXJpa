package io.miscellanea.vertx.example;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * Main class used to initialize the JPA runtime (Hibernate, in this case) and deploy the example's
 * verticles, of which there are two:
 *
 * <ol>
 *   <li><code>PeopleApiVertical</code>, which presents a simple RESTful API for creating, listing,
 *       and finding instances of <code>Person</code>
 *   <li><code>PersonJpaRepositoryVerticle</code>, which executes as a worker verticle and handles
 *       the JPA interface with the database (H2, in this case)
 * </ol>
 *
 * This is example code, and as such is light on error handling, etc. It's primary purpose is to
 * demonstrate how one might integrate JPA with Vert.x to implement a database-backed API.
 */
public class VertXJpaDeployer {
  // Fields
  private static final Logger LOGGER = LoggerFactory.getLogger(VertXJpaDeployer.class);

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
      LOGGER.error("Unable to initialize persistence manager; aborting deployment.", e);
      return;
    }

    // Configure the runtime so that it reads configuration in the following order:
    // 1. System properties specified as "-D" options on the command line
    // 2. OS environment variables (useful for Docker)
    // 3. The default configuration file included in the JAR
    var defaultStore =
        new ConfigStoreOptions()
            .setType("file")
            .setFormat("json")
            .setConfig(new JsonObject().put("path", "conf/vertxjpa-config.json"));
    var systemPropsStore = new ConfigStoreOptions().setType("sys");
    var envVarStore = new ConfigStoreOptions().setType("env");

    var configRetrieverOpts =
        new ConfigRetrieverOptions()
            .addStore(defaultStore)
            .addStore(envVarStore)
            .addStore(systemPropsStore);

    // Deploy the application's verticles.
    ConfigRetriever.create(vertx, configRetrieverOpts)
        .getConfig(
            config -> {
              int jpaCount = config.result().getInteger(ConfigProps.JpaVerticleCount.toString());

              LOGGER.debug("Deploying {} instance(s) of the JPA verticle.", jpaCount);

              // Deploy the JPA verticle. Note that we must deploy the verticle by name, not
              // instance, if we need to deploy more than once instance.
              var jpaOpts =
                  new DeploymentOptions()
                      .setConfig(config.result())
                      .setInstances(jpaCount)
                      .setWorker(true);
              vertx.deployVerticle(PersonJpaRepositoryVerticle.class.getName(), jpaOpts);

              // Deploy the REST API verticle
              var apiOpts = new DeploymentOptions().setConfig(config.result());
              vertx.deployVerticle(PeopleApiVerticle.class.getName(), apiOpts);
            });
  }
}
