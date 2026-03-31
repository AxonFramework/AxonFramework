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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.infra.ComponentDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationExtensionsTest {

    private StubExtensibleConfiguration owner;

    @BeforeEach
    void setUp() {
        owner = new StubExtensibleConfiguration();
    }

    @Nested
    class WhenExtending {

        @Test
        void createsExtensionOnFirstAccess() {
            // when
            StubExtension result = owner.extend(StubExtension.class);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        void returnsSameInstanceOnSubsequentAccess() {
            // when
            StubExtension first = owner.extend(StubExtension.class);
            StubExtension second = owner.extend(StubExtension.class);

            // then
            assertThat(first).isSameAs(second);
        }

        @Test
        void passesOwnerAsParent() {
            // when
            StubExtension extension = owner.extend(StubExtension.class);

            // then
            assertThat(extension.parent).isSameAs(owner);
        }

        @Test
        void throwsForIncompatibleConstructor() {
            // when / then
            assertThatThrownBy(() -> owner.extend(IncompatibleExtension.class))
                    .isInstanceOf(AxonConfigurationException.class);
        }

        @Test
        void supportsDifferentExtensionTypes() {
            // when
            StubExtension stubExtension = owner.extend(StubExtension.class);
            AnotherStubExtension anotherExtension = owner.extend(AnotherStubExtension.class);

            // then
            assertThat(stubExtension).isNotNull();
            assertThat(anotherExtension).isNotNull();
            assertThat(stubExtension).isNotSameAs(anotherExtension);
        }
    }

    @Nested
    class WhenValidating {

        @Test
        void validatesAllExtensions() {
            // given
            StubExtension extension = owner.extend(StubExtension.class);
            owner.extend(AnotherStubExtension.class);

            // when
            owner.extensions.validate();

            // then
            assertThat(extension.validated).isTrue();
        }
    }

    @Nested
    class WhenDescribing {

        @Test
        void describesAllExtensions() {
            // given
            owner.extend(StubExtension.class);
            owner.extend(AnotherStubExtension.class);
            RecordingDescriptor descriptor = new RecordingDescriptor();

            // when
            owner.extensions.describe(descriptor);

            // then
            assertThat(descriptor.properties).containsEntry("stub", "value");
        }
    }

    @Nested
    class WhenCopying {

        @Test
        void copiesAllExtensionsToTarget() {
            // given
            StubExtension original = owner.extend(StubExtension.class);
            owner.extend(AnotherStubExtension.class);
            StubExtensibleConfiguration targetOwner = new StubExtensibleConfiguration();

            // when
            owner.extensions.copyTo(targetOwner.extensions);

            // then
            assertThat(targetOwner.extend(StubExtension.class)).isSameAs(original);
        }
    }

    // -- test fixtures --

    static class StubExtensibleConfiguration implements ExtensibleConfiguration {

        final ConfigurationExtensions extensions = new ConfigurationExtensions(this);

        @Override
        public <T extends ConfigurationExtension<?>> T extend(Class<T> type) {
            return extensions.extend(type);
        }
    }

    static class StubExtension extends ConfigurationExtension<StubExtensibleConfiguration> {

        boolean validated = false;

        protected StubExtension(StubExtensibleConfiguration parent) {
            super(parent);
        }

        @Override
        public void validate() throws AxonConfigurationException {
            validated = true;
        }

        @Override
        public void describeTo(ComponentDescriptor descriptor) {
            descriptor.describeProperty("stub", "value");
        }
    }

    static class AnotherStubExtension extends ConfigurationExtension<StubExtensibleConfiguration> {

        protected AnotherStubExtension(StubExtensibleConfiguration parent) {
            super(parent);
        }

        @Override
        public void validate() throws AxonConfigurationException {
            // no-op
        }

        @Override
        public void describeTo(ComponentDescriptor descriptor) {
            // no-op
        }
    }

    interface IncompatibleParent extends ExtensibleConfiguration {
    }

    static class IncompatibleExtension extends ConfigurationExtension<IncompatibleParent> {

        protected IncompatibleExtension(IncompatibleParent parent) {
            super(parent);
        }

        @Override
        public void validate() throws AxonConfigurationException {
            // no-op
        }

        @Override
        public void describeTo(ComponentDescriptor descriptor) {
            // no-op
        }
    }

    /**
     * Simple recording descriptor that captures property calls for assertion.
     */
    static class RecordingDescriptor implements ComponentDescriptor {

        final java.util.Map<String, Object> properties = new java.util.LinkedHashMap<>();

        @Override
        public void describeProperty(String name, Object object) {
            properties.put(name, object);
        }

        @Override
        public void describeProperty(String name, java.util.Collection<?> collection) {
            properties.put(name, collection);
        }

        @Override
        public void describeProperty(String name, java.util.Map<?, ?> map) {
            properties.put(name, map);
        }

        @Override
        public void describeProperty(String name, String value) {
            properties.put(name, value);
        }

        @Override
        public void describeProperty(String name, Long value) {
            properties.put(name, value);
        }

        @Override
        public void describeProperty(String name, Boolean value) {
            properties.put(name, value);
        }

        @Override
        public String describe() {
            return properties.toString();
        }
    }
}
