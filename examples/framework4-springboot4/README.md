 # Axon Framework 4.x with Spring Boot 4 Example
 
 This module is a small example application to explore how Axon Framework 4.12.x can be used with the latest Spring Boot 4 version.
 
 Important notes:
 - This example intentionally uses a different Spring Boot version than the one used to build Axon Framework 4.12.x itself, and it targets Java 21.
 - Because of that version mismatch, this module cannot be part of the Axon Framework root Maven reactor (the top-level `pom.xml`).
 - Instead, it is a completely separate project that is checked into the same repository purely for convenience and local testing.
 
 ## How to run
 
 1. Include this module manually in your IDE's Maven view:
    - In IntelliJ IDEA, open the Maven tool window and click the "+" (Add Maven Project) button.
    - Select this module's `pom.xml` located at `examples/framework4-springboot4/pom.xml`.
 2. Use the provided run configuration:
    - The module contains a shared IntelliJ run configuration file: [`SpringBoot4ExampleApplication.run.xml`](.run/SpringBoot4ExampleApplication.run.xml).
    - Load/run that configuration to start the app (it launches [`org.axonframework.examples.sp4.SpringBoot4ExampleApplication`](src/main/java/org/axonframework/examples/sp4/SpringBoot4ExampleApplication.java)).
 
 That's it. This example is not built as part of the full Axon Framework build; you run it independently to validate behavior with Spring Boot 4 and Java 21.