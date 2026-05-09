/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.ghidra;

import ghidra.framework.options.Options;
import ghidra.program.model.listing.Program;
import ghidra_tblstring.tbl.TblTable;

import java.io.IOException;
import java.io.StringReader;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public final class TblRegistry {
  private final Map<String, TblTable> tablesById = new LinkedHashMap<>();
  private final Map<String, String> sourcePathsById = new LinkedHashMap<>();
  private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
  private String defaultTableId;
  private boolean suppressChangeEvents;

  private static final String OPTIONS_NAME = "tblString";
  private static final String TABLE_PREFIX = "tblString.tables.";
  private static final String ORDER_OPTION = TABLE_PREFIX + "order";
  private static final String DEFAULT_TABLE_OPTION = "tblString.defaultTableId";
  private static final String TBL_SUFFIX = ".tbl";
  private static final String NAME_SUFFIX = ".name";
  private static final String SOURCE_PATH_SUFFIX = ".sourcePath";
  private static final Pattern DIACRITICAL_MARKS = Pattern.compile("\\p{M}+");
  private static final Pattern INVALID_ID_CHARS = Pattern.compile("[^a-z0-9_-]+");
  private static final Pattern REPEATED_DASHES = Pattern.compile("-+");

  public String register(TblTable table) {
    if (table == null) {
      throw new IllegalArgumentException("Table cannot be null");
    }

    String id = uniqueIdFromName(table.getName(), tablesById.keySet());
    register(id, table);
    return id;
  }

  public void register(String id, TblTable table) {
    String normalizedId = normalizeId(id);

    if (normalizedId.isEmpty()) {
      throw new IllegalArgumentException("Table id cannot be empty");
    }

    if (table == null) {
      throw new IllegalArgumentException("Table cannot be null");
    }

    TblTable previousTable = tablesById.put(normalizedId, table);
    String previousDefaultTableId = defaultTableId;

    if (defaultTableId == null
        || defaultTableId.isEmpty()
        || !tablesById.containsKey(defaultTableId)) {
      defaultTableId = normalizedId;
    }

    if (!sameTable(previousTable, table)
        || !Objects.equals(previousDefaultTableId, defaultTableId)) {
      fireChanged();
    }
  }

  public void addChangeListener(Runnable listener) {
    changeListeners.add(Objects.requireNonNull(listener, "listener"));
  }

  public void removeChangeListener(Runnable listener) {
    changeListeners.remove(listener);
  }

  public Optional<String> getSourcePath(String id) {
    String sourcePath = sourcePathsById.get(normalizeId(id));
    if (sourcePath == null || sourcePath.isBlank()) {
      return Optional.empty();
    }

    return Optional.of(sourcePath);
  }

  public void setSourcePath(String id, String sourcePath) {
    String normalizedId = normalizeId(id);
    if (!tablesById.containsKey(normalizedId)) {
      throw new IllegalArgumentException("Unknown table id: " + normalizedId);
    }

    if (sourcePath == null || sourcePath.isBlank()) {
      if (sourcePathsById.remove(normalizedId) != null) {
        fireChanged();
      }
      return;
    }

    String previousSourcePath = sourcePathsById.put(normalizedId, sourcePath);
    if (!Objects.equals(previousSourcePath, sourcePath)) {
      fireChanged();
    }
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
    String normalizedId = normalizeId(id);
    TblTable removedTable = tablesById.remove(normalizedId);
    String removedSourcePath = sourcePathsById.remove(normalizedId);
    String previousDefaultTableId = defaultTableId;
    if (normalizedId.equals(defaultTableId)) {
      defaultTableId = tablesById.keySet().stream().findFirst().orElse(null);
    }

    if (removedTable != null
        || removedSourcePath != null
        || !Objects.equals(previousDefaultTableId, defaultTableId)) {
      fireChanged();
    }
  }

  public void clear() {
    boolean changed =
        !tablesById.isEmpty() || !sourcePathsById.isEmpty() || defaultTableId != null;
    tablesById.clear();
    sourcePathsById.clear();
    defaultTableId = null;
    if (changed) {
      fireChanged();
    }
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

  public Optional<String> getDefaultTableId() {
    if (defaultTableId == null || !tablesById.containsKey(defaultTableId)) {
      return tablesById.keySet().stream().findFirst();
    }

    return Optional.of(defaultTableId);
  }

  public void setDefaultTableId(String id) {
    String normalizedId = normalizeId(id);
    if (!tablesById.containsKey(normalizedId)) {
      throw new IllegalArgumentException("Unknown table id: " + normalizedId);
    }

    if (normalizedId.equals(defaultTableId)) {
      return;
    }

    defaultTableId = normalizedId;
    fireChanged();
  }

  public void save(Program program) {
    Options options = program.getOptions(OPTIONS_NAME);

    // Clear previous entries
    for (String name : options.getOptionNames()) {
      if (name.startsWith(TABLE_PREFIX)) {
        options.removeOption(name);
      }
    }
    if (options.contains(DEFAULT_TABLE_OPTION)) {
      options.removeOption(DEFAULT_TABLE_OPTION);
    }

    // Save current tables
    getDefaultTableId().ifPresent(id -> options.setString(DEFAULT_TABLE_OPTION, id));
    options.setString(ORDER_OPTION, String.join("\n", tablesById.keySet()));
    for (Map.Entry<String, TblTable> entry : tablesById.entrySet()) {
      String id = entry.getKey();
      TblTable table = entry.getValue();
      options.setString(tableOptionName(id, TBL_SUFFIX), table.toTblString());
      options.setString(tableOptionName(id, NAME_SUFFIX), table.getName());
      getSourcePath(id)
          .ifPresent(path -> options.setString(tableOptionName(id, SOURCE_PATH_SUFFIX), path));
    }
  }

  public void load(Program program) {
    Options options = program.getOptions(OPTIONS_NAME);

    Map<String, TblTable> loadedTables = new LinkedHashMap<>();
    Map<String, String> loadedSourcePaths = new LinkedHashMap<>();
    String loadedDefaultTableId = normalizeId(options.getString(DEFAULT_TABLE_OPTION, ""));

    for (String optionName : options.getOptionNames()) {
      Optional<String> id = tableIdFromStoredTableOptionName(optionName);
      if (id.isEmpty()) {
        continue;
      }

      String tableId = normalizeId(id.get());
      String content = options.getString(optionName, "");
      String tableName = options.getString(tableOptionName(tableId, NAME_SUFFIX), tableId);
      String sourcePath = options.getString(tableOptionName(tableId, SOURCE_PATH_SUFFIX), "");

      try {
        TblTable table = TblTable.parse(tableName, new StringReader(content));
        loadedTables.put(tableId, table);
        if (!sourcePath.isBlank()) {
          loadedSourcePaths.put(tableId, sourcePath);
        }
      } catch (IOException e) {
        throw new RuntimeException("Failed to parse table '" + tableId + "'", e);
      }
    }

    Map<String, TblTable> orderedTables = new LinkedHashMap<>();
    Map<String, String> orderedSourcePaths = new LinkedHashMap<>();
    for (String id : orderedIds(options)) {
      TblTable table = loadedTables.remove(id);
      if (table != null) {
        orderedTables.put(id, table);
        String sourcePath = loadedSourcePaths.remove(id);
        if (sourcePath != null) {
          orderedSourcePaths.put(id, sourcePath);
        }
      }
    }

    orderedTables.putAll(loadedTables);
    orderedSourcePaths.putAll(loadedSourcePaths);

    boolean previousSuppressChangeEvents = suppressChangeEvents;
    suppressChangeEvents = true;
    try {
      clear();

      tablesById.putAll(orderedTables);
      sourcePathsById.putAll(orderedSourcePaths);

      if (tablesById.containsKey(loadedDefaultTableId)) {
        defaultTableId = loadedDefaultTableId;
      } else {
        defaultTableId = tablesById.keySet().stream().findFirst().orElse(null);
      }
    } finally {
      suppressChangeEvents = previousSuppressChangeEvents;
    }
  }

  private void fireChanged() {
    if (suppressChangeEvents) {
      return;
    }

    for (Runnable listener : changeListeners) {
      listener.run();
    }
  }

  private static boolean sameTable(TblTable first, TblTable second) {
    if (first == second) {
      return true;
    }
    if (first == null || second == null) {
      return false;
    }

    return first.getName().equals(second.getName())
        && first.toTblString().equals(second.toTblString());
  }

  static String idFromName(String name) {
    String normalizedName =
        Normalizer.normalize(name == null ? "" : name.trim(), Form.NFD).toLowerCase();
    String withoutDiacritics = DIACRITICAL_MARKS.matcher(normalizedName).replaceAll("");
    String id = INVALID_ID_CHARS.matcher(withoutDiacritics).replaceAll("-");
    id = REPEATED_DASHES.matcher(id).replaceAll("-");
    id = trimDashes(id);

    if (id.isEmpty()) {
      return "table";
    }

    return id;
  }

  static String uniqueIdFromName(String name, Collection<String> existingIds) {
    String baseId = idFromName(name);
    if (!existingIds.contains(baseId)) {
      return baseId;
    }

    for (int suffix = 2; ; suffix++) {
      String id = baseId + "-" + suffix;
      if (!existingIds.contains(id)) {
        return id;
      }
    }
  }

  static String tableOptionName(String id, String suffix) {
    return TABLE_PREFIX + normalizeId(id) + suffix;
  }

  static Optional<String> tableIdFromTblOptionName(String optionName) {
    if (!optionName.startsWith(TABLE_PREFIX) || !optionName.endsWith(TBL_SUFFIX)) {
      return Optional.empty();
    }

    String id =
        optionName.substring(TABLE_PREFIX.length(), optionName.length() - TBL_SUFFIX.length());
    if (id.isEmpty() || id.contains(".")) {
      return Optional.empty();
    }

    return Optional.of(id);
  }

  static Optional<String> tableIdFromStoredTableOptionName(String optionName) {
    Optional<String> id = tableIdFromTblOptionName(optionName);
    if (id.isPresent()) {
      return id;
    }

    return legacyTableIdFromOptionName(optionName);
  }

  private static Optional<String> legacyTableIdFromOptionName(String optionName) {
    if (!optionName.startsWith(TABLE_PREFIX)) {
      return Optional.empty();
    }

    String id = optionName.substring(TABLE_PREFIX.length());
    if (id.isEmpty() || id.equals("order") || id.contains(".")) {
      return Optional.empty();
    }

    return Optional.of(normalizeId(id));
  }

  private static List<String> orderedIds(Options options) {
    String order = options.getString(ORDER_OPTION, "");
    if (order.isBlank()) {
      return List.of();
    }

    List<String> ids = new ArrayList<>();
    for (String id : order.split("\\R")) {
      String normalizedId = normalizeId(id);
      if (!normalizedId.isEmpty()) {
        ids.add(normalizedId);
      }
    }

    return ids;
  }

  private static String trimDashes(String value) {
    int start = 0;
    int end = value.length();

    while (start < end && value.charAt(start) == '-') {
      start++;
    }
    while (end > start && value.charAt(end - 1) == '-') {
      end--;
    }

    return value.substring(start, end);
  }

  private static String normalizeId(String id) {
    if (id == null || id.trim().isEmpty()) {
      return "";
    }

    return idFromName(id);
  }
}
