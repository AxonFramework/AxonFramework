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

import org.axonframework.serializer.ChainingConverterFactory;
import org.axonframework.serializer.ConverterFactory;
import org.axonframework.serializer.Serializer;
import org.axonframework.upcasting.LazyUpcasterChain;
import org.axonframework.upcasting.SimpleUpcasterChain;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

import static org.axonframework.contextsupport.spring.AutowiredBean.createAutowiredBeanWithFallback;

/**
 * BeanDefinitionParser that parses UpcasterChain elements.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class UpcasterChainBeanDefinitionParser {

    private static final String CONVERTER_FACTORY_ATTRIBUTE = "converter-factory";
    private static final String SERIALIZER_ATTRIBUTE = "serializer";
    private static final String STRATEGY_ATTRIBUTE = "strategy";
    private static final String STRATEGY_EAGER = "eager";

    /**
     * Parses the given element representing an UpcasterChain definition and returns the corresponding BeanDefinition.
     *
     * @param element       The element in the application context representing the UpcasterChain
     * @param parserContext The parserContext from the application context
     * @return The BeanDefinition representing the UpcasterChainBean
     */
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        return parse(element, parserContext, createAutowiredBeanWithFallback(new ChainingConverterFactory(),
                                                                             ConverterFactory.class,
                                                                             Serializer.class));
    }

    /**
     * Parses the given <code>element</code>, using the given <code>defaultSerializer</code> if none is explicitly
     * configured.
     *
     * @param element           The element in the application context representing the UpcasterChain
     * @param parserContext     The parserContext from the application context
     * @param defaultSerializer The serializer (definition) to use when none is explicitly configured
     * @return The BeanDefinition representing the UpcasterChainBean
     */
    public BeanDefinition parse(Element element, ParserContext parserContext, Object defaultSerializer) {
        String strategy = element.getAttribute(STRATEGY_ATTRIBUTE);
        Class<?> chainType = LazyUpcasterChain.class;
        if (STRATEGY_EAGER.equals(strategy)) {
            chainType = SimpleUpcasterChain.class;
        }
        BeanDefinition bd = BeanDefinitionBuilder.genericBeanDefinition(chainType)
                                                 .getBeanDefinition();
        bd.getConstructorArgumentValues().addIndexedArgumentValue(1, parserContext.getDelegate()
                                                                                  .parseListElement(element, bd));
        if (element.hasAttribute(CONVERTER_FACTORY_ATTRIBUTE)) {
            bd.getConstructorArgumentValues()
              .addGenericArgumentValue(new RuntimeBeanReference(element.getAttribute(CONVERTER_FACTORY_ATTRIBUTE)));
        } else if (element.hasAttribute(SERIALIZER_ATTRIBUTE)) {
            bd.getConstructorArgumentValues().addGenericArgumentValue(new RuntimeBeanReference(element.getAttribute(
                    SERIALIZER_ATTRIBUTE)));
        } else {
            bd.getConstructorArgumentValues().addIndexedArgumentValue(0, defaultSerializer);
        }
        return bd;
    }
}
