package org.axonframework.contextsupport.spring;

import org.axonframework.upcasting.LazyUpcasterChain;
import org.axonframework.upcasting.SimpleUpcasterChain;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

/**
 * BeanDefinitionParser that parses UpcasterChain elements.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class UpcasterChainBeanDefinitionParser {

    private static final String CONVERTER_FACTORY_ATTRIBUTE = "converter-factory";
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
        String strategy = element.getAttribute(STRATEGY_ATTRIBUTE);
        Class<?> chainType = LazyUpcasterChain.class;
        if (STRATEGY_EAGER.equals(strategy)) {
            chainType = SimpleUpcasterChain.class;
        }
        BeanDefinition bd = BeanDefinitionBuilder.genericBeanDefinition(chainType)
                                                 .getBeanDefinition();
        bd.getConstructorArgumentValues().addGenericArgumentValue(parserContext.getDelegate()
                                                                               .parseListElement(element, bd));
        if (element.hasAttribute(CONVERTER_FACTORY_ATTRIBUTE)) {
            bd.getConstructorArgumentValues()
              .addGenericArgumentValue(new RuntimeBeanReference(element.getAttribute(CONVERTER_FACTORY_ATTRIBUTE)));
        }
        return bd;
    }
}
