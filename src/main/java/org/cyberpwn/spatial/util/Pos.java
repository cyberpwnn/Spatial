package org.cyberpwn.spatial.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Pos {
    private int x=0;
    private int y=0;
    private int z=0;

    public Pos(double x, double y, double z)
    {
        this((int)x, (int)y, (int)z);
    }
}
