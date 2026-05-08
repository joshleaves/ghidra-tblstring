/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.tbl;

import java.util.Objects;

/**
 * Immutable mapping between one encoded byte sequence and its decoded text.
 *
 * <p>A table entry key is the raw byte pattern as it appears in program memory. The value is the
 * text appended by {@link TblStringDecoder} when that key matches. Keys are defensively copied on
 * construction and read so callers cannot mutate table state after creation.
 */
public final class TblTableEntry {
  private final byte[] key;
  private final String value;

  /**
   * Creates a table entry.
   *
   * @param key non-empty encoded byte sequence
   * @param value decoded text for the byte sequence
   * @throws NullPointerException if {@code key} or {@code value} is {@code null}
   * @throws IllegalArgumentException if {@code key} is empty
   */
  public TblTableEntry(byte[] key, String value) {
    Objects.requireNonNull(key, "key");
    if (key.length == 0) {
      throw new IllegalArgumentException("key cannot be empty");
    }

    this.key = key.clone();
    this.value = Objects.requireNonNull(value, "value");
  }

  /**
   * Returns a defensive copy of the encoded byte sequence.
   *
   * @return encoded byte sequence
   */
  public byte[] getKey() {
    return key.clone();
  }

  /**
   * Returns the number of bytes in this entry's key.
   *
   * @return key length in bytes
   */
  public int getKeyLength() {
    return key.length;
  }

  /**
   * Returns the decoded text for this entry.
   *
   * @return decoded text
   */
  public String getValue() {
    return value;
  }

  boolean matchesAt(byte[] bytes, int offset) {
    if (offset + key.length > bytes.length) {
      return false;
    }

    for (int i = 0; i < key.length; i++) {
      if (bytes[offset + i] != key[i]) {
        return false;
      }
    }

    return true;
  }
}
