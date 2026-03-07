package benchmark;

import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;

public class SingleBenchmarkMain {
    public static void main(String[] args) throws Exception {
        IBenchmark benchmark = createBenchmark(args[0]);
        Blackhole blackhole = new Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.");
        long[] timings = new long[Constants.MEASUREMENTS];

        benchmark.prepare();

        int totalSamples = Constants.WARMUP_MEASUREMENTS + Constants.MEASUREMENTS;

        for (int sample = 0; sample < totalSamples; sample++) {
            long nanos = System.nanoTime();

            for (int operation = 0; operation < Constants.OPERATIONS_PER_SAMPLE; operation++) {
                benchmark.benchmark(blackhole);
            }

            if (sample >= Constants.WARMUP_MEASUREMENTS) {
                long delta = System.nanoTime() - nanos;
                timings[sample - Constants.WARMUP_MEASUREMENTS] = Math.round((double) delta / Constants.OPERATIONS_PER_SAMPLE);
            }
        }

        Arrays.sort(timings);

        long median = percentile(timings, 0.5);
        long p05 = percentile(timings, 0.05);
        long p95 = percentile(timings, 0.95);

        System.out.println(benchmark.getClass().getSimpleName() + " " + median + " ± " + ((p95 - p05) / 2L));
    }

    private static IBenchmark createBenchmark(String className) throws Exception {
        Class<?> type = Class.forName(className);
        return (IBenchmark) type.getDeclaredConstructor().newInstance();
    }

    private static long percentile(long[] timings, double percentile) {
        int index = (int) Math.round((timings.length - 1) * percentile);
        return timings[Math.max(0, Math.min(index, timings.length - 1))];
    }
}
