package org.axonframework.upcasting;

import org.axonframework.serializer.ConverterFactory;

/**
 * @author Allard Buijze
 */
public class SimpleUpcasterChainTest extends UpcasterChainTest {

    @Override
    protected UpcasterChain createUpcasterChain(ConverterFactory converterFactory, Upcaster... upcasters) {
        return new SimpleUpcasterChain(converterFactory, upcasters);
    }
}
