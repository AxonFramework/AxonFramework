package org.axonframework.commandhandling.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for field that contain an Entity capable of handling Commands on behalf of the aggregate. When
 * a field is annotated with <code>@CommandHandlerMember</code>, it is a hint towards Command Handler discovery
 * mechanisms that the entity should also be inspected for {@link CommandHandler @CommandHandler} annotated methods.
 * <p/>
 * Note that CommandHandler detection is done using static typing. This means that only the declared type of the field
 * can be inspected. If a subclass of that type is assigned to the field, any handlers declared on that subclass will
 * be ignored.
 *
 * @author Allard Buijze
 * @since 2.2
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface CommandHandlingMember {

}
