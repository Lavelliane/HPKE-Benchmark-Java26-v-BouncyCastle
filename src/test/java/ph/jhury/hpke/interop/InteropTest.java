package ph.jhury.hpke.interop;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ph.jhury.hpke.suites.HpkeSuite;
import ph.jhury.hpke.wrappers.BcHpkeWrapper;
import ph.jhury.hpke.wrappers.Jdk26HpkeWrapper;
import ph.jhury.hpke.wrappers.SealResult;

class InteropTest {

    private static final byte[] INFO = "interop-info".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AAD = "aad".getBytes(StandardCharsets.UTF_8);

    private final Jdk26HpkeWrapper jdk = new Jdk26HpkeWrapper();
    private final BcHpkeWrapper bc = new BcHpkeWrapper();

    static Stream<Arguments> suiteAndPayload() {
        int[] sizes = {64, 1024, 65536};
        Stream.Builder<Arguments> b = Stream.builder();
        for (HpkeSuite s : HpkeSuite.values()) {
            for (int sz : sizes) {
                b.add(Arguments.of(s, sz));
            }
        }
        return b.build();
    }

    private static byte[] payload(int size) {
        byte[] p = new byte[size];
        Arrays.fill(p, (byte) 0x37);
        return p;
    }

    @ParameterizedTest(name = "{0} payload={1}")
    @MethodSource("suiteAndPayload")
    void jdkSeals_bcOpens(HpkeSuite suite, int payloadSize) {
        var km = jdk.generateKeyPair(suite);
        byte[] pt = payload(payloadSize);
        SealResult sealed = jdk.seal(suite, km.publicKey(), pt, AAD, INFO);
        byte[] opened = bc.open(suite, km.privateKey(), km.publicKey(), sealed.enc(), sealed.ciphertext(), AAD, INFO);
        assertArrayEquals(pt, opened);
    }

    @ParameterizedTest(name = "{0} payload={1}")
    @MethodSource("suiteAndPayload")
    void bcSeals_jdkOpens(HpkeSuite suite, int payloadSize) {
        var km = bc.generateKeyPair(suite);
        byte[] pt = payload(payloadSize);
        SealResult sealed = bc.seal(suite, km.publicKey(), pt, AAD, INFO);
        byte[] opened = jdk.open(suite, km.privateKey(), km.publicKey(), sealed.enc(), sealed.ciphertext(), AAD, INFO);
        assertArrayEquals(pt, opened);
    }

    @ParameterizedTest(name = "{0} payload={1}")
    @MethodSource("suiteAndPayload")
    void tamper_jdkSeal_bcRejects(HpkeSuite suite, int payloadSize) {
        var km = jdk.generateKeyPair(suite);
        byte[] pt = payload(payloadSize);
        SealResult sealed = jdk.seal(suite, km.publicKey(), pt, AAD, INFO);
        byte[] bad = Arrays.copyOf(sealed.ciphertext(), sealed.ciphertext().length);
        bad[0] ^= 0x01;
        assertThrows(
                IllegalStateException.class,
                () -> bc.open(suite, km.privateKey(), km.publicKey(), sealed.enc(), bad, AAD, INFO));
    }

    @ParameterizedTest(name = "{0} payload={1}")
    @MethodSource("suiteAndPayload")
    void tamper_bcSeal_jdkRejects(HpkeSuite suite, int payloadSize) {
        var km = bc.generateKeyPair(suite);
        byte[] pt = payload(payloadSize);
        SealResult sealed = bc.seal(suite, km.publicKey(), pt, AAD, INFO);
        byte[] bad = Arrays.copyOf(sealed.ciphertext(), sealed.ciphertext().length);
        bad[bad.length - 1] ^= 0x01;
        assertThrows(
                IllegalStateException.class,
                () -> jdk.open(suite, km.privateKey(), km.publicKey(), sealed.enc(), bad, AAD, INFO));
    }
}
