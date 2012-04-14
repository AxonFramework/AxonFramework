/*
 * Copyright (c) 2010-2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.matchers;

import org.axonframework.domain.EventMessage;
import org.axonframework.domain.Message;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;

import java.util.List;

/**
 * Utility class containing static methods to obtain instances of (List) Matchers.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public abstract class Matchers {

    private Matchers() {
    }

    /**
     * Matches a list of EventMessage if a list containing their respective payloads matches the given
     * <code>matcher</code>.
     *
     * @param matcher The mather to match against the EventMessage payloads
     * @return a Matcher that matches against the EventMessage payloads
     */
    public static Matcher<List<? extends EventMessage>> eventPayloadsMatching(final Matcher<? extends List> matcher) {
        return new PayloadsMatcher<EventMessage>(EventMessage.class, matcher);
    }

    /**
     * Matches a list of Message if a list containing their respective payloads matches the given <code>matcher</code>.
     *
     * @param matcher The mather to match against the Message payloads
     * @return a Matcher that evaluates a Message's payloads
     */
    public static Matcher<List<? extends Message>> messagePayloadsMatching(final Matcher<? extends List> matcher) {
        return new PayloadsMatcher<Message>(Message.class, matcher);
    }

    /**
     * Matches a single EventMessage if the given <code>payloadMatcher</code> matches that event's payload.
     *
     * @param payloadMatcher THe matcher to match against the EventMessage's payload
     * @return a Matcher that evaluates a Message's payload.
     */
    public static Matcher<? extends EventMessage> eventWithPayload(Matcher<?> payloadMatcher) {
        return new PayloadMatcher<EventMessage>(payloadMatcher);
    }

    /**
     * Matches a List where all the given matchers must match with at least one of the items in that list.
     *
     * @param matchers the matchers that should match against one of the items in the List.
     * @param <T>      The type of object expected in the list
     * @return a matcher that matches a number of matchers against a list
     */
    @Factory
    public static <T> ListWithAllOfMatcher listWithAllOf(Matcher<T>... matchers) {
        return new ListWithAllOfMatcher<T>(matchers);
    }

    /**
     * Matches a List of Events where at least one of the given <code>matchers</code> matches any of the Events in that
     * list.
     *
     * @param matchers the matchers that should match against one of the items in the List of Events.
     * @param <T>      The type of event to match against
     * @return a matcher that matches a number of event-matchers against a list of events
     */
    @Factory
    public static <T> ListWithAnyOfMatcher<T> listWithAnyOf(Matcher<T>... matchers) {
        return new ListWithAnyOfMatcher<T>(matchers);
    }

    /**
     * Matches a list of Events if each of the <code>matchers</code> match against an Event that comes after the Event
     * that the previous matcher matched against. This means that the given <code>matchers</code> must match in order,
     * but there may be "gaps" of unmatched events in between.
     * <p/>
     * To match the exact sequence of events (i.e. without gaps), use {@link #exactSequenceOf(org.hamcrest.Matcher[])}.
     *
     * @param matchers the matchers to match against the list of events
     * @param <T>      The type of event to match against
     * @return a matcher that matches a number of event-matchers against a list of events
     */
    @Factory
    public static <T> SequenceMatcher<T> sequenceOf(Matcher<? extends T>... matchers) {
        return new SequenceMatcher<T>(matchers);
    }

    /**
     * Matches a List of Events if each of the given <code>matchers</code> matches against the event at the respective
     * index in the list. This means the first matcher must match the first event, the second matcher the second event,
     * and so on.
     * <p/>
     * Any excess Events are ignored. If there are excess Matchers, they will be evaluated against <code>null</code>.
     * To
     * make sure the number of Events matches the number of Matchers, you can append an extra {@link #andNoMore()}
     * matcher.
     * <p/>
     * To allow "gaps" of unmatched Events, use {@link #sequenceOf(org.hamcrest.Matcher[])} instead.
     *
     * @param matchers the matchers to match against the list of events
     * @param <T>      The type of event to match against
     * @return a matcher that matches a number of event-matchers against a list of events
     */
    @Factory
    public static <T> ExactSequenceMatcher<T> exactSequenceOf(Matcher<? extends T>... matchers) {
        return new ExactSequenceMatcher<T>(matchers);
    }

    /**
     * Matches an empty List of Events.
     *
     * @return a matcher that matches an empty list of events
     */
    @Factory
    public static EmptyCollectionMatcher noEvents() {
        return new EmptyCollectionMatcher("events");
    }

    /**
     * Matches an empty List of Commands.
     *
     * @return a matcher that matches an empty list of Commands
     */
    @Factory
    public static EmptyCollectionMatcher noCommands() {
        return new EmptyCollectionMatcher("commands");
    }

    /**
     * Matches against each event of the same runtime type that has all field values equal to the fields in the
     * expected
     * event. All fields are compared, except for the aggregate identifier and sequence number, as they are generally
     * not set on the expected event.
     *
     * @param expected The event with the expected field values
     * @param <T>      The type of event to match against
     * @return a matcher that matches based on the equality of field values
     */
    @Factory
    public static <T> EqualFieldsMatcher<T> equalTo(T expected) {
        return new EqualFieldsMatcher<T>(expected);
    }

    /**
     * Matches against <code>null</code> or <code>void</code>. Can be used to make sure no trailing events remain when
     * using an Exact Sequence Matcher ({@link #exactSequenceOf(org.hamcrest.Matcher[])}).
     *
     * @return a matcher that matches against "nothing".
     */
    @Factory
    public static NullOrVoidMatcher andNoMore() {
        return nothing();
    }

    /**
     * Matches against <code>null</code> or <code>void</code>. Can be used to make sure no trailing events remain when
     * using an Exact Sequence Matcher ({@link #exactSequenceOf(org.hamcrest.Matcher[])}).
     *
     * @return a matcher that matches against "nothing".
     */
    @Factory
    public static NullOrVoidMatcher nothing() {
        return new NullOrVoidMatcher();
    }
}
