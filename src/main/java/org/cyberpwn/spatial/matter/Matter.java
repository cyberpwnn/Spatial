/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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


import org.cyberpwn.spatial.util.Pos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * When Red Matter isn't enough
 * <p>
 * UVI width
 * UVI height
 * UVI depth
 * UVI sliceCount
 * UTF author
 * UVL createdAt
 * UVI version
 * UTF sliceType (canonical class name)
 * UVI nodeCount (for each slice)
 * UVI position [(z * w * h) + (y * w) + x]
 * ??? nodeData
 */
public interface Matter {
    int VERSION = 1;

    static Matter read(File f) throws IOException, ClassNotFoundException {
        FileInputStream in = new FileInputStream(f);
        Matter m = read(in);
        in.close();
        return m;
    }

    static Matter read(byte[] data) throws IOException, ClassNotFoundException {
        InputStream in = new ByteArrayInputStream(data);
        Matter m = read(in);
        in.close();
        return m;
    }

    default Matter copy() {
        Matter m = new SpatialMatter(getWidth(), getHeight(), getDepth());
        getSliceMap().forEach((k, v) -> m.slice(k).forceInject(v));
        return m;
    }

    static Matter read(InputStream in) throws IOException, ClassNotFoundException {
        return read(in, (b) -> new SpatialMatter(b.getX(), b.getY(), b.getZ()));
    }

    static Matter readDin(DataInputStream in) throws IOException, ClassNotFoundException {
        return readDin(in, (b) -> new SpatialMatter(b.getX(), b.getY(), b.getZ()));
    }

    /**
     * Reads the input stream into a matter object using a matter factory.
     * Does not close the input stream. Be a man, close it yourself.
     *
     * @param in            the input stream
     * @param matterFactory the matter factory (size) -> new MatterImpl(size);
     * @return the matter object
     * @throws IOException shit happens yo
     */
    static Matter read(InputStream in, Function<Pos, Matter> matterFactory) throws IOException, ClassNotFoundException {
        return readDin(new DataInputStream(in), matterFactory);
    }

    static Matter readDin(DataInputStream din, Function<Pos, Matter> matterFactory) throws IOException, ClassNotFoundException {
        Matter matter = matterFactory.apply(new Pos(
                din.readInt(),
                din.readInt(),
                din.readInt()));
        int sliceCount = din.readByte();

        matter.getHeader().read(din);

        for(int i = 0; i < sliceCount; i++)
        {
            String cn = din.readUTF();
            try {
                Class<?> type = Class.forName(cn);
                MatterSlice<?> slice = matter.createSlice(matter.getClass(type), matter);
                slice.read(din);
                matter.putSlice(matter.getClass(type), slice);
            } catch (Throwable e) {
                e.printStackTrace();
                throw new IOException("Can't read class '" + cn + "' (slice count reverse at " + sliceCount + ")");
            }
        }

        return matter;
    }

    /**
     * Get the header information
     *
     * @return the header info
     */
    MatterHeader getHeader();

    /**
     * Get the width of this matter
     *
     * @return the width
     */
    int getWidth();

    /**
     * Get the height of this matter
     *
     * @return the height
     */
    int getHeight();

    /**
     * Get the depth of this matter
     *
     * @return the depth
     */
    int getDepth();

    /**
     * Get the center of this matter
     *
     * @return the center
     */
    default Pos getCenter() {
        return new Pos(getCenterX(), getCenterY(), getCenterZ());
    }

    /**
     * Create a slice from the given type (full is false)
     *
     * @param type   the type class
     * @param matter the matter this slice will go into (size provider)
     * @param <T>    the type
     * @return the slice (or null if not supported)
     */
    <T> MatterSlice<T> createSlice(Class<T> type, Matter matter);

    /**
     * Get the size of this matter
     *
     * @return the size
     */
    default Pos getSize() {
        return new Pos(getWidth(), getHeight(), getDepth());
    }

    /**
     * Get the center X of this matter
     *
     * @return the center X
     */
    default int getCenterX() {
        return (int) Math.round(getWidth() / 2D);
    }

    /**
     * Get the center Y of this matter
     *
     * @return the center Y
     */
    default int getCenterY() {
        return (int) Math.round(getHeight() / 2D);
    }

    /**
     * Get the center Z of this matter
     *
     * @return the center Z
     */
    default int getCenterZ() {
        return (int) Math.round(getDepth() / 2D);
    }

    /**
     * Return the slice for the given type
     *
     * @param t   the type class
     * @param <T> the type
     * @return the slice or null
     */
    default <T> MatterSlice<T> getSlice(Class<T> t) {
        return (MatterSlice<T>) getSliceMap().get(getClass(t));
    }

