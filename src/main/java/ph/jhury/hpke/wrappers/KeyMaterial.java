package ph.jhury.hpke.wrappers;

/** Raw public / private key bytes in HPKE wire format (aligned with Bouncy Castle HPKE serialization). */
public record KeyMaterial(byte[] publicKey, byte[] privateKey) {}
