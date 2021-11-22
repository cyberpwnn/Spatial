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

package org.cyberpwn.spatial.mantle;


import com.google.common.collect.ImmutableList;
import lombok.Data;
import org.cyberpwn.spatial.matter.Matter;
import org.cyberpwn.spatial.util.CompressedNumbers;
import org.cyberpwn.spatial.util.Function;
import org.cyberpwn.spatial.util.Pos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class MantleWriter {
    private final Mantle mantle;
    private final Map<Long, MantleChunk> cachedChunks;
    private final int radius;
    private final int x;
    private final int z;
    private final boolean infinite;

    public MantleWriter(Mantle mantle) {
        this(mantle, 0, 0, -1);
    }

    public MantleWriter(Mantle mantle, int x, int z, int radius) {
        infinite = radius == -1;
        this.mantle = mantle;
        this.cachedChunks = new HashMap<>();
        this.radius = radius;
        this.x = x;
        this.z = z;

        for(int i = -radius; i <= radius; i++) {
            for(int j = -radius; j <= radius; j++) {
                cachedChunks.put(CompressedNumbers.i2(i + x, j + z), mantle.getChunk(i + x, j + z));
            }
        }
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

        int cx = x >> 4;
        int cz = z >> 4;

        if(y < 0 || y >= mantle.getWorldHeight()) {
            return;
        }

        if(infinite || (cx >= this.x - radius && cx <= this.x + radius
            && cz >= this.z - radius && cz <= this.z + radius)) {
            MantleChunk chunk = cachedChunks.get(CompressedNumbers.i2(cx, cz));

            if(chunk == null) {
                return;
            }

            Matter matter = chunk.getOrCreate(y >> 4);
            matter.slice(matter.getClass(t)).set(x & 15, y & 15, z & 15, t);
        }
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
        } else if(cy + height - 1 > getMantle().getWorldHeight()) {
            height = getMantle().getWorldHeight() - cy + 1;
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

    public boolean isWithin(Pos pos) {
        return isWithin(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean isWithin(int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;

        if(y < 0 || y >= mantle.getWorldHeight()) {
            return false;
        }

        if(infinite) {
            return true;
        }

        return cx >= this.x - radius && cx <= this.x + radius
            && cz >= this.z - radius && cz <= this.z + radius;
    }
}
