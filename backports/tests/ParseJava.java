import javax.tools.ToolProvider;
import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.util.*;

/** Syntax-only check. Deliberately does not claim Minecraft API or bytecode compatibility. */
class ParseJava {
    public static void main(String[] args) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            var files = new ArrayList<Path>();
            for (String directory : args) try (var paths = Files.walk(Path.of(directory))) {
                paths.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
            }
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null, manager.getJavaFileObjectsFromPaths(files));
            task.parse();
            var errors = diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR).toList();
            for (var error : errors) System.err.println(error);
            if (!errors.isEmpty()) System.exit(1);
            System.out.println("Parsed " + files.size() + " Java source files; Minecraft API compilation still required.");
        }
    }
}
