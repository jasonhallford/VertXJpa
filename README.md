## Introduction
This repository contains example code for integrating [Eclipse Vert.x](http://vertx.io) with the Java Persistence API 
(JPA) 2.2 in a Java SE 11 runtime. Here, we use Hibernate as the JPA implementation, although any JPA 2.2-compliant ORM 
should work. To minimized the difficulty of installing and running the example, persistence is provided by an embedded
instance of the H2 database. Connections are pooled via the high-performance [Hikari](https://github.com/brettwooldridge/HikariCP)
connection pool.

Yes, this is overkill.

Nevertheless, the author thought it worth the negligible increase in complexity to demonstrate how one might implement
efficient connection pooling in a Java SE environment.

## Requirements
To build this example, you will need the following:
1. A version of Git capable of cloning this repository from Git Hub
1. Apache Maven v3.5 or greater
1. The latest patch release of OpenJDK 11 (build produced by the [AdoptOpenJDK Project](https://adoptopenjdk.net/) work
nicely)

## Building the Project
You may build the example in one of two ways, as a JAR or a Docker image. 
### Maven Build
You may build JAR from source using [Apache Maven](http://maven.apache.org). Assuming a version >= 3.6.0 you can build it  by
executing `mvn package` at the command line (assuming `mvn` is in the path, of course). In the project's /target
directory, this will produce
* A JAR file named __vertx-jpa-1.2.jar__, which contains just the project's classes
* A fat JAR named __vertx-jpa-1.2-fat.jar__; you can use this to run the code by executing `java -jar VertXJpa-1.0-fat.jar`
at your favorite command line
### Building as a Docker Image
You may use the included Dockerfile to create a deployable image. From the source directory, run the following
command to build the image: `docker build -t vertxjpa:1.2 .`. Here, the resulting image will have the tag
__vertxjpa:1.2__. 

Run the container with the following command: `docker run --rm -p 8080:8080 --name vertxjpa vertxjpa:1.0`. You will 
be able to connect to the app at http://localhost:8080.

## Configuring the Example
The example includes a default configuration that creates 
* One API verticle bound to port TCP/8080
* One JPA verticle

The TCP port and number of JPA verticles may be customized by setting the following properties either as OS environment
variables or JRE system proprties (i.e. "-D" properties). The latter have a higher priority than the former.
| Property          | Notes                                                        |
| ----------------- | ------------------------------------------------------------ |
| bind-port     | An integer value that sets the API verticle's TCP bind port. |
| jpa-verticle-count | An integer value that speicified the number of JPA verticles to create; defaults to 2. | 
| api-verticle-count | An integer value that speicified the number of API verticles to create; defaults to 1. |

## Running the Example
Unless configured otherwise, the sample application presents a basic RESTful API on port TCP/8080 for a generic "Person" 
resource. Encoded as JSON, a Person looks like this:

```json
{
	"name" : "John Smith",
	"age" : "35"
}
```

The API supports three methods:
1. You may POST a JSON document in the format above to http://localhost:8080/api/people to create a new person. The new 
entity may be found at the address provided in the response's `location` header
1. You may list all people by sending a GET to http://localhost:8080/api/people
1. You may list a specific person by sending a GET to http://localhost:8080/api/people/[person id], where [person id] is
a value returned by a previous POST

I recommend [Postman](https://www.postman.com/) to exercise the example, although any tool capable of generating the
necessary HTTP requests will suffice.
