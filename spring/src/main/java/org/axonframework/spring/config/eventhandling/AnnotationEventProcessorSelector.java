/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.spring.config.eventhandling;

import org.axonframework.eventhandling.EventListener;

import java.lang.annotation.Annotation;

/**
 * EventProcessorSelector implementation that selects an event processor if an Annotation is present on the
 * Event Listener class.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AnnotationEventProcessorSelector extends AbstractEventProcessorSelector {

    private final Class<? extends Annotation> annotationType;
    private final String eventProcessor;
    private final boolean inspectSuperClasses;

    /**
     * Initializes a EventProcessorSelector instance that selects the given <code>eventHandlerManager</code> for Event Listeners that are
     * annotated with the given <code>annotationType</code>.
     * <p/>
     * Note that this instance will <em>not</em> search superclasses for annotations by default. If annotation on
     * classes should also reflect on their subclasses, make sure to use the
     * {@link java.lang.annotation.Inherited @Inherited} Meta-Annotation, or use {@link
     * #AnnotationEventProcessorSelector(Class, String, boolean)}.
     * @param annotationType The type of annotation to find on the Event Listeners
     * @param eventProcessor The event processor to select if the annotation was found
     */
    public AnnotationEventProcessorSelector(Class<? extends Annotation> annotationType, String eventProcessor) {
        this(annotationType, eventProcessor, false);
    }

    /**
     * Initializes a EventProcessorSelector instance that selects the given <code>eventHandlerManager</code> for Event Listeners that are
     * annotated with the given <code>annotationType</code>. If <code>inspectSuperClasses</code> is <code>true</code>,
     * super classes will be searched for the annotation, regardless if the annotation is marked as {@link
     * java.lang.annotation.Inherited @Inherited}
     * @param annotationType      The type of annotation to find on the Event Listeners
     * @param eventProcessor      The event processor to select if the annotation was found
     * @param inspectSuperClasses Whether or not to inspect super classes as well
     */
    public AnnotationEventProcessorSelector(Class<? extends Annotation> annotationType, String eventProcessor,
                                            boolean inspectSuperClasses) {
        this.annotationType = annotationType;
        this.eventProcessor = eventProcessor;
        this.inspectSuperClasses = inspectSuperClasses;
    }


    @Override
    protected String doSelectEventHandlerManager(EventListener eventListener, Class<?> listenerType) {
        return annotationPresent(listenerType) ? eventProcessor : null;
    }

    private boolean annotationPresent(Class<?> listenerType) {
        return listenerType.isAnnotationPresent(annotationType)
                || (inspectSuperClasses
                && listenerType.getSuperclass() != null
                && annotationPresent(listenerType.getSuperclass()));
    }
}
