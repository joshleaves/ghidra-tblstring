/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.tbl;

/** Shared hexadecimal formatting/parsing helpers for .tbl byte keys. */
public final class TblHex {
  private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

  private TblHex() {}

  /**
   * Parses a whitespace-tolerant hexadecimal byte key.
   *
   * @param hex hexadecimal string, with optional whitespace between bytes
   * @return parsed bytes
   * @throws IllegalArgumentException if the key is empty, odd-length, or contains non-hex bytes
   */
  public static byte[] parseKey(String hex) {
    String normalized = hex == null ? "" : hex.replaceAll("\\s+", "");

    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("empty key");
    }

    if ((normalized.length() % 2) != 0) {
      throw new IllegalArgumentException("hex key must have an even number of digits");
    }

    byte[] bytes = new byte[normalized.length() / 2];
    for (int i = 0; i < bytes.length; i++) {
      int offset = i * 2;
      String pair = normalized.substring(offset, offset + 2);

      try {
        bytes[i] = (byte) Integer.parseInt(pair, 16);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("invalid hex byte '" + pair + "'", e);
      }
    }

    return bytes;
  }

  /**
   * Formats bytes as uppercase hexadecimal without separators.
   *
   * @param bytes bytes to format
   * @return uppercase hexadecimal text
   */
  public static String toHex(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);

    for (byte b : bytes) {
      int value = b & 0xff;
      result.append(HEX_DIGITS[(value >>> 4) & 0xf]);
      result.append(HEX_DIGITS[value & 0xf]);
    }

    return result.toString();
  }
}
