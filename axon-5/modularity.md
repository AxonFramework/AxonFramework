# Project Modularity

Below is a list of the current project modularity and the desired modularity.
The intent with adjusting the modularity is to be able to serve more users with Axon Framework then we currently do.

## Current structure

> Extensions are left out for simplicity.

1. Messaging -> JPA, JDBC, InMemory
2. Modelling -> JPA, JDBC, InMemory
3. EventSourcing -> JPA, JDBC, InMemory
4. Configuration
5. AxonServerJavaConnector
6. Disruptor
7. Spring
8. SpringBoot
9. SpringBootStarter
10. Testing
11. Legacy
12. Metrics (DropWizard)
13. Metric (Micrometer)
14. Tracing
15. [IntegrationTests]

## Desired structure

### Core

1. Messaging -> Configuration (archetypes), Metrics, Tracing
     - Messaging-Core -> InMemory, InProcess, Configuration, Metrics, Tracing
     - Event-Messaging -> Configuration (archetypes), Metrics, Tracing
     - Command-Messaging -> Configuration (archetypes), Metrics, Tracing
     - Query-Messaging -> Configuration (archetypes), Metrics, Tracing
2. EventSourcing -> InMemory, InProcess, Configuration, Metrics, Tracing
3. Modelling
4. Testing
5. Integration Tests -> unpublished

### Extensions

> Extensions are in the future separated into internal and external.

#### Internal Extensions

> Internal extension are located in the repository underneath the `extensions` folder.

1. Spring Support (`spring`)
    - Core
    - Starter
    - Auto-Configuration
2. Metrics (`metrics`)
    - Dropwizard
    - Micrometer
3. Tracing (`tracing`)
    - OpenTelemetry

#### External or still open / discussable

- Legacy (AF4)
- Axon Server Connector Java
- Disruptor
- JPA
- JDBC
- R2DBC
- Mongo
- Kafka
- JobRnr
- quartz
- AMQP
- Kotlin
- Reactor
- RXJava
- SpringCloud

### BOM

1. Dependency management BOM
2. Archetype poms for easy dependency inclusion