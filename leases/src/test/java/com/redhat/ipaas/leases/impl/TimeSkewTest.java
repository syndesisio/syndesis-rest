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
package com.redhat.ipaas.leases.impl;

import org.junit.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.skife.jdbi.v2.DBI;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class TimeSkewTest {

    private static final int LEASE_DURATION = 5 * 1000;

    /**
     * if the POSTGRESQL_HOSTNAME env is set, then use a PostgreSQL connection.
     *
     * @return
     */
    private DataSource createDataSource() {

        if( System.getenv("POSTGRESQL_HOSTNAME") !=null ) {
            System.out.println("Testing against a PostgreSQL DB");
            PGSimpleDataSource ds = new PGSimpleDataSource();

            ds.setServerName(System.getenv("POSTGRESQL_HOSTNAME"));
            ds.setDatabaseName("test");

            if (System.getenv("POSTGRESQL_DATABASE") != null) {
                ds.setDatabaseName(System.getenv("POSTGRESQL_DATABASE"));
            }
            if (System.getenv("POSTGRESQL_USER") != null) {
                ds.setUser(System.getenv("POSTGRESQL_USER"));
            }
            if (System.getenv("POSTGRESQL_PASSWORD") != null) {
                ds.setPassword(System.getenv("POSTGRESQL_PASSWORD"));
            }
            return ds;
        }

        System.out.println("Testing against a SQLite DB");
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:target/sqlite.db");
        return ds;
    }


    @Test
    public void timeSkewedBox() throws Exception {
        DataSource ds = createDataSource();
        DBI dbi = new DBI(ds);

        SqlLeaseManager manager = new SqlLeaseManager(dbi) {
            /* lets pretend to have a slow clock */
            protected long currentTimeMillis() {
                return super.currentTimeMillis() - LEASE_DURATION;
            }
        };
        manager.setLeaseDuration(LEASE_DURATION);
        try {
            manager.start();
            fail("Expected exception.");
        } catch (IllegalStateException e) {
            assertThat(e.toString()).isEqualTo("java.lang.IllegalStateException: This machine's time is not synchronized with the database.");
        }
    }
}
