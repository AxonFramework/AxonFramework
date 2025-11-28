# Axon 5 Reference Guide Changes to Process

This document tracks which API changes need to be applied to each reference guide file during the migration from Axon 4 to Axon 5.

## API Changes Available

The following files in `axon-5/` describe the API changes:
- `api-changes.md` - Main API changes document
- `getting-rid-of-aggregatelifecycle.md` - AggregateLifecycle removal
- `getting-rid-of-messagehandlingmember.md` - MessageHandlingMember removal
- `design-principles.md` - Design principles for Axon 5
- `implementation-guidelines.md` - Implementation guidelines
- `migration-baseline.md` - Migration baseline
- `modularity.md` - Modularity changes
- `reactive-native.md` - Reactive native support
- `plan-of-attack-milestone-release.md` - Release plan

---

## ROOT Module

### modules/ROOT/pages/index.adoc
**Changes to apply:**
- Update overview to reflect JDK 21 requirement
- Update dependency information (Spring Boot 3, Spring 6, Jakarta Persistence)
- Mention major architectural shifts (async-native, reactive support, DCB)
- Update terminology from aggregates to entities where conceptually appropriate

### modules/ROOT/pages/modules.adoc
**Changes to apply:**
- Update module structure reflecting new modularity (messaging split into command/event/query modules)
- Document that Spring and JDBC moved to extensions
- Note that JPA remains part of core framework
- Update dependency coordinates and groupIds

### modules/ROOT/pages/serialization.adoc
**RENAME TO:** `conversion.adoc` or update title to "Serialization and Conversion"
**Changes to apply:**
- Replace all Serializer references with Converter
- Document MessageConverter and EventConverter types
- **Explain payload conversion at handling time**
  - Show how converters enable handlers to receive payloads in their preferred representation
  - Demonstrate that same message can be converted to different types for different handlers
  - Explain how this reduces upcaster needs
- Update default from XStream to Jackson
- Remove XStream documentation, add note about XML support via JacksonConverter with XmlMapper
- Update MessageTypeResolver (replaces RevisionResolver)
- Replace @Revision with @Command/@Event/@Query annotations

### modules/ROOT/pages/spring-boot-integration.adoc
**Changes to apply:**
- Replace @Aggregate with @EventSourced annotation
- Update ApplicationConfigurer approach (MessagingConfigurer, ModellingConfigurer, EventSourcingConfigurer)
- Document new auto-configuration for entities and handlers
- Update interceptor configuration (no longer via component interfaces)

### modules/ROOT/pages/upgrading-to-4-7.adoc
**REMOVE:** This page should be removed entirely
**Note:** A separate Axon 4 to 5 migration guide will be added as a separate task

### modules/ROOT/pages/known-issues-and-workarounds.adoc
**Changes to apply:**
- Review and update for Axon 5 known issues
- Remove Axon 4 specific issues

---

## Axon Framework Commands Module

### modules/axon-framework-commands/pages/index.adoc
**Changes to apply:**
- Emphasize command-centric approach (shift from modeling-centric in Axon 4)
- Update terminology from aggregates to entities where discussing modeling patterns
- Introduce concept of stateful command handling components
- Document that entities are implementation patterns, not core framework concepts
- Clarify that command handling is the primary focus, with entities as one pattern option

### modules/axon-framework-commands/pages/command-dispatchers.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- Updated CommandBus API (async-native, optional ProcessingContext parameter)
- Documented CommandGateway changes (CommandResult return type, no CommandCallback)
- Introduced CommandDispatcher for in-handler command dispatch (automatically uses current ProcessingContext)
- Documented ProcessingContext usage:
  - Optional when dispatching: provide when available (dispatching from handler)
  - Pass null or use variant without ProcessingContext from HTTP endpoints
  - CommandDispatcher automatically provides it when used in handlers
