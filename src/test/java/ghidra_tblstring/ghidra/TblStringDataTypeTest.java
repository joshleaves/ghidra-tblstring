/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.ghidra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ghidra.docking.settings.SettingsImpl;
import ghidra.program.model.data.CharsetSettingsDefinition;
import ghidra_tblstring.TestUtils;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class TblStringDataTypeTest {
  @AfterEach
  void resetRegistrySupplier() {
    TblStringTableSettingsDefinition.setRegistrySupplier(null);
  }

  @DisplayName("constructor table id is used when instance settings are empty")
  @Test
  void constructorTableIdIsUsedWhenSettingsAreEmpty() {
    TblStringDataType dataType = new TblStringDataType(new TblRegistry(), 4, "credits");

    assertEquals("credits", dataType.getCharsetName(new SettingsImpl()));
  }

  @DisplayName("instance settings override constructor table id")
  @Test
  void settingsOverrideConstructorTableId() {
    TblStringDataType dataType = new TblStringDataType(new TblRegistry(), 4, "credits");
    SettingsImpl settings = new SettingsImpl();
    TblStringTableSettingsDefinition.TABLE.setTableId(settings, "menu");

    assertEquals("menu", dataType.getCharsetName(settings));
  }

  @DisplayName("legacy charset settings are still accepted as table ids")
  @Test
  void legacyCharsetSettingIsAcceptedAsTableId() {
    TblStringDataType dataType = new TblStringDataType(new TblRegistry(), 4, "credits");
    SettingsImpl settings = new SettingsImpl();
    CharsetSettingsDefinition.CHARSET.setCharset(settings, "legacy");

    assertEquals("legacy", dataType.getCharsetName(settings));
  }

  @DisplayName("registry default table is used when settings and constructor table id are empty")
  @Test
  void registryDefaultIsUsedAsFallback() throws IOException {
    TblRegistry registry = new TblRegistry();
    registry.register("credits", TestUtils.parseTblTableString("41=A\n"));
    TblStringDataType dataType = new TblStringDataType(registry);

    assertEquals("credits", dataType.getCharsetName(new SettingsImpl()));
  }

  @DisplayName("active plugin registry is used before private empty registries")
  @Test
  void activePluginRegistryIsUsedBeforePrivateEmptyRegistry() throws IOException {
    TblRegistry activeRegistry = new TblRegistry();
    activeRegistry.register("credits", TestUtils.parseTblTableString("41=A\n"));
    TblStringTableSettingsDefinition.setRegistrySupplier(() -> activeRegistry);
    TblStringDataType dataType = new TblStringDataType();

    assertEquals("credits", dataType.getCharsetName(new SettingsImpl()));
  }
}
