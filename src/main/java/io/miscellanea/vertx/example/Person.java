package io.miscellanea.vertx.example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * JPA entity class that represents our API's only resource, a "person" with a name and an age.
 *
 * @author Jason Hallford
 */
@Entity
@Table(name = "person")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Person {
  // Fields
  @Id @GeneratedValue private Long id;
  private String name;
  private int age;

  // Constructor

  /** Default constructor for use by JPA. */
  public Person() {}

  // Properties
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getAge() {
    return age;
  }

  public void setAge(int age) {
    this.age = age;
  }
}
