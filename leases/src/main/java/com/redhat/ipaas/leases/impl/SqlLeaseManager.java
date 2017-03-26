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
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.PreparedBatch;
import org.skife.jdbi.v2.util.LongColumnMapper;
import org.skife.jdbi.v2.util.StringColumnMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Component
public class SqlLeaseManager implements LeaseManager {
    private static final Logger LOG = LoggerFactory.getLogger(SqlLeaseManager.class);

    private final DBI dbi;
    private final ConcurrentHashMap<String, SqlLease> leases = new ConcurrentHashMap<>();
    protected ExecutorService executor = Executors.newSingleThreadExecutor();
    protected ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final String ownerId = UUID.randomUUID().toString();
    private long leaseDuration = TimeUnit.SECONDS.toMillis(60);
    private String insertSQL;

    @Autowired
    public SqlLeaseManager(DBI dbi) {
        this.dbi = dbi;
    }

    @PostConstruct
    public void start() {
        executor = Executors.newSingleThreadExecutor();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        withDB(dbi -> {
            long dbTime = 0;

            // The insert SQL statement used will depend on the DB type that we are using.
            String dbName;
            try {
                dbName = dbi.getConnection().getMetaData().getDatabaseProductName();
            } catch (Exception e) {
                throw new IllegalStateException("Could not determine the database type", e);
            }

            if ("PostgreSQL".equals(dbName)) {
                insertSQL =
                        "INSERT into leases (lease, owner, expiration) " +
                                "values (:id, :owner, :expiration) " +
                                "ON CONFLICT(lease) DO NOTHING";

                try {
                    dbTime = dbi.createQuery("select extract(epoch from now()) * 1000")
                            .map(LongColumnMapper.PRIMITIVE)
                            .first().longValue();
                } catch (Exception e) {
                    // perhaps we are running agasint cockroachdb..
                    dbTime = dbi.createQuery("SELECT NOW()::DECIMAL * 1000")
                            .map(LongColumnMapper.PRIMITIVE)
                            .first().longValue();
                }


            } else if ("SQLite".equals(dbName)) {
                insertSQL =
                        "INSERT OR IGNORE into leases (lease, owner, expiration) " +
                                "values (:id, :owner, :expiration)";

                dbTime = dbi.createQuery("SELECT (julianday('now') - 2440587.5)*86400.0 * 1000")
                        .map(LongColumnMapper.PRIMITIVE)
                        .first().longValue();
            } else {
                throw new IllegalStateException("Unsupported DB: " + dbName);
            }

            // We are really sensitive to time skews.. so fail to start up if our time
            // is too different from the DB time.
            if (dbTime != 0) {
                long now = currentTimeMillis();
                long grace = leaseDuration / 4;
                if (!(now - grace < dbTime && dbTime < now + (leaseDuration / 4))) {
                    throw new IllegalStateException("This machine's time is not synchronized with the database.");
                }
            }

        });
        createTables();
        scheduler.schedule(this::refresh, leaseDuration / 2, TimeUnit.MILLISECONDS);
    }

    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
        executor.shutdown();
    }

    public void createTables() {
        withDB(dbi -> {
            dbi.update("CREATE TABLE IF NOT EXISTS leases (lease VARCHAR PRIMARY KEY, owner VARCHAR, expiration BIGINT)");
        });
    }

    public void dropTables() {
        withDB(dbi -> {
            dbi.update("DROP TABLE leases");
        });
    }

    public SqlLease create(String leaseId, Consumer<Lease> consumer) {
        SqlLease l = new SqlLease(this, leaseId, consumer);
        // synchronize this cause we want to make sure the user does not create duplicate leases.
        synchronized (this) {
            if (leases.containsKey(leaseId)) {
                throw new IllegalStateException("A lease already exists for: " + leaseId);
            }
            leases.put(leaseId, l);
        }
        executor.execute(this::doLeaseMaintenance);
        return l;
    }

    @Override
    public String getOwnerId() {
        return ownerId;
    }

    public static class LeaseOwner {
        private String lease;
        private String owner;

        public String getLease() {
            return lease;
        }

        public void setLease(String leaseId) {
            this.lease = leaseId;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }
    }

    @Override
    public Map<String, String> getAllLeases() {
        HashMap<String, String> result = new HashMap<>();
        withDB(dbi -> {
            dbi.createQuery("SELECT lease,owner FROM leases")
                    .map(LeaseOwner.class).forEach(x -> {
                result.put(x.getLease(), x.getOwner());
            });
        });
        return result;
    }

    protected void release(SqlLease lease) {
        synchronized (this) {
            leases.remove(lease.getLeaseId());
        }
        executor.execute(() -> {
            if (lease.isAquired()) {
                lease.fireReleased();
                withDB(dbi -> {
                    dbi.createStatement("DELETE FROM leases where lease = :lease and owner = :owner")
                            .bind("lease", lease.getLeaseId())
                            .bind("owner", ownerId)
                            .execute();
                });
            }
        });
    }

    protected void refresh() {
        executor.execute(() -> {
            doLeaseMaintenance();
            try {
                scheduler.schedule(this::refresh, leaseDuration / 2, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ignore) {
            }
        });
    }

    private void doLeaseMaintenance() {
        withDB(dbi -> {
            long now = currentTimeMillis();

            // Delete expired leases..
            dbi.createStatement("DELETE FROM leases where :now > expiration")
                    .bind("now", now)
                    .execute();

            // Refresh our leases.
            dbi.createStatement("UPDATE leases SET expiration=:expiration WHERE owner=:owner")
                    .bind("expiration", now + leaseDuration)
                    .bind("owner", ownerId)
                    .execute();

            // We probably will have a big list of leases we need to attempt to take,
            // from our peers, so do this in a batch job to be a little more efficient
            // on the DB.
            PreparedBatch inserts = dbi.prepareBatch(insertSQL);
            ArrayList<SqlLease> inBatch = new ArrayList<>();

            HashSet<String> aquiredLeases = new HashSet<>();
            for (SqlLease lease : leases.values()) {
                if (!lease.isAquired()) {
                    inserts.bind("id", lease.getLeaseId());
                    inserts.bind("owner", ownerId);
                    inserts.bind("expiration", now + leaseDuration);
                    inserts.add();
                    inBatch.add(lease);
                } else {
                    aquiredLeases.add(lease.getLeaseId());
                }
            }
            inserts.execute();

            // lets find out which leases we now hold.
            dbi.createQuery("SELECT lease FROM leases WHERE owner = :owner")
                    .bind("owner", ownerId)
                    .map(StringColumnMapper.INSTANCE).forEach(leaseId -> {
                SqlLease lease = leases.get(leaseId);
                if (lease != null) {
                    aquiredLeases.remove(leaseId);
                    lease.fireAquired();
                } else {
                    LOG.warn("Found a lease that we did not request. Deleting: "+leaseId);
                    dbi.createStatement("DELETE FROM leases where lease = :lease AND owner = :owner ")
                            .bind("lease", leaseId)
                            .bind("owner", ownerId)
                            .execute();
                }
            });

            // what's left in this set are leases we had previously acquired but have
            // since lost.
            for (String leaseId : aquiredLeases) {
                SqlLease lease = leases.get(leaseId);
                if (lease != null) {
                    lease.fireReleased();
                }
            }
        });
    }

    protected void withDB(Consumer<Handle> cb) {
        final Handle h = dbi.open();
        try {
            cb.accept(h);
        } finally {
            h.close();
        }
    }

    public long getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(long leaseDuration) {
        this.leaseDuration = leaseDuration;
    }
}
