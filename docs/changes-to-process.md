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
**Changes to apply:**
- Update CommandBus API (async-native, optional ProcessingContext parameter)
- Document CommandGateway changes (result conversion, no CommandCallback)
- Introduce CommandDispatcher for in-handler command dispatch (automatically uses current ProcessingContext)
- Document ProcessingContext usage:
  - Optional when dispatching: provide when available (dispatching from handler)
  - Pass null or use variant without ProcessingContext from HTTP endpoints
  - CommandDispatcher automatically provides it when used in handlers
- Update subscription API (QualifiedName, CommandHandlingComponent)
- Remove AsynchronousCommandBus and DisruptorCommandBus references

### modules/axon-framework-commands/pages/command-handlers.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- Completely rewrote documentation with Axon 5 concepts and command-centric approach
- **Updated primary categorization: Stateless vs Stateful handlers (not "entity" vs "external")**
  - Stateless handlers: Don't maintain state in handler itself (may access state through services)
  - Stateful handlers: Maintain state in handler itself across invocations
  - Emphasized commands should change conceptual state (not just validate)
- **Documented two paradigms for stateful handlers:**
  - Entity-centric: Command handlers placed directly on entity class
  - Command-centric: Command handlers in separate component, entity passed as parameter (Axon auto-loads)
  - Explained when to use each approach
