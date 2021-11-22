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

import lombok.Getter;
import org.cyberpwn.spatial.matter.slices.BooleanMatter;
import org.cyberpwn.spatial.matter.slices.ByteMatter;
import org.cyberpwn.spatial.matter.slices.DoubleMatter;
import org.cyberpwn.spatial.matter.slices.FloatMatter;
import org.cyberpwn.spatial.matter.slices.IntMatter;
import org.cyberpwn.spatial.matter.slices.LongMatter;
import org.cyberpwn.spatial.matter.slices.ShortMatter;
import org.cyberpwn.spatial.matter.slices.StringMatter;

import java.util.HashMap;
import java.util.Map;

public class SpatialMatter implements Matter {
    protected static final Map<Class<?>, MatterSlice<?>> slicers = new HashMap<>();

    @Getter
    private final MatterHeader header;

    @Getter
    private final int width;

    @Getter
    private final int height;

    @Getter
    private final int depth;

    @Getter
    private final Map<Class<?>, MatterSlice<?>> sliceMap;

    public SpatialMatter(int width, int height, int depth) {
        if(width < 1 || height < 1 || depth < 1) {
            throw new RuntimeException("Invalid Matter Size " + width + "x" + height + "x" + depth);
        }

        this.width = width;
        this.height = height;
        this.depth = depth;
        this.header = new MatterHeader();
        this.sliceMap = new HashMap<>();
    }

    public static void registerSliceType(MatterSlice<?> s) {
        registerSliceType(s.getType(), s);
    }

    private static void registerSliceType(Class<?> type, MatterSlice<?> s) {
        slicers.put(type, s);
    }

    @Override
    public <T> MatterSlice<T> createSlice(Class<T> type, Matter m) {
        MatterSlice<?> slice = slicers.get(getClass(type));

        if(slice == null) {
            return null;
        }

        try {
            return slice.getClass().getConstructor(int.class, int.class, int.class).newInstance(getWidth(), getHeight(), getDepth());
        } catch(Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    static {
        registerSliceType(new DoubleMatter());
        registerSliceType(new ByteMatter());
        registerSliceType(new FloatMatter());
        registerSliceType(new ShortMatter());
        registerSliceType(new BooleanMatter());
        registerSliceType(new IntMatter());
        registerSliceType(new LongMatter());
        registerSliceType(new StringMatter());
    }
}
