import lombok.AllArgsConstructor;
import lombok.Data;
import org.cyberpwn.spatial.parallel.BurstExecutor;
import org.cyberpwn.spatial.parallel.GridLock;
import org.cyberpwn.spatial.parallel.HyperLock;
import org.cyberpwn.spatial.parallel.MultiBurst;
import org.cyberpwn.spatial.space.Space;
import org.cyberpwn.spatial.util.CompressedNumbers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.ReentrantLock;

@Data
@AllArgsConstructor
public class Dog {
    private String name;
    private int age;
}