package io.axoniq.demo.university

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

fun main(args: Array<String>) {
  runApplication<UniversityKotlinApplication>(*args)
}

@SpringBootApplication
class UniversityKotlinApplication {
}
