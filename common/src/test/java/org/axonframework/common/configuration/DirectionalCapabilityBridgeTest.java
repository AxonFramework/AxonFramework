/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.configuration;

import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors the real {@code EventStorageEngine}/{@code SnapshotStore} situation behind AxonIQ/axoniq-framework#397 and
 * AxonIQ/AxonFramework#5039: {@code Engine} and {@code Snapshots} are sibling interfaces (neither extends the
 * other), and a single instance is registered under both, as two separate {@code Identifier} entries, via a shared,
 * memoized builder -- exactly how {@code AxonServerConfigurationEnhancer} registers
 * {@code AxonServerEventStorageEngine}.
 */
class DirectionalCapabilityBridgeTest {

    interface Engine {

        String append();
    }

    interface Snapshots {

        String snapshot();
    }

    static class SelfHostingEngine implements Engine, Snapshots {

        @Override
        public String append() {
            return "appended";
        }

        @Override
        public String snapshot() {
            return "snapshotted";
        }
    }

    /** Mirrors a hand-written {@code TracingSnapshotStore}: narrows its delegate down to just {@code Snapshots}. */
    static class TracingSnapshots implements Snapshots {

        private final Snapshots delegate;

        TracingSnapshots(Snapshots delegate) {
            this.delegate = delegate;
        }

        @Override
        public String snapshot() {
            return "traced(" + delegate.snapshot() + ")";
        }
    }

    private DefaultComponentRegistry testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new DefaultComponentRegistry();
        // Mirrors AxonServerConfigurationEnhancer's memoizing ComponentBuilder shared across both slots.
        AtomicReference<SelfHostingEngine> shared = new AtomicReference<>();
        ComponentBuilder<SelfHostingEngine> sharedBuilder =
                config -> shared.updateAndGet(e -> e != null ? e : new SelfHostingEngine());
        testSubject.registerComponent(Engine.class, sharedBuilder);
        testSubject.registerComponent(Snapshots.class, sharedBuilder);
    }

    @Test
    void undecoratedBothSlotsAreTheSameInstance() {
        Configuration config = testSubject.build(mock());

        assertSame(config.getComponent(Engine.class), config.getComponent(Snapshots.class));
    }

    @Test
    void decoratingOnlyTheSecondarySlotDivergesFromThePrimaryWithoutTheBridge() {
        // Reproduces the residual gap: even AxonFramework#5039's engine-instanceof-SnapshotStore fix only ever
        // looks at what Engine.class resolves to. It does nothing to reconcile a decorator registered directly
        // against Snapshots.class (mirroring a tracing decorator on SnapshotStore.class).
        testSubject.registerDecorator(Snapshots.class, 0, (config, name, delegate) -> new TracingSnapshots(delegate));

        Configuration config = testSubject.build(mock());

        Engine engine = config.getComponent(Engine.class);
        Snapshots snapshots = config.getComponent(Snapshots.class);

        assertEquals("appended", engine.append());
        assertEquals("traced(snapshotted)", snapshots.snapshot());
        // These are no longer the same object -- exactly the divergence the PR review comment on axoniq-framework#397
        // (and the follow-up question in this conversation) predicted for the "what if something also decorates the
        // other slot" scenario.
        assertNotSame(engine, snapshots);
        assertFalse(snapshots instanceof Engine);
    }

    @Test
    void bridgingReconcilesTheSecondarySlotWithThePrimaryWithoutChangingItsOwnDecoration() {
        testSubject.registerDecorator(Snapshots.class, 0, (config, name, delegate) -> new TracingSnapshots(delegate));
        DirectionalCapabilityBridge.bridgeOnto(testSubject, Engine.class, Snapshots.class, null, 1);

        Configuration config = testSubject.build(mock());

        Engine engine = config.getComponent(Engine.class);
        Snapshots snapshots = config.getComponent(Snapshots.class);

        // The secondary slot's own decoration still applies...
        assertEquals("traced(snapshotted)", snapshots.snapshot());
        // ...but it is once again also a fully-capable Engine, routed straight to the primary slot's value.
        assertTrue(snapshots instanceof Engine);
        assertEquals("appended", ((Engine) snapshots).append());
    }

    @Test
    void theBridgeIsDirectionalOnlyTheSecondarySlotReflectsChangesMadeThroughIt() {
        testSubject.registerDecorator(Snapshots.class, 0, (config, name, delegate) -> new TracingSnapshots(delegate));
        DirectionalCapabilityBridge.bridgeOnto(testSubject, Engine.class, Snapshots.class, null, 1);

        Configuration config = testSubject.build(mock());

        Engine engine = config.getComponent(Engine.class);
        Snapshots snapshots = config.getComponent(Snapshots.class);

        // Snapshots.class was widened into a proxy by the bridge...
        assertTrue(java.lang.reflect.Proxy.isProxyClass(snapshots.getClass()));
        // ...but this is the documented, by-design asymmetry: Engine.class's own resolution is completely
        // untouched by any of this -- it is, and stays, the bare, original shared instance, unaware that
        // Snapshots.class was ever decorated or bridged at all.
        assertFalse(java.lang.reflect.Proxy.isProxyClass(engine.getClass()));
        assertInstanceOf(SelfHostingEngine.class, engine);
    }

    @Test
    void bridgingFailsWhenThePrimarySlotAlreadyDependsOnTheSecondary() {
        // Mirrors what actually happens when this utility is registered on top of the real
        // SnapshotSourcingConfigurationEnhancer: its Engine.class-equivalent decorator already reaches into
        // Snapshots.class-equivalent (to build a wrapper for the common, non-self-hosting case). Bridging
        // Snapshots.class back onto Engine.class then makes the two slots mutually, lazily dependent -- resolving
        // either one nests into the other's still-in-progress resolution, forever. Engine was never a safe
        // "primary" here to begin with: it already had its own reason to depend on Snapshots.
        testSubject.registerDecorator(Engine.class, 0, (config, name, delegate) -> {
            config.getComponent(Snapshots.class);
            return delegate;
        });
        DirectionalCapabilityBridge.bridgeOnto(testSubject, Engine.class, Snapshots.class, null, 1);

        Configuration config = testSubject.build(mock());

        assertThrows(StackOverflowError.class, () -> config.getComponent(Engine.class));
    }

    private static LifecycleRegistry mock() {
        return org.mockito.Mockito.mock(LifecycleRegistry.class);
    }
}
