/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.ghidra;

import ghidra.docking.settings.EnumSettingsDefinition;
import ghidra.docking.settings.Settings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * {@link ghidra.docking.settings.SettingsDefinition} used by {@code TblStringDataType}
 * to select the .tbl table used to decode a string instance.
 */
public final class TblStringTableSettingsDefinition implements EnumSettingsDefinition {
  private static final String TABLE_ID_SETTING_NAME = "tblString.tableId";
  private static final String TABLE_NAME = ".tbl Table";
  private static final String NO_TABLES_CHOICE = "<no .tbl tables registered>";

  public static final TblStringTableSettingsDefinition TABLE = new TblStringTableSettingsDefinition();

  private static Supplier<TblRegistry> registrySupplier = () -> null;

  private TblStringTableSettingsDefinition() {}

  public static void setRegistrySupplier(Supplier<TblRegistry> supplier) {
    registrySupplier = supplier != null ? supplier : () -> null;
  }

  public Optional<String> getTableId(Settings settings) {
    if (settings == null) {
      return Optional.empty();
    }

    String tableId = settings.getString(TABLE_ID_SETTING_NAME);
    if (tableId == null || tableId.isBlank()) {
      return Optional.empty();
    }

    return Optional.of(tableId);
  }

  public void setTableId(Settings settings, String tableId) {
    if (tableId == null || tableId.isBlank()) {
      settings.clearSetting(TABLE_ID_SETTING_NAME);
      return;
    }

    settings.setString(TABLE_ID_SETTING_NAME, tableId);
  }

  @Override
  public int getChoice(Settings settings) {
    List<TableChoice> choices = getTableChoices();
    if (choices.isEmpty()) {
      return 0;
    }

    String selectedTableId = getTableId(settings).orElse(null);
    for (int i = 0; i < choices.size(); i++) {
      if (choices.get(i).id.equals(selectedTableId)) {
        return i;
      }
    }

    return 0;
  }

  @Override
  public void setChoice(Settings settings, int ordinalOfValue) {
    List<TableChoice> choices = getTableChoices();
    if (ordinalOfValue < 0 || ordinalOfValue >= choices.size()) {
      settings.clearSetting(TABLE_ID_SETTING_NAME);
      return;
    }

    settings.setString(TABLE_ID_SETTING_NAME, choices.get(ordinalOfValue).id);
  }

  @Override
  public String[] getDisplayChoices(Settings settings) {
    List<TableChoice> choices = getTableChoices();
    if (choices.isEmpty()) {
      return new String[] {NO_TABLES_CHOICE};
    }

    return choices.stream().map(choice -> choice.name).toArray(String[]::new);
  }

  @Override
  public String getName() {
    return TABLE_NAME;
  }

  @Override
  public String getStorageKey() {
    return TABLE_ID_SETTING_NAME;
  }

  @Override
  public String getDescription() {
    return "Selects the .tbl table used to decode this string";
  }

  @Override
  public String getDisplayChoice(int ordinalOfValue, Settings settings) {
    String[] choices = getDisplayChoices(settings);
    if (ordinalOfValue < 0 || ordinalOfValue >= choices.length) {
      return "";
    }

    return choices[ordinalOfValue];
  }

  @Override
  public String getValueString(Settings settings) {
    return getTableId(settings).orElse(null);
  }

  @Override
  public void clear(Settings settings) {
    settings.clearSetting(TABLE_ID_SETTING_NAME);
  }

  @Override
  public void copySetting(Settings settings, Settings destSettings) {
    String tableId = settings.getString(TABLE_ID_SETTING_NAME);
    if (tableId == null) {
      destSettings.clearSetting(TABLE_ID_SETTING_NAME);
      return;
    }

    destSettings.setString(TABLE_ID_SETTING_NAME, tableId);
  }

  @Override
  public boolean hasValue(Settings settings) {
    return settings.getValue(TABLE_ID_SETTING_NAME) != null;
  }

  private static Optional<TblRegistry> getCurrentRegistry() {
    return Optional.ofNullable(registrySupplier.get());
  }

  private static List<TableChoice> getTableChoices() {
    return getCurrentRegistry().<List<TableChoice>>map(
        registry -> {
          Set<String> orderedIds = new LinkedHashSet<>();
          registry.getDefaultTableId().ifPresent(orderedIds::add);

          List<String> sortedIds = new ArrayList<>(registry.ids());
          sortedIds.sort(
              Comparator.comparing(
                  id -> registry.require(id).getName().toLowerCase(Locale.ROOT)));
          orderedIds.addAll(sortedIds);

          List<TableChoice> choices = new ArrayList<>();
          for (String id : orderedIds) {
            choices.add(new TableChoice(id, registry.require(id).getName()));
          }
          return choices;
        })
    .orElseGet(List::of);
  }

  private static final class TableChoice {
    private final String id;
    private final String name;

    private TableChoice(String id, String name) {
      this.id = id;
      this.name = name;
    }
  }
}
