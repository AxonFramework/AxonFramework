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

package org.axonframework.modelling.entity.domain.pullrequest.events;

import java.time.Instant;

/**
 * Event indicating that a Pull Request was merged.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public record PullRequestMerged(String pullRequestId, String title, String author, String mergedBy, Instant mergedAt) {
}
