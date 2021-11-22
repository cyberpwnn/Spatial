import org.cyberpwn.spatial.container.DataContainer;
import org.cyberpwn.spatial.container.NodeWritable;
import org.cyberpwn.spatial.mantle.Mantle;
import org.cyberpwn.spatial.matter.Matter;
import org.cyberpwn.spatial.matter.SpatialMatter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class LazyMansBareMinimumSanityCheck {
    @Test
    void testMantle() throws IOException, ClassNotFoundException {
        File tests = new File("mantle-tests");
        Mantle mantle = new Mantle(tests, 256);
        mantle.set(33, 225, 2344, 69);
        mantle.set(-385, 23, 24, 4.2d);
        mantle.set(445, 58, 24, "ass");
        mantle.close();
        mantle = new Mantle(tests, 256);
        assertEquals(69, mantle.get(33, 225, 2344, Integer.class));
        assertEquals(69, mantle.get(33, 225, 2344, int.class));
        assertEquals(4.2d, mantle.get(-385, 23, 24, Double.class));
        assertEquals(4.2d, mantle.get(-385, 23, 24, double.class));
        assertEquals("ass", mantle.get(445, 58, 24, String.class));
        mantle.close();

        // Cleanup
        for(File i : tests.listFiles())
        {
            i.delete();
        }

        tests.delete();
    }

    @Test
    void testMantleCustom() throws IOException, ClassNotFoundException {
        SpatialMatter.registerSliceType(new CustomDataMatter());
        File tests = new File("mantle-tests");
        Mantle mantle = new Mantle(tests, 256);
        mantle.set(0,0,0, new CustomData(4));
        mantle.close();
        mantle = new Mantle(tests, 256);
        assertEquals(4, mantle.get(0,0,0, CustomData.class).getF());
        mantle.close();

        // Cleanup
        for(File i : tests.listFiles())
        {
            i.delete();
        }

        tests.delete();
    }

    @Test
    void testMatter() throws IOException, ClassNotFoundException {
        Matter matter = new SpatialMatter(4, 4, 4);
        matter.slice(Integer.class).set(0, 0, 0, 69);
        matter.slice(int.class).set(1, 2, 0, 1337);
        matter.slice(Integer.class).set(3, 2, 3, 42);
        byte[] data = matter.write();
        Matter m = Matter.read(data);
        assertEquals(69, m.slice(int.class).get(0,0,0));
        assertEquals(1337, m.slice(Integer.class).get(1, 2, 0));
        assertEquals(42, m.slice(int.class).get(3, 2, 3));
    }

    @Test
    void testDataBits() throws IOException {
        NodeWritable<String> writer = new NodeWritable<String>() {
            @Override
            public String readNodeData(DataInputStream din) throws IOException {
                return din.readUTF();
            }

            @Override
            public void writeNodeData(DataOutputStream dos, String s) throws IOException {
                dos.writeUTF(s);
            }
        };

        DataContainer<String> d = new DataContainer<>(writer, 4);
        d.set(0, "hello");
        d.set(1, "hello");
        d.set(2, "world");
        d.set(3, "!");
        byte[] b = d.write();
        DataContainer<String> dd = new DataContainer<>(b, writer);
        assertEquals("hello", dd.get(0));
        assertEquals("hello", dd.get(1));
        assertEquals("world", dd.get(2));
        assertEquals("!", dd.get(3));
    }
}
