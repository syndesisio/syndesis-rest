/**
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.ipaas.runtime.db;

import java.util.List;

import javax.inject.Inject;
import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.RollbackException;
import javax.persistence.criteria.CriteriaQuery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JPAService {
	private final static Logger logger = LoggerFactory.getLogger(JPAService.class);

	@Inject
	private EntityManager em;

	private static ThreadLocal<EntityManager> activeEM = new ThreadLocal<>();

	public void beginTx() throws Exception {
		if (activeEM.get() != null) {
			throw new StorageException("Transaction already active.");
		}
		activeEM.set(em);
		em.getTransaction().begin();
	}

	public void commitTx() throws StorageException {
		if (activeEM.get() == null) {
			throw new StorageException("Transaction not active.");
		}
		try {
			activeEM.get().getTransaction().commit();
			//activeEM.get().close();
			activeEM.set(null);
		} catch (EntityExistsException e) {
			throw new StorageException(e);
		} catch (RollbackException e) {
			logger.error(e.getMessage(), e);
			throw new StorageException(e);
		} catch (Throwable t) {
			logger.error(t.getMessage(), t);
			throw new StorageException(t);
		}
	}
	
    public void rollbackTx() {
        if (activeEM.get() != null) {
	        try {
	            if (activeEM.get().getTransaction().isActive()) {
	            	activeEM.get().getTransaction().rollback();
	            }
	        } finally {
	            //activeEM.get().close();
	            activeEM.set(null);
	        }
        }
    }

	public <T> T get(Class<T> entityClass, Long id) {
		return em.find(entityClass, id);
	}

	public <T> void update(T entity) {
		if (!em.contains(entity)) {
			em.merge(entity);
		}
	}

	public <T> T create(T entity) {
		em.persist(entity);
		return entity;
	}

	public <T> void delete(T entity) {
		em.remove(entity);
	}
	
	public <T> List<T> getAll(Class<T> entityClass) {
		CriteriaQuery<T> criteria = em.getCriteriaBuilder().createQuery(entityClass);
		criteria.select(criteria.from(entityClass));
		List<T> resultList = em.createQuery(criteria).getResultList();
		return resultList;
	}



	//	@Transactional
	//	public Locker getUserInfoByName(String username) throws Exception
	//	{
	//		Locker locker = null;
	//		try {			
	//			String namedQuery = "UserAccountByUserName";
	//			em.create
	//			theUserAccount = (UserAccount) entityManager.createNamedQuery(namedQuery)
	//					.setParameter("userNameParam", username).getSingleResult();
	//		} catch (NoResultException nrex) {
	//			
	//		} catch (Exception e) {
	//			logger.error("getUserInfoByName(), unexpected error", e);
	//			throw new TransactionException(e);
	//		}
	//		return locker;
	//	}
}