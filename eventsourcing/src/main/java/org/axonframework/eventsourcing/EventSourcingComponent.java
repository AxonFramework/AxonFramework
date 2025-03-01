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

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.EventHandlingComponent;

/**
 * Interface describing a group of {@code EventSourcingHandlers} belonging to a single component.
 * <p>
 * As such, it allows registration of {@code EventSourcingHandlers} through the {@code EventHandlerRegistry}. Besides
 * handling and registration, it specifies which {@link #supportedEvents() events} it supports.
 *
 * @author Mateusz Nowak
 * @author Steven van Beelen
 * @since 5.0.0
 */
// todo: name - or `EventSourcedComponent`? I'm not sure if introduce another EventHandlerRegistry interface. Just ignore if no result?
public interface EventSourcingComponent extends IEventSourcingHandler, EventHandlingComponent {

}