    /**
     * Delete the slice for the given type
     *
     * @param c   the type class
     * @param <T> the type
     * @return the deleted slice, or null if it diddn't exist
     */
    default <T> MatterSlice<T> deleteSlice(Class<?> c) {
        return (MatterSlice<T>) getSliceMap().remove(getClass(c));
    }

    /**
     * Put a given slice type
     *
     * @param c     the slice type class
     * @param slice the slice to assign to the type
     * @param <T>   the slice type
     * @return the overwritten slice if there was an existing slice of that type
     */
    default <T> MatterSlice<T> putSlice(Class<?> c, MatterSlice<T> slice) {
        return (MatterSlice<T>) getSliceMap().put(getClass(c), slice);
    }

    default Class<?> getClass(Object w) {
        if(w instanceof Integer) {return Integer.class;}
        if(w instanceof Double) {return Double.class;}
        if(w instanceof Boolean) {return Boolean.class;}
        if(w instanceof Short) {return Short.class;}
        if(w instanceof Long) {return Long.class;}
        if(w instanceof Float) {return Float.class;}
        if(w instanceof Byte) {return Byte.class;}
        if(w instanceof Character) {return Character.class;}

        return w.getClass();
    }

    default Class<?> getClass(Class<?> w) {
        if(w.equals(int.class)) {return Integer.class;}
        if(w.equals(double.class)) {return Double.class;}
        if(w.equals(boolean.class)) {return Boolean.class;}
        if(w.equals(short.class)) {return Short.class;}
        if(w.equals(long.class)) {return Long.class;}
        if(w.equals(float.class)) {return Float.class;}
        if(w.equals(byte.class)) {return Byte.class;}
        if(w.equals(char.class)) {return Character.class;}

        return w;
    }

    default <T> MatterSlice<T> slice(Class<?> c) {
        MatterSlice<T> slice = (MatterSlice<T>) getSlice(getClass(c));
        if (slice == null) {
            slice = (MatterSlice<T>) createSlice(getClass(c), this);
            if (slice == null) {
                try {
                    throw new RuntimeException("Bad slice " + c.getCanonicalName() + ". Did you use SpatialMatter.register?");
                } catch (Throwable e) {
                    e.printStackTrace();
                }

                return null;
            }

            putSlice(getClass(c), slice);
        }

        return slice;
    }

    /**
     * Check if a slice exists for a given type
     *
     * @param c the slice class type
     * @return true if it exists
     */
    default boolean hasSlice(Class<?> c) {
        return getSlice(c) != null;
    }

    /**
     * Remove all slices
     */
    default void clearSlices() {
        getSliceMap().clear();
    }

    /**
     * Get the set backing the slice map keys (slice types)
     *
     * @return the slice types
     */
    default Set<Class<?>> getSliceTypes() {
        return getSliceMap().keySet();
    }

    /**
     * Get all slices
     *
     * @return the real slice map
     */
    Map<Class<?>, MatterSlice<?>> getSliceMap();

    default void write(File f) throws IOException {
        OutputStream out = new FileOutputStream(f);
        write(out);
        out.close();
    }

    default byte[] write() throws IOException {
        ByteArrayOutputStream boas = new ByteArrayOutputStream();
        write(boas);
        return boas.toByteArray();
    }

    /**
     * Remove any slices that are empty
     */
    default void trimSlices() {
        Set<Class<?>> drop = null;

        for (Class<?> i : getSliceTypes()) {
            if (getSlice(i).getEntryCount() == 0) {
                if (drop == null) {
                    drop = new HashSet<>();
                }

                drop.add(i);
            }
        }

        if (drop != null) {
            for (Class<?> i : drop) {
                deleteSlice(i);
            }
        }
    }

    /**
     * Writes the data to the output stream. The data will be flushed to the provided output
     * stream however the provided stream will NOT BE CLOSED, so be sure to actually close it
     *
     * @param out the output stream
     * @throws IOException shit happens yo
     */
    default void write(OutputStream out) throws IOException {
        writeDos(new DataOutputStream(out));
    }

    default void writeDos(DataOutputStream dos) throws IOException {
        trimSlices();
        dos.writeInt(getWidth());
        dos.writeInt(getHeight());
        dos.writeInt(getDepth());
        dos.writeByte(getSliceTypes().size());
        getHeader().write(dos);

        for (Class<?> i : getSliceTypes()) {
            getSlice(i).write(dos);
        }
    }

    default int getTotalCount() {
        int m = 0;

        for (MatterSlice<?> i : getSliceMap().values()) {
            m += i.getEntryCount();
        }

        return m;
    }
}
