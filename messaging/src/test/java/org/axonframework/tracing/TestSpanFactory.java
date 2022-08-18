/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Creates spans which record whether they were started, ended or ended in an exception. Useful for tracing logic
 * assertions in unit tests and provides several out of the box.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class TestSpanFactory implements SpanFactory {

    private final Logger logger = LoggerFactory.getLogger(TestSpanFactory.class);

    private final Deque<TestSpan> activeSpan = new ArrayDeque<>();
    public final List<TestSpan> createdSpans = new CopyOnWriteArrayList<>();
    public final Map<Message<?>, TestSpan> propagatedContexts = new HashMap<>();
    public final Map<Message<?>, TestSpan> spanParents = new HashMap<>();


    public void reset() {
        this.activeSpan.clear();
        this.createdSpans.clear();
        this.propagatedContexts.clear();
        this.spanParents.clear();
        logger.info("SpanFactory cleared");
    }

    public void verifyNotStarted(String name) {
        assertFalse(findSpan(name).started);
        assertFalse(findSpan(name).ended);
    }

    public void verifySpanCompleted(String name) {
        assertTrue(findSpan(name).started);
        assertTrue(findSpan(name).ended);
    }

    public void verifySpanCompleted(String name, Message<?> message) {
        assertTrue(findSpan(name, message).started);
        assertTrue(findSpan(name, message).ended);
    }

    public void verifySpanActive(String name) {
        assertTrue(findSpan(name).started);
        assertFalse(findSpan(name).ended);
    }

    public void verifySpanActive(String name, Message<?> message) {
        assertTrue(findSpan(name, message).started);
        assertFalse(findSpan(name, message).ended);
    }


    public void verifySpanHasException(String name, Class<?> exceptionClass) {
        assertInstanceOf(exceptionClass, findSpan(name).exception);
    }

    public void verifyNoSpan(String name) {
        Optional<TestSpan> span = createdSpans.stream()
                                              .filter(s -> s.name.equals(name))
                                              .findFirst();

        assertFalse(span.isPresent());
    }


    public void verifySpanPropagated(String name, Message<?> message) {
        assertTrue(createdSpans.stream()
                               .anyMatch(s -> s.name.equals(name)
                                       && propagatedContexts.containsKey(message)
                                       && propagatedContexts.get(message) == s),
                   createMessageForName(name));
    }

    public void verifySpanParentSet(String name, Message<?> message) {
        TestSpan span = findSpan(name);
        assertTrue(spanParents.containsKey(message));
        assertSame(spanParents.get(message), span);
    }

    private TestSpan findSpan(String name) {
        Optional<TestSpan> span = createdSpans.stream().filter(s -> s.name.equals(name))
                                              .findFirst();

        assertTrue(span.isPresent(), () -> createMessageForName(name));
        return span.get();
    }

    private String createMessageForName(String name) {
        return String.format(
                "No span matching name %s, but got the following recorded spans: %s",
                name,
                createdSpans.stream().map(TestSpan::toString).collect(Collectors.joining("\n")));
    }

    private TestSpan findSpan(String name, Message<?> message) {
        Optional<TestSpan> span = createdSpans.stream().filter(s -> s.name.equals(name) && s.message.equals(message))
                                              .findFirst();

        assertTrue(span.isPresent(), () -> String.format(
                "No span matching name %s and message %s, but got the following recorded spans: \n%s",
                name,
                message,
                createdSpans.stream().map(TestSpan::toString).collect(Collectors.joining("\n"))));
        return span.get();
    }

    @Override
    public Span createRootTrace(String operationName) {
        TestSpan span = new TestSpan(TestSpanType.ROOT, operationName, null);
        createdSpans.add(span);
        return span;
    }

    @Override
    public Span createHandlerSpan(String operationName, Message<?> parentMessage, boolean isChildTrace,
                                  Message<?>... linkedParents) {
        TestSpan span = new TestSpan(isChildTrace ? TestSpanType.HANDLER_CHILD : TestSpanType.HANDLER_LINK,
                                     operationName,
                                     parentMessage);
        createdSpans.add(span);
        spanParents.put(parentMessage, span);
        return span;
    }

    @Override
    public Span createDispatchSpan(String operationName, Message<?> parentMessage, Message<?>... linkedSiblings) {
        TestSpan span = new TestSpan(TestSpanType.DISPATCH, operationName, parentMessage);
        createdSpans.add(span);
        return span;
    }

    @Override
    public Span createInternalSpan(String operationName) {
        TestSpan span = new TestSpan(TestSpanType.INTERNAL, operationName, null);
        createdSpans.add(span);
        return span;
    }

    @Override
    public Span createInternalSpan(String operationName, Message<?> message) {
        TestSpan span = new TestSpan(TestSpanType.INTERNAL, operationName, message);
        createdSpans.add(span);
        return span;
    }

    @Override
    public void registerTagProvider(SpanAttributesProvider provider) {

    }

    @Override
    public <M extends Message<?>> M propagateContext(M message) {
        if(activeSpan.isEmpty()) {
            return message;
        }
        propagatedContexts.put(message, activeSpan.getFirst());
        return message;
    }

    public enum TestSpanType {
        ROOT,
        HANDLER_CHILD,
        HANDLER_LINK,
        DISPATCH,
        INTERNAL
    }

    public class TestSpan implements Span {

        public final TestSpanType type;
        public final String name;
        public final Message<?> message;
        public boolean started;
        public boolean ended;
        public Throwable exception;

        public TestSpan(TestSpanType type, String name, Message<?> message) {
            this.type = type;
            this.name = name;
            this.message = message;
        }

        @Override
        public Span start() {
            started = true;
            activeSpan.addFirst(this);
            logger.info("+ {}", name);
            return this;
        }

        @Override
        public void end() {
            ended = true;
            activeSpan.remove(this);
            logger.info("- {}", name);
        }

        @Override
        public Span recordException(Throwable t) {
            logger.info("Recorded exception for span with name {}", name, t);
            this.exception = t;
            return this;
        }

        @Override
        public String toString() {
            return "TestSpan{" +
                    "type=" + type +
                    ", name='" + name + '\'' +
                    ", message=" + message +
                    ", started=" + started +
                    ", ended=" + ended +
                    ", exception=" + exception +
                    '}';
        }
    }
}
