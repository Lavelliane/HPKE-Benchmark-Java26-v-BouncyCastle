package ph.jhury.hpke.benchmarks;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import ph.jhury.hpke.wrappers.SealResult;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(3)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 10, time = 3)
@Threads(1)
public class OpenBenchmark {

    private static final int BATCH = 10;

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

        @Param({"64", "1024", "65536"})
        public String payloadSize;

        HpkeWrapper wrapper;
        HpkeSuite hpkeSuite;
        KeyMaterial km;
        byte[][] encs;
        byte[][] cts;
        final AtomicInteger idx = new AtomicInteger();

        @Setup(Level.Trial)
        public void setup() {
            hpkeSuite = HpkeSuite.valueOf(suite);
            wrapper = library.equals("JDK26") ? new Jdk26HpkeWrapper() : new BcHpkeWrapper();
            int len = Integer.parseInt(payloadSize);
            km = wrapper.generateKeyPair(hpkeSuite);
            byte[] payload = new byte[len];
            Arrays.fill(payload, (byte) 0x5a);
            encs = new byte[BATCH][];
            cts = new byte[BATCH][];
            for (int i = 0; i < BATCH; i++) {
                SealResult s = wrapper.seal(hpkeSuite, km.publicKey(), payload, BenchmarkConstants.AAD, BenchmarkConstants.INFO);
                encs[i] = s.enc();
                cts[i] = s.ciphertext();
            }
            idx.set(0);
        }
    }

    @Benchmark
    public void open(ThreadState state, Blackhole bh) {
        int i = Math.floorMod(state.idx.getAndIncrement(), BATCH);
        byte[] pt = state.wrapper.open(
                state.hpkeSuite,
                state.km.privateKey(),
                state.km.publicKey(),
                state.encs[i],
                state.cts[i],
                BenchmarkConstants.AAD,
                BenchmarkConstants.INFO);
        bh.consume(pt);
    }
}
