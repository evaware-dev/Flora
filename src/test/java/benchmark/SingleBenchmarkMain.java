package benchmark;

import java.util.Arrays;

public class SingleBenchmarkMain {
    public static void main(String[] args) throws Exception {
        IBenchmark benchmark = createBenchmark(args[0]);
        BenchmarkSink sink = new BenchmarkSink();
        long[] timings = new long[Constants.MEASUREMENTS];

        benchmark.prepare();

        int totalSamples = Constants.WARMUP_MEASUREMENTS + Constants.MEASUREMENTS;

        for (int sample = 0; sample < totalSamples; sample++) {
            long nanos = System.nanoTime();

            for (int operation = 0; operation < Constants.OPERATIONS_PER_SAMPLE; operation++) {
                benchmark.benchmark(sink);
            }

            if (sample >= Constants.WARMUP_MEASUREMENTS) {
                long delta = System.nanoTime() - nanos;
                timings[sample - Constants.WARMUP_MEASUREMENTS] = Math.round((double) delta / Constants.OPERATIONS_PER_SAMPLE);
            }
        }

        Arrays.sort(timings);

        long min = timings[0];
        long max = timings[timings.length - 1];
        double avg = average(timings);
        long median = percentile(timings, 0.5);
        long p05 = percentile(timings, 0.05);
        long p95 = percentile(timings, 0.95);
        benchmark.close();

        System.out.printf(
                "%s avg=%.2f ns median=%d ns min=%d ns max=%d ns p05=%d ns p95=%d ns spread=%d ns checksum=%d%n",
                benchmark.getClass().getSimpleName(),
                avg,
                median,
                min,
                max,
                p05,
                p95,
                p95 - p05,
                sink.value()
        );
    }

    private static IBenchmark createBenchmark(String className) throws Exception {
        Class<?> type = Class.forName(className);
        return (IBenchmark) type.getDeclaredConstructor().newInstance();
    }

    private static long percentile(long[] timings, double percentile) {
        int index = (int) Math.round((timings.length - 1) * percentile);
        return timings[Math.max(0, Math.min(index, timings.length - 1))];
    }

    private static double average(long[] timings) {
        long sum = 0L;
        for (long timing : timings) {
            sum += timing;
        }
        return (double) sum / timings.length;
    }
}
