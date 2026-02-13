# Issue Examples

## Bug Report Example

**Source**: [AxonFramework/AxonFramework#3784](https://github.com/AxonFramework/AxonFramework/issues/3784)

**Title**: Polymorphic `@EventSourcedEntity` - doesn't evolve immutable entities

**Body**:

### Bug Description

The `AnnotationBasedEntityEvolvingComponent` has an overly restrictive type check that prevents events from being applied when using polymorphic immutable entities. When an event handler returns a new entity instance of a different type (within the same polymorphic hierarchy), the entity evolution fails silently and the state is not updated.

### Current Behaviour

In `AnnotationBasedEntityEvolvingComponent.java:133`, the type check uses:
```java
existing.getClass().isAssignableFrom(resultPayload.getClass())
```

This check only succeeds when:
- The returned type is exactly the same as the existing type, OR
- The returned type is a direct subclass of the existing type

This fails for polymorphic scenarios where:
- Entity types share a common interface but are not in an inheritance relationship
- Event handlers return a different concrete type within the same sealed hierarchy
- Immutable entity patterns require creating new instances of different types based on state transitions

### Example Failure Scenario

```java
sealed interface CourseState permits InitialState, CourseCreatedState, CoursePublishedState {}

@EventSourcedEntity(concreteTypes = {InitialState.class, CourseCreatedState.class, CoursePublishedState.class})
class Course {
    // properties

    @EventHandler
    CourseCreatedState on(CourseCreatedEvent event) {
        return new CourseCreatedState(...);  // Returns different type
    }
}
```

When `state` is `InitialState` and the handler returns `CourseCreatedState`, the check `InitialState.class.isAssignableFrom(CourseCreatedState.class)` fails because they are sibling types, not parent-child types.

### Wanted Behaviour

The type check should accept any type that is compatible with the generic type parameter `E`, allowing polymorphic entity evolution to work correctly. For immutable entities, event handlers should be able to return any compatible type within the polymorphic hierarchy.

### Possible Workarounds

Wrap the polymorphic state within a non-polymorphic entity wrapper:

```java
@EventSourcedEntity
class Course {
    private CourseState state;  // Polymorphic state wrapped inside

    @EventHandler
    void on(CourseCreatedEvent event) {
        this.state = new CourseCreatedState(...);
    }
}
```

### Root Cause Analysis

The issue has two components:

1. **Type check too restrictive** (Line 133): Using `existing.getClass().isAssignableFrom(resultPayload.getClass())` only accepts exact types or subtypes, failing for sibling types. **[FIXED]** Changed to `entityType.isAssignableFrom(resultPayload.getClass())`.

2. **Handler discovery limitation** (Line 99-102): The `AnnotatedHandlerInspector` is created with the interface type but event handlers are defined on concrete implementations. When looking up handlers via `inspector.getHandlers(entity.getClass())`, the inspector cannot find them because it was never informed about the concrete types.

### Resolution Decision

Based on team discussion, the decision is to: if the fix is simple let's do it, but if not **drop native polymorphic entity support** from Axon Framework 5.0 and revisit proper sealed type support in a future release.

---

## Enhancement Request Example

**Source**: [AxonFramework/AxonFramework#3929](https://github.com/AxonFramework/AxonFramework/issues/3929)

**Title**: Allow usage of the `AccessSerializingRepository`

**Body**:

### Enhancement Description

The `AccessSerializingRepository` is a wrapper around any other `Repository`, which ensure that concurrent access to the same entity occur serial. This is essentially a replacement for AF4 locking mechanism.
The `AccessSerializingRepository` was introduced early in the AF5 build cycle, but we skipped actually setting it up throughout our configuration.

This ticket is there for that effort: to configure it in the right place so that it's taking into account, given the intended benefit it was made for (read: more optimal loading and invoking of entities).

However, we should figure out whether setting this as a plain default makes sense.
This requires drafting use cases describing when the `AccessSerializingRepository` would solve an issue for the user.
Based on the likelihood of these occurring, we can discuss whether it's set by default when Axon Framework constructs a `Repository`, or whether it is a clear toggle (in both the declarative and annotated) approach of constructing an entity+repository.

Furthermore, with the initial stab to this issue in PR #3964, I encountered a **dead lock** when the `AccessSerializingRepository` was used, specifically in the `EventProcessingDeclarativeEventSourcedPooledStreamingIT` test. This test does the following:

1. AF app + Axon Server start
2. Event Processor with Event Handler for type x starts
3. Events of type X are stored
4. Event Handler for type x is invoked by Event Processor
5. Event Handler loads entity (this hits the `AccessSerializingRepository`)
6. Based on entity state, decides to dispatch command Y.
7. Command Handler for command Y is invoked.
8. Command Handler loads **the same** entity instance.

Step 8, although invoked, moves back up the chain into the Event Handler. There, the Event Handler used the `CompletableFuture` result from the dispatched command as a resulting `MessageStream`, which we wait on.

If we decide to add the `AccessSerializingRepository` in what ever fashion possible, we need to ensure handlers that invoke handlers loading the same entity instance do not cause a deadlock within the system.

### Current Behaviour

The `AccessSerializingRepository` is not used at all.

### Wanted Behaviour

Any `Repository` is wrapped by an `AccessSerializingRepository` (assuming it's applicable).

### Possible Workarounds

Manual registration by the user.
