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
**Changes to apply:**
- Document handler resolution changes (all matching handlers invoked)
- Document ProcessingContext injection (mandatory - always available in handlers)
- Show ProcessingContext must be passed to components during handling
- **Document message type concept**: Java class is no longer message identity
  - Explain MessageType (QualifiedName + version) as message identifier
  - Show handlers declare type via @Event annotation or parameter type
  - Explain payload conversion at handling time (different handlers can receive different representations)
  - Show practical example: same event to multiple handlers with different payload types
  - Explain how this reduces need for upcasters
- Document MessageStream return types
- Update parameter injection (EventAppender, QueryDispatcher, CommandDispatcher)
- Document event name resolution (@Event annotation, qualified names)
- Update interceptor patterns

### modules/events/pages/event-versioning.adoc
**Changes to apply:**
- **Document fundamental shift in versioning approach**
  - Explain that payload conversion at handling time reduces need for upcasters
  - Upcasters still needed for structural changes, not simple type conversions
  - Show when to use upcasters vs when conversion suffices
- Update upcasting approach (now part of Converter, not separate)
- Document MessageType version field
- Replace @Revision with @Event annotation
- Update conversion examples
- Show practical examples of version handling without upcasters

### modules/events/pages/infrastructure.adoc
**Changes to apply:**
- Document EventStore DCB changes (tags, types, EventCriteria)
- Update EventStorageEngine API (async, conditions-based)
- Document EventStoreTransaction for sourcing and appending
- Update JPA storage engine (aggregate-based vs DCB-based, still part of core)
- Remove JDBC storage engine references (moved to external extension)
- Document Axon Server storage engine options
- Update stored format changes (table and column renames for JPA)
- Remove DomainEventStream references (replaced by MessageStream)

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
**Status:** ✅ COMPLETED
**Changes applied:**
- Updated to reflect async-native messaging with dedicated section
- Introduced MessageStream concept with factory methods and usage examples
- Documented reactive programming support (imperative and reactive styles)
- Updated terminology for ProcessingContext with overview section
- Fixed MessageType vs class name issue - now correctly states MessageType identifies message structure
- Updated aggregate → entity terminology throughout
- Removed reference to GenericEventMessage.asEventMessage() (removed in Axon 5)
- Added "Understanding messages" section explaining message type, payload, metadata, and identifier
- Reorganized content for better flow
- Cross-referenced anatomy-message.adoc for detailed explanations
- Updated AxonIQ Platform references to AxonIQ Console

### modules/messaging-concepts/pages/anatomy-message.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- Documented MessageType and QualifiedName as primary message identity
  - Explained fundamental shift: Java class is no longer message identity
  - MessageType = QualifiedName + version
  - Decouples message identity from Java representation
  - Enables payload conversion at handling time
- Updated Metadata to String-only values (Map<String, String>)
- Removed factory method references (GenericMessage.asMessage no longer exists)
- Documented message method renames (identifier(), payload(), metadata(), type(), etc.)
- Updated serialization to conversion flow (payloadAs, withConvertedPayload)
- Documented @Command, @Event, @Query annotations for defining message types
  - Explained namespace, name, and version parameters
  - Showed default behavior (uses FQCN if not annotated)
- Explained how payload conversion reduces need for upcasters
- Added comprehensive code examples throughout
- Reorganized content for better flow and understanding

### modules/messaging-concepts/pages/exception-handling.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- Added major section on "Results vs exceptions" philosophy:
  - Explained that exceptions lose value in distributed systems
  - Emphasized using result objects for expected failures
  - Reserved exceptions for truly exceptional circumstances
  - Provided clear guidance on when to use each approach
  - Added practical code examples for both patterns
- Updated exception handling in distributed scenarios
- Documented HandlerExecutionException details map for structured error information
- Added TIP encouraging consideration of results vs exceptions
- Kept existing content about HandlerExecutionException and @ExceptionHandler
- Simplified interceptor-based error handling section
- Added reference to unit-of-work.adoc for ProcessingContext lifecycle details
- No RollbackConfiguration references found (already removed)
- Focused content on exception handling philosophy and practices rather than lifecycle callbacks

### modules/messaging-concepts/pages/message-correlation.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- Updated correlation terminology to align with industry standards:
  - Old `traceId` → New `correlationId` (root/original message)
  - Old `correlationId` → New `causationId` (immediate parent message)
  - Added IMPORTANT callout explaining the terminology change
- Added "Understanding correlation" section with visual example showing message chain
- Documented updated MessageOriginProvider with correct correlationId and causationId
- Updated metadata type from Map<String, Object> to Map<String, String>
- Updated references from UnitOfWork to ProcessingContext
- Added comprehensive code examples showing correlation flow through message chains
- Added "Correlation in distributed scenarios" section:
  - Explained use cases (tracing, logging, debugging, monitoring)
  - Connected to observability tools (Zipkin, Jaeger, OpenTelemetry)
- Updated all code examples to use correct return types and method signatures
- Clarified that correlation data providers should not return null

### modules/messaging-concepts/pages/message-intercepting.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- Complete rewrite with correct Axon 5 interceptor API:
  - MessageDispatchInterceptor: interceptOnDispatch(message, context, chain) returns MessageStream
  - MessageHandlerInterceptor: interceptOnHandle(message, context, chain) returns MessageStream
- Updated chain types: MessageDispatchInterceptorChain, MessageHandlerInterceptorChain
- Documented ProcessingContext parameter throughout (nullable for dispatch interceptors)
- Showed MessageStream return types in all examples
- Updated all code examples to use new API signatures
- Removed UnitOfWork references, replaced with ProcessingContext
- Documented using ProcessingContext for lifecycle callbacks (whenComplete, onError)
- Updated configuration examples to use MessagingConfigurer
- Kept @ExceptionHandler content (relatively unchanged)
- Showed command, event, and query interceptor examples for both dispatch and handler types
- Updated structural validation section
- Maintained annotated @MessageHandlerInterceptor content with correct patterns

