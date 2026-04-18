package benchmark;

import java.util.List;
import java.util.stream.Collectors;

public class Benchmarks {
    public static void main(String[] args) {
        List<Class<? extends IBenchmark>> benchmarks = List.of(
                BlazingBenchmark.class,
                FloraBenchmark.class
        );

        for (Class<? extends IBenchmark> benchmark : benchmarks) {
            runFork(benchmark);
        }
    }

    private static void runFork(Class<? extends IBenchmark> benchmark) {
        ProcessBuilder builder = new ProcessBuilder(
                javaBin(),
                "-cp",
                System.getProperty("java.class.path"),
                SingleBenchmarkMain.class.getName(),
                benchmark.getName()
        );
        builder.redirectErrorStream(true);

        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            int exitCode = process.waitFor();

            if (!output.isEmpty()) {
                System.out.println(output.lines().collect(Collectors.joining(System.lineSeparator())));
            }

            if (exitCode != 0) {
                throw new IllegalStateException("Benchmark failed: " + benchmark.getSimpleName());
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to run benchmark " + benchmark.getSimpleName(), e);
        }
    }

    private static String javaBin() {
        String separator = System.getProperty("file.separator");
        return System.getProperty("java.home") + separator + "bin" + separator + "java";
    }
}
