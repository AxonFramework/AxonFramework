package io.axoniq.demo.university._ext

import org.assertj.core.api.Assertions.assertThat
import org.axonframework.common.property.PropertyAccessStrategy
import org.junit.jupiter.api.Test

@OptIn(ExperimentalStdlibApi::class)
internal class KotlinReflectPropertyAccessStrategyTest {

  @JvmInline
  value class PersonId(val value: Int)

  @Test
  fun `access property from data class`() {
    data class Person(val id: Int, val name: String)

    val property = PropertyAccessStrategy.getProperty(Person::class.java, "id")

    assertThat(property.getValue<Int>(Person(42, "Douglas"))).isEqualTo(42)
  }

  @Test
  fun `access property from data class with value id`() {
    data class Person(val id: PersonId, val name: String)

    val person = Person(PersonId(42), "Douglas")

    val property = PropertyAccessStrategy.getProperty(Person::class.java, "id")

    assertThat(property.getValue<PersonId>(person)).isEqualTo(PersonId(42))
  }

}