- **Handler types within stateful handlers:**
  - Creational handlers: Must be static methods (constructors cannot have @CommandHandler in Axon 5)
  - Instance handlers: Instance methods on entity, or methods with entity parameter
  - Static factory methods can return any value (not required to return entity instance like Axon 4)
  - Handlers return what the method returns (no automatic @EntityId extraction like Axon 4)
  - Removed @CreationPolicy (doesn't exist in Axon 5)
  - Updated all examples to use static methods instead of constructors for creational handlers
- **Documented @EntityCreator annotation:**
  - Explained distinction: @CommandHandler creates via commands, @EntityCreator reconstitutes from events
  - Pattern 1: Creation with identifier only (mutable entities, always creates instance)
  - Pattern 2: Creation from origin event (immutable entities, requires event to exist)
  - Pattern 3: Polymorphic entities with static factory method
  - Documented @InjectEntityId for disambiguating identifier from event payload
  - Added IMPORTANT callout emphasizing the two annotations serve different purposes
- **Documented ProcessingContext injection:**
  - Showed ProcessingContext as injectable parameter in handlers
  - Explained it's mandatory on handling side (automatically created by Axon)
  - Demonstrated accessing correlation data and resources
  - Added note about passing ProcessingContext to all components during handling
- **Documented message type concept extensively:**
  - Explained MessageType (QualifiedName + version) as message identifier
  - Showed @Command annotation with namespace, name, version, routingKey attributes
  - Demonstrated payload conversion at handling time with multiple handlers receiving different representations
  - Explained how this reduces need for upcasters (upcasters still needed for event store structural changes)
  - Provided clear examples of same command handled with different Java types
- **Replaced AggregateLifecycle.apply with EventAppender:**
  - Showed EventAppender as injectable parameter in all command handlers
  - Explained EventAppender is ProcessingContext-aware
  - Demonstrated single and multiple event appending
  - Added warning about not storing/reusing EventAppender across invocations
- **Documented creational vs instance command handlers:**
  - Showed @CommandHandler on constructors (creational)
  - Showed @CommandHandler on static factory methods (creational alternative)
  - Showed @CommandHandler on instance methods
  - Explained automatic return of @EntityId value from creational handlers
- **Comprehensive parameter injection documentation:**
  - Listed all injectable parameters (command payload, EventAppender, ProcessingContext, CommandDispatcher, QueryDispatcher, @MetadataValue)
  - Provided complete example with all parameter types
  - Cross-referenced supported-parameters-annotated-handlers.adoc
- **Documented entity command handlers:**
  - Used @EventSourced annotation (Spring) and @EventSourcedEntity (non-Spring)
  - Used @EntityId annotation instead of @AggregateIdentifier
  - Showed proper separation of command handlers (validation/decision) vs event sourcing handlers (state changes)
- **Documented external command handlers:**
  - Explained when to use external handlers
  - Showed EventSourcedRepository usage with ProcessingContext
  - Demonstrated manual entity loading with repository.load(id, context)
  - Showed EventAppender.forContext(context) pattern for external handlers
  - Provided examples for coordinating multiple entities
- **Added explicit handler declaration:**
  - Documented commandName attribute on @CommandHandler
  - Documented payloadType attribute for conversion
  - Explained use cases for explicit declaration
- **Configuration examples:**
  - Spring Boot auto-discovery with @EventSourced and @Component
  - Configuration API with ApplicationConfigurer, ModellingConfigurer, EventSourcingConfigurer
- Added comprehensive code examples throughout
- Added proper xrefs (command-dispatchers.adoc for routing, configuration.adoc, messaging-concepts)
- Used TODO comment for unit-of-work.adoc → processing-context.adoc rename

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
**Status:** Most core content now covered in command-handlers.adoc
**Changes to apply:**
- Evaluate if this page is still needed or should be merged/removed
- Most fundamental concepts now in command-handlers.adoc:
  - @EntityCreator annotation (all patterns including polymorphic)
  - EventAppender.append instead of AggregateLifecycle.apply
  - Creational command handlers (static methods)
  - @EventSourced annotation
  - Entity-centric vs command-centric paradigms
- If kept, focus on advanced entity modeling topics:
  - EntityMetamodel details (if needed for advanced use cases)
  - Detailed immutable entity patterns beyond basic examples
  - Advanced declarative modeling features
- Consider consolidating or creating advanced modeling guide instead

### modules/axon-framework-commands/pages/modeling/aggregate-creation-from-another-aggregate.adoc
**RENAME TO:** `entity-creation-from-another-entity.adoc` OR **CONSIDER REMOVAL**
**Status:** Likely obsolete with command-centric approach
**Changes to apply:**
- Evaluate necessity: This pattern (entity creating another entity) may be an anti-pattern in command-centric design
- Alternative: Commands should target specific entities, not have entities create other entities
- If this represents a valid advanced pattern, update terminology and EventAppender usage
- Otherwise, consider removing or consolidating into advanced patterns section

### modules/axon-framework-commands/pages/modeling/aggregate-polymorphism.adoc
**RENAME TO:** `entity-polymorphism.adoc` OR **MERGE INTO command-handlers.adoc**
**Status:** Basic polymorphism already covered in command-handlers.adoc
**Changes to apply:**
- Basic polymorphic @EntityCreator pattern already documented in command-handlers.adoc
- Evaluate if advanced polymorphism details warrant separate page
- If kept as separate page:
  - Update terminology from aggregate to entity
  - Document advanced polymorphic entity metamodel features
  - Update code examples with EventAppender
  - Focus on complex polymorphic scenarios not covered in basics
- Consider merging into command-handlers.adoc or creating "Advanced Entity Patterns" page

### modules/axon-framework-commands/pages/modeling/multi-entity-aggregates.adoc
**RENAME TO:** `entity-hierarchies.adoc` or `child-entities.adoc`
**Status:** Advanced pattern - keep as separate page
**Changes to apply:**
- This represents advanced state management (parent-child entity relationships)
- NOT covered in command-handlers.adoc (appropriately kept separate)
- Update terminology from aggregate to entity throughout
- Document @EntityMember annotation changes:
  - Multiple children of same type support
  - Custom routing for child entities
- Document event routing changes (no longer forwards to all children by default)
- Update all code examples with EventAppender
- Focus on when and why to use entity hierarchies vs separate entities

### modules/axon-framework-commands/pages/modeling/state-stored-aggregates.adoc
**RENAME TO:** `state-stored-entities.adoc`
**Status:** Alternative to event sourcing - keep as separate advanced page
**Changes to apply:**
- This represents alternative state management strategy (state-stored vs event-sourced)
- NOT covered in command-handlers.adoc (appropriately kept separate)
- Update terminology from aggregate to entity throughout
- Document state-stored entity configuration and when to use
- Update Repository usage patterns (different from EventSourcedRepository)
- Explain trade-offs: state-stored vs event-sourced entities
- Update all code examples with modern Axon 5 APIs
- Note: Most users should use event sourcing; this is for specific use cases

---

## Events Module

### modules/events/pages/index.adoc
**Changes to apply:**
- Introduce MessageStream concept
- Document EventBus to EventSink rename
- Overview of async-native event handling
- Introduce Dynamic Consistency Boundary (DCB) concept

### modules/events/pages/event-dispatchers.adoc
**Changes to apply:**
- Introduce EventSink concept carefully (EventBus is well-known, EventSink is more technical)
- Explain that EventSink is the publishing/sending side of event distribution
- Clarify relationship between EventBus concept and EventSink implementation
- Document context parameter for publish operations (OPTIONAL on dispatch side)
- Update to async API (CompletableFuture returns)
- Document ProcessingContext usage:
  - Optional when dispatching: pass it when available (from within handler) for correlation
  - Can pass null or use method without ProcessingContext when dispatching from HTTP/outside handler
  - Show both patterns with examples
- Update EventGateway (if applicable)

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
**Changes to apply:**
- Update QueryBus API (async-native, optional ProcessingContext parameter, MessageStream)
- Document QueryGateway changes (no ResponseType, query vs queryMany)
- Introduce QueryDispatcher for in-handler query dispatch (automatically uses current ProcessingContext)
- Document ProcessingContext usage:
  - Optional when dispatching: provide when available (dispatching from handler)
  - Pass null or use variant without ProcessingContext from HTTP endpoints
  - QueryDispatcher automatically provides it when used in handlers
- Remove scatter-gather support
- Update subscription API (QualifiedName instead of String+Type)
- Document handler uniqueness requirement (no more duplicates)
- Document streaming query API

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

