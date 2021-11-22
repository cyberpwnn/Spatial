import org.cyberpwn.spatial.matter.slices.RawMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

// Raw Matter is how we adapt our data
public class DogMatter extends RawMatter<Dog> {
    // This is useful for registry (later)
    public DogMatter() {
        this(1, 1, 1);
    }

    // This is used by reflective matter (internal)
    public DogMatter(int width, int height, int depth) {
        super(width, height, depth, Dog.class);
    }

    // Write our "dog" to a data output
    @Override
    public void writeNode(Dog b, DataOutputStream dos) throws IOException {
        dos.writeUTF(b.getName());
        dos.writeInt(b.getAge());
    }

    // Read a dog from a data input
    @Override
    public Dog readNode(DataInputStream din) throws IOException {
        return new Dog(din.readUTF(), din.readInt());
    }
}
