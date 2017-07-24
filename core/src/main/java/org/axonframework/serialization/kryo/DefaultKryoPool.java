package org.axonframework.serialization.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.pool.KryoCallback;
import com.esotericsoftware.kryo.pool.KryoPool;

/**
 * @author Reda.Housni-Alaoui
 * @since 3.1
 */
public class DefaultKryoPool implements KryoPool {

	private final KryoPool delegate;

	public DefaultKryoPool() {
		this.delegate = new KryoPool
				.Builder(new DefaultKryoFactory())
				.softReferences()
				.build();
	}

	@Override
	public Kryo borrow() {
		return delegate.borrow();
	}

	@Override
	public void release(Kryo kryo) {
		delegate.release(kryo);
	}

	@Override
	public <T> T run(KryoCallback<T> callback) {
		return delegate.run(callback);
	}
}