- Removed AsynchronousCommandBus and DisruptorCommandBus references
- Added comprehensive code examples showing all three dispatch mechanisms
- Added section explaining ProcessingContext usage in command dispatching
- **Added command routing section:**
  - Explained routing key concept and why it matters for consistency
  - Documented @Command annotation's routingKey attribute (replaces @TargetAggregateIdentifier from Axon 4)
  - Documented custom RoutingStrategy configuration
  - Covered built-in strategies (AnnotationRoutingStrategy, MetadataRoutingStrategy)
  - Added examples for Configuration API and Spring Boot configuration
  - Added custom RoutingStrategy implementation example

### modules/axon-framework-commands/pages/command-handlers.adoc
**Changes to apply:**
- Document ProcessingContext injection in handlers (mandatory - always created by Axon)
- Show ProcessingContext must be passed to all components during handling
- **Document message type concept**: Java class is no longer message identity
  - Explain MessageType (QualifiedName + version) as message identifier
  - Show handlers declare type via @Command annotation or parameter type
  - Explain payload conversion at handling time (different handlers can receive different representations)
  - Show how this reduces need for upcasters
- Update to use EventAppender instead of AggregateLifecycle.apply
- Show creational vs instance command handlers
- Document declarative and reflection-based handler registration
- Update parameter injection examples

### modules/axon-framework-commands/pages/configuration.adoc
**Changes to apply:**
- Replace Configurer with ApplicationConfigurer hierarchy
- Document MessagingConfigurer, ModellingConfigurer approach
- Update component registration using ComponentBuilder
- Document decorator pattern for component customization
- Update interceptor registration approach

### modules/axon-framework-commands/pages/infrastructure.adoc
**Changes to apply:**
- Document Repository changes (async-native, ManagedEntity)
- Update CommandBus implementations
- Document Command Handling Component registration patterns

### modules/axon-framework-commands/pages/modeling/aggregate.adoc
**RENAME TO:** `event-sourced-entity.adoc`
**Changes to apply:**
- Replace aggregate terminology with entity throughout
- Document EntityMetamodel and declarative modeling
- Show immutable entity support (records, data classes)
- Update entity constructor patterns (no-arg not required)
- Document @EntityCreator annotation
- Replace AggregateLifecycle.apply with EventAppender.append
- Show creational command handlers (static methods)
- Document @EventSourced annotation

### modules/axon-framework-commands/pages/modeling/aggregate-creation-from-another-aggregate.adoc
**RENAME TO:** `entity-creation-from-another-entity.adoc`
**Changes to apply:**
- Update terminology from aggregate to entity
- Update code examples to use EventAppender
- Document new entity creation patterns

### modules/axon-framework-commands/pages/modeling/aggregate-polymorphism.adoc
**RENAME TO:** `entity-polymorphism.adoc`
**Changes to apply:**
- Update terminology from aggregate to entity
- Document polymorphic entity metamodel support
- Update code examples

### modules/axon-framework-commands/pages/modeling/multi-entity-aggregates.adoc
**RENAME TO:** `entity-hierarchies.adoc` or `child-entities.adoc`
**Changes to apply:**
- Update terminology from aggregate to entity
- Document @EntityMember changes (multiple children of same type, custom routing)
- Document event routing changes (no longer forwards to all by default)
- Update child entity patterns

### modules/axon-framework-commands/pages/modeling/state-stored-aggregates.adoc
**RENAME TO:** `state-stored-entities.adoc`
**Changes to apply:**
- Update terminology from aggregate to entity
- Document state-stored entity configuration
- Update Repository usage patterns

---

## Events Module

### modules/events/pages/index.adoc
**Changes to apply:**
- Introduce MessageStream concept
- Document EventBus to EventSink rename
- Overview of async-native event handling
- Introduce Dynamic Consistency Boundary (DCB) concept

