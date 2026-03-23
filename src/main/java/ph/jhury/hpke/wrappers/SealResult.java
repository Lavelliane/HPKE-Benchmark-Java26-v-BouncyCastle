package ph.jhury.hpke.wrappers;

/** HPKE base mode: KEM encapsulation and AEAD ciphertext. */
public record SealResult(byte[] enc, byte[] ciphertext) {}
