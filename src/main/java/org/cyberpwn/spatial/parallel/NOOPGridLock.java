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


import org.cyberpwn.spatial.util.Run;

import java.io.IOException;
import java.util.function.Supplier;

public class NOOPGridLock extends GridLock {
    public NOOPGridLock(int x, int z) {
        super(x, z);
    }

    @Override
    public void with(int x, int z, Runnable r) {
        r.run();
    }

    @Override
    public void withNasty(int x, int z, Run.Throwable r) throws Throwable {
        r.run();
    }

    @Override
    public void withIO(int x, int z, Run.IO r) throws IOException {
        r.run();
    }

    @Override
    public <T> T withResult(int x, int z, Supplier<T> r) {
        return r.get();
    }

    @Override
    public void withAll(Runnable r) {
        r.run();
    }

    @Override
    public <T> T withAllResult(Supplier<T> r) {
        return r.get();
    }

    @Override
    public boolean tryLock(int x, int z) {
        return true;
    }

    @Override
    public boolean tryLock(int x, int z, long timeout) {
        return true;
    }

    @Override
    public void lock(int x, int z) {

    }

    @Override
    public void unlock(int x, int z) {

    }
}
