/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.tbl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ghidra_tblstring.TestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class TblHexTest {
  @DisplayName("formats bytes as uppercase hexadecimal")
  @Test
  void toHexFormatsUppercaseHexadecimal() {
    assertEquals("0080FF", TblHex.toHex(TestUtils.toBytesArray(0x00, 0x80, 0xff)));
  }

  @DisplayName("parses whitespace tolerant hexadecimal keys")
  @Test
  void parseKeyAcceptsWhitespaceInsideHexKeys() {
    assertArrayEquals(TestUtils.toBytesArray(0x81, 0x40), TblHex.parseKey("81 40"));
  }

  @DisplayName("rejects invalid hexadecimal keys")
  @Test
  void parseKeyRejectsInvalidKeys() {
    assertEquals("empty key", assertThrows(IllegalArgumentException.class, () -> TblHex.parseKey("")).getMessage());
    assertEquals(
        "hex key must have an even number of digits",
        assertThrows(IllegalArgumentException.class, () -> TblHex.parseKey("123")).getMessage());

    IllegalArgumentException invalidByte =
        assertThrows(IllegalArgumentException.class, () -> TblHex.parseKey("4G"));
    assertEquals("invalid hex byte '4G'", invalidByte.getMessage());
    assertTrue(invalidByte.getCause() instanceof NumberFormatException);
  }
}
