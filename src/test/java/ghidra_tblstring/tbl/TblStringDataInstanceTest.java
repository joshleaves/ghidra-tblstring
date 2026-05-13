/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.tbl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ghidra.docking.settings.SettingsImpl;
import ghidra.program.model.data.StringDataInstance;
import ghidra_tblstring.ghidra.TblRegistry;
import ghidra_tblstring.ghidra.TblStringDataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class TblStringDataInstanceTest {
  @DisplayName("renders decoded values with quotes")
  @Test
  void rendersDecodedValuesWithQuotes() {
    TblStringDataInstance instance =
        new TblStringDataInstance(dataType(), new SettingsImpl(), null, 1, "HELLO");

    assertEquals("\"HELLO\"", instance.getStringRepresentation());
  }

  @DisplayName("does not render failed decodes as quoted null")
  @Test
  void rendersNullDecodedValuesAsUnknown() {
    TblStringDataInstance instance =
        new TblStringDataInstance(dataType(), new SettingsImpl(), null, 1, null);

    assertEquals(StringDataInstance.UNKNOWN, instance.getStringRepresentation());
  }

  private TblStringDataType dataType() {
    return new TblStringDataType(new TblRegistry());
  }
}
