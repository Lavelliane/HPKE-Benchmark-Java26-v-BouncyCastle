package ph.jhury.hpke.wrappers;

import ph.jhury.hpke.suites.HpkeSuite;

public interface HpkeWrapper {

    KeyMaterial generateKeyPair(HpkeSuite suite);

    SealResult seal(HpkeSuite suite, byte[] recipientPublicKey, byte[] plaintext, byte[] aad, byte[] info);

    byte[] open(
            HpkeSuite suite,
            byte[] recipientPrivateKey,
            byte[] recipientPublicKey,
            byte[] enc,
            byte[] ciphertext,
            byte[] aad,
            byte[] info);
}
