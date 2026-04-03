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
import org.axonframework.common.infra.MockComponentDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationExtensionsTest {

    private StubExtendedConfiguration owner;

    @BeforeEach
    void setUp() {
        owner = new StubExtendedConfiguration();
    }

    @Nested
    class WhenExtending {

        @Test
        void createsExtensionOnFirstAccess() {
            // when
            StubExtension result = owner.extension(StubExtension.class);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        void returnsSameInstanceOnSubsequentAccess() {
            // when
            StubExtension first = owner.extension(StubExtension.class);
            StubExtension second = owner.extension(StubExtension.class);

            // then
            assertThat(first).isSameAs(second);
        }

        @Test
        void passesOwnerAsParent() {
            // when
            StubExtension extension = owner.extension(StubExtension.class);

            // then
            assertThat(extension.parent).isSameAs(owner);
        }

        @Test
        void throwsForIncompatibleConstructor() {
            // when / then
            assertThatThrownBy(() -> owner.extension(IncompatibleExtension.class))
                    .isInstanceOf(AxonConfigurationException.class);
        }

        @Test
        void supportsDifferentExtensionTypes() {
            // when
            StubExtension stubExtension = owner.extension(StubExtension.class);
            AnotherStubExtension anotherExtension = owner.extension(AnotherStubExtension.class);

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
            StubExtension stubExtension = owner.extension(StubExtension.class);
            AnotherStubExtension anotherExtension = owner.extension(AnotherStubExtension.class);

            // when
            owner.extensions.validate();

            // then
            assertThat(stubExtension.validated).isTrue();
            assertThat(anotherExtension.validated).isTrue();
        }
    }

    @Nested
    class WhenDescribing {

        @Test
        void describesExtensionsAsNamedMap() {
            // given
            var stubExtension = owner.extension(StubExtension.class);
            var anotherExtension = owner.extension(AnotherStubExtension.class);
            var descriptor = new MockComponentDescriptor();

            // when
            owner.extensions.describeTo(descriptor);

            // then
            java.util.Map<String, ?> extensionsMap = descriptor.getProperty("extensions");
            assertThat(extensionsMap).containsKey("stubExtension");
            assertThat(extensionsMap).containsKey("anotherStubExtension");
            assertThat(extensionsMap.get("stubExtension")).isSameAs(stubExtension);
            assertThat(extensionsMap.get("anotherStubExtension")).isSameAs(anotherExtension);
        }

        @Test
        void extensionDescribesItsOwnProperties() {
            // given
            owner.extension(StubExtension.class);
            var descriptor = new MockComponentDescriptor();

            // when
            owner.extension(StubExtension.class).describeTo(descriptor);

            // then
            assertThat(descriptor.getDescribedProperties()).containsEntry("stub", "value");
        }
    }

    @Nested
    class WhenCopying {

        @Test
        void copiesAllExtensionsToTarget() {
            // given
            StubExtension original = owner.extension(StubExtension.class);
            owner.extension(AnotherStubExtension.class);
            StubExtendedConfiguration targetOwner = new StubExtendedConfiguration();

            // when
            owner.extensions.copyTo(targetOwner.extensions);

            // then
            assertThat(targetOwner.extension(StubExtension.class)).isSameAs(original);
        }
    }

    // -- test fixtures --

    static class StubExtendedConfiguration implements ExtendedConfiguration, ExtensibleConfigurer {

        final ConfigurationExtensions extensions = new ConfigurationExtensions(this);

        @Override
        public <T extends ConfigurationExtension<?>> T extension(Class<T> type) {
            return extensions.extension(type);
        }

        @Override
        public <T extends ConfigurationExtension<?>> ExtensibleConfigurer extend(Class<T> type,
                                                                                 UnaryOperator<T> customization) {
            return extensions.extend(type, customization);
        }
    }

    static class StubExtension implements ConfigurationExtension<StubExtendedConfiguration> {

        final StubExtendedConfiguration parent;
        boolean validated = false;

        protected StubExtension(StubExtendedConfiguration parent) {
            this.parent = parent;
        }

        @Override
        public String name() {
            return "stubExtension";
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

    static class AnotherStubExtension implements ConfigurationExtension<StubExtendedConfiguration> {

        boolean validated = false;

        protected AnotherStubExtension(StubExtendedConfiguration parent) {
        }

        @Override
        public String name() {
            return "anotherStubExtension";
        }

        @Override
        public void validate() throws AxonConfigurationException {
            validated = true;
        }

        @Override
        public void describeTo(ComponentDescriptor descriptor) {
            descriptor.describeProperty("another", "data");
        }
    }

    interface IncompatibleParent extends ExtendedConfiguration {
    }

    static class IncompatibleExtension implements ConfigurationExtension<IncompatibleParent> {

        protected IncompatibleExtension(IncompatibleParent parent) {
        }

        @Override
        public String name() {
            return "incompatible";
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

}
