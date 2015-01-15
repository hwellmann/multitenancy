/*
 * Copyright 2014 Harald Wellmann.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.blogspot.hwellmann.multitenancy;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.UserTransaction;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;

import com.blogspot.hwellmann.multitenancy.model.Author;
import com.blogspot.hwellmann.multitenancy.tenant.TenantHolder;


/**
 * @author Harald Wellmann
 *
 */
@RunWith(PaxExam.class)
//@Transactional
public class AuthorTest {

    @PersistenceContext
    private EntityManager em;

    @Inject
    private UserTransaction tx;

    @Inject
    private TenantHolder tenantHolder;

    @Test
    public void shouldCreateAuthor() throws Exception  {
        tenantHolder.setTenantId("blue");

        tx.begin();
        Author wilde = new Author();
        wilde.setFirstName("Oscar");
        wilde.setLastName("Wilde");
        em.persist(wilde);
        tx.commit();
    }

}
