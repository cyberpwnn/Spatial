# Spatial
Store stuff in space

A very simple collections &amp; general util api

[![Latest version of 'Spatial' @ Cloudsmith](https://api-prd.cloudsmith.io/v1/badges/version/arcane/archive/maven/Spatial/latest/a=noarch;xg=art.arcane/?render=true&show_latest=true)](https://cloudsmith.io/~arcane/repos/archive/packages/detail/maven/Spatial/latest/a=noarch;xg=art.arcane/)

```groovy
maven { url "https://dl.cloudsmith.io/public/arcane/archive/maven/" }
```

```groovy
implementation 'org.cyberpwn:Spatial:<VERSION>'
```

# Layer 1: Data Bits
The simplest form of repeating data stored as small as possible in memory & when written!

```java
// We need to teach the system how to write our data type (String) to/from bytes
// This can support literally any type
NodeWritable<String> writer = new NodeWritable<String>() {
    @Override
    public String readNodeData(DataInputStream din) throws IOException {
        // Reading data input -> String
        return din.readUTF();
    }

    @Override
    public void writeNodeData(DataOutputStream dos, String s) throws IOException {
        // Writing our data to the stream
        dos.writeUTF(s);
    }
};

// This data container will store an array of 4 Strings.
DataContainer<String> d = new DataContainer<>(writer, 4);
d.set(0, "hello");
d.set(1, "hello");
d.set(2, "world");
d.set(3, "!");

// Now we write the data to bytes
byte[] b = d.write();

// Now we read the data back from bytes, providing our existing "String Writer" from above
DataContainer<String> dd = new DataContainer<>(b, writer);
assertEquals("hello", dd.get(0));
assertEquals("hello", dd.get(1));
assertEquals("world", dd.get(2));
assertEquals("!", dd.get(3));
```

# Layer 2: Matter
Matter is in essence a wrapper for data bits but in 3D space, composed by slices. For example, If we have a 2x2x2 cube of data, note that each index in this cube can not just store a single type of data set by the matter, but instead each SLICE of the matter can store it's own type of data.

```java
// Create our matter cube as described above
Matter matter = new SpatialMatter(2, 2, 2);

// Store a string in 0,0,0 on the String slice
matter.slice(String.class).set(0,0,0, "Hello");

// Store a double in 0,0,0 on the double slice! (wont overwrite the string data above!)
matter.slice(double.class).set(0,0,0, 3.14);

// Just like before, write to binary
byte[] data = matter.write();

// Read the data
Matter m = Matter.read(data);
assertEquals("Hello", m.slice(String.class).get(0,0,0));
assertEquals(3.14, m.slice(double.class).get(0,0,0));
```

## Matter is a "Hunk"
The Hunk API is a very powerful set of tools for dealing with 3D data

```java
// MatterSlice<String> extends Hunk<String>
Hunk<String> h = m.slice(String.class);

h.getCenter();

// Get the closest in-bounds coordinate (our size is 2x2x2)
h.getClosest(33, 66, 77);
h.contains(33, 44, 55); // false, not in 2x2x2

// Get a "view" of the northern 2D (2x2x1) plane
// Hunk "views" will read / write from the parent
Hunk<String> view = h.cropFace(HunkFace.NORTH);

// Fill every position
h.fill("mood");

// If null return "missing"
h.getOr(0, 1, 1, "missing");

// Iterate the hunk
h.iterateSync((x,y,z,value) -> {});
h.iterateSync((x,y,z) -> {});

// Count non-null entries
int cnt = h.getNonNullEntries();

// Insert hunks into other hunks 
h.insert(otherHunk); // Will clip if bigger
h.insert(otherHunk, true); // invert Y
h.insert(3, 3, 3, otherHunk); // Insert with offset

// Crop out a copy between 1x1x1 to 5x5x5 making a 4x4x4 sized hunk
Hunk<String> copy = h.crop(1, 1, 1, 5, 5, 5);

// You can listen for changes
h.listen((x,y,z,dataChangedTo) -> {});
```

# Layer 3: The Mantle

The Mantle knows no bounds, it can span forever (int min/max signed i32). The mantle is essentially chunks of data packed into region files, using the sizes of minecraft's system. Each chunk section is 16 by 16 by 16, stacked into "chunks" vertically. These chunks are put into regions. 32x32 chunks per region (1024 chunks). 

```java
// We need a folder to store our data in
File dataFolder = new File("mantle");

// Create the mantle. We need to define a max height, from 0 to X.
Mantle mantle = new Mantle(dataFolder, 256);

// Set some data. It figures out which slices to use
mantle.set(33, 225, 2344, 69); // uses int slice
mantle.set(-385, 23, 24, 4.2d); // uses double slice
mantle.set(445, 58, 24, "ass"); // uses String slice

// Saves & Unloads everything when you are done
mantle.close(); 

// Load up the mantle again
mantle = new Mantle(dataFolder, 256);
assertEquals(69, mantle.get(33, 225, 2344, Integer.class));
assertEquals(69, mantle.get(33, 225, 2344, int.class));
assertEquals(4.2d, mantle.get(-385, 23, 24, Double.class));
assertEquals(4.2d, mantle.get(-385, 23, 24, double.class));
assertEquals("ass", mantle.get(445, 58, 24, String.class));
mantle.close();
```

### Custom Data Types
Yeah strings are great and all, but what about MY data?

First, we need our data we actually want to store. NOTE: These classes should be immutable because they are used in data palettes.
```java
// It's a dog class. You have seen this before...
@Data
@AllArgsConstructor
public class Dog
{
  private String name;
  private int age;
}
```

Second, we need an adapter. In a sense we need to teach Matter/Mantle how to save/load our data properly.

```java
// Raw Matter is how we adapt our data 
public class DogMatter extends RawMatter<Dog> {
    // This is useful for registry (later)
    public DogMatter() {
        this(1,1,1);
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
```

Finally, before we can use it, we need to register it. This way, the Matter system can figure out what a Dog is, and what "Matter Slice" it should use to store "Dogs"

```java
// Do this once generally on startup perhaps? Just do this before you use this slice type.
SpatialMatter.registerSliceType(new DogMatter());
```

Now we can use it just like before in Matter
```java
Matter matter = new SpatialMatter(2, 2, 2);

// Set dog to 0,0,0
matter.slice(Dog.class).set(0,0,0, new Dog("Jack", 5));

// Write to binary then read it again (proof)
byte[] data = matter.write();
matter = Matter.read(data);

assertEquals("Jack", matter.slice(Dog.class).get(0,0,0).getName());
```

You can also use this in the mantle! Where the real magic happens

```java
File dataFolder = new File("mantle");
Mantle mantle = new Mantle(dataFolder, 256);
mantle.set(33, 225, 2344, new Dog("Rusty", 9));

// Some advanced stuff if your interested
// A significantly advanced writer. Think WorldEdit for MC
MantleWriter w = mantle.write();

// x, y, z, radius, fill?, data
w.setSphere(0, 128, 0, 64, true, new Dog("Jimbo", 3)); // SO MUCH JIMBO

// x, y, z, radX, radY, radZ, fill?, data
w.setElipsoid(0, 128, 0, 3, 6, 9, false, "woah");

// Draw a multi-segment line with data in 3D
List<Pos> points = List.of(
    new Pos(1, 2, 1),
    new Pos(33, 35, -23),
    new Pos(39, 56, -222));
w.setLine(points, 
    3, true, "curved-line");

// Saves & Unloads everything when you are done
mantle.close(); 

// Load up the mantle again
mantle = new Mantle(dataFolder, 256);
assertEquals("Jimbo", mantle.get(-2, 126, 3, Dog.class).getName());
```
