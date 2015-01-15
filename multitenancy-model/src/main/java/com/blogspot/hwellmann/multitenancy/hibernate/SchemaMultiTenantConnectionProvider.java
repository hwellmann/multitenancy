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
package com.blogspot.hwellmann.multitenancy.hibernate;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.Context;
import javax.sql.DataSource;

import org.hibernate.HibernateException;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.jdbc.connections.spi.AbstractDataSourceBasedMultiTenantConnectionProviderImpl;
import org.hibernate.engine.jndi.spi.JndiService;
import org.hibernate.service.spi.ServiceRegistryAwareService;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.service.spi.Stoppable;

/**
 * @author Harald Wellmann
 *
 */
public class SchemaMultiTenantConnectionProvider extends
    AbstractDataSourceBasedMultiTenantConnectionProviderImpl implements
    ServiceRegistryAwareService, Stoppable {

    /**
     * Identifies the DataSource name to use for {@link #selectAnyDataSource} handling
     */
    public static final String TENANT_IDENTIFIER_TO_USE_FOR_ANY_KEY = "hibernate.multi_tenant.datasource.identifier_for_any";

    private Map<String, DataSource> dataSourceMap;
    private JndiService jndiService;
    private String tenantIdentifierForAny;
    private String baseJndiNamespace;

    @Override
    protected DataSource selectAnyDataSource() {
        return selectDataSource(tenantIdentifierForAny);
    }

    @Override
    protected DataSource selectDataSource(String tenantIdentifier) {
        DataSource dataSource = dataSourceMap().get(tenantIdentifier);
        if (dataSource == null) {
            dataSource = (DataSource) jndiService
                .locate(baseJndiNamespace + '/' + tenantIdentifierForAny);
            dataSourceMap().put(tenantIdentifier, dataSource);
        }
        return dataSource;
    }

    /* (non-Javadoc)
     * @see org.hibernate.engine.jdbc.connections.spi.AbstractDataSourceBasedMultiTenantConnectionProviderImpl#getConnection(java.lang.String)
     */
    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = super.getConnection(tenantIdentifier);
        System.out.println("setting schema " + tenantIdentifier);
        try (Statement st = connection.createStatement()) {
            st.execute("SET search_path TO " + tenantIdentifier);
        }
        return connection;
    }

    private Map<String, DataSource> dataSourceMap() {
        if (dataSourceMap == null) {
            dataSourceMap = new ConcurrentHashMap<String, DataSource>();
        }
        return dataSourceMap;
    }

    @Override
    public void injectServices(ServiceRegistryImplementor serviceRegistry) {
        final Object dataSourceConfigValue = serviceRegistry.getService(ConfigurationService.class)
            .getSettings().get(AvailableSettings.DATASOURCE);
        if (dataSourceConfigValue == null || !String.class.isInstance(dataSourceConfigValue)) {
            throw new HibernateException(
                "Improper set up of DataSourceBasedMultiTenantConnectionProviderImpl");
        }
        final String jndiName = (String) dataSourceConfigValue;

        jndiService = serviceRegistry.getService(JndiService.class);
        if (jndiService == null) {
            throw new HibernateException(
                "Could not locate JndiService from DataSourceBasedMultiTenantConnectionProviderImpl");
        }

        final Object namedObject = jndiService.locate(jndiName);
        if (namedObject == null) {
            throw new HibernateException("JNDI name [" + jndiName + "] could not be resolved");
        }

        if (DataSource.class.isInstance(namedObject)) {
            final int loc = jndiName.lastIndexOf("/");
            this.baseJndiNamespace = jndiName.substring(0, loc);
            this.tenantIdentifierForAny = jndiName.substring(loc + 1);
            dataSourceMap().put(tenantIdentifierForAny, (DataSource) namedObject);
        }
        else if (Context.class.isInstance(namedObject)) {
            this.baseJndiNamespace = jndiName;
            this.tenantIdentifierForAny = (String) serviceRegistry
                .getService(ConfigurationService.class).getSettings()
                .get(TENANT_IDENTIFIER_TO_USE_FOR_ANY_KEY);
            if (tenantIdentifierForAny == null) {
                throw new HibernateException(
                    "JNDI name named a Context, but tenant identifier to use for ANY was not specified");
            }
        }
        else {
            throw new HibernateException("Unknown object type [" + namedObject.getClass().getName()
                + "] found in JNDI location [" + jndiName + "]");
        }
    }

    @Override
    public void stop() {
        if (dataSourceMap != null) {
            dataSourceMap.clear();
            dataSourceMap = null;
        }
    }
}
