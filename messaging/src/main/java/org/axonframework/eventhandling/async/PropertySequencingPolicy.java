package org.axonframework.eventhandling.async;

import static org.axonframework.common.BuilderUtils.assertNonNull;

import java.util.function.Function;

import org.axonframework.common.property.Property;
import org.axonframework.common.property.PropertyAccessStrategy;
import org.axonframework.eventhandling.EventMessage;

/**
 * A {@link SequencingPolicy} implementation that extracts the sequence identifier from the event message payload based
 * on a given property or property extractor. If the event message payload is not of a supported type, a fallback 
 * sequencing policy is used. By default the fallback sequencing policy raises an exception.
 *
 * @param <T>
 *            The type of the supported event payloads.
 * @param <K>
 *            The type of the extracted property.
 *
 * @author Nils Christian Ehmke
 * @since 4.5.2
 */
@SuppressWarnings( "rawtypes" )
public final class PropertySequencingPolicy<T, K> implements SequencingPolicy<EventMessage> {

	private final Class<T> payloadClass;
	private final Function<T, K> propertyExtractor;
	private final SequencingPolicy<EventMessage> fallbackSequencingPolicy;

	@SuppressWarnings( "unchecked" )
	private PropertySequencingPolicy( final Builder builder ) {
		payloadClass = builder.payloadClass;
		propertyExtractor = builder.propertyExtractor;
		fallbackSequencingPolicy = builder.fallbackSequencingPolicy;
	}

	@Override
	public Object getSequenceIdentifierFor( final EventMessage eventMessage ) {
		if ( payloadClass.isAssignableFrom( eventMessage.getPayloadType( ) ) ) {
			@SuppressWarnings( "unchecked" )
			final T castedPayload = ( T ) eventMessage.getPayload( );
			return propertyExtractor.apply( castedPayload );
		}

		return fallbackSequencingPolicy.getSequenceIdentifierFor( eventMessage );
	}

	/**
	 * Instantiate a Builder to be able to create a {@link PropertySequencingPolicy}.
	 * <p>
	 * Upon initialization of this builder, the following fields are defaulted:
	 * <ul>
	 * <li>The {@code fallbackSequencingPolicy} defaults to an exception raising sequencing policy.
	 * </ul>
	 * The following fields of this builder are <b>hard requirements</b> and as such should be provided:
	 * <ul>
	 * <li>The {@code payloadClass} which defines the supported type of event message payload.</li>
	 * <li>The {@code propertyExtractor} which is applied to the payload and extract the sequence identifier.</li>
	 * <li>The {@code fallbackSequencingPolicy} which defines the behaviour in case of an unsupported event payload.</li>
	 * </ul>
	 *
	 * @param payloadClass
	 *                      The class of the supported event payloads.
	 * @param propertyClass
	 *                      The class of the extracted property.
	 *
	 * @param <T>
	 *                      The type of the supported event payloads.
	 * @param <K>
	 *                      The type of the extracted property.
	 *
	 * @return a Builder to be able to create a {@link PropertySequencingPolicy}
	 */
	public static <T, K> Builder<T, K> builder( final Class<T> payloadClass, final Class<K> propertyClass ) {
		return new Builder<>( payloadClass );
	}

	/**
	 * Builder class to instantiate a {@link PropertySequencingPolicy}.
	 * <p>
	 * Upon initialization of this builder, the following fields are defaulted:
	 * <ul>
	 * <li>The {@code fallbackSequencingPolicy} defaults to an exception raising sequencing policy.
	 * </ul>
	 * The following fields of this builder are <b>hard requirements</b> and as such should be provided:
	 * <ul>
	 * <li>The {@code payloadClass} which defines the supported type of event message payload.</li>
	 * <li>The {@code propertyExtractor} which is applied to the payload and extract the sequence identifier.</li>
	 * <li>The {@code fallbackSequencingPolicy} which defines the behaviour in case of an unsupported event payload.</li>
	 * </ul>
	 *
	 * @param <T>
	 *            The type of the supported event payloads.
	 * @param <K>
	 *            The type of the extracted property.
	 */
	public static final class Builder<T, K> {

