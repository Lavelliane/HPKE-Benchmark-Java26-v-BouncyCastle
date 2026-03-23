package ph.jhury.hpke.benchmarks;

import java.nio.charset.StandardCharsets;

final class BenchmarkConstants {

    static final byte[] INFO = "benchmark-hpke-info".getBytes(StandardCharsets.UTF_8);
    static final byte[] AAD = "benchmark-aad".getBytes(StandardCharsets.UTF_8);

    private BenchmarkConstants() {}
}
