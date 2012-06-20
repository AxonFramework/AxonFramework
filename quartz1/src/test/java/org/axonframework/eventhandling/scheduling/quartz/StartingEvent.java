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

package org.axonframework.eventhandling.scheduling.quartz;

import org.axonframework.domain.ApplicationEvent;

/**
 * @author Allard Buijze
 */
public class StartingEvent extends ApplicationEvent {

    private static final long serialVersionUID = -6273851928820885751L;
    private final String association;

    /**
     * Initialize an application event with the given <code>source</code>. Source may be null. In that case, the source
     * type and source description will be set to <code>Object.class</code> and <code>[unknown source]</code>
     * respectively.
     *
     * @param source the instance that reported this event. If any.
     */
    public StartingEvent(Object source, String association) {
        super(source);
        this.association = association;
    }

    public String getAssociation() {
        return association;
    }
}
