package sweetie.evaware.flora.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventTypeProcessorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesAndCompilesDirectBusAccessor() throws Exception {
        Path sources = Files.createDirectories(temporaryDirectory.resolve("sources/example"));
        Path generated = Files.createDirectories(temporaryDirectory.resolve("generated"));
        Path classes = Files.createDirectories(temporaryDirectory.resolve("classes"));
        Path eventSource = sources.resolve("LoginEvent.java");
        Files.writeString(eventSource, """
                package example;

                import sweetie.evaware.flora.api.EventType;

                @EventType
                public final class LoginEvent {
                }
                """);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-processor", EventTypeProcessor.class.getName(),
                    "-s", generated.toString(),
                    "-d", classes.toString()
            );
            boolean compiled = compiler.getTask(null, files, null, options, null,
                    files.getJavaFileObjects(eventSource)).call();
            assertTrue(compiled);
        }

        Path generatedSource = generated.resolve("example/LoginEventBus.java");
        assertTrue(Files.isRegularFile(generatedSource));
        String code = Files.readString(generatedSource);
        assertTrue(code.contains("Flora.getBus(example.LoginEvent.class)"));
        assertTrue(code.contains("public static final sweetie.evaware.flora.core.FloraBus<example.LoginEvent> BUS"));
        assertTrue(code.contains("public static void post(example.LoginEvent event)"));
        assertTrue(Files.isRegularFile(classes.resolve("example/LoginEventBus.class")));
    }

    @Test
    void rejectsGenericEventTypes() throws Exception {
        Path sources = Files.createDirectories(temporaryDirectory.resolve("generic-sources/example"));
        Path generated = Files.createDirectories(temporaryDirectory.resolve("generic-generated"));
        Path classes = Files.createDirectories(temporaryDirectory.resolve("generic-classes"));
        Path eventSource = sources.resolve("GenericEvent.java");
        Files.writeString(eventSource, """
                package example;

                import sweetie.evaware.flora.api.EventType;

                @EventType
                public final class GenericEvent<T> {
                }
                """);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-processor", EventTypeProcessor.class.getName(),
                    "-s", generated.toString(),
                    "-d", classes.toString()
            );
            boolean compiled = compiler.getTask(null, files, null, options, null,
                    files.getJavaFileObjects(eventSource)).call();
            assertFalse(compiled);
        }
    }
}
