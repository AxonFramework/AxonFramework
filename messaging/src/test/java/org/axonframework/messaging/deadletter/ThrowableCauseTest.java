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

package org.axonframework.messaging.deadletter;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating {@link ThrowableCause}.
 *
 * @author Steven van Beelen
 */
class ThrowableCauseTest {

    private static final String BLOB_OF_TEXT = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, "
            + "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. "
            + "Gravida quis blandit turpis cursus in. "
            + "Nulla facilisi etiam dignissim diam quis enim lobortis scelerisque fermentum. "
            + "Egestas maecenas pharetra convallis posuere morbi leo urna. "
            + "Dictumst quisque sagittis purus sit amet volutpat consequat. "
            + "At volutpat diam ut venenatis tellus in metus vulputate eu. "
            + "Imperdiet dui accumsan sit amet nulla facilisi. Eget est lorem ipsum dolor sit amet. "
            + "Vestibulum morbi blandit cursus risus at ultrices mi tempus imperdiet. "
            + "Sed tempus urna et pharetra pharetra massa massa. Dolor magna eget est lorem. "
            + "Purus semper eget duis at tellus. "
            + "Tincidunt augue interdum velit euismod in pellentesque massa placerat duis."
            + "\n\n"
            + "Quis ipsum suspendisse ultrices gravida dictum fusce ut. "
            + "Nascetur ridiculus mus mauris vitae ultricies leo integer malesuada. "
            + "Sit amet purus gravida quis blandit turpis cursus in. "
            + "Gravida rutrum quisque non tellus. "
            + "Eros donec ac odio tempor orci dapibus. "
            + "Dictum varius duis at consectetur lorem donec massa sapien."
            + "Tincidunt arcu non sodales neque sodales ut etiam sit amet. "
            + "Sagittis aliquam malesuada bibendum arcu vitae. "
            + "Vel turpis nunc eget lorem dolor sed viverra. "
            + "In egestas erat imperdiet sed euismod nisi. "
            + "Lorem ipsum dolor sit amet consectetur.";

    @Test
    void constructThrowableCauseWithThrowable() {
        Throwable testThrowable = new RuntimeException("some-message");

        ThrowableCause testSubject = new ThrowableCause(testThrowable);

        assertEquals(testThrowable.getClass().getName(), testSubject.type());
        assertEquals(testThrowable.getMessage(), testSubject.message());
    }

    @Test
    void constructThrowableCauseWithTypeAndMessage() {
        String testType = "type";
        String testMessage = "message";

        ThrowableCause testSubject = new ThrowableCause(testType, testMessage);

        assertEquals(testType, testSubject.type());
        assertEquals(testMessage, testSubject.message());
    }

    @Test
    void asCauseWrapsThrowable() {
        Throwable testThrowable = new RuntimeException("some-message");

        ThrowableCause result = ThrowableCause.asCause(testThrowable);

        assertEquals(testThrowable.getClass().getName(), result.type());
        assertEquals(testThrowable.getMessage(), result.message());
    }

    @Test
    void asCauseReturnsCauseAsIs() {
        ThrowableCause testCause = new ThrowableCause("type", "message");

        ThrowableCause result = ThrowableCause.asCause(testCause);

        assertEquals(testCause, result);
    }

    @Test
    void truncateLeavesMessageAsIsIfItDoesNotExceedMessageSize() {
        Throwable testThrowable = new RuntimeException("some-message");

        ThrowableCause result = ThrowableCause.truncated(testThrowable);

        assertEquals(testThrowable.getClass().getName(), result.type());
        assertEquals(testThrowable.getMessage(), result.message());
    }

    @Test
    void truncateShortensMessageIfItExceedsMessageSize() {
        String textThatShouldBeTruncated = "truncated-text-at-the-end";
        Throwable testThrowable = new RuntimeException(BLOB_OF_TEXT + textThatShouldBeTruncated);

        ThrowableCause result = ThrowableCause.truncated(testThrowable);

        assertEquals(testThrowable.getClass().getName(), result.type());
        assertNotEquals(testThrowable.getMessage(), result.getMessage());
        assertFalse(result.getMessage().contains(textThatShouldBeTruncated));
    }

    @Test
    void truncateWithSpecifiedMessageSizeLeavesMessageAsIsIfItDoesNotExceedGivenSize() {
        String testMessage = "some-message";
        Throwable testThrowable = new RuntimeException(testMessage);

        ThrowableCause result = ThrowableCause.truncated(testThrowable, testMessage.length());

        assertEquals(testThrowable.getClass().getName(), result.type());
        assertEquals(testThrowable.getMessage(), result.message());
    }

    @Test
    void truncateWithSpecifiedMessageSizeShortensMessageIfItExceedsGivenSize() {
        String textThatShouldBeTruncated = "truncated-text-at-the-end";
        Throwable testThrowable = new RuntimeException(textThatShouldBeTruncated);
        int testMessageSize = textThatShouldBeTruncated.length() - 3;

        ThrowableCause result = ThrowableCause.truncated(testThrowable, testMessageSize);

        assertEquals(testThrowable.getClass().getName(), result.type());
        assertNotEquals(testThrowable.getMessage(), result.getMessage());
        assertFalse(result.getMessage().contains(textThatShouldBeTruncated));
        assertTrue(result.getMessage().contains(textThatShouldBeTruncated.substring(0, testMessageSize)));
    }
}