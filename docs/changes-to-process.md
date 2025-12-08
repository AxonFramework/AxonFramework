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
**Status:** ✅ COMPLETED
**Changes applied:**
- Updated overview to mention Axon Framework 5
- Introduced messaging-centric approach with three core message types (Commands, Events, Queries)
- Mentioned CQRS, Event Sourcing, and Domain-Driven Design support
- Added Axoniq Platform section for monitoring and management
- Updated reference sections table with current modules

### modules/ROOT/pages/modules.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- Updated module structure reflecting new organization
- Listed main modules: axon-messaging, axon-modelling, axon-eventsourcing, axon-test, axon-server-connector
- Documented extension modules organization
- Updated dependency coordinates and groupIds
- Added Axon Bill of Materials (BOM) recommendation

### modules/ROOT/pages/conversion.adoc
**Status:** ✅ COMPLETED
**Note:** Renamed from serialization.adoc
**Changes applied:**
- Updated title to "Conversion" with page alias for serialization.adoc
- Replaced all Serializer references with Converter throughout the document
- Documented MessageConverter and EventConverter types with clear explanations of their roles
- Added comprehensive section on "Understanding message types and conversion" explaining:
  - MessageType vs Java class concept
  - Payload conversion at handling time with detailed examples
  - How different handlers can receive the same message in different representations
  - How this approach reduces upcaster needs
- Updated default from XStream to Jackson with clear statement that JacksonConverter is now the default
- Removed all XStream-specific documentation
- Added XML support section explaining how to use JacksonConverter with XmlMapper
- Updated MessageTypeResolver section (replacing RevisionResolver)
- Replaced all @Revision references with @Command/@Event/@Query annotations
- Updated all code examples to use Axon 5 APIs (Converter instead of Serializer)
- Added section on choosing the right converter
- Maintained sections on lenient deserialization, generic types, and ContentTypeConverters with updated terminology

### modules/ROOT/pages/spring-boot-integration.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- Documented Spring Boot Starter integration with auto-configuration
- Updated dependency coordinates (org.axonframework.extensions.spring)
- Documented automatic detection and registration of message handlers
- Explained Axon Server configuration (recommended)
- Documented alternative configurations without Axon Server
- Updated infrastructure configuration section
- Modern Spring Boot 3 compatible examples

### modules/ROOT/pages/upgrading-to-4-7.adoc
**Status:** ✅ REMOVED
**Note:** File removed as expected - A separate Axon 4 to 5 migration guide will be added as a separate task

### modules/ROOT/pages/known-issues-and-workarounds.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- Reviewed and updated for Axon 5 context
- Maintained relevant issues (PostgreSQL Large Object Storage, Sequence generation with JPA)
- Updated cross-references to current module structure
- Kept framework-agnostic issues that still apply

---

## Axon Framework Commands Module

### modules/axon-framework-commands/pages/index.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- Rewrote introduction with command-centric, messaging-focused approach
- Emphasized that commands express intent to change state (not just modeling constructs)
- Documented command-centric vs modeling-centric shift from Axon 4
- Introduced stateless vs stateful command handler categorization
- Documented entities as optional implementation patterns (not core framework concepts)
- Clarified that entities are tools for organizing stateful command handling logic
- Explained two entity patterns: event-sourced and state-stored
- Updated table of contents to prioritize command-handlers over modeling
- Added "Getting started" section with typical workflow
- Removed outdated Axon 4 video tutorial reference

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
**Status:** ✅ COMPLETED
**Changes applied:**
- **Complete rewrite with command-centric focus**
- **Introduced ApplicationConfigurer hierarchy:**
  - Documented MessagingConfigurer for messaging infrastructure
  - Documented ModellingConfigurer for domain modeling
  - Documented EventSourcingConfigurer for event sourcing
  - Explained the enhance() pattern for wrapping configurers
