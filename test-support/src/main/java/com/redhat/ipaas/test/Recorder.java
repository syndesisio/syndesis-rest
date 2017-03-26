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
package com.redhat.ipaas.test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by chirino on 3/23/17.
 */
public class Recorder<T> {
    private final Recordings.RecordingInvocationHandler ih;
    private final T proxy;

    public Recorder(Recordings.RecordingInvocationHandler ih, T proxy) {
        this.ih = ih;
        this.proxy = proxy;
    }

    public T proxy() {
        return proxy;
    }

    public List<Recordings.Invocation> invocations() {
        return this.ih.recordedInvocations;
    }

    public void reset(int count) {
        CountDownLatch latch = new CountDownLatch(count);
        ih.latch = latch;
        ih.recordedInvocations.clear();
    }

    public boolean await(int time, TimeUnit unit) throws InterruptedException {
        return ih.latch.await(time, unit);
    }

    public void await() throws InterruptedException {
        ih.latch.await();
    }


}
