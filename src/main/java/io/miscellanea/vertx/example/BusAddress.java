package io.miscellanea.vertx.example;

/**
 * An enumeration of Vert.x event bus addresses.
 *
 * @author Jason Hallford
 */
public enum BusAddress {
    RepositoryPersonCreate("repo.person.create"),
    RepositoryPersonFind("repo.person.find"),
    RepositoryPersonList("repo.person.list");

    private final String name;

    BusAddress(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
