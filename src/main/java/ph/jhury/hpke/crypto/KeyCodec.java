package ph.jhury.hpke.crypto;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;
import java.util.Optional;
import ph.jhury.hpke.suites.HpkeSuite;

/**
 * Converts between JCA keys and raw HPKE key bytes in the same layout as Bouncy Castle's
 * {@link org.bouncycastle.crypto.hpke.HPKE#serializePublicKey} /
 * {@link org.bouncycastle.crypto.hpke.HPKE#serializePrivateKey}.
 */
public final class KeyCodec {

    private KeyCodec() {}

    public static byte[] exportPublicKey(HpkeSuite suite, PublicKey publicKey) throws Exception {
        return switch (suite.keyAlgorithm()) {
            case "EC" -> exportEcPublic((ECPublicKey) publicKey);
            case "X25519" -> exportXecPublic((XECPublicKey) publicKey, 32);
            case "X448" -> exportXecPublic((XECPublicKey) publicKey, 56);
            default -> throw new IllegalArgumentException(suite.keyAlgorithm());
        };
    }

    public static byte[] exportPrivateKey(HpkeSuite suite, PrivateKey privateKey) throws Exception {
        return switch (suite.keyAlgorithm()) {
            case "EC" -> exportEcPrivate((ECPrivateKey) privateKey);
            case "X25519" -> exportXecPrivate((XECPrivateKey) privateKey, 32);
            case "X448" -> exportXecPrivate((XECPrivateKey) privateKey, 56);
            default -> throw new IllegalArgumentException(suite.keyAlgorithm());
        };
    }

    public static PublicKey importPublicKey(HpkeSuite suite, byte[] raw) throws Exception {
        return switch (suite.keyAlgorithm()) {
            case "EC" -> importEcPublic(suite, raw);
            case "X25519" -> importXecPublic("X25519", NamedParameterSpec.X25519, raw, 32);
            case "X448" -> importXecPublic("X448", NamedParameterSpec.X448, raw, 56);
            default -> throw new IllegalArgumentException(suite.keyAlgorithm());
        };
    }

    public static PrivateKey importPrivateKey(HpkeSuite suite, byte[] privateRaw, byte[] publicRaw)
            throws Exception {
        return switch (suite.keyAlgorithm()) {
            case "EC" -> importEcPrivate(suite, privateRaw, publicRaw);
            case "X25519" -> importXecPrivate("X25519", NamedParameterSpec.X25519, privateRaw, 32);
            case "X448" -> importXecPrivate("X448", NamedParameterSpec.X448, privateRaw, 56);
            default -> throw new IllegalArgumentException(suite.keyAlgorithm());
        };
    }

