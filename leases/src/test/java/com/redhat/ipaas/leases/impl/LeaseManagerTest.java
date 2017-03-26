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

import com.redhat.ipaas.leases.Lease;
import com.redhat.ipaas.leases.LeaseManager;
import com.redhat.ipaas.test.Recorder;
import com.redhat.ipaas.test.Recordings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.skife.jdbi.v2.DBI;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class LeaseManagerTest {

    private static final int LEASE_DURATION = 5 * 1000;
    private SqlLeaseManager manager1;
    private SqlLeaseManager manager2;

    @Before
    public void before() {
        DataSource ds = createDataSource();
        DBI dbi = new DBI(ds);

        this.manager1 = new SqlLeaseManager(dbi);
        this.manager1.setLeaseDuration(LEASE_DURATION);
        try {
            this.manager1.dropTables();
        } catch (Exception e) {
        }
        this.manager1.start();

        this.manager2 = new SqlLeaseManager(dbi);
        this.manager2.setLeaseDuration(LEASE_DURATION);
        this.manager2.start();

    }

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

    @After
    public void after() {
        this.manager1.stop();
        this.manager2.stop();
    }

    protected LeaseManager getManager1() {
        return this.manager1;
    }
    protected LeaseManager getManager2() {
        return this.manager2;
    }

    @Test
    public void aquireAndCloseLease() throws Exception {

        // Verify we can get a lease.
        Recorder<Consumer> c1 = Recordings.recorder((Consumer<Lease>) x1 -> {}, Consumer.class);
        Lease lease1 = getManager1().create("a", c1.proxy());

        assertThat(c1.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(c1.invocations().size()).isEqualTo(1);
        assertThat(lease1.isAquired()).isTrue();

        // Verify that we can't get a 2nd lease, since the first one still exits.
        Recorder<Consumer> c2 = Recordings.recorder((Consumer<Lease>) x1 -> {}, Consumer.class);
        Lease lease2 = getManager2().create("a", c2.proxy());

        assertThat(c2.await(2, TimeUnit.SECONDS)).isFalse();

        assertThat(getManager1().getOwnerId()).isNotEqualTo(getManager2().getOwnerId());

        Map<String, String> allLeases = getManager1().getAllLeases();
        assertThat(allLeases.size()).isEqualTo(1);
        assertThat(allLeases.get("a")).isEqualTo(getManager1().getOwnerId());


        // Release lease1 so that lease2 can get a lease.
        c1.reset(1);
        c2.reset(1);
        lease1.release();

        assertThat(c1.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(c1.invocations().size()).isEqualTo(1);
        assertThat(lease1.isAquired()).isFalse();

        assertThat(c2.await(LEASE_DURATION, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(c2.invocations().size()).isEqualTo(1);
        assertThat(lease2.isAquired()).isTrue();

    }

    @Test
    public void aquireAndStallLease() throws Exception {

        // Verify we can get a lease.
        Recorder<Consumer> c1 = Recordings.recorder((Consumer<Lease>) x1 -> {}, Consumer.class);
        Lease lease1 = getManager1().create("a", c1.proxy());

        assertThat(c1.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(c1.invocations().size()).isEqualTo(1);
        assertThat(lease1.isAquired()).isTrue();

        // Verify that we can't get a 2nd lease, since the first one still exits.
        Recorder<Consumer> c2 = Recordings.recorder((Consumer<Lease>) x1 -> {}, Consumer.class);
        Lease lease2 = getManager2().create("a", c2.proxy());

        assertThat(c2.await(2, TimeUnit.SECONDS)).isFalse();

        CountDownLatch stallDone = new CountDownLatch(1);
        // simulate a s stalled JVM that does not renew it's leases..
        c1.reset(1);
        c2.reset(1);
        manager1.executor.execute(()->{
            try {
                Thread.sleep(LEASE_DURATION+1000);
                stallDone.countDown();
            } catch (InterruptedException e) {
            }
        });

        assertThat(c2.await(LEASE_DURATION+1000, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(c2.invocations().size()).isEqualTo(1);
        assertThat(lease2.isAquired()).isTrue();

        // Wait till we are not stalled out anymore..
        stallDone.await();
        // We should get evented that we have lost the lease.
        assertThat(c1.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(c1.invocations().size()).isEqualTo(1);
        assertThat(lease1.isAquired()).isFalse();

    }
}
