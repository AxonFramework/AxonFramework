# Axon 5 Documentation Inventory & Action Plan

**Last Updated**: 2025-11-06
**Total Files**: 73 `.adoc` files
**Files Updated**: 13
**Files Remaining**: 60

**Class Reference Fixes**: ✅ All imports and class references verified and corrected (see CLASS_REFERENCE_FIXES.md)

---

## Priority 1: CRITICAL - Files That MUST Be Updated (High Impact)

### 1. Command Model (5 files) - ✅ **COMPLETED**
Core domain modeling concepts that affect everyone.

| File | Old API References | Action Required | Status |
|------|-------------------|-----------------|---------|
| `axon-framework-commands/pages/modeling/command-model.adoc` | 8 occurrences of old APIs | **FULL REWRITE** - Aggregate → Entity, @EntityCreator, EventAppender, static creation handlers | ✅ **COMPLETED** |
| `axon-framework-commands/pages/modeling/multi-entity-command-models.adoc` | 6 occurrences | **UPDATE** - @AggregateMember → @EntityMember, child entity routing | ✅ **COMPLETED** |
| `axon-framework-commands/pages/modeling/state-stored-entities.adoc` | 3 occurrences | **UPDATE** - State-stored entities, immutable patterns | ✅ **COMPLETED** |
| `axon-framework-commands/pages/modeling/entity-polymorphism.adoc` | 2 occurrences | **UPDATE** - Polymorphic entity metamodels | ✅ **COMPLETED** |
| `axon-framework-commands/pages/modeling/entity-creation-patterns.adoc` | 3 occurrences | **REWRITE FOR DCB** - Event-driven entity creation pattern | ✅ **COMPLETED** |

**Search Patterns**:
```bash
grep -r "@AggregateIdentifier\|@AggregateMember\|AggregateLifecycle\|@AggregateRoot" axon-framework-commands/pages/modeling/
```

---

### 2. Event Processors (4 files) - **CRITICAL**
TrackingEventProcessor removed, PooledStreamingEventProcessor is now default.

| File | Old API References | Action Required | Status |
|------|-------------------|-----------------|---------|
| `events/pages/event-processors/streaming.adoc` | **54 occurrences** | **MAJOR REWRITE** - Remove all TEP content, document PSEP configuration | ⚠️ Partial (marked TODOs) |
| `events/pages/event-processors/index.adoc` | Multiple | **UPDATE** - Default processor changed, configuration API changed | ❌ Not Started |
| `events/pages/event-processors/dead-letter-queue.adoc` | 4 occurrences | **UPDATE** - DLQ with PSEP, @SequencingPolicy | ❌ Not Started |
| `tuning/pages/event-processing.adoc` | 4 occurrences | **UPDATE** - Tuning PSEP instead of TEP | ❌ Not Started |

**Search Patterns**:
```bash
grep -r "TrackingEventProcessor\|TrackingToken\|@ProcessingGroup" events/pages/event-processors/
```

---

### 3. Configuration (2 files) - **CRITICAL**
Entire configuration API has been inverted.

| File | Old API References | Action Required | Status |
|------|-------------------|-----------------|---------|
| `axon-framework-commands/pages/configuration.adoc` | 3 occurrences | **FULL REWRITE** - Documet MessagingConfigurer, ModellingConfigurer, EventSourcingConfigurer | ❌ Not Started |
| `ROOT/pages/spring-boot-integration.adoc` | Unknown | **UPDATE** - Spring Boot 3, Jakarta, new config approach | ❌ Not Started |

**Key Changes**:
- `Configurer` → `ApplicationConfigurer` (layered: MessagingConfigurer → ModellingConfigurer → EventSourcingConfigurer)
- No more `Configuration.eventBus()` - components retrieved differently
- Module-based configuration

---

### 4. Testing (1 file partially done, needs completion)

| File | Status | Remaining Work |
|------|--------|----------------|
| `testing/pages/commands-events.adoc` | ✅ Major update done | Resolve all TODO markers (time-based testing, state detection) |
| `testing/pages/sagas-1.adoc` | ⚠️ Partial | Complete saga testing examples, time manipulation |

---

