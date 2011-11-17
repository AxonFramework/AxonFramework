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
class SagaManagerBeanDefinitionParser extends AbstractBeanDefinitionParser {

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
}