- **Updated entity configuration:**
  - Changed from @Aggregate to @EventSourced
  - Showed Spring Boot auto-configuration
  - Showed Configuration API with EntityModule
  - Added custom repository configuration examples
- **Updated command handler registration:**
  - Used CommandHandlingModule.named() with annotatedCommandHandlingComponent()
  - Showed proper handler registration for non-entity handlers
  - Both Spring Boot (@Component) and Configuration API examples
- **Documented component registration patterns:**
  - Basic component registration with ComponentBuilder
  - Component replacement (last registration wins)
  - Component decoration with decorators and example LoggingCommandBus
- **Updated interceptor registration:**
  - Dispatch interceptors (before routing)
  - Handler interceptors (after routing)
  - Both Spring Boot (bean-based) and Configuration API (explicit registration)
  - Reference to messaging-concepts for details
- **Added configuration builder pattern:**
  - Showed fluent chaining of configurers
  - Complete example from EventSourcingConfigurer through entities, handlers, interceptors
- **Added configuration enhancers:**
  - Explained ConfigurationEnhancer for advanced scenarios
  - Showed how to register and use enhancers
- All examples use Axon 5 APIs (ProcessingContext, EventAppender, Repository interface, async patterns)

### modules/axon-framework-commands/pages/infrastructure.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- **Complete rewrite with user-centric focus**
- **Added comprehensive Repository section:**
  - Explained why repositories are needed and what they do
  - Documented async-native API (CompletableFuture return types)
  - Explained ManagedEntity concept and its role in lifecycle management
  - Showed load(), loadOrCreate(), persist() methods
  - Documented ProcessingContext requirement
  - Provided examples of using repositories in external command handlers
  - Documented EventSourcedRepository configuration (Spring Boot and Configuration API)
- **Updated CommandBus section:**
  - Removed AsynchronousCommandBus (no longer exists in Axon 5)
  - Removed DisruptorCommandBus (no longer exists in Axon 5)
  - Kept only SimpleCommandBus with updated examples
  - Updated configuration examples to use ApplicationConfigurer instead of Configurer
  - Documented interceptors with xref to messaging-concepts
- **Updated CommandGateway section:**
  - Simplified to focus on practical usage
  - Showed default CommandGateway interface methods (send, sendAndWait, sendAndForget)
  - Provided realistic REST controller example
  - Documented custom gateway creation with method signature behaviors
  - Updated configuration examples for both Spring Boot and Configuration API
- **Added Command Handling Component registration section:**
  - Documented Spring Boot auto-registration with @Component and @EventSourced
  - Showed manual registration with Configuration API
  - Provided examples for both stateless handlers and event-sourced entities
- **Updated Distributed CommandBus section:**
  - Kept AxonServerCommandBus documentation
  - Kept DistributedCommandBus with extension references
  - Updated routing strategy examples with ApplicationConfigurer
- Removed lengthy, implementation-focused content in favor of practical, user-centric documentation
- All code examples use Axon 5 APIs (ProcessingContext, EventAppender, ApplicationConfigurer)

### modules/axon-framework-commands/pages/modeling/aggregate.adoc
**RENAME TO:** `event-sourced-entity.adoc`
**Status:** ⏸️ TEMPORARILY REMOVED FROM NAVIGATION
**Note:** Commented out in nav.adoc - Development team to decide on reinstatement
**Previous status:** Most core content now covered in command-handlers.adoc
**Changes to apply if reinstated:**
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
**Status:** ⏸️ TEMPORARILY REMOVED FROM NAVIGATION
**Note:** Commented out in nav.adoc - Development team to decide on reinstatement
**Previous status:** Likely obsolete with command-centric approach
**Changes to apply if reinstated:**
- Evaluate necessity: This pattern (entity creating another entity) may be an anti-pattern in command-centric design
- Alternative: Commands should target specific entities, not have entities create other entities
- If this represents a valid advanced pattern, update terminology and EventAppender usage
- Otherwise, consider removing or consolidating into advanced patterns section

