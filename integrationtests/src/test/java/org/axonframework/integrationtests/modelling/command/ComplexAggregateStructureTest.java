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

package org.axonframework.integrationtests.modelling.command;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.modelling.command.EntityId;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregate;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;

class ComplexAggregateStructureTest {

    @Test
    void commandsAreRoutedToCorrectEntity() throws Exception {
        AggregateModel<Book> bookAggregateModel = AnnotatedAggregateMetaModelFactory.inspectAggregate(Book.class);
        EventBus mockEventBus = SimpleEventBus.builder().build();
        AnnotatedAggregate<Book> bookAggregate = AnnotatedAggregate.initialize(
                (Callable<Book>) () -> {
                    Book aggregate = new Book();
                    aggregate.handle(new CreateBookCommand("book1"));
                    return aggregate;
                },
                bookAggregateModel, mockEventBus
        );
        bookAggregate.handle(command(new CreateBookCommand("book1")));
        bookAggregate.handle(command(new CreatePageCommand("book1")));
        bookAggregate.handle(command(new CreateParagraphCommand("book1", 0)));
        bookAggregate.handle(command(new CreateParagraphCommand("book1", 0)));
        bookAggregate.handle(command(new UpdateParagraphCommand("book1", 0, 0, "Hello world")));
        bookAggregate.handle(command(new UpdateParagraphCommand("book1", 0, 1, "Hello world2")));

        assertEquals("Hello world",
                     bookAggregate.getAggregateRoot().getPages().get(0).getParagraphs().get(0).getText());
        assertEquals("Hello world2",
                     bookAggregate.getAggregateRoot().getPages().get(0).getParagraphs().get(1).getText());
    }

    private CommandMessage<Object> command(Object payload) {
        return GenericCommandMessage.asCommandMessage(payload);
    }

    @SuppressWarnings("unused")
    public static class Book {

        @AggregateIdentifier
        private String bookId;

        @AggregateMember
        private final List<Page> pages = new ArrayList<>();
        private int lastPage = -1;

        public Book() {
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(CreateBookCommand cmd) {
            apply(new BookCreatedEvent(cmd.getBookId()));
        }

        @CommandHandler
        public void handle(CreatePageCommand cmd) {
            apply(new PageCreatedEvent(cmd.getBookId(), lastPage + 1));
        }

        @EventSourcingHandler
        protected void handle(BookCreatedEvent event) {
            this.bookId = event.getBookId();
        }

        @EventSourcingHandler
        protected void handle(PageCreatedEvent event) {
            this.lastPage = event.getPageId();
            pages.add(new Page(event.getPageId()));
        }

        public List<Page> getPages() {
            return pages;
        }

        public String getBookId() {
            return bookId;
        }
    }

    @SuppressWarnings("unused")
    public static class Page {

        @EntityId
        private final int pageNumber;

        @AggregateMember
        private final List<Paragraph> paragraphs = new ArrayList<>();

        private int lastParagraphId = -1;

        public Page(int pageNumber) {
            this.pageNumber = pageNumber;
        }

        @CommandHandler
        public void handle(CreateParagraphCommand cmd) {
            apply(new ParagraphCreatedEvent(cmd.getBookId(), pageNumber, lastParagraphId + 1));
        }

        @EventSourcingHandler
        protected void handle(ParagraphCreatedEvent event) {
            this.lastParagraphId = event.getParagraphId();
            this.paragraphs.add(new Paragraph(event.getParagraphId()));
        }

        public List<Paragraph> getParagraphs() {
            return paragraphs;
        }

        public int getPageNumber() {
            return pageNumber;
        }
    }

    @SuppressWarnings("unused")
    public static class Paragraph {

        @EntityId
        private final int paragraphId;

        private String text;

        public Paragraph(int paragraphId) {
            this.paragraphId = paragraphId;
        }

        @CommandHandler
        public void handle(UpdateParagraphCommand cmd) {
            Assert.isTrue(cmd.getParagraphId() == paragraphId, () -> "UpdatePageCommand reached the wrong paragraph");
            apply(new ParagraphUpdatedEvent(cmd.getBookId(), cmd.getPageNumber(), paragraphId, cmd.getText()));
        }

        @EventSourcingHandler
        public void handle(ParagraphUpdatedEvent event) {
            if (event.getParagraphId() == paragraphId) {
                this.text = event.getText();
            }
        }

        public int getParagraphId() {
            return paragraphId;
        }

        public String getText() {
            return text;
        }
    }

    public static class CreateBookCommand {

        private final String bookId;

        private CreateBookCommand(String bookId) {
            this.bookId = bookId;
        }

        public String getBookId() {
            return bookId;
        }
    }

    public static class BookCreatedEvent {

        private final String bookId;

        public BookCreatedEvent(String bookId) {
            this.bookId = bookId;
        }

        public String getBookId() {
            return bookId;
        }
    }

    public static class CreatePageCommand {

        @TargetAggregateIdentifier
        private final String bookId;

        private CreatePageCommand(String bookId) {
            this.bookId = bookId;
        }

        public String getBookId() {
            return bookId;
        }
    }

    @SuppressWarnings("unused")
    public static class PageCreatedEvent {

        private final String bookId;

        private final int pageId;

        public PageCreatedEvent(String bookId, int pageId) {
            this.bookId = bookId;
            this.pageId = pageId;
        }

        public String getBookId() {
            return bookId;
        }

        public int getPageId() {
            return pageId;
        }
    }

    @SuppressWarnings("unused")
    public static class CreateParagraphCommand {

        private final String bookId;

        private final int pageNumber;

        private CreateParagraphCommand(String bookId, int pageNumber) {
            this.bookId = bookId;
            this.pageNumber = pageNumber;
        }

        public String getBookId() {
            return bookId;
        }

        public int getPageNumber() {
            return pageNumber;
        }
    }

    @SuppressWarnings("unused")
    public static class ParagraphCreatedEvent {

        private final String bookId;
        private final int pageNumber;
        private final int paragraphId;

        public ParagraphCreatedEvent(String bookId, int pageNumber, int paragraphId) {
            this.bookId = bookId;
            this.pageNumber = pageNumber;
            this.paragraphId = paragraphId;
        }

        public String getBookId() {
            return bookId;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public int getParagraphId() {
            return paragraphId;
        }
    }

    public static class UpdateParagraphCommand {

        @TargetAggregateIdentifier
        private final String bookId;

        private final int pageNumber;
        private final int paragraphId;
        private final String text;

        private UpdateParagraphCommand(String bookId, int pageNumber, int paragraphId, String text) {
            this.bookId = bookId;
            this.pageNumber = pageNumber;
            this.paragraphId = paragraphId;
            this.text = text;
        }

        public String getBookId() {
            return bookId;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public int getParagraphId() {
            return paragraphId;
        }

        public String getText() {
            return text;
        }
    }

    @SuppressWarnings("unused")
    public static class ParagraphUpdatedEvent {

        private final String bookId;
        private final int pageNumber;
        private final int paragraphId;
        private final String text;

        public ParagraphUpdatedEvent(String bookId, int pageNumber, int paragraphId, String text) {
            this.bookId = bookId;
            this.pageNumber = pageNumber;
            this.paragraphId = paragraphId;
            this.text = text;
        }

        public String getBookId() {
            return bookId;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public int getParagraphId() {
            return paragraphId;
        }

        public String getText() {
            return text;
        }
    }
}
