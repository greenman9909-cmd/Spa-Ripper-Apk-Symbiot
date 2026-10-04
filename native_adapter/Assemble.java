import brut.androlib.smali.SmaliBuilder;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/** Assemble the original code with a small, recorded network-boundary patch. */
public final class Assemble {
    public static void main(String[] args) throws Exception {
        DexBuilder dex = new DexBuilder(new Opcodes(29));
        SmaliBuilder assembler = new SmaliBuilder(34);
        List<Path> paths;
        try (java.util.stream.Stream<Path> files = Files.walk(Path.of(args[0]))) {
            paths = files.filter(p -> p.toString().endsWith(".smali")).sorted().collect(Collectors.toList());
        }
        int completed = 0;
        for (Path path : paths) {
            if (!assembler.buildFile(path.toFile(), dex)) throw new IllegalStateException("Cannot assemble " + path);
            if (++completed % 1000 == 0) System.out.println("Assembled " + completed + "/" + paths.size());
        }
        FileDataStore out = new FileDataStore(Path.of(args[1]).toFile());
        try { dex.writeTo(out); } finally { out.raf.close(); }
        System.out.println("Assembled " + paths.size() + " original classes");
    }
}
