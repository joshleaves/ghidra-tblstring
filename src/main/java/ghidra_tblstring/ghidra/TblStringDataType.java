/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.ghidra;

import ghidra.docking.settings.Settings;
import ghidra.program.model.data.AbstractStringDataType;
import ghidra.program.model.data.CharsetSettingsDefinition;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.DataTypeManager;
import ghidra.program.model.data.StringDataType;
import ghidra.program.model.data.StringLayoutEnum;
import ghidra.program.model.listing.Program;
import ghidra.program.model.mem.MemBuffer;
import ghidra.program.model.mem.Memory;
import ghidra_tblstring.tbl.TblStringDecoder;
import ghidra_tblstring.tbl.TblTable;

public class TblStringDataType extends AbstractStringDataType {

  public static final String NAME = "tblString";

  private final TblRegistry registry;
  private final int fixedLength;
  private final String tableCharsetName;

  public TblStringDataType() {
    this(new TblRegistry(), -1, null, null);
  }

  public TblStringDataType(TblRegistry registry) {
    this(registry, -1, null, null);
  }

  public TblStringDataType(TblRegistry registry, int fixedLength) {
    this(registry, fixedLength, null, null);
  }

  public TblStringDataType(TblRegistry registry, int fixedLength, String tableId) {
    this(registry, fixedLength, tableId, null);
  }

  private TblStringDataType(
      TblRegistry registry, int fixedLength, String tableId, DataTypeManager dtm) {
    super(
      NAME, // data type name
      NAME, // mnemonic
      ".tblString", // default label
      "TBL", // default label prefix
      "tbl", // default abbrev label prefix
      "Table-based decoded string", // description
      null,
      StringDataType.dataType, // replacement data type
      StringLayoutEnum.FIXED_LEN, // StringLayoutEnum
      dtm // data type manager
    );
    this.registry = registry != null ? registry : new TblRegistry();
    this.fixedLength = fixedLength > 0 ? fixedLength : 1;
    this.tableCharsetName = tableId;
  }

  @Override
  public int getLength() {
    return -1;
  }

  @Override
  public int getAlignedLength() {
    return getLength();
  }

  @Override
  public int getLength(MemBuffer buf, int maxLength) {
    if (maxLength > 0) {
      return maxLength;
    }

    return fixedLength;
  }

  @Override
  public String getMnemonic(Settings settings) {
    String id = getTableId(settings);
    if (id != null) {
      return "tbl:" + id;
    }
    return NAME;
  }

  @Override
  public Object getValue(MemBuffer buf, Settings settings, int length) {
    try {
      int byteLength = getReadLength(length);
      byte[] bytes = new byte[byteLength];
      buf.getBytes(bytes, 0);

      String id = getTableId(settings);
      if (id == null) {
        return null;
      }

      TblTable table = getTable(buf, id);
      if (table == null) {
        return null;
      }

      return TblStringDecoder.decode(bytes, table);
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public String getRepresentation(MemBuffer buf, Settings settings, int length) {
    Object value = getValue(buf, settings, length);
    if (value != null) {
      return "\"" + value.toString() + "\"";
    }

    String id = getTableId(settings);
    if (id == null) {
      return "<no-table>";
    }

    if (getTable(buf, id) == null) {
      return "<missing-table:" + id + ">";
    }

    return "<error>";
  }

  @Override
  public String getCharsetName(Settings settings) {
    if (settings != null) {
      String id = CharsetSettingsDefinition.CHARSET.getCharset(settings, null);
      if (id != null && !id.isBlank()) {
        return id;
      }
    }

    if (tableCharsetName != null && !tableCharsetName.isBlank()) {
      return tableCharsetName;
    }

    return "default";
  }

  private String getTableId(Settings settings) {
    String id = getCharsetName(settings);
    if (id != null && !id.isBlank()) {
      return id;
    }

    return null;
  }

  private TblTable getTable(MemBuffer buf, String id) {
    TblTable table = registry.get(id).orElse(null);
    if (table != null) {
      return table;
    }

    loadRegistry(buf);
    return registry.get(id).orElse(null);
  }

  private void loadRegistry(MemBuffer buf) {
    if (buf == null) {
      return;
    }

    Memory memory = buf.getMemory();
    if (memory == null) {
      return;
    }

    Program program = memory.getProgram();
    if (program == null) {
      return;
    }

    registry.load(program);
  }

  private int getReadLength(int length) {
    if (length > 0) {
      return length;
    }

    return fixedLength;
  }

  @Override
  public Class<?> getValueClass(Settings settings) {
    return String.class;
  }

  @Override
  public boolean isEquivalent(DataType dataType) {
    return dataType instanceof TblStringDataType;
  }

  @Override
  public DataType clone(DataTypeManager dtm) {
    if (dtm == getDataTypeManager()) {
      return this;
    }
    return new TblStringDataType(registry, fixedLength, tableCharsetName, dtm);
  }

  @Override
  public String getDescription() {
    return "Table-based decoded string";
  }
}