### modules/events/pages/event-dispatchers.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- Introduced EventSink concept with clear explanation of EventBus → EventSink rename
- Explained EventSink as the publishing/sending side of event distribution
- Clarified EventSink can distribute to internal handlers, processors, stores, AND external systems (Kafka, RabbitMQ)
- Documented EventAppender for entity event dispatching (replaces AggregateLifecycle.apply)
- Updated terminology from Aggregate to Entity throughout
- Documented ProcessingContext usage:
  - Optional when dispatching: provide when available (from within handler) for correlation
  - Pass null or use method without ProcessingContext when dispatching from HTTP endpoints/outside handlers
  - Added examples for both patterns
- Updated to async API (CompletableFuture<Void> returns)
- Documented EventGateway with comprehensive examples:
  - Publishing without ProcessingContext (from HTTP endpoints)
  - Publishing with ProcessingContext (from event handlers)
- Added direct EventSink usage section for advanced scenarios
- Added Configuration section (Spring Boot and Configuration API examples)
- Added Summary table comparing EventAppender, EventGateway, and EventSink
- Verified all xrefs point to existing files (using current filenames until files are renamed)

### modules/events/pages/event-handlers.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- **Documented handler resolution changes**: All matching event handlers are now invoked (not just one per instance)
  - Provided clear example showing multiple handlers for same event
- **Documented ProcessingContext injection**:
  - Explained that ProcessingContext is mandatory and always created by framework on handling side
  - Showed how to inject ProcessingContext as parameter
  - Explained passing ProcessingContext to components for correlation
  - Added examples of accessing metadata and registering lifecycle callbacks
- **Documented message type concept extensively**:
  - Explained MessageType (QualifiedName + version) as event identity, not Java class
  - Documented @Event annotation with namespace, name, and version attributes
  - Showed three ways handlers declare event types: by parameter type, explicit eventName, and payloadType conversion
  - Provided comprehensive example showing same event to three different handlers with different payload representations
  - Explained payload conversion at handling time and how it reduces need for upcasters
  - Clarified when upcasters are still needed (structural changes in event store)
- **Documented parameter injection**:
  - EventAppender (with IMPORTANT note about parameter injection, not field injection)
  - CommandDispatcher (with ProcessingContext-awareness)
  - ProcessingContext
  - Metadata and @MetadataValue
  - Aggregate-specific parameters (@SourceId, @SequenceNumber, @AggregateType)
  - Event processor parameters (TrackingToken, ReplayStatus, @ReplayContext)
  - Complete example showing all parameter types together
- **Documented event handler return values**:
  - Void return type
  - CompletableFuture for async processing
  - MessageStream for advanced scenarios
- **Documented registration**:
  - Spring Boot auto-configuration examples
  - Configuration API examples
- **Added comprehensive best practices section**:
  - Keep handlers focused
  - Use ProcessingContext for correlation
  - Inject dispatchers as parameters, not fields
  - Handle events idempotently
  - Use result objects instead of exceptions for expected failures
- Cross-referenced processing-context.adoc, supported-parameters-annotated-handlers.adoc, and exception-handling.adoc
- All code examples verified against actual Axon 5 APIs

### modules/events/pages/event-versioning.adoc
**Status:** ✅ COMPLETED

**Changes applied:**
- **Added new "Event versioning approach" section** explaining payload conversion as the primary versioning mechanism:
  - Documented payload conversion at handling time with comprehensive examples
  - Explained when payload conversion is sufficient (adding fields, removing fields, renaming, type changes, restructuring)
  - Explained when upcasters are needed (splitting events, merging events, complex transformations, changing event identity)
  - Added example showing EnrichedOrderPlacedEvent receiving additional computed fields from OrderPlacedEvent
  - Referenced xref:ROOT:conversion.adoc for complete conversion details