## Priority 2: HIGH - Files With Many Old API References

### 5. Monitoring & Metrics (5 files)

| File | Old API References | Action Required | Status |
|------|-------------------|-----------------|---------|
| `monitoring/pages/metrics.adoc` | **15 occurrences** | **UPDATE** - Metric names may have changed, UnitOfWork → ProcessingContext | ❌ Not Started |
| `monitoring/pages/processors.adoc` | Unknown | **UPDATE** - PSEP monitoring instead of TEP | ❌ Not Started |
| `monitoring/pages/message-tracking.adoc` | Unknown | **UPDATE** - Message tracking with new APIs | ❌ Not Started |
| `monitoring/pages/health.adoc` | Unknown | **VERIFY** - Health check APIs | ❌ Not Started |
| `monitoring/pages/tracing.adoc` | Unknown | **UPDATE** - Distributed tracing with ProcessingContext | ❌ Not Started |

---

### 6. Event Store & Infrastructure (3 files)

| File | Old API References | Action Required | Status |
|------|-------------------|-----------------|---------|
| `events/pages/infrastructure.adoc` | Unknown | **MAJOR REWRITE** - Dynamic Consistency Boundary, new EventStore API | ❌ Not Started |
| `tuning/pages/event-snapshots.adoc` | Unknown | **UPDATE** - Snapshotting with DCB | ❌ Not Started |
| `ROOT/pages/serialization.adoc` | Unknown | **UPDATE** - Serializer → Converter, no more XStream | ❌ Not Started |

**Key Changes**:
- `EventStore.publish()` → `EventStoreTransaction` with `EventAppender`
- Aggregate-based vs DCB-based event storage
- `DomainEventStream` → `MessageStream`

---

### 7. Command Infrastructure (2 files)

| File | Old API References | Action Required | Status |
|------|-------------------|-----------------|---------|
| `axon-framework-commands/pages/infrastructure.adoc` | 6 occurrences | **UPDATE** - CommandBus async API, routing changes | ❌ Not Started |
| `tuning/pages/command-processing.adoc` | Unknown | **UPDATE** - Command processing optimization | ❌ Not Started |

---

## Priority 3: MEDIUM - Terminology & Method Name Updates

### 8. Message Concepts (2 files - mostly done)

| File | Status | Remaining Work |
|------|--------|----------------|
| `messaging-concepts/pages/message-intercepting.adoc` | ✅ Updated | Verify all examples compile |
| `messaging-concepts/pages/message-correlation.adoc` | ⚠️ Needs check | 2 old API references - verify correlation still works same way |

---

### 9. Query Handling (4 files)

| File | Old API References | Action Required | Status |
|------|-------------------|-----------------|---------|
| `queries/pages/query-dispatchers.adoc` | 1 occurrence | **UPDATE** - QueryBus async API, MessageStream results | ❌ Not Started |
| `queries/pages/query-handlers.adoc` | Unknown | **UPDATE** - QueryHandler parameter injection | ❌ Not Started |
| `queries/pages/implementations.adoc` | Unknown | **VERIFY** - Query implementations still same | ❌ Not Started |
| `queries/pages/configuration.adoc` | Unknown | **UPDATE** - Query configuration with new Configurer | ❌ Not Started |

---

### 10. Sagas (4 files)

| File | Old API References | Action Required | Status |
|------|-------------------|-----------------|---------|
| `sagas/pages/implementation.adoc` | Unknown | **UPDATE** - Saga implementation patterns, EventAppender | ❌ Not Started |
| `sagas/pages/infrastructure.adoc` | Unknown | **UPDATE** - Saga repository, manager configuration | ❌ Not Started |
| `sagas/pages/associations.adoc` | Unknown | **VERIFY** - Association API unchanged? | ❌ Not Started |
| `sagas/pages/index.adoc` | Unknown | **UPDATE** - Overview and terminology | ❌ Not Started |

---

### 11. Event Handling (4 files)

