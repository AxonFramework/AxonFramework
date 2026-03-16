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

package org.axonframework.messaging.eventhandling.replay.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


import org.axonframework.messaging.core.annotation.MessageHandler;
import org.axonframework.messaging.eventhandling.replay.ReplayStatus;
import org.axonframework.messaging.eventhandling.replay.ReplayStatusChange;

/**
 * Annotation that can be placed on a method that is to be invoked when the {@link ReplayStatus} is about to change.
 * <p>
 * In doing so, this handler has two concrete moments when it is invoked:
 * <ol>
 *     <li>When the {@code ReplayStatus} changes from {@link ReplayStatus#REGULAR} to {@link ReplayStatus#REPLAY}</li>
 *     <li>When the {@code ReplayStatus} changes from {@link ReplayStatus#REPLAY} to {@link ReplayStatus#REGULAR}</li>
 * </ol>
 * <p>
 * Methods annotated with this annotation thus process replay status changes, typically to prepare for and finalize an event
 * replay. Actions to consider during a {@code ReplayStatus} change are cleaning up the projection state, clearing
 * caches, or switching storage solution aliases. To that end, the {@code ReplayStatusChange} message contains the
 * {@code ReplayStatus} that will be changed to.
 *
 * @author Simon Zambrovski
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @see org.axonframework.messaging.eventhandling.replay.ReplayStatusChangeHandler
 * @since 5.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@MessageHandler(messageType = ReplayStatusChange.class)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface ReplayStatusChangeHandler {

}
