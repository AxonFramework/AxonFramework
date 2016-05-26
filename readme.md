## Axon Framework

A CQRS Framework for Scalable, High-Performance Java Applications

## Introduction

For more information, visit our website: [http://www.axonframework.org](http://www.axonframework.org).

## Issue tracker

If you're looking for the issue tracker, visit [http://issues.axonframework.org](http://issues.axonframework.org).

## Changelog

### 3.0.0

- The Unit of Work was refactored to only coordinate the processes involved in the handling of a message. In all other
  ways it is domain agnostic now, e.g. it does not know what aggregates or events are anymore.
- Common components related to messaging have been moved to org.axonframework.messaging.
  Note that this may cause issues in applications that store serialized versions of MetaData and Message instances,
  e.g. in applications that use the QuartzEventScheduler.
