/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.tracing;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Abstract class test for all intermediate factories that result in a {@link Span}. Contains facilities
 * to setup tests in a good manner and run them.
 *
 * @param <BI> The builder class
 * @param <SI> The span factory class
 * @since 4.9.0
 * @author Mitchell Herrijgers
 */
public abstract class IntermediateSpanFactoryTest<BI, SI> {
    private final TestSpanFactory spanFactory = new TestSpanFactory();

    /**
     * Creates a new factory builder that also sets the provided span factory.
     */
    protected abstract BI createBuilder(SpanFactory spanFactory);

    /**
     * Executes the builder method and returns the factory based on the builder.
     */
    protected abstract SI createFactoryBasedOnBuilder(BI builder);

    @Test
    void cannotSetNullSpanFactory() {
        Assertions.assertThrows(AxonConfigurationException.class, () -> createBuilder(null));
    }

    /**
     * Executes the test based on the given parameters.
     * @param builderAdditions The additions to the builder of the intermediate factory
     * @param invocation The invocation of the intermediate factory
     * @param definition The definition of the expected span
     */
    protected void test(Function<BI, BI> builderAdditions, Function<SI, Span> invocation, ExpectedSpan definition) {
        test(new SpanFactoryTestDefinition(builderAdditions, invocation, definition));
    }

    /**
     * Executes the test based on the given parameters with all settings at default.
     *
     * @param invocation The invocation of the intermediate factory
     * @param definition The definition of the expected span
     */
    protected void test(Function<SI, Span> invocation, ExpectedSpan definition) {
        test(new SpanFactoryTestDefinition(builder -> builder, invocation, definition));
    }

    /**
     * Executes the test based on the given definition.
     * @param testDefinition The definition of the test
     */
    protected void test(SpanFactoryTestDefinition testDefinition) {
        BI builder = createBuilder(spanFactory);
        builder = testDefinition.getBuilderAdditions().apply(builder);
        SI spanFactory = createFactoryBasedOnBuilder(builder);
        Span span = testDefinition.getInvocation().apply(spanFactory);
        testDefinition.getExpectedSpan().assertSpan((TestSpanFactory.TestSpan) span);
    }

    protected <M extends Message<?>> void testContextPropagation(M message, BiConsumer<SI, M> invocation) {
        SI siSpanFactory = createFactoryBasedOnBuilder(createBuilder(this.spanFactory));
        this.spanFactory.createRootTrace(() -> "dummy trace").run(() -> {
            invocation.accept(siSpanFactory, message);
            this.spanFactory.verifySpanPropagated("dummy trace", message);
        });
    }

    /**
     * Creates a new {@link ExpectedSpan} with the given name and type.
     * @param expectedName The name of the span
     * @param expectedType The type of the span
     * @return The {@link ExpectedSpan} instance
     */
    protected ExpectedSpan expectedSpan(String expectedName, TestSpanFactory.TestSpanType expectedType) {
        return new ExpectedSpan(expectedName, expectedType);
    }

    /**
     * Definition of a test for an intermediate span factory. Contains the additions to the builder, the invocation
     * of the factory and the expected span.
     */
    protected class SpanFactoryTestDefinition {
        private final Function<BI, BI> builderAdditions;
        private final Function<SI, Span> invocation;
        private final ExpectedSpan expectedSpan;

        private SpanFactoryTestDefinition(Function<BI, BI> builderAdditions, Function<SI, Span> invocation, ExpectedSpan expectedSpan) {
            this.builderAdditions = builderAdditions;
            this.invocation = invocation;
            this.expectedSpan = expectedSpan;
        }

        public Function<BI, BI> getBuilderAdditions() {
            return builderAdditions;
        }

        public Function<SI, Span> getInvocation() {
            return invocation;
        }

        public ExpectedSpan getExpectedSpan() {
            return expectedSpan;
        }
    }

    /**
     * Holder object for the properties of an expected span. Used to assert the span created by the intermediate
     * factory.
     */
    protected static class ExpectedSpan {
        private final String name;
        private final TestSpanFactory.TestSpanType type;
        private final Map<String, String> attributes = new HashMap<>();
        private Message<?> message = null;

        private ExpectedSpan(String name, TestSpanFactory.TestSpanType type) {
            this.name = name;
            this.type = type;
        }

        public ExpectedSpan expectAttribute(String key, String value) {
            attributes.put(key, value);
            return this;
        }

        public ExpectedSpan withMessage(Message<?> message) {
            this.message = message;
            return this;
        }

        public void assertSpan(TestSpanFactory.TestSpan span) {
            Assertions.assertEquals(name, span.getName());
            Assertions.assertEquals(type, span.getType());
            attributes.forEach((key, value) -> Assertions.assertEquals(value, span.getAttribute(key)));
            if(message != null) {
                Assertions.assertSame(message, span.getMessage());
            }
        }

    }
}
