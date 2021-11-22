import org.cyberpwn.spatial.container.Palette;
import org.cyberpwn.spatial.matter.slices.RawMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class CustomDataMatter extends RawMatter<CustomData> {
    public CustomDataMatter() {
        this(1, 1, 1);
    }

    public CustomDataMatter(int width, int height, int depth) {
        super(width, height, depth, CustomData.class);
    }

    @Override
    public Palette<CustomData> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(CustomData b, DataOutputStream dos) throws IOException {
        dos.writeInt(b.getF());
    }

    @Override
    public CustomData readNode(DataInputStream din) throws IOException {
        return new CustomData(din.readInt());
    }
}
