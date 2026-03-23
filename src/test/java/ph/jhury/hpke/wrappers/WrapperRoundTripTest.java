package ph.jhury.hpke.wrappers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ph.jhury.hpke.suites.HpkeSuite;

class WrapperRoundTripTest {

    private static final byte[] INFO = "hpke-benchmark-info".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AAD = "aad".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PLAINTEXT = new byte[64];

    static {
        Arrays.fill(PLAINTEXT, (byte) 0x42);
    }

    static Stream<Arguments> suiteAndLibrary() {
        Stream.Builder<Arguments> b = Stream.builder();
        for (HpkeSuite s : HpkeSuite.values()) {
            b.add(Arguments.of(Named.of(s.name(), s), new Jdk26HpkeWrapper()));
            b.add(Arguments.of(Named.of(s.name(), s), new BcHpkeWrapper()));
        }
        return b.build();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("suiteAndLibrary")
    void roundTrip_decryptsAndLengths(HpkeSuite suite, HpkeWrapper wrapper) {
        KeyMaterial km = wrapper.generateKeyPair(suite);
        SealResult sealed = wrapper.seal(suite, km.publicKey(), PLAINTEXT, AAD, INFO);
        assertEquals(suite.nenc(), sealed.enc().length);
        assertEquals(PLAINTEXT.length + 16, sealed.ciphertext().length);

        byte[] pt = wrapper.open(
                suite, km.privateKey(), km.publicKey(), sealed.enc(), sealed.ciphertext(), AAD, INFO);
        assertArrayEquals(PLAINTEXT, pt);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("suiteAndLibrary")
    void tamper_fails(HpkeSuite suite, HpkeWrapper wrapper) {
        KeyMaterial km = wrapper.generateKeyPair(suite);
        SealResult sealed = wrapper.seal(suite, km.publicKey(), PLAINTEXT, AAD, INFO);
        byte[] badCt = Arrays.copyOf(sealed.ciphertext(), sealed.ciphertext().length);
        badCt[badCt.length - 1] ^= 0x01;
        assertThrows(
                IllegalStateException.class,
                () -> wrapper.open(
                        suite, km.privateKey(), km.publicKey(), sealed.enc(), badCt, AAD, INFO));
    }
}
