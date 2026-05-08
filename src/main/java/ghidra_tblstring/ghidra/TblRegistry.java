/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.ghidra;

import ghidra.framework.options.Options;
import ghidra.program.model.listing.Program;
import ghidra_tblstring.tbl.TblTable;
import ghidra_tblstring.tbl.TblTableEntry;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class TblRegistry {
  private final Map<String, TblTable> tablesById = new LinkedHashMap<>();

  private static final String OPTIONS_NAME = "TableString";
  private static final String TABLE_PREFIX = "tables.";

  public void register(String id, TblTable table) {
    String normalizedId = normalizeId(id);

    if (normalizedId.isEmpty()) {
      throw new IllegalArgumentException("Table id cannot be empty");
    }

    if (table == null) {
      throw new IllegalArgumentException("Table cannot be null");
    }

    tablesById.put(normalizedId, table);
  }

  public Optional<TblTable> get(String id) {
    return Optional.ofNullable(tablesById.get(normalizeId(id)));
  }

  public TblTable require(String id) {
    String normalizedId = normalizeId(id);
    TblTable table = tablesById.get(normalizedId);

    if (table == null) {
      throw new IllegalArgumentException("Unknown table id: " + normalizedId);
    }

    return table;
  }

  public boolean contains(String id) {
    return tablesById.containsKey(normalizeId(id));
  }

  public void remove(String id) {
    tablesById.remove(normalizeId(id));
  }

  public void clear() {
    tablesById.clear();
  }

  public Collection<String> ids() {
    return Collections.unmodifiableCollection(tablesById.keySet());
  }

  public Collection<TblTable> tables() {
    return Collections.unmodifiableCollection(tablesById.values());
  }

  public boolean isEmpty() {
    return tablesById.isEmpty();
  }

  public int size() {
    return tablesById.size();
  }

  public void save(Program program) {
    Options options = program.getOptions(OPTIONS_NAME);

    // Clear previous entries
    for (String name : options.getOptionNames()) {
      if (name.startsWith(TABLE_PREFIX)) {
        options.removeOption(name);
      }
    }

    // Save current tables
    for (Map.Entry<String, TblTable> entry : tablesById.entrySet()) {
      String id = entry.getKey();
      String serialized = serialize(entry.getValue());
      options.setString(TABLE_PREFIX + id, serialized);
    }
  }

  public void load(Program program) {
    clear();

    Options options = program.getOptions(OPTIONS_NAME);

    for (String name : options.getOptionNames()) {
      if (!name.startsWith(TABLE_PREFIX)) continue;

      String id = name.substring(TABLE_PREFIX.length());
      String content = options.getString(name, "");

      try {
        TblTable table = TblTable.parse(id, new StringReader(content));
        register(id, table);
      } catch (IOException e) {
        throw new RuntimeException("Failed to parse table '" + id + "'", e);
      }
    }
  }

  private static String serialize(TblTable table) {
    StringBuilder sb = new StringBuilder();

    for (TblTableEntry entry : table.getEntries()) {
      sb.append(bytesToHex(entry.getKey()));
      sb.append('=');
      sb.append(entry.getValue());
      sb.append('\n');
    }

    return sb.toString();
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02X", b & 0xFF));
    }
    return sb.toString();
  }

  private static String normalizeId(String id) {
    if (id == null) {
      return "";
    }

    return id.trim();
  }
}