- **Added IMPORTANT note** that upcasters are not yet available in Axon 5.0 but will be reintroduced in future releases
- **Updated "Event Upcasting" section**:
  - Revised introduction to explain upcasters are for scenarios where payload conversion isn't sufficient
  - Added cross-references to payload conversion sections
  - Added NOTE explaining when to use upcasters vs payload conversion
- **Replaced @Revision with @Event annotation** throughout all examples:
  - Updated ComplaintEvent examples to use @Event(name = "Complaint", version = "1.0")
  - Added "Event version identification" subsection showing @Event annotation with namespace, name, and version fields
- **Updated configuration examples**:
  - Replaced Configurer with MessagingConfigurer in Configuration API example
  - Updated import statement to org.axonframework.messaging.core.configuration.MessagingConfigurer
- **Terminology updates**:
  - Changed "Serializer" to "Converter" in serialization formats note
  - Changed "aggregate" to "entity" in snapshot upcasting section
  - Changed "Streaming Event Processor" to "streaming event processor" (lowercase)
  - Updated SnapshotFilter warning to reference @Event annotation instead of @Revision
  - Updated RevisionSnapshotFilter reference to VersionSnapshotFilter
- **Structured document for clarity**:
  - Payload conversion approach comes first (lines 8-97)
  - Event upcasting section comes second with clear note about future availability (lines 99-481)
  - Custom event transformation section at the end (lines 483-644)
- **Added "Custom event transformation" section**:
  - Documented practical workaround for event transformation by wrapping EventConverter
  - Provided complete example of TransformingEventConverter that converts to JsonNode intermediate representation
  - Showed how to check message type and version using event.type().qualifiedName().name() and event.type().version()
  - Demonstrated transformation logic (adding fields, renaming fields)
  - Included both Configuration API and Spring Boot registration examples
  - Added NOTE explaining this is a bridge solution until full upcasting infrastructure is available
- All code examples verified against Axon 5 APIs:
  - EventConverter.convertPayload() and convertEvent() methods verified
  - MessageType.qualifiedName().name() and version() methods verified
  - EventMessage.type() method verified
  - Converter.convert() method usage verified
  - DelegatingEventConverter and JacksonConverter verified
- Note: Upcaster APIs (SimpleSerializedType, IntermediateEventRepresentation, SingleEventUpcaster, etc.) remain documented but noted as not yet available in 5.0

### modules/events/pages/infrastructure.adoc
**Status:** ✅ COMPLETED

**Changes applied:**
- **Added Dynamic Consistency Boundaries (DCB) documentation section**:
  - Documented event tags with example: `tags.with("customerId", customerId).with("region", "EU")`
  - Explained EventCriteria for querying events by tags
  - Documented ConsistencyMarker for preventing write conflicts
  - Added NOTE comparing DCB vs traditional aggregate-based storage
  - Emphasized that DCB mainly impacts write side (command handling), not read side
- **Removed JDBC Event Storage Engine section**: Replaced with NOTE directing users to external extension
- **Updated JPA Event Storage Engine section**:
  - Documented entity classes: `AggregateEventEntry` and `AggregateSnapshotEntry`
  - Updated persistence.xml example to show correct entity classes
  - Added NOTE clarifying that DCB is not yet supported for JPA-based storage (Axon Server only)
  - Updated concurrency note to explain aggregate identifier + sequence number constraints
  - Fixed PersistenceExceptionTranslator → PersistenceExceptionResolver
- **Updated "Influencing serialization" section to "Configuring event conversion"**:
  - Changed from XStreamSerializer to JacksonConverter as default
  - Updated terminology from Serializer to Converter throughout
  - Added reference to xref:ROOT:conversion.adoc[Conversion]
  - Provided custom ObjectMapper configuration example
  - Changed "Serializing events vs 'the others'" to "Converting events vs other messages"