### modules/messaging-concepts/pages/supported-parameters-annotated-handlers.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- Replaced all UnitOfWork references with ProcessingContext
- Added ProcessingContext as injectable parameter in all handler types
- Added EventAppender parameter (available in command and event handlers)
- Added CommandDispatcher parameter (available in all handler types)
- Added QueryUpdateEmitter parameter (available in query handlers)
- Updated InterceptorChain to MessageHandlerInterceptorChain
- Added @AggregateType parameter (available in all handler types)
- Added @ReplayContext parameter (available in event handlers during replay)
- Removed DeadLetter parameter (not verified in Axon 5 implementation)
- Removed ScopeDescriptor reference (moved to legacy/stash, deadlines not available in 5.0)
- Fixed typo: Changed "CommandMessage" to "EventMessage" in event handler description
- Updated all message method name descriptions to current Axon 5 APIs
- Clarified when parameters are available (for example, @SequenceNumber and @SourceId only for aggregate-sourced events)
- Verified all parameter types against actual ParameterResolverFactory implementations in codebase
- Added comprehensive code examples section demonstrating:
  - ProcessingContext usage with cleanup actions
  - EventAppender for publishing follow-up events
  - Event handler with metadata and aggregate information
  - QueryUpdateEmitter for subscription queries
  - CommandDispatcher for command orchestration
  - ReplayContext handling during event replay
- Updated language throughout to be clearer and more concise

### modules/messaging-concepts/pages/timeouts.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- Updated introduction to emphasize async-native design and ProcessingContext
- Replaced all UnitOfWork references with ProcessingContext
- Removed @DeadlineHandler references (deadlines not available in Axon 5.0)
- Removed deadline manager configuration properties
- Updated section title from "Unit of work timeouts" to "Processing context timeouts"
- Added comprehensive "Async processing and timeouts" section covering:
  - CompletableFuture timeout patterns with `.orTimeout()` and `.exceptionally()`
  - Framework-managed timeouts with Spring WebMVC (`@Async`)
  - Spring WebFlux reactive timeout examples with `Mono.timeout()`
  - Multiple timeout strategies:
    - Fail fast for user-facing operations
    - Long-running background operations
    - Retry with exponential backoff
  - Best practices for choosing appropriate timeouts
- Added practical code examples demonstrating:
  - Application-level timeout handling with CompletableFuture
  - Spring Boot automatic async timeout management
  - Reactive timeout handling with WebFlux
  - Synchronous blocking with timeout
  - Background processing timeout strategies
- Updated all code examples to use CommandDispatcher instead of CommandGateway
- Emphasized separation between handler timeouts and transaction/processing timeouts
- Provided clear guidance on when to use different timeout approaches

### modules/messaging-concepts/pages/unit-of-work.adoc → processing-context.adoc
**Status:** ✅ COMPLETED (MAJOR REWRITE)
**Changes applied:**
- **Renamed file** from `unit-of-work.adoc` to `processing-context.adoc` with page alias for backward compatibility
- **Updated all xrefs** across the entire reference guide to point to `processing-context.adoc`
- **Complete rewrite** focusing on ProcessingContext as the core abstraction for managing message processing lifecycle
- **Documented ProcessingLifecycle phases** with detailed explanation:
  - Pre-Invocation (-10000)
  - Invocation (0)
  - Post-Invocation (10000)
  - Prepare-Commit (20000)
  - Commit (30000)
  - After-Commit (40000)
- **Documented phase execution rules**: sequential phase execution, parallel actions within phases, failure handling
- **Documented lifecycle registration methods**: Both async (`on*`) and sync (`runOn*`) variants for all phases
- **Documented error and completion handlers**: `onError()`, `whenComplete()`, `doFinally()`
- **Replaced all UnitOfWork references** with ProcessingContext throughout
- **Removed CurrentUnitOfWork references** - documented that thread-local access no longer exists
- **Documented type-safe resource management** using `ResourceKey<T>`:
  - `putResource()`, `getResource()`, `containsResource()`
  - `computeResourceIfAbsent()`, `updateResource()`, `removeResource()`
  - Complete resource lifecycle patterns with code examples
- **Documented accessing framework components** via `component(Class<T>)` method
- **Documented accessing current message** via `Message.fromContext(context)`
- **Removed nesting functionality** - documented that contexts are independent, no `root()` equivalent
- **Documented context propagation** when dispatching messages:
  - Using EventAppender (automatic context propagation)
  - Using CommandDispatcher (explicit context passing)
  - Creating branched contexts with `withResource()`
- **Documented transaction management** integration with ProcessingContext
- **Documented rollback configuration** options
- **Added comprehensive code examples** demonstrating:
  - Injecting ProcessingContext in handlers
  - Registering lifecycle callbacks
  - Resource management patterns
  - Transaction interceptor implementation
  - Error handling patterns
  - Context propagation patterns
- **Added advanced usage section** covering:
  - Custom phases
  - Checking lifecycle state
  - Testing with StubProcessingContext
- **Added complete migration guide** from Axon 4 UnitOfWork with side-by-side comparison table
- **Documented key architectural changes**:
  - No thread-local access
  - No nesting support
  - Async-native with CompletableFuture
  - Type-safe resource keys
  - Message as resource (context doesn't revolve around single message)
- **Added best practices section** with 7 key recommendations
- **Updated Spring Boot and Axon Configuration examples** for transaction management
- All examples use correct Axon 5 APIs and patterns

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

