/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.ghidra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ghidra_tblstring.TestUtils;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class TblRegistryTest {
  @DisplayName("generates url-safe ids from table names")
  @Test
  void idFromNameBuildsUrlSafeIds() {
    assertEquals("final-fantasy-vi-credits", TblRegistry.idFromName("Final Fantasy VI Credits"));
    assertEquals("cafe-au-lait", TblRegistry.idFromName("Café au lait"));
    assertEquals("table", TblRegistry.idFromName(" !!! "));
  }

  @DisplayName("generates unique ids from table names")
  @Test
  void uniqueIdFromNameAddsCollisionSuffixes() {
    List<String> existingIds =
        List.of("final-fantasy-vi-credits", "final-fantasy-vi-credits-2");

    assertEquals(
        "final-fantasy-vi-credits-3",
        TblRegistry.uniqueIdFromName("Final Fantasy VI Credits", existingIds));
  }

  @DisplayName("registering a table can allocate a stable id")
  @Test
  void registerTableAllocatesStableId() throws IOException {
    TblRegistry registry = new TblRegistry();

    String id = registry.register(TestUtils.parseTblTableString("41=A\n"));

    assertEquals("test", id);
    assertEquals(1, registry.size());
    assertFalse(registry.get(id).isEmpty());
    assertEquals("test", registry.getDefaultTableId().orElseThrow());
  }

  @DisplayName("explicit ids are normalized")
  @Test
  void explicitIdsAreNormalized() throws IOException {
    TblRegistry registry = new TblRegistry();

    registry.register("Final Fantasy VI Credits", TestUtils.parseTblTableString("41=A\n"));

    assertFalse(registry.get("final-fantasy-vi-credits").isEmpty());
  }

  @DisplayName("rejects empty explicit ids")
  @Test
  void rejectsEmptyExplicitIds() throws IOException {
    TblRegistry registry = new TblRegistry();

    assertThrows(
        IllegalArgumentException.class,
        () -> registry.register(" ", TestUtils.parseTblTableString("41=A\n")));
  }

  @DisplayName("default table ids are normalized")
  @Test
  void defaultTableIdsAreNormalized() throws IOException {
    TblRegistry registry = new TblRegistry();

    registry.register("Final Fantasy VI Credits", TestUtils.parseTblTableString("41=A\n"));
    registry.setDefaultTableId("Final Fantasy VI Credits");

    assertEquals("final-fantasy-vi-credits", registry.getDefaultTableId().orElseThrow());
  }

  @DisplayName("removing the default table selects another table")
  @Test
  void removeDefaultTableSelectsFallback() throws IOException {
    TblRegistry registry = new TblRegistry();

    registry.register("first", TestUtils.parseTblTableString("41=A\n"));
    registry.register("second", TestUtils.parseTblTableString("42=B\n"));

    registry.remove("first");

    assertEquals("second", registry.getDefaultTableId().orElseThrow());
  }

  @DisplayName("tracks optional source paths")
  @Test
  void tracksOptionalSourcePaths() throws IOException {
    TblRegistry registry = new TblRegistry();

    registry.register("Credits", TestUtils.parseTblTableString("41=A\n"));
    registry.setSourcePath("credits", "/tmp/credits.tbl");
    registry.register("credits", TestUtils.parseTblTableString("42=B\n"));

    assertEquals("/tmp/credits.tbl", registry.getSourcePath("Credits").orElseThrow());

    registry.remove("Credits");

    assertFalse(registry.getSourcePath("credits").isPresent());
  }

  @DisplayName("notifies listeners when registry content changes")
  @Test
  void notifiesListenersWhenRegistryChanges() throws IOException {
    TblRegistry registry = new TblRegistry();
    AtomicInteger changeCount = new AtomicInteger();
    Runnable listener = changeCount::incrementAndGet;

    registry.addChangeListener(listener);

    registry.register("credits", TestUtils.parseTblTableString("41=A\n"));
    registry.register("credits", TestUtils.parseTblTableString("41=A\n"));
    registry.setSourcePath("credits", "/tmp/credits.tbl");
    registry.setSourcePath("credits", "/tmp/credits.tbl");
    registry.register("menu", TestUtils.parseTblTableString("42=B\n"));
    registry.setDefaultTableId("menu");
    registry.remove("credits");
    registry.clear();
    registry.clear();

    assertEquals(6, changeCount.get());

    registry.removeChangeListener(listener);
    registry.register("other", TestUtils.parseTblTableString("43=C\n"));

    assertEquals(6, changeCount.get());
  }

  @DisplayName("recognizes table content option names")
  @Test
  void recognizesTableContentOptionNames() {
    assertEquals(
        "credits",
        TblRegistry.tableIdFromTblOptionName("tblString.tables.credits.tbl").orElseThrow());
    assertEquals(
        "credits",
        TblRegistry.tableIdFromTblOptionName(TblRegistry.tableOptionName("Credits", ".tbl"))
            .orElseThrow());
    assertFalse(TblRegistry.tableIdFromTblOptionName("tblString.tables.credits.name").isPresent());
    assertFalse(
        TblRegistry.tableIdFromTblOptionName("tblString.tables.credits.sourcePath")
            .isPresent());
    assertFalse(TblRegistry.tableIdFromTblOptionName("tblString.tables.order").isPresent());
  }

  @DisplayName("recognizes legacy table option names")
  @Test
  void recognizesLegacyTableOptionNames() {
    assertEquals(
        "credits",
        TblRegistry.tableIdFromStoredTableOptionName("tblString.tables.credits").orElseThrow());
    assertEquals(
        "credits",
        TblRegistry.tableIdFromStoredTableOptionName("tblString.tables.Credits").orElseThrow());
    assertFalse(TblRegistry.tableIdFromStoredTableOptionName("tblString.tables.order").isPresent());
    assertFalse(
        TblRegistry.tableIdFromStoredTableOptionName("tblString.tables.credits.name")
            .isPresent());
    assertFalse(
        TblRegistry.tableIdFromStoredTableOptionName("tblString.tables.credits.sourcePath")
            .isPresent());
  }
}