- **Updated all configuration examples to use MessagingConfigurer**:
  - Axon Server configuration: `MessagingConfigurer.create()` instead of `DefaultConfigurer.defaultConfiguration()`
  - JPA configuration: `configurer.registerEventSink()` with EmbeddedEventStore
  - Mongo configuration: Updated to use MessagingConfigurer and registerEventSink
  - InMemory configuration: Updated to use MessagingConfigurer and registerEventSink
  - Converter configuration: Shows EventConverter registration with DelegatingEventConverter
- **Updated Spring Boot auto-configuration note**: Removed JDBC references, kept only JPA
- **Replaced DomainEventStream reference**: Changed to "transaction" in MongoEventStorageEngine description
- **Updated EventStore introduction**: Changed "from aggregates" to "from entities", "based on aggregate identifier" to "based on given criteria"
- All imports updated to use correct Axon 5 packages

### modules/events/pages/event-processors/index.adoc
**Changes to apply:**
- Remove ProcessingGroup layer documentation
- Document direct Event Handler to Event Processor assignment
- Update configuration API (EventProcessorModule)
- Remove EventProcessingModule/Configurer references

### modules/events/pages/event-processors/dead-letter-queue.adoc
**Changes to apply:**
- Update stored format changes (column renames)
- Document MessageType usage in DLQ entries
- Update code examples

### modules/events/pages/event-processors/streaming.adoc
**CRITICAL CHANGES:**
**Changes to apply:**
- Remove TrackingEventProcessor documentation entirely
- Document PooledStreamingEventProcessor as default and recommended
- Explain threading model differences and benefits
- Document SequencingPolicy configuration (now more important with DCB)
- Document @SequencingPolicy annotation
- Update configuration examples
- Document StreamableEventSource (replaces StreamableMessageSource)
- Update token management (now async with CompletableFuture)

### modules/events/pages/event-processors/subscribing.adoc
**Changes to apply:**
- Update configuration examples
- Document async nature of processing
- Update code examples with ProcessingContext

---

## Queries Module

### modules/queries/pages/index.adoc
**Changes to apply:**
- Remove scatter-gather query documentation entirely
- Document MessageStream return types
- Overview of async-native querying
- Introduce QueryDispatcher concept

### modules/queries/pages/configuration.adoc
**Changes to apply:**
- Update QueryBus/QueryGateway configuration
- Document QueryHandlingComponent registration
- Update interceptor registration patterns

### modules/queries/pages/implementations.adoc
**Changes to apply:**
- Update QueryBus implementations
- Document subscription query infrastructure changes

### modules/queries/pages/query-dispatchers.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- Documented removal of scatter-gather query support with link to GitHub issue for feedback
- Documented point-to-point query routing behavior when multiple handlers exist
- Updated QueryGateway API documentation:
  - No more ResponseTypes - use Class types directly
  - query() for single result, queryMany() for list results
  - All methods return CompletableFuture (async-native)
  - Optional ProcessingContext parameter documented
- Documented ProcessingContext usage:
  - Provide when dispatching from within message handlers (for correlation data)
  - Omit or pass null when dispatching from HTTP endpoints/outside handlers
  - Comprehensive examples for both patterns
- Updated subscription query documentation:
  - Returns single Publisher that combines initial results and updates
  - No need to explicitly close subscriptions - cancelling Publisher automatically closes query
  - QueryUpdateEmitter must be injected as handler parameter (not field)
  - QueryUpdateEmitter is ProcessingContext-aware
  - Added clear WRONG vs CORRECT examples
  - Updated code examples to use Publisher API with Flux.from() and dispose()
- Updated streaming query documentation:
  - Documented Publisher-based streaming
  - Native Flux support for fine-grained control
  - Back-pressure, cancellation, and error handling
  - Transaction leaking concerns
- Added Configuration section (Spring Boot and Configuration API examples)
- Added Summary table comparing query types
- **Note:** QueryDispatcher does not exist in Axon 5 - QueryGateway with ProcessingContext is the recommended approach
- Verified all xrefs point to existing files
- Style guide compliance verified (heading capitalization, API/HTTP capitalization)

