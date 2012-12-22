package org.axonframework.common.property;


/**
 * PropertyAccessStrategy implementation that finds properties defined according to the Uniform Access Principle
 * (see <a href="http://en.wikipedia.org/wiki/Uniform_access_principle">wikipedia</a>).
 * For example, a property called <code>myProperty</code>, it will use a method called <code>myProperty()</code>;
 *
 * @author Maxim Fedorov
 * @author Allard Buijze
 * @since 2.0
 */
public class UniformPropertyAccessStrategy extends AbstractMethodPropertyAccessStrategy {

    @Override
    protected String getterName(String property) {
        return property;
    }

    @Override
    protected int getPriority() {
        return -1024;
    }
}
