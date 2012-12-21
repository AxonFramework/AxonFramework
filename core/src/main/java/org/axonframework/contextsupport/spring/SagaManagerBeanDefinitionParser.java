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

import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

/**
 * Parses the saga-manager element. If that element contains an async child element, processing is forwarded to the
 * AsyncSagaManagerBeanDefinitionParser. Otherwise, the SyncSagaManagerBeanDefinitionParser will process the element.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SagaManagerBeanDefinitionParser extends AbstractBeanDefinitionParser {

    private final AsyncSagaManagerBeanDefinitionParser async;
    private final SyncSagaManagerBeanDefinitionParser sync;

    /**
     * Initializes a SagaManagerBeanDefinitionParser.
     */
    SagaManagerBeanDefinitionParser() {
        async = new AsyncSagaManagerBeanDefinitionParser();
        sync = new SyncSagaManagerBeanDefinitionParser();
    }

    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        if (DomUtils.getChildElementByTagName(element, "async") != null) {
            return async.parseInternal(element, parserContext);
        }
        return sync.parseInternal(element, parserContext);
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }
}
