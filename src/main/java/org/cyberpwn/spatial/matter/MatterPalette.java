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

package org.cyberpwn.spatial.matter;

import org.cyberpwn.spatial.container.DataPalette;
import org.cyberpwn.spatial.util.IOAdapter;
import org.cyberpwn.spatial.util.Varint;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class MatterPalette<T> implements IOAdapter<T> {
    private final MatterSlice<T> slice;
    private final DataPalette<T> palette;

    public MatterPalette(MatterSlice<T> slice) {
        this.slice = slice;
        palette = new DataPalette<T>();
    }

    public MatterPalette(MatterSlice<T> slice, DataInputStream din) throws IOException {
        this.slice = slice;
        palette = DataPalette.getPalette(this, din);
    }

    public void writeNode(T t, DataOutputStream dos) throws IOException {
        Varint.writeUnsignedVarInt(palette.getIndex(t), dos);
    }

    public T readNode(DataInputStream din) throws IOException {
        return palette.get(Varint.readUnsignedVarInt(din));
    }

    public void writePalette(DataOutputStream dos) throws IOException {
        palette.write(this, dos);
    }

    @Override
    public void write(T t, DataOutputStream dos) throws IOException {
        slice.writeNode(t, dos);
    }

    @Override
    public T read(DataInputStream din) throws IOException {
        return slice.readNode(din);
    }

    public void assign(T b) {
        palette.getIndex(b);
    }
}
