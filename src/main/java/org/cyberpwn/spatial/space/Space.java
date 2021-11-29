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

package org.cyberpwn.spatial.space;
import com.google.common.collect.ImmutableList;
import org.cyberpwn.spatial.mantle.Mantle;
import org.cyberpwn.spatial.util.Consume;
import org.cyberpwn.spatial.util.Function;
import org.cyberpwn.spatial.util.Pos;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class Space
{
    private final AtomicBoolean closed;
    private final File folder;
    private final Map<Integer, Mantle> mantles;

    public Space(File folder)
    {
        this.closed = new AtomicBoolean();
        this.folder = folder;
        this.mantles = new HashMap<>();
    }

    public void clear()
    {
        mantles.values().forEach(Mantle::clear);
        mantles.clear();

        if(folder.exists() && folder.isDirectory())
        {
            for(File i : folder.listFiles())
            {
                if(i.isDirectory())
                {
                    for(File j : i.listFiles())
                    {
                        if(!j.delete())
                        {
                            j.deleteOnExit();
                        }
                    }
                }

                i.delete();
            }

            folder.delete();
        }
    }

    public <T> void iterateRegion(int x, int y, int z, int radius, Class<T> type, Consume.Four<Integer, Integer, Integer, T> iterator)
    {
        iterateRegion(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius, type, iterator);
    }

    public <T> void iterateRegion(int x1, int y1, int z1, int x2, int y2, int z2, Class<T> type, Consume.Four<Integer, Integer, Integer, T> iterator) {
        int xi = Math.min(x1, x2);
        int xa = Math.max(x1, x2);
        int yi = Math.min(y1, y2);
        int ya = Math.max(y1, y2);
        int zi = Math.min(z1, z2);
        int za = Math.max(z1, z2);

        for(int i = xi; i < xa; i++)
        {
            for(int j = yi; j < ya; j++)
            {
                for(int k = zi; k < za; k++)
                {
                    T t = get(i, j, k, type);

                    if(t != null)
                    {
                        iterator.accept(i, j, k, t);
                    }
                }
            }
        }
    }

    /**
     * Save & unload regions that have not been used for more than the
     * specified amount of milliseconds
     *
     * @param idleDuration
     *     the duration
     */
    public synchronized void trim(long idleDuration) {
        if(closed.get()) {
            throw new RuntimeException("The Space is closed");
        }

        for(Mantle i : mantles.values()) {
            i.trim(idleDuration);
        }

        for(Integer i : new HashSet<>(mantles.keySet()))
        {
            if(mantles.get(i).getLoadedRegions().isEmpty())
            {
                mantles.remove(i).close();
            }
        }
    }

    /**
     * Gets the data that the current block position This method will attempt to find a
     * Tectonic Plate either by loading it or creating a new one. This method uses
     * the hyper lock packaged with each Mantle. The hyperlock allows locking of multiple
     * threads at a single region while still allowing other threads to continue
     * reading & writing other regions. Hyperlocks are slow sync, but in multicore
     * environments, they drastically speed up loading & saving large counts of plates
     *
     * @param x
     *     the block's x coordinate
     * @param y
     *     the block's y coordinate
     * @param z
     *     the block's z coordinate
     * @param type
     *     the class representing the type of data being requested
     * @param <T>
     *     the type assumed from the provided class
     * @return the returned result (or null) if it doesnt exist
     */
    public <T> T get(int x, int y, int z, Class<T> type)
    {
        if(closed.get())
        {
            throw new RuntimeException("Closed!");
        }

        if(!hasMantle(y))
        {
            return null;
        }

        return getMantle(y).get(x, y & 511, z, type);
    }

    /**
     * Set data T at the given block position. This method will attempt to find a
     * Tectonic Plate either by loading it or creating a new one. This method uses
     * the hyper lock packaged with each Mantle. The hyperlock allows locking of multiple
     * threads at a single region while still allowing other threads to continue
     * reading & writing other regions. Hyperlocks are slow sync, but in multicore
     * environments, they drastically speed up loading & saving large counts of plates
     *
     * @param x
     *     the block's x coordinate
     * @param y
     *     the block's y coordinate
     * @param z
     *     the block's z coordinate
     * @param t
     *     the data to set at the block
     * @param <T>
     *     the type of data (generic method)
     */
    public <T> void set(int x, int y, int z, T t) {
        if(closed.get())
        {
            throw new RuntimeException("Closed!");
        }

        getMantle(y).set(x, y & 511, z, t);
    }

    public void close()
    {
        if(closed.get())
        {
            return;
        }

        closed.set(true);
        mantles.values().forEach(Mantle::close);
        mantles.clear();
    }

    public void saveAll()
    {
        if(closed.get())
        {
            throw new RuntimeException("Closed!");
        }

        mantles.values().forEach(Mantle::saveAll);
    }

    public <T> void remove(int x, int y, int z, Class<T> t) {
        if(closed.get())
        {
            throw new RuntimeException("Closed!");
        }

        if(!hasMantle(y))
        {
            return;
        }

        getMantle(y).remove(x, y & 511, z, t);
    }

    public boolean hasMantle(int y)
    {
        return mantles.containsKey(y >> 9) || new File(folder, Integer.toHexString(y >> 9)).exists();
    }

    /**
     * Get the mantle responsible for the given Y level
     * @param y the raw y level you need to access
     * @return the mantle responsible for storing that y location
     */
    private Mantle getMantle(int y)
    {
        if(closed.get())
        {
            throw new RuntimeException("Closed!");
        }

        return mantles.computeIfAbsent(y >> 9, k -> new Mantle(new File(folder, Integer.toHexString(k)), 512));
    }

    private static Set<Pos> getBallooned(Set<Pos> vset, double radius) {
        Set<Pos> returnset = new HashSet<>();
        int ceilrad = (int) Math.ceil(radius);

        for(Pos v : vset) {
            int tipx = v.getX();
            int tipy = v.getY();
            int tipz = v.getZ();

            for(int loopx = tipx - ceilrad; loopx <= tipx + ceilrad; loopx++) {
                for(int loopy = tipy - ceilrad; loopy <= tipy + ceilrad; loopy++) {
                    for(int loopz = tipz - ceilrad; loopz <= tipz + ceilrad; loopz++) {
                        if(hypot(loopx - tipx, loopy - tipy, loopz - tipz) <= radius) {
                            returnset.add(new Pos(loopx, loopy, loopz));
                        }
                    }
                }
            }
        }
        return returnset;
    }

    private static Set<Pos> getHollowed(Set<Pos> vset) {
        Set<Pos> returnset = new HashSet<>();
        for(Pos v : vset) {
            double x = v.getX();
            double y = v.getY();
            double z = v.getZ();
            if(!(vset.contains(new Pos(x + 1, y, z))
                && vset.contains(new Pos(x - 1, y, z))
                && vset.contains(new Pos(x, y + 1, z))
                && vset.contains(new Pos(x, y - 1, z))
                && vset.contains(new Pos(x, y, z + 1))
                && vset.contains(new Pos(x, y, z - 1)))) {
                returnset.add(v);
            }
        }
        return returnset;
    }

    private static double hypot(double... pars) {
        double sum = 0;
        for(double d : pars) {
            sum += Math.pow(d, 2);
        }
        return Math.sqrt(sum);
    }

    private static double lengthSq(double x, double y, double z) {
        return (x * x) + (y * y) + (z * z);
    }

    private static double lengthSq(double x, double z) {
        return (x * x) + (z * z);
    }

    public <T> void setData(int x, int y, int z, T t) {
        if(t == null) {
            return;
        }

        set(x, y, z, t);
    }

    /**
     * Set a sphere into the mantle
     *
     * @param cx
     *     the center x
     * @param cy
     *     the center y
     * @param cz
     *     the center z
     * @param radius
     *     the radius of this sphere
     * @param fill
     *     should it be filled? or just the outer shell?
     * @param data
     *     the data to set
     * @param <T>
     *     the type of data to apply to the mantle
     */
    public <T> void setSphere(int cx, int cy, int cz, double radius, boolean fill, T data) {
        setElipsoid(cx, cy, cz, radius, radius, radius, fill, data);
    }

    public <T> void setElipsoid(int cx, int cy, int cz, double rx, double ry, double rz, boolean fill, T data) {
        setElipsoidFunction(cx, cy, cz, rx, ry, rz, fill, (a, b, c) -> data);
    }

    /**
     * Set an elipsoid into the mantle
     *
     * @param cx
     *     the center x
     * @param cy
     *     the center y
     * @param cz
     *     the center z
     * @param rx
     *     the x radius
     * @param ry
     *     the y radius
     * @param rz
     *     the z radius
     * @param fill
     *     should it be filled or just the outer shell?
     * @param data
     *     the data to set
     * @param <T>
     *     the type of data to apply to the mantle
     */
    public <T> void setElipsoidFunction(int cx, int cy, int cz, double rx, double ry, double rz, boolean fill, Function.Three<Integer, Integer, Integer, T> data) {
        rx += 0.5;
        ry += 0.5;
        rz += 0.5;
        final double invRadiusX = 1 / rx;
        final double invRadiusY = 1 / ry;
        final double invRadiusZ = 1 / rz;
        final int ceilRadiusX = (int) Math.ceil(rx);
        final int ceilRadiusY = (int) Math.ceil(ry);
        final int ceilRadiusZ = (int) Math.ceil(rz);
        double nextXn = 0;

        forX:
        for(int x = 0; x <= ceilRadiusX; ++x) {
            final double xn = nextXn;
            nextXn = (x + 1) * invRadiusX;
            double nextYn = 0;
            forY:
            for(int y = 0; y <= ceilRadiusY; ++y) {
                final double yn = nextYn;
                nextYn = (y + 1) * invRadiusY;
                double nextZn = 0;
                for(int z = 0; z <= ceilRadiusZ; ++z) {
                    final double zn = nextZn;
                    nextZn = (z + 1) * invRadiusZ;

                    double distanceSq = lengthSq(xn, yn, zn);
                    if(distanceSq > 1) {
                        if(z == 0) {
                            if(y == 0) {
                                break forX;
                            }
                            break forY;
                        }
                        break;
                    }

                    if(!fill) {
                        if(lengthSq(nextXn, yn, zn) <= 1 && lengthSq(xn, nextYn, zn) <= 1 && lengthSq(xn, yn, nextZn) <= 1) {
                            continue;
                        }
                    }

                    setData(x + cx, y + cy, z + cz, data.apply(x + cx, y + cy, z + cz));
                    setData(-x + cx, y + cy, z + cz, data.apply(-x + cx, y + cy, z + cz));
                    setData(x + cx, -y + cy, z + cz, data.apply(x + cx, -y + cy, z + cz));
                    setData(x + cx, y + cy, -z + cz, data.apply(x + cx, y + cy, -z + cz));
                    setData(-x + cx, y + cy, -z + cz, data.apply(-x + cx, y + cy, -z + cz));
                    setData(-x + cx, -y + cy, z + cz, data.apply(-x + cx, -y + cy, z + cz));
                    setData(x + cx, -y + cy, -z + cz, data.apply(x + cx, -y + cy, -z + cz));
                    setData(-x + cx, y + cy, -z + cz, data.apply(-x + cx, y + cy, -z + cz));
                    setData(-x + cx, -y + cy, -z + cz, data.apply(-x + cx, -y + cy, -z + cz));
                }
            }
        }
    }

    /**
     * Set a cuboid of data in the mantle
     *
     * @param x1
     *     the min x
     * @param y1
     *     the min y
     * @param z1
     *     the min z
     * @param x2
     *     the max x
     * @param y2
     *     the max y
     * @param z2
     *     the max z
     * @param data
     *     the data to set
     * @param <T>
     *     the type of data to apply to the mantle
     */
    public <T> void setCuboid(int x1, int y1, int z1, int x2, int y2, int z2, T data) {
        int j, k;

        for(int i = x1; i <= x2; i++) {
            for(j = x1; j <= x2; j++) {
                for(k = x1; k <= x2; k++) {
                    setData(i, j, k, data);
                }
            }
        }
    }

    /**
     * Set a pyramid of data in the mantle
     *
     * @param cx
     *     the center x
     * @param cy
     *     the base y
     * @param cz
     *     the center z
     * @param data
     *     the data to set
     * @param size
     *     the size of the pyramid (width of base & height)
     * @param filled
     *     should it be filled or hollow
     * @param <T>
     *     the type of data to apply to the mantle
     */
    @SuppressWarnings("ConstantConditions")
    public <T> void setPyramid(int cx, int cy, int cz, T data, int size, boolean filled) {
        int height = size;

        for(int y = 0; y <= height; ++y) {
            size--;
            for(int x = 0; x <= size; ++x) {
                for(int z = 0; z <= size; ++z) {
                    if((filled && z <= size && x <= size) || z == size || x == size) {
                        setData(x + cx, y + cy, z + cz, data);
                        setData(-x + cx, y + cy, z + cz, data);
                        setData(x + cx, y + cy, -z + cz, data);
                        setData(-x + cx, y + cy, -z + cz, data);
                    }
                }
            }
        }
    }

    /**
     * Set a 3d line
     *
     * @param a
     *     the first point
     * @param b
     *     the second point
     * @param radius
     *     the radius
     * @param filled
     *     hollow or filled?
     * @param data
     *     the data
     * @param <T>
     *     the type of data to apply to the mantle
     */
    public <T> void setLine(Pos a, Pos b, double radius, boolean filled, T data) {
        setLine(ImmutableList.of(a, b), radius, filled, data);
    }

    public <T> void setLine(List<Pos> vectors, double radius, boolean filled, T data) {
        setLineConsumer(vectors, radius, filled, (_x, _y, _z) -> data);
    }

    /**
     * Set lines for points
     *
     * @param vectors
     *     the points
     * @param radius
     *     the radius
     * @param filled
     *     hollow or filled?
     * @param data
     *     the data to set
     * @param <T>
     *     the type of data to apply to the mantle
     */
    public <T> void setLineConsumer(List<Pos> vectors, double radius, boolean filled, Function.Three<Integer, Integer, Integer, T> data) {
        Set<Pos> vset = new HashSet<>();

        for(int i = 0; vectors.size() != 0 && i < vectors.size() - 1; i++) {
            Pos pos1 = vectors.get(i);
            Pos pos2 = vectors.get(i + 1);
            int x1 = pos1.getX();
            int y1 = pos1.getY();
            int z1 = pos1.getZ();
            int x2 = pos2.getX();
            int y2 = pos2.getY();
            int z2 = pos2.getZ();
            int tipx = x1;
            int tipy = y1;
            int tipz = z1;
            int dx = Math.abs(x2 - x1);
            int dy = Math.abs(y2 - y1);
            int dz = Math.abs(z2 - z1);

            if(dx + dy + dz == 0) {
                vset.add(new Pos(tipx, tipy, tipz));
                continue;
            }

            int dMax = Math.max(Math.max(dx, dy), dz);
            if(dMax == dx) {
                for(int domstep = 0; domstep <= dx; domstep++) {
                    tipx = x1 + domstep * (x2 - x1 > 0 ? 1 : -1);
                    tipy = (int) Math.round(y1 + domstep * ((double) dy) / ((double) dx) * (y2 - y1 > 0 ? 1 : -1));
                    tipz = (int) Math.round(z1 + domstep * ((double) dz) / ((double) dx) * (z2 - z1 > 0 ? 1 : -1));

                    vset.add(new Pos(tipx, tipy, tipz));
                }
            } else if(dMax == dy) {
                for(int domstep = 0; domstep <= dy; domstep++) {
                    tipy = y1 + domstep * (y2 - y1 > 0 ? 1 : -1);
                    tipx = (int) Math.round(x1 + domstep * ((double) dx) / ((double) dy) * (x2 - x1 > 0 ? 1 : -1));
                    tipz = (int) Math.round(z1 + domstep * ((double) dz) / ((double) dy) * (z2 - z1 > 0 ? 1 : -1));

                    vset.add(new Pos(tipx, tipy, tipz));
                }
            } else /* if (dMax == dz) */ {
                for(int domstep = 0; domstep <= dz; domstep++) {
                    tipz = z1 + domstep * (z2 - z1 > 0 ? 1 : -1);
                    tipy = (int) Math.round(y1 + domstep * ((double) dy) / ((double) dz) * (y2 - y1 > 0 ? 1 : -1));
                    tipx = (int) Math.round(x1 + domstep * ((double) dx) / ((double) dz) * (x2 - x1 > 0 ? 1 : -1));

                    vset.add(new Pos(tipx, tipy, tipz));
                }
            }
        }

        vset = getBallooned(vset, radius);

        if(!filled) {
            vset = getHollowed(vset);
        }

        setConsumer(vset, data);
    }

    /**
     * Set a cylinder in the mantle
     *
     * @param cx
     *     the center x
     * @param cy
     *     the base y
     * @param cz
     *     the center z
     * @param data
     *     the data to set
     * @param radius
     *     the radius
     * @param height
     *     the height of the cyl
     * @param filled
     *     filled or not
     */
    public <T> void setCylinder(int cx, int cy, int cz, T data, double radius, int height, boolean filled) {
        setCylinder(cx, cy, cz, data, radius, radius, height, filled);
    }

    /**
     * Set a cylinder in the mantle
     *
     * @param cx
     *     the center x
     * @param cy
     *     the base y
     * @param cz
     *     the center z
     * @param data
     *     the data to set
     * @param radiusX
     *     the x radius
     * @param radiusZ
     *     the z radius
     * @param height
     *     the height of this cyl
     * @param filled
     *     filled or hollow?
     */
    public <T> void setCylinder(int cx, int cy, int cz, T data, double radiusX, double radiusZ, int height, boolean filled) {
        int affected = 0;
        radiusX += 0.5;
        radiusZ += 0.5;

        if(height == 0) {
            return;
        } else if(height < 0) {
            height = -height;
            cy = cy - height;
        }
        if(cy < 0) {
            cy = 0;
        }

        final double invRadiusX = 1 / radiusX;
        final double invRadiusZ = 1 / radiusZ;
        final int ceilRadiusX = (int) Math.ceil(radiusX);
        final int ceilRadiusZ = (int) Math.ceil(radiusZ);
        double nextXn = 0;

        forX:
        for(int x = 0; x <= ceilRadiusX; ++x) {
            final double xn = nextXn;
            nextXn = (x + 1) * invRadiusX;
            double nextZn = 0;
            for(int z = 0; z <= ceilRadiusZ; ++z) {
                final double zn = nextZn;
                nextZn = (z + 1) * invRadiusZ;
                double distanceSq = lengthSq(xn, zn);

                if(distanceSq > 1) {
                    if(z == 0) {
                        break forX;
                    }

                    break;
                }

                if(!filled) {
                    if(lengthSq(nextXn, zn) <= 1 && lengthSq(xn, nextZn) <= 1) {
                        continue;
                    }
                }

                for(int y = 0; y < height; ++y) {
                    setData(cx + x, cy + y, cz + z, data);
                    setData(cx + -x, cy + y, cz + z, data);
                    setData(cx + x, cy + y, cz + -z, data);
                    setData(cx + -x, cy + y, cz + -z, data);
                }
            }
        }
    }

    public <T> void set(Pos pos, T data) {
        try {
            setData(pos.getX(), pos.getY(), pos.getZ(), data);
        } catch(Throwable e) {
            e.printStackTrace();
        }
    }

    public <T> void set(List<Pos> positions, T data) {
        for(Pos i : positions) {
            set(i, data);
        }
    }

    public <T> void set(Set<Pos> positions, T data) {
        for(Pos i : positions) {
            set(i, data);
        }
    }

    public <T> void setConsumer(Set<Pos> positions, Function.Three<Integer, Integer, Integer, T> data) {
        for(Pos i : positions) {
            set(i, data.apply(i.getX(), i.getY(), i.getZ()));
        }
    }
}
