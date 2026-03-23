package ph.jhury.hpke.wrappers;

import java.security.PrivateKey;
import java.security.PublicKey;
import javax.crypto.Cipher;
import javax.crypto.spec.HPKEParameterSpec;
import ph.jhury.hpke.crypto.KeyCodec;
import ph.jhury.hpke.suites.HpkeSuite;

/** HPKE via JDK 26 {@link Cipher} API and {@link HPKEParameterSpec}. */
public final class Jdk26HpkeWrapper implements HpkeWrapper {

    @Override
    public KeyMaterial generateKeyPair(HpkeSuite suite) {
        try {
            var kpg = KeyCodec.keyPairGenerator(suite);
            var kp = kpg.generateKeyPair();
            byte[] pub = KeyCodec.exportPublicKey(suite, kp.getPublic());
            byte[] priv = KeyCodec.exportPrivateKey(suite, kp.getPrivate());
            return new KeyMaterial(pub, priv);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public SealResult seal(HpkeSuite suite, byte[] recipientPublicKey, byte[] plaintext, byte[] aad, byte[] info) {
        try {
            PublicKey pub = KeyCodec.importPublicKey(suite, recipientPublicKey);
            HPKEParameterSpec spec =
                    HPKEParameterSpec.of(suite.jdkKemId(), suite.jdkKdfId(), suite.jdkAeadId()).withInfo(info);
            Cipher sender = Cipher.getInstance("HPKE");
            sender.init(Cipher.ENCRYPT_MODE, pub, spec);
            if (aad != null && aad.length > 0) {
                sender.updateAAD(aad);
            }
            byte[] enc = sender.getIV();
            byte[] ciphertext = sender.doFinal(plaintext);
            return new SealResult(enc, ciphertext);
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
            PrivateKey priv = KeyCodec.importPrivateKey(suite, recipientPrivateKey, recipientPublicKey);
            HPKEParameterSpec spec = HPKEParameterSpec.of(suite.jdkKemId(), suite.jdkKdfId(), suite.jdkAeadId())
                    .withInfo(info)
                    .withEncapsulation(enc);
            Cipher recipient = Cipher.getInstance("HPKE");
            recipient.init(Cipher.DECRYPT_MODE, priv, spec);
            if (aad != null && aad.length > 0) {
                recipient.updateAAD(aad);
            }
            return recipient.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
