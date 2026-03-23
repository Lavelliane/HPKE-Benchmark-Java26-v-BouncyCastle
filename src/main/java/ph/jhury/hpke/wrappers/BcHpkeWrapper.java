package ph.jhury.hpke.wrappers;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.hpke.HPKE;
import ph.jhury.hpke.suites.HpkeSuite;

/** HPKE via Bouncy Castle {@link HPKE} lightweight API (RFC 9180). */
public final class BcHpkeWrapper implements HpkeWrapper {

    private static HPKE hpke(HpkeSuite suite) {
        return new HPKE(HPKE.mode_base, suite.bcKemId(), suite.bcKdfId(), suite.bcAeadId());
    }

    @Override
    public KeyMaterial generateKeyPair(HpkeSuite suite) {
        try {
            HPKE hp = hpke(suite);
            AsymmetricCipherKeyPair kp = hp.generatePrivateKey();
            byte[] pub = hp.serializePublicKey(kp.getPublic());
            byte[] priv = hp.serializePrivateKey(kp.getPrivate());
            return new KeyMaterial(pub, priv);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public SealResult seal(HpkeSuite suite, byte[] recipientPublicKey, byte[] plaintext, byte[] aad, byte[] info) {
        try {
            HPKE hp = hpke(suite);
            var pkR = hp.deserializePublicKey(recipientPublicKey);
            byte[][] out = hp.seal(pkR, info, aad, plaintext, null, null, null);
            // BC returns [ciphertext, enc] (see org.bouncycastle.crypto.hpke.HPKE#seal)
            return new SealResult(out[1], out[0]);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public byte[] open(
            HpkeSuite suite,
            byte[] recipientPrivateKey,
            byte[] recipientPublicKey,
            byte[] enc,
            byte[] ciphertext,
            byte[] aad,
            byte[] info) {
        try {
            HPKE hp = hpke(suite);
            AsymmetricCipherKeyPair skR = hp.deserializePrivateKey(recipientPrivateKey, recipientPublicKey);
            return hp.open(enc, skR, info, aad, ciphertext, null, null, null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