    public static KeyPairGenerator keyPairGenerator(HpkeSuite suite) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(suite.keyAlgorithm());
        String curve = suite.ecCurveName();
        if (curve != null) {
            kpg.initialize(new ECGenParameterSpec(curve));
        } else {
            kpg.initialize(suite.keyAlgorithm().equals("X25519") ? 255 : 448);
        }
        return kpg;
    }

    private static byte[] exportEcPublic(ECPublicKey pub) {
        ECPoint w = pub.getW();
        EllipticCurve curve = pub.getParams().getCurve();
        int fieldBytes = (curve.getField().getFieldSize() + 7) / 8;
        return encodeEcPointUncompressed(w, fieldBytes);
    }

    /** Uncompressed SEC1: 0x04 || x || y (big-endian, fixed width). */
    private static byte[] encodeEcPointUncompressed(ECPoint p, int fieldBytes) {
        byte[] xb = unsignedBigIntegerToFixedLength(p.getAffineX(), fieldBytes);
        byte[] yb = unsignedBigIntegerToFixedLength(p.getAffineY(), fieldBytes);
        byte[] out = new byte[1 + 2 * fieldBytes];
        out[0] = 0x04;
        System.arraycopy(xb, 0, out, 1, fieldBytes);
        System.arraycopy(yb, 0, out, 1 + fieldBytes, fieldBytes);
        return out;
    }

    private static byte[] exportEcPrivate(ECPrivateKey priv) {
        ECParameterSpec params = priv.getParams();
        int len = (params.getCurve().getField().getFieldSize() + 7) / 8;
        return unsignedBigIntegerToFixedLength(priv.getS(), len);
    }

    private static byte[] exportXecPublic(XECPublicKey pub, int len) {
        return unsignedBigIntegerToLe(pub.getU(), len);
    }

    private static byte[] exportXecPrivate(XECPrivateKey priv, int len) {
        Optional<byte[]> scalar = priv.getScalar();
        if (scalar.isEmpty()) {
            throw new IllegalStateException("XEC private key has no scalar");
        }
        byte[] s = scalar.get();
        if (s.length == len) {
            return Arrays.copyOf(s, len);
        }
        // Normalize length if provider returns shorter (sign-padded) encoding
        byte[] out = new byte[len];
        System.arraycopy(s, 0, out, out.length - s.length, s.length);
        return out;
    }

    private static PublicKey importEcPublic(HpkeSuite suite, byte[] raw) throws Exception {
        ECParameterSpec ecSpec = namedEcSpec(suite.ecCurveName());
        EllipticCurve curve = ecSpec.getCurve();
        int fieldBytes = (curve.getField().getFieldSize() + 7) / 8;
        if (raw.length != 1 + 2 * fieldBytes || raw[0] != 0x04) {
            throw new IllegalArgumentException("Invalid uncompressed EC point encoding");
        }
        BigInteger ax = new BigInteger(1, Arrays.copyOfRange(raw, 1, 1 + fieldBytes));
        BigInteger ay = new BigInteger(1, Arrays.copyOfRange(raw, 1 + fieldBytes, raw.length));
        ECPoint point = new ECPoint(ax, ay);
        ECPublicKeySpec spec = new ECPublicKeySpec(point, ecSpec);
        return (PublicKey) KeyFactory.getInstance("EC").generatePublic(spec);
    }

    private static PrivateKey importEcPrivate(HpkeSuite suite, byte[] sk, byte[] pk) throws Exception {
        ECParameterSpec ecSpec = namedEcSpec(suite.ecCurveName());
        BigInteger s = new BigInteger(1, sk);
        ECPrivateKeySpec spec = new ECPrivateKeySpec(s, ecSpec);
        return (PrivateKey) KeyFactory.getInstance("EC").generatePrivate(spec);
    }

    private static ECParameterSpec namedEcSpec(String name) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec(name));
        ECPublicKey ep = (ECPublicKey) kpg.generateKeyPair().getPublic();
        return ep.getParams();
    }

    private static PublicKey importXecPublic(String algorithm, NamedParameterSpec named, byte[] raw, int len)
            throws Exception {
        if (raw.length != len) {
            throw new IllegalArgumentException("Unexpected public key length: " + raw.length);
        }
        KeyFactory kf = KeyFactory.getInstance(algorithm);
        BigInteger u = leToUnsignedBigInteger(raw);
        XECPublicKeySpec spec = new XECPublicKeySpec(named, u);
        return kf.generatePublic(spec);
    }

    private static PrivateKey importXecPrivate(
            String algorithm, NamedParameterSpec named, byte[] raw, int len) throws Exception {
        if (raw.length != len) {
            throw new IllegalArgumentException("Unexpected private key length: " + raw.length);
        }
        KeyFactory kf = KeyFactory.getInstance(algorithm);
        XECPrivateKeySpec spec = new XECPrivateKeySpec(named, raw);
        return kf.generatePrivate(spec);
    }

    /** RFC 7748 / HPKE wire format: little-endian. */
    private static byte[] unsignedBigIntegerToLe(BigInteger v, int length) {
        byte[] le = new byte[length];
        byte[] mag = v.toByteArray();
        int srcPos = mag[0] == 0 ? 1 : 0;
        int magLen = mag.length - srcPos;
        for (int i = 0; i < magLen && i < length; i++) {
            le[i] = mag[mag.length - 1 - i];
        }
        return le;
    }

    private static BigInteger leToUnsignedBigInteger(byte[] le) {
        byte[] be = new byte[le.length + 1];
        for (int i = 0; i < le.length; i++) {
            be[be.length - 1 - i] = le[i];
        }
        return new BigInteger(be);
    }

    private static byte[] unsignedBigIntegerToFixedLength(BigInteger v, int length) {
        byte[] mag = v.toByteArray();
        byte[] out = new byte[length];
        int srcPos = mag[0] == 0 ? 1 : 0;
        int magLen = mag.length - srcPos;
        System.arraycopy(mag, srcPos, out, out.length - magLen, magLen);
        return out;
    }
}