| File | Old API References | Action Required | Status |
|------|-------------------|-----------------|---------|
| `events/pages/event-handlers.adoc` | Unknown | **UPDATE** - Handler parameter injection, ProcessingContext | ❌ Not Started |
| `events/pages/event-dispatchers.adoc` | 5 occurrences | **UPDATE** - Event dispatching with EventSink | ❌ Not Started |
| `events/pages/event-versioning.adoc` | Unknown | **VERIFY** - Upcasting still same | ❌ Not Started |
| `events/pages/index.adoc` | Unknown | **UPDATE** - Overview | ❌ Not Started |

---

### 12. Deadlines (3 files)

| File | Old API References | Action Required | Status |
|------|-------------------|-----------------|---------|
| `deadlines/pages/deadline-managers.adoc` | Unknown | **UPDATE** - DeadlineManager API changes | ❌ Not Started |
| `deadlines/pages/event-schedulers.adoc` | Unknown | **UPDATE** - EventScheduler API changes | ❌ Not Started |
| `deadlines/pages/index.adoc` | Unknown | **UPDATE** - Overview | ❌ Not Started |

---

## Priority 4: LOW - Documentation & Meta Files

### 13. Release Notes & General (5 files)

| File | Action Required | Status |
|------|-----------------|---------|
| `release-notes/pages/major-releases.adoc` | **UPDATE** - Add Axon 5.0 release notes | ❌ Not Started |
| `release-notes/pages/minor-releases.adoc` | **UPDATE** - Update for 5.x releases | ❌ Not Started |
| `ROOT/pages/modules.adoc` | **VERIFY** - Module structure changes | ❌ Not Started |
| `ROOT/pages/known-issues-and-workarounds.adoc` | **UPDATE** - Axon 5 issues | ❌ Not Started |
| `ROOT/pages/upgrading-to-4-7.adoc` | **ADD** - Need "upgrading-to-5-0.adoc" | ❌ Not Started |

---

### 14. Other Pages (6 files)

| File | Priority | Action Required | Status |
|------|----------|-----------------|---------|
| `messaging-concepts/pages/supported-parameters-annotated-handlers.adoc` | Medium | **UPDATE** - ProcessingContext, EventAppender parameters | ❌ Not Started |
| `messaging-concepts/pages/exception-handling.adoc` | Low | **VERIFY** - Exception handling unchanged? | ❌ Not Started |
| `messaging-concepts/pages/timeouts.adoc` | Low | **VERIFY** - Timeout handling unchanged? | ❌ Not Started |
| `ROOT/pages/index.adoc` | Low | **UPDATE** - Landing page, version references | ❌ Not Started |
| `tuning/pages/rdbms-tuning.adoc` | Low | **VERIFY** - RDBMS tuning tips still apply | ❌ Not Started |
| `tuning/pages/index.adoc` | Low | **UPDATE** - Overview | ❌ Not Started |

---

## Features That May Need "REMOVED - NOT YET IMPLEMENTED" Markers

Based on analysis of api-changes.md and codebase, these features might not be fully implemented yet:

### ⚠️ To Investigate & Possibly Mark for Removal

1. **Snapshot Configuration** - The snapshot configuration API may have changed significantly
   - File: `tuning/pages/event-snapshots.adoc`
   - Action: Check if snapshots work with DCB, document or mark for removal

2. **Aggregate Creation from Another Aggregate** - This pattern may not be supported with DCB
   - File: `axon-framework-commands/pages/modeling/entity-creation-patterns.adoc`
   - Action: Verify if this still works, likely needs removal or major rewrite

3. **XStream Serialization** - Explicitly removed
   - Search for XStream references
   - Action: Remove all XStream examples, note it's no longer supported

4. **Old Event Store APIs** - `DomainEventStream`, `BlockingStream` removed
   - Files: All event store documentation
   - Action: Replace with `MessageStream` examples

5. **TrackingToken APIs** - Public tracking token manipulation may be limited
   - Files: Event processor tuning, monitoring
   - Action: Document PSEP token management or mark as internal

---

## Quick Search Commands for Each Category

