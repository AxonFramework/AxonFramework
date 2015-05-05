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

package org.axonframework.spring.config.xml;

import org.axonframework.spring.config.annotation.AggregateAnnotationCommandHandlerFactoryBean;
import org.axonframework.spring.config.annotation.SpringContextParameterResolverFactoryBuilder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
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
        BeanDefinitionBuilder builder =
                BeanDefinitionBuilder.genericBeanDefinition(AggregateAnnotationCommandHandlerFactoryBean.class)
                                     .addPropertyValue("aggregateType", element.getAttribute("aggregate-type"))
                                     .addPropertyReference("repository", element.getAttribute("repository"))
                                     .addPropertyReference("commandBus", element.getAttribute("command-bus"))
                                     .addPropertyValue("parameterResolverFactory",
                                                       SpringContextParameterResolverFactoryBuilder.getBeanReference(
                                                               parserContext.getRegistry()));
        if (element.hasAttribute("command-target-resolver")) {
            builder.addPropertyReference("commandTargetResolver", element.getAttribute("command-target-resolver"));
        }
        return builder.getBeanDefinition();
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }
}
