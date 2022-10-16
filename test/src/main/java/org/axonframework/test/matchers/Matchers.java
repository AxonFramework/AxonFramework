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

package org.axonframework.test.matchers;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.hamcrest.Matcher;

import java.util.List;
import java.util.function.Predicate;

/**
 * Utility class containing static methods to obtain instances of (List) Matchers.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public abstract class Matchers {

    /**
     * Matches a list of Messages if a list containing their respective payloads matches the given
     * {@code matcher}.
     *
     * @param matcher The mather to match against the Message payloads
     * @return a Matcher that matches against the Message payloads
     */
    public static Matcher<List<Message<?>>> payloadsMatching(final Matcher<? extends List<?>> matcher) {
        return new PayloadsMatcher(matcher);
    }

    /**
     * Matches a single Message if the given {@code payloadMatcher} matches that message's payload.
     *
     * @param payloadMatcher The matcher to match against the Message's payload
     * @return a Matcher that evaluates a Message's payload.
     */
    public static Matcher<Message<?>> messageWithPayload(Matcher<?> payloadMatcher) {
        return new PayloadMatcher<>(payloadMatcher);
    }

    /**
     * Matches a List where all the given matchers must match with at least one of the items in that list.
     *
     * @param matchers the matchers that should match against one of the items in the List.
     * @return a matcher that matches a number of matchers against a list
     */
    @SafeVarargs
    public static <T> Matcher<List<T>> listWithAllOf(Matcher<T>... matchers) {
        return new ListWithAllOfMatcher<>(matchers);
    }

    /**
     * Matches a List of Events where at least one of the given {@code matchers} matches any of the Events in that
     * list.
     *
     * @param matchers the matchers that should match against one of the items in the List of Events.
     * @return a matcher that matches a number of event-matchers against a list of events
     */
    @SafeVarargs
    public static <T> Matcher<List<T>> listWithAnyOf(Matcher<T>... matchers) {
        return new ListWithAnyOfMatcher<>(matchers);
    }

    /**
     * Matches a list of Events if each of the {@code matchers} match against an Event that comes after the Event
     * that the previous matcher matched against. This means that the given {@code matchers} must match in order,
     * but there may be "gaps" of unmatched events in between.
     * <p/>
     * To match the exact sequence of events (i.e. without gaps), use {@link #exactSequenceOf(org.hamcrest.Matcher[])}.
     *
     * @param matchers the matchers to match against the list of events
     * @return a matcher that matches a number of event-matchers against a list of events
     */
    @SafeVarargs
    public static <T> Matcher<List<T>> sequenceOf(Matcher<T>... matchers) {
        return new SequenceMatcher<>(matchers);
    }

    /**
     * Matches a List of Events if each of the given {@code matchers} matches against the event at the respective
     * index in the list. This means the first matcher must match the first event, the second matcher the second event,
     * and so on.
     * <p/>
     * Any excess Events are ignored. If there are excess Matchers, they will be evaluated against {@code null}.
     * To
     * make sure the number of Events matches the number of Matchers, you can append an extra {@link #andNoMore()}
     * matcher.
     * <p/>
     * To allow "gaps" of unmatched Events, use {@link #sequenceOf(org.hamcrest.Matcher[])} instead.
     *
     * @param matchers the matchers to match against the list of events
     * @return a matcher that matches a number of event-matchers against a list of events
     */
    @SafeVarargs
    public static <T> Matcher<List<T>> exactSequenceOf(Matcher<T>... matchers) {
        return new ExactSequenceMatcher<>(matchers);
    }

    /**
     * Returns a Matcher that matches with values defined by the given {@code predicate}.
     * <p>
     * This method is a synonym for {@link #predicate(Predicate)} to allow for better readability
     *
     * @param predicate The predicate defining matching values
     * @param <T>       The type of value matched against
     * @return A Matcher that matches against values defined by the predicate
     */
    public static <T> Matcher<T> matches(Predicate<T> predicate) {
        return predicate(predicate);
    }

    /**
     * Returns a Matcher that matches with values defined by the given {@code predicate}.
     * <p>
     * This method is a synonym for {@link #matches(Predicate)} to allow for better readability
     *
     * @param predicate The predicate defining matching values
     * @param <T>       The type of value matched against
     * @return A Matcher that matches against values defined by the predicate
     */
    public static <T> Matcher<T> predicate(Predicate<T> predicate) {
        return new PredicateMatcher<>(predicate);
    }

    /**
     * Matches an empty List of Events.
     *
     * @return a matcher that matches an empty list of events
     */
    public static Matcher<List<EventMessage<?>>> noEvents() {
        return new EmptyCollectionMatcher<>("events");
    }

    /**
     * Matches an empty List of Commands.
     *
     * @return a matcher that matches an empty list of Commands
     */
    public static Matcher<List<CommandMessage<?>>> noCommands() {
        return new EmptyCollectionMatcher<>("commands");
    }

    /**
     * Returns a Matcher that matches with exact class type defined by the given {@code expected}.
     *
     * @param expected The expected event class
     * @param <T>      The type of event to match against
     * @return a matcher that matches based on the class
     */
    public static <T> ExactClassMatcher<T> exactClassOf(Class<T> expected) {
        return new ExactClassMatcher<>(expected);
    }

    /**
     * Matches against each event of the same runtime type that has all field values equal to the fields in the expected
     * event. All fields are compared, except for the aggregate identifier and sequence number, as they are generally
     * not set on the expected event.
     *
     * @param expected The event with the expected field values
     * @param <T>      The type of event to match against
     * @return a matcher that matches based on the equality of field values
     * @deprecated Please use the {@link #deepEquals(Object)} instead. Using this method will lead to unwanted
     * exceptions when ran on JDK 17 and up, due to adjustments in reflective access.
     */
    @SuppressWarnings("DeprecatedIsStillUsed") // Used in tests
    @Deprecated
    public static <T> EqualFieldsMatcher<T> equalTo(T expected) {
        return new EqualFieldsMatcher<>(expected);
    }

    /**
     * Matches against each event of the same runtime type that has all field values equal to the fields in the expected
     * event. All fields are compared, except for the aggregate identifier and sequence number, as they are generally
     * not set on the expected event.
     *
     * @param expected The event with the expected field values
     * @param filter   The filter describing the Fields to include in the comparison
     * @param <T>      The type of event to match against
     * @return a matcher that matches based on the equality of field values
     * @deprecated Please use the {@link #deepEquals(Object, FieldFilter)} instead. Using this method will lead to
     * unwanted exceptions when ran on JDK 17 and up, due to adjustments in reflective access.
     */
    @SuppressWarnings("DeprecatedIsStillUsed") // Used in tests
    @Deprecated
    public static <T> EqualFieldsMatcher<T> equalTo(T expected, FieldFilter filter) {
        return new EqualFieldsMatcher<>(expected, filter);
    }

    /**
     * Constructs a deep equals {@link Matcher}. This {@code Matcher} will first perform a regular equals check based on
     * the given {@code expected} and {@code actual} (provided during the {@link Matcher#matches(Object)} invocation).
     * If this fails and given type <em>does not</em> override {@link Object#equals(Object)}, this {@code Matcher} will
     * match the fields of the given {@code expected} and {@code actual}.
     *
     * @param expected The object to match against.
     * @param <T>      The type of object to match against.
     * @return A matcher matching on {@link Object#equals(Object)} firstly, followed by field value equality if equals
     * isn't implemented.
     */
    public static <T> Matcher<T> deepEquals(T expected) {
        return deepEquals(expected, AllFieldsFilter.instance());
    }

    /**
     * Constructs a deep equals {@link Matcher}. This {@code Matcher} will first perform a regular equals check based on
     * the given {@code expected} and {@code actual} (provided during the {@link Matcher#matches(Object)} invocation).
     * If this fails and given type <em>does not</em> override {@link Object#equals(Object)}, this {@code Matcher} will
     * match the fields of the given {@code expected} and {@code actual}. Fields can be in- or excluded for this last
     * step through the {@code filter}.
     *
     * @param expected The object to match against.
     * @param filter   The filter describing the Fields to include in the comparison.
     * @param <T>      The type of object to match against.
     * @return A matcher matching on {@link Object#equals(Object)} firstly, followed by field value equality if equals
     * isn't implemented.
     */
    public static <T> Matcher<T> deepEquals(T expected, FieldFilter filter) {
        return new DeepEqualsMatcher<>(expected, filter);
    }

    /**
     * Matches against {@code null} or {@code void}. Can be used to make sure no trailing events remain when
     * using an Exact Sequence Matcher ({@link #exactSequenceOf(org.hamcrest.Matcher[])}).
     *
     * @return a matcher that matches against "nothing".
     */
    public static <T> Matcher<T> andNoMore() {
        return nothing();
    }

    /**
     * Matches against {@code null} or {@code void}. Can be used to make sure no trailing events remain when
     * using an Exact Sequence Matcher ({@link #exactSequenceOf(org.hamcrest.Matcher[])}).
     *
     * @return a matcher that matches against "nothing".
     */
    public static <T> Matcher<T> nothing() {
        return new NullOrVoidMatcher<>();
    }

    private Matchers() {
    }
}
