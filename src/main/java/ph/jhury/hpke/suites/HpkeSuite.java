package ph.jhury.hpke.suites;

import javax.crypto.spec.HPKEParameterSpec;
import org.bouncycastle.crypto.hpke.HPKE;

/**
 * RFC 9180 suite combinations under test. JDK uses {@link HPKEParameterSpec} int constants; BC uses
 * {@link HPKE} short identifiers.
 */
public enum HpkeSuite {
    P256_SHA256_A128(
            "EC",
            "secp256r1",
            HPKEParameterSpec.KEM_DHKEM_P_256_HKDF_SHA256,
            HPKEParameterSpec.KDF_HKDF_SHA256,
            HPKEParameterSpec.AEAD_AES_128_GCM,
            HPKE.kem_P256_SHA256,
            HPKE.kdf_HKDF_SHA256,
            HPKE.aead_AES_GCM128,
            65),
    P256_SHA256_A256(
            "EC",
            "secp256r1",
            HPKEParameterSpec.KEM_DHKEM_P_256_HKDF_SHA256,
            HPKEParameterSpec.KDF_HKDF_SHA256,
            HPKEParameterSpec.AEAD_AES_256_GCM,
            HPKE.kem_P256_SHA256,
            HPKE.kdf_HKDF_SHA256,
            HPKE.aead_AES_GCM256,
            65),
    P256_SHA256_CCP(
            "EC",
            "secp256r1",
            HPKEParameterSpec.KEM_DHKEM_P_256_HKDF_SHA256,
            HPKEParameterSpec.KDF_HKDF_SHA256,
            HPKEParameterSpec.AEAD_CHACHA20_POLY1305,
            HPKE.kem_P256_SHA256,
            HPKE.kdf_HKDF_SHA256,
            HPKE.aead_CHACHA20_POLY1305,
            65),
    X25519_SHA256_A128(
            "X25519",
            null,
            HPKEParameterSpec.KEM_DHKEM_X25519_HKDF_SHA256,
            HPKEParameterSpec.KDF_HKDF_SHA256,
            HPKEParameterSpec.AEAD_AES_128_GCM,
            HPKE.kem_X25519_SHA256,
            HPKE.kdf_HKDF_SHA256,
            HPKE.aead_AES_GCM128,
            32),
    X25519_SHA256_A256(
            "X25519",
            null,
            HPKEParameterSpec.KEM_DHKEM_X25519_HKDF_SHA256,
            HPKEParameterSpec.KDF_HKDF_SHA256,
            HPKEParameterSpec.AEAD_AES_256_GCM,
            HPKE.kem_X25519_SHA256,
            HPKE.kdf_HKDF_SHA256,
            HPKE.aead_AES_GCM256,
            32),
    X25519_SHA256_CCP(
            "X25519",
            null,
            HPKEParameterSpec.KEM_DHKEM_X25519_HKDF_SHA256,
            HPKEParameterSpec.KDF_HKDF_SHA256,
            HPKEParameterSpec.AEAD_CHACHA20_POLY1305,
            HPKE.kem_X25519_SHA256,
            HPKE.kdf_HKDF_SHA256,
            HPKE.aead_CHACHA20_POLY1305,
            32),
    P384_SHA384_A256(
            "EC",
            "secp384r1",
            HPKEParameterSpec.KEM_DHKEM_P_384_HKDF_SHA384,
            HPKEParameterSpec.KDF_HKDF_SHA384,
            HPKEParameterSpec.AEAD_AES_256_GCM,
            HPKE.kem_P384_SHA384,
            HPKE.kdf_HKDF_SHA384,
            HPKE.aead_AES_GCM256,
            97),
    X448_SHA512_A256(
            "X448",
            null,
            HPKEParameterSpec.KEM_DHKEM_X448_HKDF_SHA512,
            HPKEParameterSpec.KDF_HKDF_SHA512,
            HPKEParameterSpec.AEAD_AES_256_GCM,
            HPKE.kem_X448_SHA512,
            HPKE.kdf_HKDF_SHA512,
            HPKE.aead_AES_GCM256,
            56);

    private final String keyAlgorithm;
    private final String ecCurveName;
    private final int jdkKemId;
    private final int jdkKdfId;
    private final int jdkAeadId;
    private final short bcKemId;
    private final short bcKdfId;
    private final short bcAeadId;
    private final int nenc;

    HpkeSuite(
            String keyAlgorithm,
            String ecCurveName,
            int jdkKemId,
            int jdkKdfId,
            int jdkAeadId,
            short bcKemId,
            short bcKdfId,
            short bcAeadId,
            int nenc) {
        this.keyAlgorithm = keyAlgorithm;
        this.ecCurveName = ecCurveName;
        this.jdkKemId = jdkKemId;
        this.jdkKdfId = jdkKdfId;
        this.jdkAeadId = jdkAeadId;
        this.bcKemId = bcKemId;
        this.bcKdfId = bcKdfId;
        this.bcAeadId = bcAeadId;
        this.nenc = nenc;
    }

    public String keyAlgorithm() {
        return keyAlgorithm;
    }

    public String ecCurveName() {
        return ecCurveName;
    }

    public int jdkKemId() {
        return jdkKemId;
    }

    public int jdkKdfId() {
        return jdkKdfId;
    }

    public int jdkAeadId() {
        return jdkAeadId;
    }

    public short bcKemId() {
        return bcKemId;
    }

    public short bcKdfId() {
        return bcKdfId;
    }

    public short bcAeadId() {
        return bcAeadId;
    }

    /** Expected KEM encapsulation length (Nenc) in bytes. */
    public int nenc() {
        return nenc;
    }
}
