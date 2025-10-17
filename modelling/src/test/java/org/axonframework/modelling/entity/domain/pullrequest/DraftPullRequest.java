/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.modelling.entity.domain.pullrequest;

import org.axonframework.eventhandling.annotations.EventHandler;
import org.axonframework.modelling.entity.domain.pullrequest.events.PullRequestMarkedAsReadyForReview;

/**
 * Represents a Pull Request in draft state.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public record DraftPullRequest(String id, String title, String author) implements PullRequest {

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getAuthor() {
        return author;
    }

    @EventHandler
    public OpenedPullRequest on(PullRequestMarkedAsReadyForReview event) {
        return new OpenedPullRequest(event.pullRequestId(), event.title(), event.author());
    }
}
