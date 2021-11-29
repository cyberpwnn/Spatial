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


import lombok.Getter;
import org.cyberpwn.spatial.matter.Matter;
import org.cyberpwn.spatial.matter.MatterSlice;
import org.cyberpwn.spatial.parallel.BurstExecutor;
import org.cyberpwn.spatial.parallel.HyperLock;
import org.cyberpwn.spatial.parallel.MultiBurst;
import org.cyberpwn.spatial.util.CompressedNumbers;
import org.cyberpwn.spatial.util.Consume;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The mantle can store any type of data slice anywhere and manage regions & IO on it's own.
 * This class is fully thread safe read & writeNodeData
 */
public class Mantle {
    private final File dataFolder;
    private final int worldHeight;
    private final Map<Long, Long> lastUse;
    @Getter
    private final Map<Long, MantleRegion> loadedRegions;
    private final HyperLock hyperLock;
    private final Set<Long> unload;
    private final AtomicBoolean closed;
    private final MultiBurst ioBurst;
    private final AtomicBoolean io;

    /**
     * Create a new mantle
     *
     * @param dataFolder
     *     the data folder
     * @param worldHeight
     *     the world's height (in blocks)
     */
    public Mantle(File dataFolder, int worldHeight) {
        this.hyperLock = new HyperLock();
        this.closed = new AtomicBoolean(false);
        this.dataFolder = dataFolder;
        this.worldHeight = worldHeight;
        this.io = new AtomicBoolean(false);
        unload = new HashSet<>();
        loadedRegions = new HashMap<>();
        lastUse = new HashMap<>();
        ioBurst = MultiBurst.burst;
    }

    public void clear()
    {
        loadedRegions.clear();
        lastUse.clear();
        unload.clear();
        hyperLock.clear();

        if(dataFolder.exists() && dataFolder.isDirectory())
        {
            for(File i : dataFolder.listFiles())
            {
                i.delete();
            }
        }

        dataFolder.delete();
    }

    /**
     * Get the file for a region
     *
     * @param folder
     *     the folder
     * @param x
     *     the x coord
     * @param z
     *     the z coord
     * @return the file
     */
    public static File fileForRegion(File folder, int x, int z) {
        return fileForRegion(folder, key(x, z));
    }

    /**
     * Get the file for the given region
     *
     * @param folder
     *     the data folder
     * @param key
     *     the region key
     * @return the file
     */
    public static File fileForRegion(File folder, Long key) {
        File f = new File(folder, "p." + key + ".ttp");
        if(!f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }
        return f;
    }

    /**
     * Get the long value representing a chunk or region coordinate
     *
     * @param x
     *     the x
     * @param z
     *     the z
     * @return the value
     */
    public static Long key(int x, int z) {
        return CompressedNumbers.i2(x, z);
    }

    public MantleChunk getChunk(int x, int z) {
        return get(x >> 5, z >> 5).getOrCreate(x & 31, z & 31);
    }

    public void deleteChunk(int x, int z) {
        get(x >> 5, z >> 5).delete(x & 31, z & 31);
    }

    /**
     * Check very quickly if a tectonic plate exists via cached or the file system
     *
     * @param x
     *     the x region coordinate
     * @param z
     *     the z region coordinate
     * @return true if it exists
     */
    public boolean hasTectonicPlate(int x, int z) {
        Long k = key(x, z);
        return loadedRegions.containsKey(k) || fileForRegion(dataFolder, k).exists();
    }

    /**
     * Iterate data in a chunk
     *
     * @param x
     *     the chunk x
     * @param z
     *     the chunk z
     * @param type
     *     the type of data to iterate
     * @param iterator
     *     the iterator (x,y,z,data) -> do stuff
     * @param <T>
     *     the type of data to iterate
     */
    public <T> void iterateChunk(int x, int z, Class<T> type, Consume.Four<Integer, Integer, Integer, T> iterator) {
        if(!hasTectonicPlate(x >> 5, z >> 5)) {
            return;
        }

        get(x >> 5, z >> 5).getOrCreate(x & 31, z & 31).iterate(type, iterator);
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
        if(closed.get()) {
            throw new RuntimeException("The Mantle is closed");
        }

        if(y < 0 || y >= worldHeight) {
            return;
        }

        Matter matter = get((x >> 4) >> 5, (z >> 4) >> 5)
            .getOrCreate((x >> 4) & 31, (z >> 4) & 31)
            .getOrCreate(y >> 4);
        matter.slice(matter.getClass(t))
            .set(x & 15, y & 15, z & 15, t);
    }


    public <T> void remove(int x, int y, int z, Class<T> t) {
        if(closed.get()) {
            throw new RuntimeException("The Mantle is closed");
        }

        if(y < 0 || y >= worldHeight) {
            return;
        }

        Matter matter = get((x >> 4) >> 5, (z >> 4) >> 5)
            .getOrCreate((x >> 4) & 31, (z >> 4) & 31)
            .getOrCreate(y >> 4);
        matter.slice(t)
            .set(x & 15, y & 15, z & 15, null);
    }

