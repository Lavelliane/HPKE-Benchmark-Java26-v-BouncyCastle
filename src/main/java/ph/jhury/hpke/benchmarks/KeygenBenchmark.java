package ph.jhury.hpke.benchmarks;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import ph.jhury.hpke.suites.HpkeSuite;
import ph.jhury.hpke.wrappers.BcHpkeWrapper;
import ph.jhury.hpke.wrappers.HpkeWrapper;
import ph.jhury.hpke.wrappers.Jdk26HpkeWrapper;
import ph.jhury.hpke.wrappers.KeyMaterial;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(3)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 10, time = 3)
@Threads(1)
public class KeygenBenchmark {

    @State(Scope.Thread)
    public static class ThreadState {
        @Param({
            "P256_SHA256_A128",
            "P256_SHA256_A256",
            "P256_SHA256_CCP",
            "X25519_SHA256_A128",
            "X25519_SHA256_A256",
            "X25519_SHA256_CCP",
            "P384_SHA384_A256",
            "X448_SHA512_A256"
        })
        public String suite;

        @Param({"JDK26", "BC"})
        public String library;

        HpkeWrapper wrapper;
        HpkeSuite hpkeSuite;

        @Setup(Level.Trial)
        public void setup() {
            hpkeSuite = HpkeSuite.valueOf(suite);
            wrapper = library.equals("JDK26") ? new Jdk26HpkeWrapper() : new BcHpkeWrapper();
        }
    }

    @Benchmark
    public void generate(ThreadState state, Blackhole bh) {
        KeyMaterial km = state.wrapper.generateKeyPair(state.hpkeSuite);
        bh.consume(km.publicKey());
        bh.consume(km.privateKey());
    }
}
