/*
 * Copyright (c) 2010. Axon Framework
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

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.Query;

/**
 * @author Ben Z. Tels
 *
 */
public class EntityManagerMock implements EntityManager {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear() {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean contains(Object arg0) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Query createNamedQuery(String arg0) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Query createNativeQuery(String arg0) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Query createNativeQuery(String arg0, Class arg1) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Query createNativeQuery(String arg0, String arg1) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Query createQuery(String arg0) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T> T find(Class<T> arg0, Object arg1) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void flush() {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getDelegate() {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FlushModeType getFlushMode() {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T> T getReference(Class<T> arg0, Object arg1) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public EntityTransaction getTransaction() {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isOpen() {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void joinTransaction() {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void lock(Object arg0, LockModeType arg1) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T> T merge(T arg0) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void persist(Object arg0) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void refresh(Object arg0) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void remove(Object arg0) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setFlushMode(FlushModeType arg0) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

}
