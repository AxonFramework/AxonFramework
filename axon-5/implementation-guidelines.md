# Implementation Guidelines

When constructing Axon Framework 5, and you are in doubt, take the following guidelines into account:

1. Discover the essence of your API!
  This means, deriving an API that provides us the flexibility to "move into the future."
  Thus, not necessarily *implementing* all our ideas in 5.0.0, but provide space for changes in 5.[1..N].0. 
2. Ease of use when using the Framework.
  a. Bare-bones for maximum freedom.
  b. Declarative configuration style. 
  c. Annotation-based.
3. Don't make any assumptions on the threading model.
4. Support both reactive and non-reactive application design.
5. Support both annotation-less and annotation based application design.
6. Composition over inheritance.
7. Contribution by outsiders should be feasible, so discuss with people outside the development team.