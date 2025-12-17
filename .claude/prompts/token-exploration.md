# Task: Document TrackingToken Internals in Axon Framework 4

## Thinking Mode
Use **ultrathink** - this task requires deep analysis of interrelated classes and synthesis of complex behaviors.

## Objective
Create comprehensive documentation explaining how TrackingToken works internally. This document will serve as context for future enhancements and bug fixes.

## Output
File: `./.claude/knowledge/tracking-tokens-explanation.md`

## Scope

### In Scope
Focus exclusively on these core token classes:
- `messaging/src/main/java/org/axonframework/eventhandling/TrackingToken.java`
- `messaging/src/main/java/org/axonframework/eventhandling/ReplayToken.java`
- `messaging/src/main/java/org/axonframework/eventhandling/GapAwareTrackingToken.java`
- `messaging/src/main/java/org/axonframework/eventhandling/WrappedToken.java`
- `messaging/src/main/java/org/axonframework/eventhandling/GlobalSequenceTrackingToken.java`

### Out of Scope
- Do NOT explore token usage in EventProcessors or other downstream consumers
- We only need to understand the token classes themselves

## Analysis Approach

1. **Start with tests** - Read the test classes first to understand expected behavior
2. **Read JavaDocs** - Extract documentation from the source files
3. **Analyze implementations** - Understand each method's logic

## Required Content

### Token Comparison Methods
Document all comparison/boundary methods with:
- `covers()` - all implementations
- `upperBound()`
- `lowerBound()`

For each method, explain:
- General purpose
- Behavior with tokens at greater/lower/equal positions
- Edge cases
- Concrete examples

Consider using a comparison table to clarify differences between token types.

### ReplayToken Deep Dive
Special focus on:
- How replay calculation works
- `ReplayToken#advancedTo()` method - this is the PRIMARY reason for this documentation
- How it uses the comparison methods internally

### Diagrams
Include Mermaid diagrams where helpful (class relationships, state transitions, etc.)

## Critical Constraint

⚠️ **The `position()` method returns only an ESTIMATE and must NOT be used for decision-making in any other method.** Document this clearly and exclude `position()` from behavioral analysis.

## Role
Act as a senior software developer specializing in technical documentation. Capture all implementation details needed for future maintenance.