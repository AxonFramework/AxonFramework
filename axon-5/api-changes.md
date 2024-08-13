Version and Dependency Compatibility
====================================

* Axon Framework is no longer based on JDK 8, but on JDK 21 instead.
* Spring Boot 2 is no longer supported. You should upgrade to Spring Boot 3 or higher.
* Spring Framework 5 is no longer supported. You should upgrade to Spring Framework 6 or higher.
* Javax Persistence is completely replaced for Jakarta Persistence. This means the majority of `javax` reference no
  longer apply.

Major API Changes
=================

* **All** code marked as `@Deprecated` in Axon Framework 4 is removed entirely. Each deprecation contains the
  recommended resolution path in the JavaDoc. It is strongly recommended to (1) upgrade to the latest Axon Framework 4
  version, (2) adjust any deprecations from Axon Framework you are using as recommended, and then (3) to make the change
  towards Axon Framework 5.
* We no longer support message handler annotated constructors. For example, the constructor of an aggregate can no
  longer contain the `@CommandHandler` annotation. Instead, the `@CreationPolicy` should be used.

Other API changes
=================

TODO

Moved / Remove Classes
======================

### Moved / Renamed

| Axon 4                                                       | Axon 5                                                       |
|--------------------------------------------------------------|--------------------------------------------------------------|
| org.axonframework.common.caching.EhCache3Adapter             | org.axonframework.common.caching.EhCacheAdapter              |
| org.axonframework.eventsourcing.MultiStreamableMessageSource | org.axonframework.eventhandling.MultiStreamableMessageSource |

### Removed

| Class                                                           | Why                                                                                       |
|-----------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| org.axonframework.messaging.unitofwork.AbstractUnitOfWork       | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
| org.axonframework.messaging.unitofwork.BatchingUnitOfWork       | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
| org.axonframework.messaging.unitofwork.CurrentUnitOfWork        | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
| org.axonframework.messaging.unitofwork.DefaultUnitOfWork        | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
| org.axonframework.messaging.unitofwork.ExecutionResult          | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
| org.axonframework.messaging.unitofwork.MessageProcessingContext | Made obsolete through the rewrite of the `UnitOfWork` (see [Unit of Work](#unit-of-work)) |
