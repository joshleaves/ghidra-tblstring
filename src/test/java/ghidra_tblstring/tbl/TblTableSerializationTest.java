/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.tbl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ghidra_tblstring.TestUtils;
import java.io.IOException;
import java.io.StringReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class TblTableSerializationTest {
  @DisplayName("serializes escaped values")
  @Test
  void serializeEscapesValues() throws IOException {
    TblTable table =
        TestUtils.parseTblTableString(
            "01=line\\nnext\n"
                + "02=carriage\\rreturn\n"
                + "03=tab\\tvalue\n"
                + "04=slash\\\\value\n"
                + "20= \n");

    assertEquals(
        "01=line\\nnext\n"
            + "02=carriage\\rreturn\n"
            + "03=tab\\tvalue\n"
            + "04=slash\\\\value\n"
            + "20= \n",
        table.toTblString());
  }

  @DisplayName("serialized tables round-trip through parser")
  @Test
  void serializeRoundTripsThroughParser() throws IOException {
    TblTable original =
        TestUtils.parseTblTableString(
            "01=line\\nnext\n"
                + "02=carriage\\rreturn\n"
                + "03=tab\\tvalue\n"
                + "04=slash\\\\value\n"
                + "20= \n");

    TblTable reloaded = TblTable.parse("test", new StringReader(original.toTblString()));

    assertEquals(original.getEntries().size(), reloaded.getEntries().size());
    for (int i = 0; i < original.getEntries().size(); i++) {
      assertArrayEquals(
          original.getEntries().get(i).getKey(), reloaded.getEntries().get(i).getKey());
      assertEquals(
          original.getEntries().get(i).getValue(), reloaded.getEntries().get(i).getValue());
    }
  }
}
