import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.io.File;
import java.util.*;
public class JavaSyntaxCheck {
 public static void main(String[] paths)throws Exception {
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new IllegalStateException("JDK compiler unavailable");
  DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
  try(StandardJavaFileManager fm=compiler.getStandardFileManager(diagnostics,null,null)){
   Iterable<? extends JavaFileObject> files=fm.getJavaFileObjectsFromStrings(Arrays.asList(paths));
   JavacTask task=(JavacTask)compiler.getTask(null,fm,diagnostics,Arrays.asList("-proc:none"),null,files);task.parse();
   for(Diagnostic<?> d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new IllegalStateException(d.toString());
  }
  System.out.println("PASS: Java syntax for "+paths.length+" Android source files (not Android SDK type checking).");
 }
}
