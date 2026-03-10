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

package org.axonframework.test.extension;

import org.axonframework.test.FixtureExecutionException;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;


class ProvidedAxonTestFixtureTest {

    private static final AxonTestFixture mockedFixture = mock(AxonTestFixture.class);
    private final AxonTestFixtureProvider provider = () -> mockedFixture;

    @Test
    void findOnFieldWhenAnnotatedOnMethod() throws NoSuchMethodException {
        FieldDiscoveryTest test = new FieldDiscoveryTest();
        test.fieldProvider = provider;
        Optional<AxonTestFixtureProvider> result = ProvidedAxonTestFixtureUtils.findProvider(test,
                                                                                             test.getClass(),
                                                                                             test.getClass().getDeclaredMethod(
                                                                                                     "testMethod"));
        assertThat(result).isPresent();
        assertThat(result.get().get()).isSameAs(mockedFixture);
    }

    static class FieldDiscoveryTest {
        @ProvidedAxonTestFixture
        AxonTestFixtureProvider fieldProvider;

        @Test
        @ProvidedAxonTestFixture
        void testMethod() {
        }
    }

    @Test
    void findOnStaticMethod() throws NoSuchMethodException {
        StaticMethodDiscoveryTest test = new StaticMethodDiscoveryTest();
        Optional<AxonTestFixtureProvider> result = ProvidedAxonTestFixtureUtils.findProvider(test,
                                                                                             test.getClass(),
                                                                                             test.getClass().getDeclaredMethod(
                                                                                                     "testMethod"));
        assertThat(result).isPresent();
        assertThat(result.get().get()).isNotNull();
    }

    static class StaticMethodDiscoveryTest {
        @ProvidedAxonTestFixture
        static AxonTestFixtureProvider staticMethodProvider() {
            return () -> mock(AxonTestFixture.class);
        }

        @Test
        @ProvidedAxonTestFixture
        void testMethod() {
        }
    }

    public static class ValueProvider implements AxonTestFixtureProvider {

        @Override
        public AxonTestFixture get() {
            return mock(AxonTestFixture.class);
        }
    }

    @Test
    void findByValue() throws NoSuchMethodException {
        ValueDiscoveryTest test = new ValueDiscoveryTest();
        Optional<AxonTestFixtureProvider> result = ProvidedAxonTestFixtureUtils.findProvider(test,
                                                                                             test.getClass(),
                                                                                             test.getClass().getDeclaredMethod(
                                                                                                     "testMethod"));
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(ValueProvider.class);
        assertThat(result.get().get()).isNotNull();
    }

    static class ValueDiscoveryTest {
        @Test
        @ProvidedAxonTestFixture(ValueProvider.class)
        void testMethod() {
        }
    }

    @Test
    void testInheritance() throws NoSuchMethodException {
        DerivedTest test = new DerivedTest();
        Optional<AxonTestFixtureProvider> result = ProvidedAxonTestFixtureUtils.findProvider(test,
                                                                                             test.getClass(),
                                                                                             DerivedTest.class.getDeclaredMethod(
                                                                                                     "findInBaseClass"));
        assertThat(result).isPresent();
        assertThat(result.get().get()).isSameAs(BaseTest.BASE_FIXTURE);
    }

    @ProvidedAxonTestFixture
    static class BaseTest {

        protected static final AxonTestFixture BASE_FIXTURE = mock(AxonTestFixture.class);
        @ProvidedAxonTestFixture
        AxonTestFixtureProvider baseProvider = () -> BASE_FIXTURE;
    }

    static class DerivedTest extends BaseTest {

        @Test
        void findInBaseClass() {
        }
    }

    @Test
    void doNotFailWhenAnnotatedOnTestMethodWithoutValue() throws NoSuchMethodException {
        MisusedAnnotationOnMethodTest test = new MisusedAnnotationOnMethodTest();
        // We use MisusedTest.class as testClass and 'test' as testInstance to avoid finding providers in ProvidedAxonTestFixtureTest
        Optional<AxonTestFixtureProvider> result = ProvidedAxonTestFixtureUtils.findProvider(test,
                                                                                             test.getClass(),
                                                                                             MisusedAnnotationOnMethodTest.class.getDeclaredMethod(
                                                                                                     "testMethod"));
        assertThat(result).isEmpty();
    }

    @Test
    void failWhenAnnotatedOnProviderFieldWithValue() throws NoSuchMethodException {
        IllegalProviderTest test = new IllegalProviderTest();
        assertThatThrownBy(() -> ProvidedAxonTestFixtureUtils.findProvider(test,
                                                                           test.getClass(),
                                                                           test.getClass().getDeclaredField("illegalProvider")))
                .isInstanceOf(FixtureExecutionException.class)
                .hasMessageContaining("forbidden to set the value");
    }

    static class IllegalProviderTest {
        @ProvidedAxonTestFixture(ValueProvider.class)
        AxonTestFixtureProvider illegalProvider;
    }

    @Test
    void failWhenAmbiguousFieldAndMethod() throws NoSuchMethodException {
        AmbiguousFieldAndMethodTest test = new AmbiguousFieldAndMethodTest();
        assertThatThrownBy(() -> ProvidedAxonTestFixtureUtils.findProvider(test,
                                                                           test.getClass(),
                                                                           test.getClass().getDeclaredMethod("testMethod")))
                .isInstanceOf(FixtureExecutionException.class)
                .hasMessageContaining("Ambiguous")
                .hasMessageContaining("both a field and a method");
    }

    static class AmbiguousFieldAndMethodTest {
        @ProvidedAxonTestFixture
        AxonTestFixtureProvider fieldProvider = () -> mockedFixture;

        @ProvidedAxonTestFixture
        AxonTestFixtureProvider methodProvider() {
            return () -> mockedFixture;
        }

        @Test
        void testMethod() {
        }
    }

    static class MisusedAnnotationOnMethodTest {
        // annotation used on test method needs value

        @Test
        @ProvidedAxonTestFixture
        void testMethod() {
        }
    }

    @ProvidedAxonTestFixture
    static class MisusedAnnotationOnClassTest {
        // annotation used on test class needs value

        @Test
        void testMethod() {
        }
    }
}
