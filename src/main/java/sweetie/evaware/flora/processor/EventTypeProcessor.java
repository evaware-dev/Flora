package sweetie.evaware.flora.processor;

import sweetie.evaware.flora.api.EventType;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Generated;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * Generates direct event bus accessors. The generated code has no reflective
 * lookup on its posting path and is also consumable from Kotlin through kapt.
 */
public final class EventTypeProcessor extends AbstractProcessor {
    private Filer filer;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment environment) {
        super.init(environment);
        filer = environment.getFiler();
        messager = environment.getMessager();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(EventType.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(EventType.class)) {
            if (!(element instanceof TypeElement eventType)) {
                continue;
            }
            generate(eventType);
        }
        return true;
    }

    private void generate(TypeElement eventType) {
        if (!eventType.getModifiers().contains(Modifier.PUBLIC)) {
            error(eventType, "@EventType requires a public event type");
            return;
        }
        if (eventType.getNestingKind().isNested() && !eventType.getModifiers().contains(Modifier.STATIC)) {
            error(eventType, "@EventType requires a top-level or static nested event type");
            return;
        }
        if (!eventType.getTypeParameters().isEmpty()) {
            error(eventType, "@EventType does not support generic event types");
            return;
        }
        if (eventType.asType().getKind() == TypeKind.ERROR) {
            return;
        }

        PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(eventType);
        String packageName = packageElement.getQualifiedName().toString();
        String eventName = eventType.getQualifiedName().toString();
        String relativeName = packageName.isEmpty()
                ? eventName
                : eventName.substring(packageName.length() + 1);
        String generatedSimpleName = relativeName.replace('.', '_') + "Bus";
        String generatedName = packageName.isEmpty()
                ? generatedSimpleName
                : packageName + '.' + generatedSimpleName;

        try {
            JavaFileObject source = filer.createSourceFile(generatedName, eventType);
            try (Writer writer = source.openWriter()) {
                if (!packageName.isEmpty()) {
                    writer.write("package " + packageName + ";\n\n");
                }
                writer.write("@" + Generated.class.getCanonicalName() + "(\""
                        + EventTypeProcessor.class.getCanonicalName() + "\")\n");
                writer.write("public final class " + generatedSimpleName + " {\n");
                writer.write("    public static final sweetie.evaware.flora.core.FloraBus<" + eventName
                        + "> BUS = sweetie.evaware.flora.Flora.getBus(" + eventName + ".class);\n\n");
                writer.write("    private " + generatedSimpleName + "() {\n    }\n\n");
                writer.write("    public static sweetie.evaware.flora.core.FloraBus<" + eventName
                        + "> get() {\n        return BUS;\n    }\n\n");
                writer.write("    public static void post(" + eventName + " event) {\n");
                writer.write("        BUS.post(event);\n    }\n");
                writer.write("}\n");
            }
        } catch (IOException failure) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Unable to generate " + generatedName + ": " + failure.getMessage(), eventType);
        }
    }

    private void error(TypeElement eventType, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, eventType);
    }
}
