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

package org.cyberpwn.spatial.matter.slices;


import lombok.Getter;
import org.cyberpwn.spatial.hunk.storage.MappedHunk;
import org.cyberpwn.spatial.hunk.storage.PaletteOrHunk;
import org.cyberpwn.spatial.matter.MatterReader;
import org.cyberpwn.spatial.matter.MatterSlice;
import org.cyberpwn.spatial.matter.MatterWriter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class RawMatter<T> extends PaletteOrHunk<T> implements MatterSlice<T> {
    protected final Map<Class<?>, MatterWriter<?, T>> writers;
    protected final Map<Class<?>, MatterReader<?, T>> readers;
    @Getter
    private final Class<T> type;

    public RawMatter(int width, int height, int depth, Class<T> type) {
        super(width, height, depth, true, () -> new MappedHunk<>(width, height, depth));
        writers = new HashMap<>();
        readers = new HashMap<>();
        this.type = type;
    }

    protected <W> void registerWriter(Class<W> mediumType, MatterWriter<W, T> injector) {
        writers.put(mediumType, injector);
    }

    protected <W> void registerReader(Class<W> mediumType, MatterReader<W, T> injector) {
        readers.put(mediumType, injector);
    }

    @Override
    public <W> MatterWriter<W, T> writeInto(Class<W> mediumType) {
        return (MatterWriter<W, T>) writers.get(mediumType);
    }

    @Override
    public <W> MatterReader<W, T> readFrom(Class<W> mediumType) {
        return (MatterReader<W, T>) readers.get(mediumType);
    }

    @Override
    public abstract void writeNode(T b, DataOutputStream dos) throws IOException;

    @Override
    public abstract T readNode(DataInputStream din) throws IOException;
}
