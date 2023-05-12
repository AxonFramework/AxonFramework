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
1. Messaging-Core -> InMemory, InProcess, Configuration, Metrics, Tracing
2. Event-Messaging -> Configuration (archetypes), Metrics, Tracing
3. Command-Messaging -> Configuration (archetypes), Metrics, Tracing
4. Query-Messaging -> Configuration (archetypes), Metrics, Tracing
5. Messaging -> Configuration (archetypes), Metrics, Tracing
6. EventSourcing -> InMemory, InProcess, Configuration, Metrics, Tracing
7. Annotations
8. Testing
9. Integration Tests -> unpublished

### Extensions
> Each extension belong to its corresponding repository.

1. Spring
   i. Core
   ii. Starter
   iii. Auto-Configuration
   iv. SpringCloud
2. Legacy
   i. AF4
3. Axon Server Connector Java
4. Disruptor
5. JPA
6. JDBC
7. R2DBC
8. Mongo
9. Kafka
10. OpenTelemetry
11. MicroMeter
12. JobRnr
13. quartz
14. AMQP
15. Kotlin
16. Reactor
17. RXJava

### BOM

1. Dependency management BOM
2. Archetype poms for easy dependency inclusion