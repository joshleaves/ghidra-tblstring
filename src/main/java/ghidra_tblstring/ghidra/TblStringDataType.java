/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.ghidra;

import ghidra.docking.settings.Settings;
import ghidra.docking.settings.SettingsDefinition;
import ghidra.program.model.data.AbstractStringDataType;
import ghidra.program.model.data.CharsetSettingsDefinition;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.DataTypeManager;
import ghidra.program.model.data.StringDataInstance;
import ghidra.program.model.data.StringDataType;
import ghidra.program.model.data.StringLayoutEnum;
import ghidra.program.model.listing.Program;
import ghidra.program.model.mem.MemBuffer;
import ghidra.program.model.mem.Memory;
import ghidra_tblstring.tbl.TblStringDataInstance;
import ghidra_tblstring.tbl.TblStringDecoder;
import ghidra_tblstring.tbl.TblTable;
import java.util.Optional;

/**
 * Ghidra string data type that decodes bytes through a selected {@code .tbl} table.
 *
 * <p>The datatype is fixed-length when applied, but returns {@code -1} from {@link #getLength()} so
 * Ghidra lets the user control the selected byte range. Table resolution prefers per-data settings,
 * then legacy charset settings, then constructor/default registry fallbacks.
 */
public class TblStringDataType extends AbstractStringDataType {
  public static final String NAME = "tblString";

  private final TblRegistry registry;
  private final int fixedLength;
  private final String tableCharsetName;

  /** Creates a datatype backed by an empty private registry. */
  public TblStringDataType() {
    this(new TblRegistry(), -1, null, null);
  }

  /**
   * Creates a datatype backed by the supplied registry.
   *
   * @param registry registry used for table lookup
   */
  public TblStringDataType(TblRegistry registry) {
    this(registry, -1, null, null);
  }

  /**
   * Creates a datatype with a fixed fallback read length.
   *
   * @param registry registry used for table lookup
   * @param fixedLength fallback length used when Ghidra does not supply one
   */
  public TblStringDataType(TblRegistry registry, int fixedLength) {
    this(registry, fixedLength, null, null);
  }

  /**
   * Creates a datatype with an explicit constructor table id.
   *
   * @param registry registry used for table lookup
   * @param fixedLength fallback length used when Ghidra does not supply one
   * @param tableId table id used when data settings do not specify one
   */
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
    String id = getConfiguredOrDefaultTableId(settings).orElse(null);
    if (id == null) {
      return NAME;
    }

    return "tbl:" + getTableDisplayName(id);
  }

  @Override
  public Object getValue(MemBuffer buf, Settings settings, int length) {
    try {
      int byteLength = getReadLength(length);
      byte[] bytes = new byte[byteLength];
      buf.getBytes(bytes, 0);

      String id = getTableId(buf, settings);
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
    String id = getTableId(buf, settings);
    if (id == null) {
      return "<no-table>";
    }

    TblTable table = getTable(buf, id);
    if (table == null) {
      return "<missing-table:" + id + ">";
    }

    try {
      int byteLength = getReadLength(length);
      byte[] bytes = new byte[byteLength];
      buf.getBytes(bytes, 0);
      return "\"" + TblStringDecoder.decode(bytes, table) + "\"";
    } catch (Exception e) {
      return "<error>";
    }
  }

  @Override
  protected SettingsDefinition[] getBuiltInSettingsDefinitions() {
    return new SettingsDefinition[] {TblStringTableSettingsDefinition.TABLE};
  }

  @Override
  public String getCharsetName(Settings settings) {
    String id = getConfiguredOrDefaultTableId(settings).orElse(null);
    if (id != null && !id.isBlank()) {
      return id;
    }

    return "default";
  }

  @Override
  public StringDataInstance getStringDataInstance(MemBuffer buf, Settings settings, int length) {
    String decoded = (String) getValue(buf, settings, length);
    return new TblStringDataInstance(this, settings, buf, length, decoded);
  }

  private String getTableDisplayName(String id) {
    return TblStringTableSettingsDefinition.getCurrentRegistry()
        .flatMap(currentRegistry -> currentRegistry.get(id))
        .or(() -> registry.get(id))
        .map(TblTable::getName)
        .filter(name -> name != null && !name.isBlank())
        .orElse(id);
  }

  private String getTableId(MemBuffer buf, Settings settings) {
    Optional<String> configuredTableId = getConfiguredTableId(settings);
    if (configuredTableId.isPresent()) {
      return configuredTableId.get();
    }

    Optional<String> currentDefaultTableId =
        TblStringTableSettingsDefinition.getCurrentRegistry().flatMap(TblRegistry::getDefaultTableId);
    if (currentDefaultTableId.isPresent()) {
      return currentDefaultTableId.get();
    }

    if (registry.getDefaultTableId().isEmpty()) {
      loadRegistry(buf);
    }

    return registry.getDefaultTableId().orElse(null);
  }

  private Optional<String> getConfiguredTableId(Settings settings) {
    Optional<String> tableId = TblStringTableSettingsDefinition.TABLE.getTableId(settings);
    if (tableId.isPresent()) {
      return tableId;
    }

    Optional<String> legacyTableId = getLegacyCharsetTableId(settings);
    if (legacyTableId.isPresent()) {
      return legacyTableId;
    }

    if (tableCharsetName != null && !tableCharsetName.isBlank()) {
      return Optional.of(tableCharsetName);
    }

    return Optional.empty();
  }

  private Optional<String> getConfiguredOrDefaultTableId(Settings settings) {
    return getConfiguredTableId(settings).or(this::getCurrentDefaultTableId).or(registry::getDefaultTableId);
  }

  private Optional<String> getCurrentDefaultTableId() {
    return TblStringTableSettingsDefinition.getCurrentRegistry().flatMap(TblRegistry::getDefaultTableId);
  }

  private static Optional<String> getLegacyCharsetTableId(Settings settings) {
    if (settings == null) {
      return Optional.empty();
    }

    String tableId = CharsetSettingsDefinition.CHARSET.getCharset(settings, null);
    if (tableId == null || tableId.isBlank()) {
      return Optional.empty();
    }

    return Optional.of(tableId);
  }

  private TblTable getTable(MemBuffer buf, String id) {
    TblTable currentTable =
        TblStringTableSettingsDefinition.getCurrentRegistry()
            .flatMap(currentRegistry -> currentRegistry.get(id))
            .orElse(null);
    if (currentTable != null) {
      return currentTable;
    }

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