		private final Class<T> payloadClass;
		private Function<T, K> propertyExtractor;
		private SequencingPolicy<EventMessage> fallbackSequencingPolicy = ExceptionRaisingSequencingPolicy.instance( );

		private Builder( final Class<T> payloadClass ) {
			assertNonNull( payloadClass, "Payload class may not be null" );
			this.payloadClass = payloadClass;
		}

		/**
		 * Defines the property extractor, a function which is applied to the event message payload to extract the 
		 * sequence identifier. This is usually some kind of getter reference.
		 *
		 * @param propertyExtractor
		 *                          The new property extractor.
		 *
		 * @return The current Builder instance, for fluent interfacing
		 */
		public Builder<T, K> propertyExtractor( final Function<T, K> propertyExtractor ) {
			assertNonNull( propertyExtractor, "Property extractor may not be null" );
			this.propertyExtractor = propertyExtractor;
			return this;
		}

		/**
		 * Defines the name of the property to be extracted as sequence identifier. This can be used as an alternative
		 * to {@link #propertyExtractor(Function)}.
		 *
		 * @param propertyName
		 *                     The new name of the property to be extracted.
		 *
		 * @return The current Builder instance, for fluent interfacing
		 */
		public Builder<T, K> propertyName( final String propertyName ) {
			assertNonNull( propertyName, "Property may not be null" );
			final Property<T> property = PropertyAccessStrategy.getProperty( payloadClass, propertyName );
			assertNonNull( property, "Property cannot be found" );
			this.propertyExtractor = property::getValue;
			return this;
		}

		/**
		 * Defines the fallback sequencing policy, the sequencing policy which is applied if the event message payload
		 * is not of a supported type. Defaults to a policy that simply raises an exception.
		 *
		 * @param fallbackSequencingPolicy
		 *                                 The new fallback sequencing policy.
		 *
		 * @return The current Builder instance, for fluent interfacing
		 */
		public Builder<T, K> fallbackSequencingPolicy( final SequencingPolicy<EventMessage> fallbackSequencingPolicy ) {
			assertNonNull( fallbackSequencingPolicy, "Fallback sequencing policy may not be null" );
			this.fallbackSequencingPolicy = fallbackSequencingPolicy;
			return this;
		}

		/**
		 * Initializes a {@link PropertySequencingPolicy} as specified through this Builder.
		 *
		 * @return a {@link PropertySequencingPolicy} as specified through this Builder
		 */
		public PropertySequencingPolicy<T, K> build( ) {
			validate( );
			return new PropertySequencingPolicy<>( this );
		}

		private void validate( ) {
			assertNonNull( payloadClass, "The payload class is a hard requirement and should be provided" );
			assertNonNull( propertyExtractor, "The property extractor is a hard requirement and should be provided" );
			assertNonNull( fallbackSequencingPolicy, "The fallback sequencing policy is a hard requirement and should be provided" );
		}

		/**
		 * A simple implementation of a {@link SequencingPolicy} that raises an {@link IllegalArgumentException}.
		 */
		private static final class ExceptionRaisingSequencingPolicy implements SequencingPolicy<EventMessage> {

			private static final ExceptionRaisingSequencingPolicy INSTANCE = new ExceptionRaisingSequencingPolicy( );

			public static ExceptionRaisingSequencingPolicy instance( ) {
				return INSTANCE;
			}

			@Override
			public Object getSequenceIdentifierFor( final EventMessage eventMessage ) {
				throw new IllegalArgumentException( "The event message payload is not of a supported type. Either make sure that the processor only consumes supported events or add a fallback sequencing policy." );
			}

		}

	}

}
