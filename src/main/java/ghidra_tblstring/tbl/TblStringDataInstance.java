/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.tbl;

import ghidra.docking.settings.Settings;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.StringDataInstance;
import ghidra.program.model.mem.MemBuffer;

/**
 * String-data instance that returns the decoded {@code tblString} value already produced by
 * {@link ghidra_tblstring.ghidra.TblStringDataType}.
 */
public class TblStringDataInstance extends StringDataInstance {
  private final String decoded;

  /**
   * Creates a string instance around a decoded table-string value.
   *
   * @param dataType owning data type
   * @param settings data instance settings
   * @param buf memory buffer for the data instance
   * @param length applied data length
   * @param decodedStr decoded string value, or {@code null} when decoding failed
   */
  public TblStringDataInstance(DataType dataType, Settings settings, MemBuffer buf, int length, String decodedStr) {
    super(dataType, settings, buf, length);
    decoded = decodedStr;
  }

  @Override
  public String getStringRepresentation() {
    if (decoded == null) {
      return UNKNOWN;
    }

    return "\"" + decoded + "\"";
  }
}