### modules/queries/pages/query-handlers.adoc
**Changes to apply:**
- Document ProcessingContext injection (mandatory - always available in handlers)
- Show ProcessingContext must be passed to components during handling
- **Document message type concept**: Java class is no longer message identity
  - Explain MessageType (QualifiedName + version) as message identifier
  - Show handlers declare type via @Query annotation or parameter type
  - Explain payload conversion at handling time
- Update MessageStream return types
- Document QueryHandlerName (combines query name and response name)
- Update parameter injection examples
- Remove ResponseType from examples
- Document query name resolution (@Query annotation, qualified names)

---

## Sagas Module

**CRITICAL:** Sagas are NOT available in Axon 5

### modules/sagas/pages/index.adoc
**COMPLETE REWRITE:**
**Changes to apply:**
- Add prominent notice that **Sagas are not available in Axon 5**
- Explain that Sagas will be reintroduced in a later version in a different format
- **Document alternatives for Axon 5:**
  1. **Stateful event handlers** - Event handlers that maintain state
  2. **Event handlers with custom state storage** - Store state in appropriate storage (database, cache, etc.)
  3. **Replace deadlines with database checks** - Instead of deadline scheduling, use regular checks on database state
- Provide migration patterns from Axon 4 Sagas to these alternatives
- Show practical examples of each alternative approach
- Explain when to use which alternative

### modules/sagas/pages/associations.adoc
**REWRITE OR REMOVE:**
**Changes to apply:**
- Either remove this page or rewrite to explain association patterns in alternatives
- If kept: Show how to implement association logic in custom event handlers
- Document querying state by association keys in custom storage

### modules/sagas/pages/implementation.adoc
**REWRITE:**
**Changes to apply:**
- Rewrite to show implementation patterns for alternatives
- **Stateful event handler pattern:**
  - How to maintain state across events
  - State storage strategies
  - Loading and updating state
- **Custom storage pattern:**
  - Choosing appropriate storage
  - State persistence
  - Querying state
- **Database check pattern:**
  - Scheduling regular checks (e.g., with scheduled tasks)
  - Checking state conditions
  - Triggering actions based on state

### modules/sagas/pages/infrastructure.adoc
**REWRITE OR REMOVE:**
**Changes to apply:**
- Rewrite to show infrastructure for alternative patterns
- Document storage configuration for custom solutions
- Show integration with Spring scheduling or similar for database checks
- Or remove if not applicable to alternatives

---

## Deadlines Module

**CRITICAL:** Deadlines are NOT available in Axon 5.0

### modules/deadlines/pages/index.adoc
**COMPLETE REWRITE:**
**Changes to apply:**
- Add prominent notice that **Deadlines are not available in Axon 5.0**
- Explain that Deadlines will be introduced with a renewed API in later versions
- **Document alternatives for Axon 5.0:**
  1. **Spring @Scheduled tasks** - Use Spring's scheduling for periodic checks
  2. **Database-based scheduling** - Store deadline/timeout information, check periodically
  3. **External scheduling frameworks** - Quartz, JobRunr, or similar job scheduling systems
- Provide migration patterns from Axon 4 Deadlines to these alternatives
- Show practical examples of each alternative approach
- Explain when to use which alternative
- Note: Event Scheduling also not available in current form

### modules/deadlines/pages/deadline-managers.adoc
**REWRITE OR REMOVE:**
**Changes to apply:**
- Rewrite to show alternative scheduling patterns or remove entirely
- If kept: Show implementation patterns for alternatives
- Document scheduled task configuration
- Show database-based deadline checking patterns

### modules/deadlines/pages/event-schedulers.adoc
**REWRITE OR REMOVE:**
**Changes to apply:**
- Note that event scheduling is not available in current form
- Rewrite to show alternatives for scheduling future actions
- Document using scheduled tasks to trigger events
- Or remove if not applicable to alternatives

