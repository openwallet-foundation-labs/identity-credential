/*
 * Copyright 2015 Robert Bachmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.licel.jcardsim.utils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * Java version of .Net's <code>AutoResetEvent</code>.
 * <p>See <a href=
 * "http://msdn.microsoft.com/en-us/library/system.threading.autoresetevent(v=vs.110).aspx">MSDN</a>.</p>
 */
public final class AutoResetEvent {
    private final static int SIGNALED = 1;
    private final static int NOT_SIGNALED = 0;
    private final Sync sync = new Sync();

    /**
     * Wake up one thread that is waiting.
     */
    public void signal() {
        sync.releaseShared(SIGNALED);
    }

    /**
     * Reset.
     */
    public void reset() {
        sync.releaseShared(NOT_SIGNALED);
    }

    /**
     * Wait until thread is signaled or interrupted.
     * @param time time to wait
     * @param unit time unit of <code>time</code>
     * @return true if signaled
     * @throws InterruptedException if the thread is interrupted
     */
    public boolean await(long time, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(time));
    }

    private static class Sync extends AbstractQueuedSynchronizer {
        Sync() {
            setState(NOT_SIGNALED);
        }

        protected int tryAcquireShared(int ignore) {
            if (compareAndSetState(SIGNALED, NOT_SIGNALED)) {
                return 1;
            }
            return -1;
        }

        protected boolean tryReleaseShared(int state) {
            setState(state);
            return true;
        }
    }
}
