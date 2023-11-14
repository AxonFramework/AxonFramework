Version and Dependency Compatibility
=====================

* Axon Framework is no longer based on JDK 8, but on JDK 21 instead.
* Spring Boot 2 is no longer supported. You should upgrade to Spring Boot 3 or higher.
* Spring Framework 5 is no longer supported. You should upgrade to Spring Framework 6 or higher.
* Javax Persistence is completely replaced for Jakarta Persistence. This means the majority of `javax` reference no longer apply.

Major API Changes
=================

TODO

Other API changes
=================

TODO

Moved / Remove Classes
======================

### Moved / Renamed

| Axon 4                                                        | Axon 5                                                       |
|---------------------------------------------------------------|--------------------------------------------------------------|
| org.axonframework.common.caching.EhCache3Adapter              | org.axonframework.common.caching.EhCacheAdapter              |
| org.axonframework.eventsourcing.MultiStreamableMessageSource  | org.axonframework.eventhandling.MultiStreamableMessageSource |

### Removed

| Class | Why  |
|-------|------|