### modules/axon-framework-commands/pages/modeling/aggregate-polymorphism.adoc
**RENAME TO:** `entity-polymorphism.adoc` OR **MERGE INTO command-handlers.adoc**
**Status:** ⏸️ TEMPORARILY REMOVED FROM NAVIGATION
**Note:** Commented out in nav.adoc - Development team to decide on reinstatement
**Previous status:** Basic polymorphism already covered in command-handlers.adoc
**Changes to apply if reinstated:**
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
**Status:** ⏸️ TEMPORARILY REMOVED FROM NAVIGATION
**Note:** Commented out in nav.adoc - Development team to decide on reinstatement
**Previous status:** Advanced pattern - keep as separate page
**Changes to apply if reinstated:**
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
**Status:** ⏸️ TEMPORARILY REMOVED FROM NAVIGATION
**Note:** Commented out in nav.adoc - Development team to decide on reinstatement
**Previous status:** Alternative to event sourcing - keep as separate advanced page
**Changes to apply if reinstated:**
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
**Status:** ✅ COMPLETED
**Changes applied:**
- Removed scatter-gather query documentation entirely
- Added overview of async-native querying with CompletableFuture and Publisher
- Documented MessageType-based routing (qualified name + version)
- Updated query type list to remove scatter-gather, emphasize point-to-point, subscription, and streaming queries
- Improved section descriptions with Publisher integration details
- Style guide compliance verified

### modules/queries/pages/configuration.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- Updated to use MessagingConfigurer API for configuration
- Documented QueryHandlingModule registration approach
- Added comprehensive examples for Configuration API and Spring Boot
- Documented QueryBus configuration (AxonServerQueryBus and SimpleQueryBus)
- Documented QueryGateway configuration with auto-configuration details
- Updated interceptor registration patterns (dispatch and handler interceptors)
- Added complete code examples for both configuration approaches
- Removed scatter-gather query references
- Style guide compliance verified

### modules/queries/pages/implementations.adoc
**Status:** ✅ COMPLETED
**Changes applied:**
- Updated QueryGateway section to document async-native operations
- Documented query() and queryMany() methods with CompletableFuture
- Documented streamingQuery() with Publisher
- Documented subscriptionQuery() with Publisher combining initial result and updates
- Updated AxonServerQueryBus section with distributed query handling features
- Updated SimpleQueryBus section with local query handling details
- Added comprehensive subscription query infrastructure section:
  - QueryUpdateEmitter with context-aware usage pattern
  - Subscription lifecycle documentation
  - Update buffer configuration and management
  - Code examples for client-side subscription queries with Reactor
- Style guide compliance verified

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
**Status:** ✅ COMPLETED
**Changes applied:**
- Added overview emphasizing async-native query handling (CompletableFuture, Publisher)
- Documented message type concept and query name resolution:
  - Explained MessageType (QualifiedName + version) as message identifier
  - Documented @Query annotation usage with namespace, name, and version
  - Showed how handlers declare types via @Query annotation or parameter type
  - Explained payload conversion at handling time
  - Demonstrated how different handlers can receive same query in different representations
  - Explained reduction in need for upcasters
- Added comprehensive parameter injection section:
  - ProcessingContext injection (mandatory - always available in handlers)
  - Showed ProcessingContext must be passed to components during handling
  - Documented QueryUpdateEmitter creation from ProcessingContext (context-aware)
  - Added WARNING about QueryUpdateEmitter injection (must use forContext(), not field injection)
  - Documented other parameters (Metadata, @MetadataValue, Message)
- Updated query handler return values:
  - Removed ResponseType references throughout
  - Updated to use query() for single results, queryMany() for multiple results
  - Added CompletableFuture as async return type option
  - Added streaming query return values section (Publisher, Flux)
  - Documented that streaming queries allow efficient handling of large result sets
- Added code examples throughout showing ProcessingContext usage and correlation
- Style guide compliance verified

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

