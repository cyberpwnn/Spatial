/*
 * Spatial is a spatial api for Java...
 * Copyright (c) 2021 Arcane Arts
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.cyberpwn.spatial.parallel;

import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@SuppressWarnings("ALL")
public class BurstExecutor {
    private final ExecutorService executor;
    private final List<Future<?>> futures;
    @Setter
    private boolean multicore = true;

    public BurstExecutor(ExecutorService executor, int burstSizeEstimate) {
        this.executor = executor;
        futures = new ArrayList<>(burstSizeEstimate);
    }

    @SuppressWarnings("UnusedReturnValue")
    public Future<?> queue(Runnable r) {
        if(!multicore) {
            r.run();
            return CompletableFuture.completedFuture(null);
        }

        synchronized(futures) {

            Future<?> c = executor.submit(r);
            futures.add(c);
            return c;
        }
    }

    public BurstExecutor queue(List<Runnable> r) {
        if(!multicore) {
            for(Runnable i : new ArrayList<>(r)) {
                i.run();
            }

            return this;
        }

        synchronized(futures) {
            for(Runnable i : new ArrayList<>(r)) {
                queue(i);
            }
        }

        return this;
    }

    public BurstExecutor queue(Runnable[] r) {
        if(!multicore) {
            for(Runnable i : r) {
                i.run();
            }

            return this;
        }

        synchronized(futures) {
            for(Runnable i : r) {
                queue(i);
            }
        }

        return this;
    }

    public void complete() {
        if(!multicore) {
            return;
        }

        synchronized(futures) {
            if(futures.isEmpty()) {
                return;
            }

            try {
                for(Future<?> i : futures) {
                    i.get();
                }

                futures.clear();
            } catch(InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }
}
