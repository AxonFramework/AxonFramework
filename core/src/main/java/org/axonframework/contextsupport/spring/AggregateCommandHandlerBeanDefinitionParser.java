/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.contextsupport.spring;

import org.axonframework.commandhandling.annotation.AggregateAnnotationCommandHandler;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

/**
 * Bean Definition parser that parses AggregateAnnotationCommandHandler type beans (aggregate-command-handler element).
 *
 * @author Allard Buijze
 * @since 1.2
 */
public class AggregateCommandHandlerBeanDefinitionParser extends AbstractBeanDefinitionParser {

    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(AggregateAnnotationCommandHandler.class);
        beanDefinition.getConstructorArgumentValues()
                      .addIndexedArgumentValue(0, element.getAttribute("aggregate-type"));
        beanDefinition.getConstructorArgumentValues()
                      .addIndexedArgumentValue(1, new RuntimeBeanReference(element.getAttribute("repository")));
        beanDefinition.getConstructorArgumentValues()
                      .addIndexedArgumentValue(2, new RuntimeBeanReference(element.getAttribute("command-bus")));
        if (element.hasAttribute("command-target-resolver")) {
            beanDefinition.getConstructorArgumentValues()
                          .addIndexedArgumentValue(3, new RuntimeBeanReference(
                                  element.getAttribute("command-target-resolver")));
        }
        beanDefinition.setInitMethodName("subscribe");
        beanDefinition.setDestroyMethodName("unsubscribe");
        return beanDefinition;
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }
}
