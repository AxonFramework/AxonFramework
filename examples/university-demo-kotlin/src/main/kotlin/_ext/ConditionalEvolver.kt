package io.axoniq.demo.university._ext

/**
 * Conditionally evolves current instance.
 * @param condition A condition to execute the evolution.
 * @param evolver A function to be executed.
 * @return itself or evolved version.
 */
fun <T> T.conditionalEvolve(
  condition: Boolean,
  evolver: (T) -> T
): T {
  return if (condition) {
    evolver(this)
  } else {
    this
  }
}
