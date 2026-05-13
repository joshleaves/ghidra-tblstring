/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.ui.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class ViewTblRegistryActionTest {
  @DisplayName("displays edge spaces as tokens")
  @Test
  void displayValueShowsEdgeSpacesAsTokens() {
    assertEquals("<SP>maison", ViewTblRegistryAction.displayValue(" maison"));
    assertEquals("maison<SP>", ViewTblRegistryAction.displayValue("maison "));
    assertEquals("ma maison", ViewTblRegistryAction.displayValue("ma maison"));
    assertEquals("<SP>ma maison", ViewTblRegistryAction.displayValue(" ma maison"));
    assertEquals("ma maison<SP>", ViewTblRegistryAction.displayValue("ma maison "));
    assertEquals("<SP>ma maison<SP>", ViewTblRegistryAction.displayValue(" ma maison "));
  }

  @DisplayName("displays every edge space in a run")
  @Test
  void displayValueShowsEveryEdgeSpaceInRuns() {
    assertEquals("<SP><SP>maison", ViewTblRegistryAction.displayValue("  maison"));
    assertEquals("maison<SP><SP>", ViewTblRegistryAction.displayValue("maison  "));
    assertEquals("<SP><SP>maison<SP><SP>", ViewTblRegistryAction.displayValue("  maison  "));
  }

  @DisplayName("keeps interior whitespace literal")
  @Test
  void displayValueKeepsInteriorWhitespaceLiteral() {
    assertEquals("ma maison", ViewTblRegistryAction.displayValue("ma maison"));
    assertEquals("ma\tmaison", ViewTblRegistryAction.displayValue("ma\tmaison"));
    assertEquals("ma\nmaison", ViewTblRegistryAction.displayValue("ma\nmaison"));
  }

  @DisplayName("displays edge control characters as tokens")
  @Test
  void displayValueShowsEdgeControlCharactersAsTokens() {
    assertEquals("<TAB>maison<TAB>", ViewTblRegistryAction.displayValue("\tmaison\t"));
    assertEquals("<NEWLINE>maison<CR>", ViewTblRegistryAction.displayValue("\nmaison\r"));
    assertEquals("<x01>maison<x02>", ViewTblRegistryAction.displayValue("\u0001maison\u0002"));
  }

  @DisplayName("displays empty values explicitly")
  @Test
  void displayValueShowsEmptyValuesExplicitly() {
    assertEquals("<EMPTY>", ViewTblRegistryAction.displayValue(""));
  }

  @DisplayName("decodes displayed space tokens when editing values")
  @Test
  void unescapeCellValueDecodesDisplayedSpaceTokens() {
    assertEquals(" maison ", ViewTblRegistryAction.unescapeCellValue("<SP>maison<SP>"));
    assertEquals(" maison ", ViewTblRegistryAction.unescapeCellValue("<SPACE>maison<SPACE>"));
  }
}