---

## Messaging Concepts Module

### modules/messaging-concepts/pages/index.adoc
**Changes to apply:**
- Update to reflect async-native messaging
- Introduce MessageStream concept
- Document reactive programming support
- Update terminology for ProcessingContext

### modules/messaging-concepts/pages/anatomy-message.adoc
**Changes to apply:**
- **Document MessageType and QualifiedName as primary message identity**
  - Explain fundamental shift: Java class is no longer message identity
  - MessageType = QualifiedName + version
  - Decouples message identity from Java representation
  - Enables payload conversion at handling time
- Update Metadata to String-only values
- Remove factory method references (GenericMessage.asMessage)
- Document message method renames (getIdentifier to identifier, etc.)
- Update serialization to conversion flow (payloadAs, withConvertedPayload)
- Document @Command, @Event, @Query annotations for message types
- Explain how this reduces need for upcasters

### modules/messaging-concepts/pages/exception-handling.adoc
**Changes to apply:**
- Update exception handling patterns with ProcessingContext
- Remove RollbackConfiguration references
- Document error handling with ProcessingLifecycle (onError, whenComplete, doFinally)
- Update interceptor-based error handling

### modules/messaging-concepts/pages/message-correlation.adoc
**Changes to apply:**
- Update correlation data handling (traceId, correlationId, causationId terminology changes)
- Document MessageOriginProvider changes
- Update metadata propagation with ProcessingContext
- Document correlation in distributed scenarios

### modules/messaging-concepts/pages/message-intercepting.adoc
**Changes to apply:**
- Update interceptor interfaces (ProcessingContext parameter, chain parameter)
- Document MessageStream return types from interceptors
- Update dispatch interceptor result handling capability
- Document interceptor registration via ApplicationConfigurer (not component interfaces)
- Remove MessageDispatchInterceptorSupport/MessageHandlerInterceptorSupport
- Update Spring Boot auto-configuration for interceptors
- Show before-and-after interception patterns

### modules/messaging-concepts/pages/supported-parameters-annotated-handlers.adoc
**Changes to apply:**
- Add ProcessingContext as injectable parameter
- Add EventAppender, CommandDispatcher, QueryDispatcher parameters
- Remove UnitOfWork parameter
- Update Message method names in examples
- Document Converter parameter options

### modules/messaging-concepts/pages/timeouts.adoc
**Changes to apply:**
- Update timeout handling with async APIs
- Document CompletableFuture timeout patterns
- **Highlight framework support for async processing:**
  - Many libraries (especially Spring) have excellent async support
  - Can return CompletableFuture, Mono, or Flux from controllers/handlers
  - Framework handles async processing and timeout management automatically
  - Show Spring WebFlux/WebMVC async controller examples
  - Explain how framework timeout configuration integrates with Axon's async operations
- Update examples showing both manual timeout handling and framework-managed approaches

### modules/messaging-concepts/pages/unit-of-work.adoc
**MAJOR REWRITE:**
**Changes to apply:**
- Replace UnitOfWork with ProcessingContext and ProcessingLifecycle
- Remove CurrentUnitOfWork references (no longer exists)
- Document phase changes (pre/post-invocation, prepare-commit, commit, after-commit)
- Document resource management via ProcessingContext
- Document lifecycle actions (onError, whenComplete, doFinally)
- Remove nesting functionality
- Document that UoW no longer revolves around a Message
- Update all code examples
- Document async-native flow with CompletableFuture

---

## Monitoring Module

### modules/monitoring/pages/index.adoc
**Changes to apply:**
- Update overview for monitoring async-native architecture
- Document metrics for new components (PooledStreamingEventProcessor)
- Update module location (moved to extensions)

### modules/monitoring/pages/health.adoc
**Changes to apply:**
- Update health check examples
- Document health indicators for new processor types
- Update configuration approach

