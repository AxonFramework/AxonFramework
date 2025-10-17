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
import org.axonframework.modelling.entity.domain.pullrequest.events.PullRequestClosed;
import org.axonframework.modelling.entity.domain.pullrequest.events.PullRequestMerged;

import java.time.Instant;

/**
 * Represents a Pull Request in opened state (ready for review).
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public record OpenedPullRequest(String id, String title, String author) implements PullRequest {

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
    public MergedPullRequest on(PullRequestMerged event) {
        return new MergedPullRequest(event.pullRequestId(), event.title(), event.author(), event.mergedBy(), event.mergedAt());
    }

    @EventHandler
    public ClosedPullRequest on(PullRequestClosed event) {
        return new ClosedPullRequest(event.pullRequestId(), event.title(), event.author(), event.closedBy(), event.closedAt());
    }
}
