package org.axonframework.sample.app.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * @author Jettro Coenradie
 */
@Repository
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class JpaContactNameRepository implements ContactNameRepository {
    private static final Logger logger = LoggerFactory.getLogger(JpaContactNameRepository.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean claimContactName(String contactName) {
        try {
            entityManager.persist(new ClaimedContactName(contactName));
            entityManager.flush();
            return true;
        } catch (RuntimeException e) {
            logger.warn("Unable to claim contact name.", e);
            return false;
        }
    }

    @Override
    public void cancelContactName(String contactName) {
        ClaimedContactName claimedContactName = entityManager.getReference(ClaimedContactName.class, contactName);
        entityManager.remove(claimedContactName);
    }
}
