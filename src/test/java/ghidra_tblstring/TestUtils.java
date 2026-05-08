/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring;

import java.io.IOException;
import java.io.StringReader;

import ghidra_tblstring.tbl.TblTable;

public final class TestUtils {
  private TestUtils() {}

  public static TblTable parseTblTableString(String contents) throws IOException {
    return TblTable.parse("test", new StringReader(contents));
  }

  public static byte[] toBytesArray(int... values) {
    byte[] bytes = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      bytes[i] = (byte) values[i];
    }
    return bytes;
  }
}