### modules/monitoring/pages/message-tracking.adoc
**Changes to apply:**
- Update correlation tracking (traceId, correlationId, causationId changes)
- Document ProcessingContext in tracking
- Update examples

### modules/monitoring/pages/metrics.adoc
**Changes to apply:**
- Update metrics for new event processor types
- Document metrics module location (extensions)
- Update Micrometer and Dropwizard integration
- Update configuration examples

### modules/monitoring/pages/processors.adoc
**Changes to apply:**
- Remove TrackingEventProcessor monitoring
- Document PooledStreamingEventProcessor monitoring
- Update processor status and control APIs
- Update AxonIQ Console integration examples

### modules/monitoring/pages/tracing.adoc
**Changes to apply:**
- Update tracing module location (extensions/tracing/opentelemetry)
- Document OpenTelemetry integration
- Update span creation for new APIs
- Document tracing with ProcessingContext
- Update distributed tracing examples

---

## Testing Module

**CRITICAL:** This entire module requires complete rewrite

### modules/testing/pages/index.adoc
**COMPLETE REWRITE:**
**Changes to apply:**
- Replace AggregateTestFixture with AxonTestFixture
- Replace SagaTestFixture with AxonTestFixture
- Document that fixture now based on ApplicationConfigurer
- Explain benefits (no duplicate configuration, integration testing support)
- Document given-when-then style with new fixture
- Note that legacy fixtures available but deprecated
- Update all overview examples

### modules/testing/pages/commands-events.adoc
**RENAME TO:** `testing-with-axontestfixture.adoc` or similar
**COMPLETE REWRITE:**
**Changes to apply:**
- Remove all AggregateTestFixture references
- Document AxonTestFixture creation from ApplicationConfigurer
- Show testing entities (not aggregates)
- Show testing event handlers
- Show testing command handlers
- Document given phase (any message type)
- Document when phase (any message type)
- Document then phase (any message type)
- Update all code examples
- Document testing with declarative configuration
- Show integration testing capabilities (upcasters, snapshots, DLQ)

### modules/testing/pages/sagas-1.adoc
**REMOVE OR REWRITE:**
**Changes to apply:**
- Remove SagaTestFixture references entirely (Sagas not available in Axon 5)
- Rewrite to show testing alternatives:
  - Testing stateful event handlers with AxonTestFixture
  - Testing event handlers with custom state storage
  - Testing scheduled database checks
- Update all examples

---

## Tuning Module

### modules/tuning/pages/index.adoc
**Changes to apply:**
- Update performance tuning overview for async-native architecture
- Document PooledStreamingEventProcessor tuning
- Update terminology from aggregates to entities

### modules/tuning/pages/command-processing.adoc
**Changes to apply:**
- Remove DisruptorCommandBus tuning (removed from framework)
- Update entity loading and caching strategies
- Document Repository tuning
- Update async command processing patterns
- Update terminology from aggregate to entity

### modules/tuning/pages/event-processing.adoc
**Changes to apply:**
- Remove TrackingEventProcessor tuning entirely
- Document PooledStreamingEventProcessor tuning (thread pools, batch sizes)
- Document SequencingPolicy impact on performance
- Update token store considerations
- Document DCB impact on event sourcing performance
- Update streaming optimizations

### modules/tuning/pages/event-snapshots.adoc
**Changes to apply:**
- Update snapshotting for entities (not aggregates)
- Document snapshot triggering and creation changes
- Update converter/serialization for snapshots
- Document snapshot validation in test fixtures
- Update snapshot format considerations

### modules/tuning/pages/rdbms-tuning.adoc
**Changes to apply:**
- Update table schemas (aggregate_event_entry, etc.) for JPA
- Document global index sequence tuning
- Update JPA configuration (remains in core framework)
- Remove JDBC configuration references (moved to external extension)
- Document aggregate-based vs DCB-based storage performance
- Update connection pool recommendations

