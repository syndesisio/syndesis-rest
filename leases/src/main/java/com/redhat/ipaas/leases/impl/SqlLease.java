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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class SqlLease implements Lease {

    private final SqlLeaseManager manager;
    private final String leaseId;
    private final Consumer<Lease> subscriber;
    private final AtomicBoolean aquired = new AtomicBoolean();

    public SqlLease(SqlLeaseManager manager, String leaseId, Consumer<Lease> subscriber) {
        this.manager = manager;

        this.leaseId = leaseId;
        this.subscriber = subscriber;
    }

    public String getLeaseId() {
        return leaseId;
    }

    public boolean isAquired() {
        return aquired.get();
    }

    public void fireAquired() {
        if( aquired.compareAndSet(false, true) ) {
            subscriber.accept(this);
        }
    }

    public void fireReleased() {
        if( aquired.compareAndSet(true, false) ) {
            subscriber.accept(this);
        }
    }

    @Override
    public void release() {
        manager.release(this);
    }
}
