/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.tbl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ghidra_tblstring.TestUtils;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class TblTableTest {

  private static void assertEntry(TblTableEntry entry, byte[] expectedKey, String expectedValue) {
    assertArrayEquals(expectedKey, entry.getKey());
    assertEquals(expectedValue, entry.getValue());
  }

  @DisplayName("parses simple tbl entries")
  @Test
  void parseReadsSimpleEntries() throws IOException {
    TblTable table = TestUtils.parseTblTableString("41=A\n42=B\n8140=あ\n");

    assertEquals("test", table.getName());
    assertEquals(3, table.getEntries().size());
    assertEntry(table.getEntries().get(0), TestUtils.toBytesArray(0x41), "A");
    assertEntry(table.getEntries().get(1), TestUtils.toBytesArray(0x42), "B");
    assertEntry(table.getEntries().get(2), TestUtils.toBytesArray(0x81, 0x40), "あ");
  }

  @DisplayName("ignores blank lines and comments")
  @Test
  void parseIgnoresBlankLinesAndComments() throws IOException {
    TblTable table = TestUtils.parseTblTableString("\n# comment\n; another comment\n41=A\n");

    assertEquals(1, table.getEntries().size());
    assertEntry(table.getEntries().get(0), TestUtils.toBytesArray(0x41), "A");
  }

  @DisplayName("strips UTF-8 BOM on first line")
  @Test
  void parseStripsUtf8BomOnFirstLine() throws IOException {
    TblTable table = TestUtils.parseTblTableString("\uFEFF41=A\n");

    assertEquals(1, table.getEntries().size());
    assertEntry(table.getEntries().get(0), TestUtils.toBytesArray(0x41), "A");
  }

  @DisplayName("accepts table id declarations")
  @Test
  void parseAcceptsTableIdDeclarations() throws IOException {
    TblTable table = TblTable.parse("", new java.io.StringReader("@HIRA\n41=A\n"));

    assertEquals("HIRA", table.getName());
    assertEntry(table.getEntries().get(0), TestUtils.toBytesArray(0x41), "A");
  }

  @DisplayName("allows whitespace around keys and separators")
  @Test
  void parseAllowsWhitespaceInsideHexKeyAndAroundSeparator() throws IOException {
    TblTable table = TestUtils.parseTblTableString("81 40 =あ\n  42\t= B\n");

    assertEntry(table.getEntries().get(0), TestUtils.toBytesArray(0x81, 0x40), "あ");
    assertEntry(table.getEntries().get(1), TestUtils.toBytesArray(0x42), " B");
  }

  @DisplayName("preserves value whitespace")
  @Test
  void parsePreservesValueWhitespace() throws IOException {
    TblTable table = TestUtils.parseTblTableString("41=  A  \n");

    assertEntry(table.getEntries().get(0), TestUtils.toBytesArray(0x41), "  A  ");
  }

  @DisplayName("allows single-space values")
  @Test
  void parseAllowsSingleSpaceValues() throws IOException {
    TblTable table = TestUtils.parseTblTableString("20= \n");

    assertEntry(table.getEntries().get(0), TestUtils.toBytesArray(0x20), " ");
  }

  @DisplayName("unescapes common escape sequences")
  @Test
  void parseUnescapesCommonEscapeSequences() throws IOException {
    TblTable table =
        TestUtils.parseTblTableString("01=line\\nnext\n02=carriage\\rreturn\n03=tab\\tvalue\n04=slash\\\\value\n");

    assertEntry(table.getEntries().get(0), TestUtils.toBytesArray(0x01), "line\nnext");
    assertEntry(table.getEntries().get(1), TestUtils.toBytesArray(0x02), "carriage\rreturn");
    assertEntry(table.getEntries().get(2), TestUtils.toBytesArray(0x03), "tab\tvalue");
    assertEntry(table.getEntries().get(3), TestUtils.toBytesArray(0x04), "slash\\value");
  }

  @DisplayName("keeps unknown escapes as literal characters")
  @Test
  void parseKeepsUnknownEscapesAsEscapedCharacter() throws IOException {
    TblTable table = TestUtils.parseTblTableString("41=foo\\xbar\n");

    assertEntry(table.getEntries().get(0), TestUtils.toBytesArray(0x41), "fooxbar");
  }

  @DisplayName("allows empty values")
  @Test
  void parseAllowsEmptyValues() throws IOException {
    TblTable table = TestUtils.parseTblTableString("00=\n");

    assertEntry(table.getEntries().get(0), TestUtils.toBytesArray(0x00), "");
  }

  @DisplayName("sorts longest keys first without mutating original order")
  @Test
  void parseSortsLongestKeysFirstSeparatelyFromOriginalEntries() throws IOException {
    TblTable table = TestUtils.parseTblTableString("41=A\n8140=あ\n82=B\n814243=ABC\n");

    List<TblTableEntry> entries = table.getEntries();
    assertEntry(entries.get(0), TestUtils.toBytesArray(0x41), "A");
    assertEntry(entries.get(1), TestUtils.toBytesArray(0x81, 0x40), "あ");
    assertEntry(entries.get(2), TestUtils.toBytesArray(0x82), "B");
    assertEntry(entries.get(3), TestUtils.toBytesArray(0x81, 0x42, 0x43), "ABC");

    List<TblTableEntry> longestFirst = table.getLongestFirstEntries();
    assertEntry(longestFirst.get(0), TestUtils.toBytesArray(0x81, 0x42, 0x43), "ABC");
    assertEntry(longestFirst.get(1), TestUtils.toBytesArray(0x81, 0x40), "あ");
    assertEntry(longestFirst.get(2), TestUtils.toBytesArray(0x41), "A");
    assertEntry(longestFirst.get(3), TestUtils.toBytesArray(0x82), "B");
  }

  @DisplayName("exposes immutable entry lists")
  @Test
  void entriesListIsImmutable() throws IOException {
    TblTable table = TestUtils.parseTblTableString("41=A\n");

    assertThrows(
        UnsupportedOperationException.class, () -> table.getEntries().add(table.getEntries().get(0)));
    assertThrows(
        UnsupportedOperationException.class,
        () -> table.getLongestFirstEntries().add(table.getLongestFirstEntries().get(0)));
  }

  @DisplayName("defensively copies keys on construction")
  @Test
  void entryDefensivelyCopiesKeyOnConstruction() {
    byte[] key = TestUtils.toBytesArray(0x41);
    TblTableEntry entry = new TblTableEntry(key, "A");

    key[0] = 0x42;

    assertArrayEquals(TestUtils.toBytesArray(0x41), entry.getKey());
  }

  @DisplayName("defensively copies keys on read")
  @Test
  void entryDefensivelyCopiesKeyOnRead() {
    TblTableEntry entry = new TblTableEntry(TestUtils.toBytesArray(0x41), "A");
    byte[] key = entry.getKey();

    key[0] = 0x42;

    assertArrayEquals(TestUtils.toBytesArray(0x41), entry.getKey());
  }

  @DisplayName("rejects invalid table constructor arguments")
  @Test
  void tableRejectsInvalidConstructorArguments() {
    TblTableEntry entry = new TblTableEntry(TestUtils.toBytesArray(0x41), "A");
    TblTableEntry duplicateEntry = new TblTableEntry(TestUtils.toBytesArray(0x41), "B");

    assertThrows(NullPointerException.class, () -> new TblTable(null, List.of(entry)));
    assertThrows(NullPointerException.class, () -> new TblTable("test", null));
    assertThrows(
        NullPointerException.class, () -> new TblTable("test", Collections.singletonList(null)));
    assertThrows(IllegalArgumentException.class, () -> new TblTable("test", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TblTable("test", List.of(entry, duplicateEntry)));
  }

  @DisplayName("rejects invalid entry constructor arguments")
  @Test
  void entryRejectsInvalidConstructorArguments() {
    assertThrows(NullPointerException.class, () -> new TblTableEntry(null, "A"));
    assertThrows(
        NullPointerException.class, () -> new TblTableEntry(TestUtils.toBytesArray(0x41), null));
    assertThrows(IllegalArgumentException.class, () -> new TblTableEntry(new byte[0], "A"));
  }

  @DisplayName("rejects empty tables")
  @Test
  void parseRejectsEmptyTable() {
    IOException exception =
        assertThrows(
            IOException.class, () -> TestUtils.parseTblTableString("\n# comment\n; comment\n"));

    assertEquals("Table is empty", exception.getMessage());
  }

  @DisplayName("rejects lines without separators")
  @Test
  void parseRejectsMissingSeparator() {
    IOException exception =
        assertThrows(IOException.class, () -> TestUtils.parseTblTableString("41A\n"));

    assertEquals("Invalid .tbl line 1: missing '='", exception.getMessage());
  }

  @DisplayName("rejects empty keys")
  @Test
  void parseRejectsEmptyKey() {
    IOException exception =
        assertThrows(IOException.class, () -> TestUtils.parseTblTableString("=A\n"));

    assertEquals("Invalid .tbl line 1: empty key", exception.getMessage());
  }

  @DisplayName("rejects odd-length hex keys")
  @Test
  void parseRejectsOddLengthHexKeys() {
    IOException exception =
        assertThrows(IOException.class, () -> TestUtils.parseTblTableString("123=A\n"));

    assertEquals(
        "Invalid .tbl line 1: hex key must have an even number of digits", exception.getMessage());
  }

  @DisplayName("rejects invalid hex bytes")
  @Test
  void parseRejectsInvalidHexBytes() {
    IOException exception =
        assertThrows(IOException.class, () -> TestUtils.parseTblTableString("4G=A\n"));

    assertEquals("Invalid .tbl line 1: invalid hex byte '4G'", exception.getMessage());
    assertTrue(exception.getCause() instanceof NumberFormatException);
  }

  @DisplayName("rejects duplicate keys")
  @Test
  void parseRejectsDuplicateKeys() {
    IOException exception =
        assertThrows(IOException.class, () -> TestUtils.parseTblTableString("41=A\n 4 1=B\n"));

    assertEquals("Invalid .tbl line 2: duplicate key '41'", exception.getMessage());
  }

  @DisplayName("rejects normal entries containing bracket tokens")
  @Test
  void parseRejectsNormalEntriesContainingBracketTokens() {
    IOException exception =
        assertThrows(IOException.class, () -> TestUtils.parseTblTableString("41=[A]\n"));

    assertEquals(
        "Invalid .tbl line 1: normal entries cannot contain '[' or ']' characters",
        exception.getMessage());
  }

  @DisplayName("rejects unsupported table switches")
  @Test
  void parseRejectsUnsupportedTableSwitches() {
    IOException exception =
        assertThrows(IOException.class, () -> TestUtils.parseTblTableString("!F8=[KATA],0\n"));

    assertEquals("Invalid .tbl line 1: table switching is not supported", exception.getMessage());
  }

  @DisplayName("rejects invalid table id declarations")
  @Test
  void parseRejectsInvalidTableIdDeclarations() {
    IOException exception =
        assertThrows(IOException.class, () -> TblTable.parse("", new java.io.StringReader("@BAD-ID\n41=A\n")));

    assertEquals("Invalid .tbl line 1: invalid table id 'BAD-ID'", exception.getMessage());
  }

  @DisplayName("reports physical line numbers after skipped lines")
  @Test
  void parseReportsPhysicalLineNumberAfterSkippedLines() {
    IOException exception =
        assertThrows(IOException.class, () -> TestUtils.parseTblTableString("# comment\n\n123=A\n"));

    assertEquals(
        "Invalid .tbl line 3: hex key must have an even number of digits", exception.getMessage());
  }

  @DisplayName("parses end tokens as dump mappings")
  @Test
  void parseEndTokensAsDumpMappings() throws IOException {
    TblTable table = TestUtils.parseTblTableString("/FF=[END]\\n\\n\n");

    assertEntry(table.getEntries().get(0), TestUtils.toBytesArray(0xff), "[END]\n\n");
  }

  @DisplayName("parses simple control codes as dump mappings")
  @Test
  void parseSimpleControlCodesAsDumpMappings() throws IOException {
    TblTable table = TestUtils.parseTblTableString("$FD=[linebreak]\\n\n$FE=[Color],palette=$%X,index=%D\n");

    assertEntry(table.getEntries().get(0), TestUtils.toBytesArray(0xfd), "[linebreak]\n");
    assertEntry(table.getEntries().get(1), TestUtils.toBytesArray(0xfe), "[Color]");
  }
}
