/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.ghidra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import ghidra.docking.settings.SettingsImpl;
import ghidra_tblstring.TestUtils;
import ghidra_tblstring.tbl.TblTable;
import ghidra_tblstring.tbl.TblTableEntry;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class TblStringTableSettingsDefinitionTest {
  @AfterEach
  void resetRegistrySupplier() {
    TblStringTableSettingsDefinition.setRegistrySupplier(null);
  }

  @DisplayName("table choices show the default table first without duplicates")
  @Test
  void tableChoicesShowDefaultFirstWithoutDuplicates() {
    TblRegistry registry = new TblRegistry();
    registry.register(
        "credits-2b",
        new TblTable(
            "credits.2b.tbl",
            List.of(new TblTableEntry(TestUtils.toBytesArray(0x41), "A"))));
    registry.register(
        "credits-1b",
        new TblTable(
            "credits.1b.tbl",
            List.of(new TblTableEntry(TestUtils.toBytesArray(0x42), "B"))));
    registry.setDefaultTableId("credits-1b");
    TblStringTableSettingsDefinition.setRegistrySupplier(() -> registry);

    SettingsImpl settings = new SettingsImpl();

    assertArrayEquals(
        new String[] {"credits.1b.tbl", "credits.2b.tbl"},
        TblStringTableSettingsDefinition.TABLE.getDisplayChoices(settings));
  }

  @DisplayName("table choices store table ids")
  @Test
  void tableChoicesStoreIds() {
    TblRegistry registry = new TblRegistry();
    registry.register(
        "credits-2b",
        new TblTable(
            "credits.2b.tbl",
            List.of(new TblTableEntry(TestUtils.toBytesArray(0x41), "A"))));
    registry.register(
        "credits-1b",
        new TblTable(
            "credits.1b.tbl",
            List.of(new TblTableEntry(TestUtils.toBytesArray(0x42), "B"))));
    registry.setDefaultTableId("credits-1b");
    TblStringTableSettingsDefinition.setRegistrySupplier(() -> registry);

    SettingsImpl settings = new SettingsImpl();
    TblStringTableSettingsDefinition.TABLE.setChoice(settings, 1);

    assertEquals("credits-2b", TblStringTableSettingsDefinition.TABLE.getTableId(settings).orElseThrow());
    assertEquals(1, TblStringTableSettingsDefinition.TABLE.getChoice(settings));
  }

  @DisplayName("invalid table choices clear the stored table id")
  @Test
  void invalidTableChoicesClearStoredTableId() {
    TblRegistry registry = new TblRegistry();
    registry.register(
        "credits",
        new TblTable(
            "credits.tbl",
            List.of(new TblTableEntry(TestUtils.toBytesArray(0x41), "A"))));
    TblStringTableSettingsDefinition.setRegistrySupplier(() -> registry);

    SettingsImpl settings = new SettingsImpl();
    TblStringTableSettingsDefinition.TABLE.setTableId(settings, "credits");

    TblStringTableSettingsDefinition.TABLE.setChoice(settings, 99);

    assertFalse(TblStringTableSettingsDefinition.TABLE.getTableId(settings).isPresent());
  }

  @DisplayName("empty registries expose a placeholder choice")
  @Test
  void emptyRegistriesExposePlaceholderChoice() {
    TblStringTableSettingsDefinition.setRegistrySupplier(TblRegistry::new);

    assertArrayEquals(
        new String[] {"<no .tbl tables registered>"},
        TblStringTableSettingsDefinition.TABLE.getDisplayChoices(new SettingsImpl()));
  }

  @DisplayName("table id lookup tolerates null settings")
  @Test
  void tableIdLookupToleratesNullSettings() throws IOException {
    TblRegistry registry = new TblRegistry();
    registry.register("credits", TestUtils.parseTblTableString("41=A\n"));
    TblStringTableSettingsDefinition.setRegistrySupplier(() -> registry);

    assertFalse(TblStringTableSettingsDefinition.TABLE.getTableId(null).isPresent());
  }
}