```bash
# Find files with most work needed
find . -name "*.adoc" -exec sh -c '
  count=$(grep -c "getMetaData\|getPayload\|getCurrentUnitOfWork\|AggregateLifecycle\|@AggregateIdentifier\|TrackingEventProcessor\|CommandCallback\|GenericCommandMessage.as\|GenericEventMessage.as" "$1" 2>/dev/null || echo 0)
  [ "$count" -gt 0 ] && echo "$count: $1"
' _ {} \; | sort -rn

# Find all aggregate-related old APIs
grep -r "@AggregateIdentifier\|@TargetAggregateIdentifier\|@AggregateMember\|AggregateLifecycle\|@AggregateRoot" --include="*.adoc" | wc -l

# Find all old method names
grep -r "getMetaData\|getPayload\|getIdentifier\|getMessage" --include="*.adoc" | wc -l

# Find all UnitOfWork references
grep -r "CurrentUnitOfWork\|UnitOfWork\.get" --include="*.adoc" | wc -l

# Find all TrackingEventProcessor references
grep -r "TrackingEventProcessor\|TrackingToken" --include="*.adoc" | wc -l

# Find all test fixture references
grep -r "AggregateTestFixture\|SagaTestFixture" --include="*.adoc" | wc -l

# Find all serializer references
grep -r "Serializer\|XStream" --include="*.adoc" | wc -l

# Find all configuration references
grep -r "Configurer\|Configuration\.eventBus\|Configuration\.commandBus" --include="*.adoc" | wc -l
```

---

## Completion Statistics

| Category | Total Files | Updated | Remaining | % Complete |
|----------|-------------|---------|-----------|------------|
| **Overall** | **73** | **13** | **60** | **18%** |
| Priority 1 (Critical) | 16 | 7 | 9 | 44% |
| Priority 2 (High) | 19 | 1 | 18 | 5% |
| Priority 3 (Medium) | 27 | 4 | 23 | 15% |
| Priority 4 (Low) | 11 | 1 | 10 | 9% |

**Latest Session Achievements**:
- ✅ All Priority 1 Command Model files completed (5 files)
- ✅ All class references verified and corrected (15 import fixes)
- ✅ Removed obsolete AggregateLifecycle, GenericJpaRepository, AggregateConfigurer references
- ✅ Rewrote aggregate-creation pattern for DCB model

---

## Recommended Action Order

1. ~~**Week 1**: Command Model (5 files)~~ ✅ **COMPLETED** - Core concepts everyone needs
2. **Week 2**: Event Processors (4 files) - Major architectural change
3. **Week 3**: Configuration (2 files) + Event Store (3 files) - Infrastructure
4. **Week 4**: Testing (complete TODOs) + Monitoring (5 files)
5. **Week 5**: Commands & Queries (6 files combined)
6. **Week 6**: Sagas, Events, Deadlines (11 files combined)
7. **Week 7**: Sweep for remaining old API references, update all examples
8. **Week 8**: Polish, verify all code compiles, remove TODOs

---

## Files Investigated and Resolved

1. ~~`axon-framework-commands/pages/modeling/entity-creation-patterns.adoc`~~ - ✅ **REWRITTEN** for DCB model with event-driven entity creation pattern
2. XStream serialization examples anywhere - **TO INVESTIGATE**
3. Any "advanced" TrackingToken manipulation examples - **TO INVESTIGATE**
4. Aggregate snapshot configuration (may need rewrite for DCB) - **TO INVESTIGATE**

---

## Templates for Common Updates

### Template 1: Aggregate → Entity Terminology
```bash
# Search and review (don't auto-replace)
sed -i 's/@AggregateIdentifier/@EntityIdentifier/g' file.adoc  # Review first!
sed -i 's/@AggregateMember/@EntityMember/g' file.adoc
sed -i 's/Aggregate/Entity/g' file.adoc  # Be careful - only in appropriate contexts
```

### Template 2: Old Method Names
```bash
# These require manual review
getMetaData() → metadata()
getPayload() → payload()
getIdentifier() → identifier()
getMessage() → depends on context
```

### Template 3: Add Deprecation Warning
```adoc
[WARNING]
====
🚧 **Axon 5 Update In Progress**

This documentation section is being updated for Axon Framework 5.
Some code examples may reference Axon 4 APIs.

For current Axon 5 APIs, see [api-changes.md](../../../axon-5/api-changes.md).
====
```
