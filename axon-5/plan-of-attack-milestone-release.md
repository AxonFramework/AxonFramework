# Plan of Attack for Milestone Release

This document serves the purpose of drafting a plan of attack towards a milestone of Axon Framework 5.

## Goal

Release a milestone of Axon Framework 5 at or before the AxonIQ Conference of 2023 in September.

## Plan

1. Restart design principle sessions, holding several _before_ starting development. 
   Intended to iron out the remaining major points we can think of.
2. Start development once we've held sufficient (whatever sufficient may be) design sessions.
   Desire is that development starts half April!
3. DO NOT start development alone, especially when working on APIs! 
   Ensure pairing is in place when important parts are build. 
   This allows quick discussions on the way forward without wasting effort.
4. To maintain speed, frequent weekly sessions are required.
   For example, twice or thrice a week, in sessions between one or two hours.
   The way we're picking up the Inspector Axon project as a fair example.
5. Schedule reoccurring session to work on Axon Framework 5, according to point four.
6. A mixed attendance makes sense, although a main contributor, should be present as frequent as possible.
7. If a pairing session proofs insufficient to deduce a thorough API, hold a new design principles session.
8. Once a new API is in place, discuss it with the design principles team.
   Do so to make certain we stick to the design principles.
9. Once some approaches become common practice within Axon Framework 5, solo programming should become an option.
   Done to ensure speed is maintained to reach the goal.
   You can think of boilerplate code, POJO construction, JavaDoc, or providing a second/third/... implementation of a discussed interface.

## Roadmaps

This section contains roadmaps to proceed towards a milestone release of Axon Framework 5.

### Roadmap for 01-05-2023 to 05-05-2023

1. Mob-styled PoC for the reactive UnitOfWork as the core of the framework.
   Points to take into account:
   1. [Single-interface approach](reactive-native.md#one-interface-approach)
   2. [Multi-interface approach](reactive-native.md#two-interfaces-approach) 
   3. Reactive Transaction Manager
2. Define developer Personas for Axon Framework as a means to debate the module/component break down.
3. Mob-styled PoC for [`Configuration` API](design-principles.md#configuration).
4. Mob-styled PoC for [`Message`](design-principles.md#messaging) interfaces.
   Point to take into account:
   1. Allow definition of namespaces.
   2. Separate message name from payload type.
5. Mob-styled PoC for stateful message handlers.
6. Mob-styled PoC for message bus design.
7. Mob-styled PoC for the Event Store.
   Point to take into account:
   1. Aggregate events can be published with a set of identifiers the event belongs to. 
      This supports aggregate break down / "kill the aggregate"-like solutions.
8. Pass through of Axon Framework 4 marking components as deprecated or moved to extension/AxonServer.
   Point to take into account:
   1. Move JDBC or JPA outside of the Framework.
   2. Move Spring outside of the Framework.
   3. Drop components that aren't used a lot, like `ConflictResolution` or the `DisruptorCommandBus`.
9. Mob-styled PoC for converter design (`Serializer` replacement).
10. Mob-styled PoC for [declarative aggregate configuration](design-principles.md#commands--command-modelling--aggregates). 