    /**
     * Gets the data tat the current block position This method will attempt to find a
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
     *     the class representing the type of data being requested
     * @param <T>
     *     the type assumed from the provided class
     * @return the returned result (or null) if it doesnt exist
     */
    @SuppressWarnings("unchecked")

    public <T> T get(int x, int y, int z, Class<T> t) {
        if(closed.get()) {
            throw new RuntimeException("The Mantle is closed");
        }

        if(!hasTectonicPlate((x >> 4) >> 5, (z >> 4) >> 5)) {
            return null;
        }

        if(y < 0 || y >= worldHeight) {
            return null;
        }

        return (T) get((x >> 4) >> 5, (z >> 4) >> 5)
            .getOrCreate((x >> 4) & 31, (z >> 4) & 31)
            .getOrCreate(y >> 4).slice(t)
            .get(x & 15, y & 15, z & 15);
    }

    /**
     * Is this mantle closed
     *
     * @return true if it is
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Closes the Mantle. By closing the mantle, you can no longer read or writeNodeData
     * any data to the mantle or it's Tectonic Plates. Closing will also flush any
     * loaded regions to the disk in parallel.
     */
    public synchronized void close() {
        if(closed.get()) {
            return;
        }

        closed.set(true);
        saveAll();
        loadedRegions.clear();
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
            throw new RuntimeException("The Mantle is closed");
        }

        io.set(true);
        unload.clear();

        for(Long i : lastUse.keySet()) {
            hyperLock.withLong(i, () -> {
                if(System.currentTimeMillis() - lastUse.get(i) >= idleDuration) {
                    unload.add(i);
                }
            });
        }

        for(Long i : unload) {
            hyperLock.withLong(i, () -> {
                MantleRegion m = loadedRegions.remove(i);
                lastUse.remove(i);

                try {
                    m.write(fileForRegion(dataFolder, i));
                } catch(IOException e) {
                    e.printStackTrace();
                }
            });
        }
        io.set(false);
    }

    /**
     * This retreives a future of the Tectonic Plate at the given coordinates.
     * All methods accessing tectonic plates should go through this method
     *
     * @param x
     *     the region x
     * @param z
     *     the region z
     * @return the future of a tectonic plate.
     */
    private MantleRegion get(int x, int z) {
        if(io.get()) {
            try {
                return getSafe(x, z).get();
            } catch(InterruptedException e) {
                e.printStackTrace();
            } catch(ExecutionException e) {
                e.printStackTrace();
            }
        }

        MantleRegion p = loadedRegions.get(key(x, z));

        if(p != null) {
            return p;
        }

        try {
            return getSafe(x, z).get();
        } catch(InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return get(x, z);
    }

    /**
     * This retreives a future of the Tectonic Plate at the given coordinates.
     * All methods accessing tectonic plates should go through this method
     *
     * @param x
     *     the region x
     * @param z
     *     the region z
     * @return the future of a tectonic plate.
     */
    private Future<MantleRegion> getSafe(int x, int z) {
        Long k = key(x, z);
        MantleRegion p = loadedRegions.get(k);

        if(p != null) {
            lastUse.put(k, System.currentTimeMillis());
            return CompletableFuture.completedFuture(p);
        }

        return ioBurst.completeValue(() -> hyperLock.withResult(x, z, () -> {
            lastUse.put(k, System.currentTimeMillis());
            MantleRegion region = loadedRegions.get(k);

            if(region != null) {
                return region;
            }

            File file = fileForRegion(dataFolder, x, z);

            if(file.exists()) {
                try {
                    region = MantleRegion.read(worldHeight, file);
                    loadedRegions.put(k, region);
                } catch(Throwable e) {
                    e.printStackTrace();
                    region = new MantleRegion(worldHeight, x, z);
                    loadedRegions.put(k, region);
                }

                return region;
            }

            region = new MantleRegion(worldHeight, x, z);
            loadedRegions.put(k, region);
            return region;
        }));
    }

    public void saveAll() {
        if(loadedRegions.isEmpty())
        {
            return;
        }

        BurstExecutor b = ioBurst.burst(loadedRegions.size());
        for(Long i : loadedRegions.keySet()) {
            b.queue(() -> {
                try {
                    if(!dataFolder.exists())
                    {
                        dataFolder.mkdirs();
                    }
                    loadedRegions.get(i).write(fileForRegion(dataFolder, i));
                } catch(IOException e) {
                    e.printStackTrace();
                }
            });
        }

        try {
            b.complete();
        } catch(Throwable e) {
            e.printStackTrace();
        }
    }

    public int getWorldHeight() {
        return worldHeight;
    }

    public void deleteChunkSlice(int x, int z, Class<?> c) {
        getChunk(x, z).deleteSlices(c);
    }

    public int getLoadedRegionCount() {
        return loadedRegions.size();
    }

    public <T> void set(int x, int y, int z, MatterSlice<T> slice) {
        if(slice.isEmpty()) {
            return;
        }

        slice.iterateSync((xx, yy, zz, t) -> set(x + xx, y + yy, z + zz, t));
    }

    public boolean isChunkLoaded(int x, int z) {
        return loadedRegions.containsKey(key(x >> 5, z >> 5));
    }
}
