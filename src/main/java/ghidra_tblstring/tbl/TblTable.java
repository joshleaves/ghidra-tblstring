/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.tbl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable parsed representation of a {@code .tbl} character table.
 *
 * <p>The original entry order is preserved for display/export purposes. A second longest-key-first
 * view is prepared for decoding, because common {@code .tbl} files can contain overlapping keys
 * such as {@code 1F} and {@code 1F00}; the longest key must win at each input offset.
 */
public final class TblTable {
  private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

  private final String name;
  private final List<TblTableEntry> entries;
  private final List<TblTableEntry> longestFirstEntries;

  /**
   * Creates an immutable table from pre-parsed entries.
   *
   * @param name human-readable table name
   * @param entries non-empty entry list in source/display order, with unique keys
   * @throws NullPointerException if {@code name}, {@code entries}, or any entry is {@code null}
   * @throws IllegalArgumentException if {@code entries} is empty or contains duplicate keys
   */
  public TblTable(String name, List<TblTableEntry> entries) {
    this.name = Objects.requireNonNull(name, "name");

    List<TblTableEntry> copiedEntries =
        new ArrayList<>(Objects.requireNonNull(entries, "entries"));
    if (copiedEntries.isEmpty()) {
      throw new IllegalArgumentException("entries cannot be empty");
    }
    Set<String> seenKeys = new HashSet<>();
    for (TblTableEntry entry : copiedEntries) {
      Objects.requireNonNull(entry, "entry");
      String key = bytesToHex(entry.getKey());
      if (!seenKeys.add(key)) {
        throw new IllegalArgumentException("duplicate key: " + key);
      }
    }

    this.entries = Collections.unmodifiableList(copiedEntries);

    List<TblTableEntry> sorted = new ArrayList<>(copiedEntries);
    sorted.sort(
        Comparator.comparingInt((TblTableEntry entry) -> entry.getKeyLength()).reversed());
    this.longestFirstEntries = Collections.unmodifiableList(sorted);
  }

  /**
   * Returns the table name.
   *
   * @return table name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns entries in source/display order.
   *
   * @return immutable source-order entries
   */
  public List<TblTableEntry> getEntries() {
    return entries;
  }

  /**
   * Returns entries sorted from longest key to shortest key for decoding.
   *
   * @return immutable longest-key-first entries
   */
  public List<TblTableEntry> getLongestFirstEntries() {
    return longestFirstEntries;
  }

  /**
   * Parses a common {@code .tbl} file.
   *
   * <p>Supported syntax is one {@code HEX=VALUE} mapping per line. Empty lines and lines starting
   * with {@code #} or {@code ;} are ignored. A leading {@code @TableID} line is accepted and used
   * as the table name when {@code name} is blank. End-token entries such as {@code /FF=[END]\n}
   * and simple control-code entries such as {@code $FD=[linebreak]\n} are decoded as normal dump
   * mappings using their bracketed label and formatting suffix. Whitespace is allowed inside hex
   * keys, values are kept verbatim after {@code =}, and the escape sequences {@code \n}, {@code \r},
   * {@code \t}, and {@code \\} are decoded.
   *
   * @param name table name assigned to the parsed table
   * @param reader source reader
   * @return parsed table
   * @throws IOException if a line is malformed, a key is duplicated, or the table has no entries
   * @throws NullPointerException if {@code name} or {@code reader} is {@code null}
   */
  public static TblTable parse(String name, Reader reader) throws IOException {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(reader, "reader");

    String tableName = name;
    List<TblTableEntry> entries = new ArrayList<>();
    Set<String> seenKeys = new HashSet<>();

    try (BufferedReader bufferedReader = new BufferedReader(reader)) {
      String line;
      int lineNumber = 0;

      while ((line = bufferedReader.readLine()) != null) {
        lineNumber++;
        line = stripBom(line).stripLeading();

        if (line.isBlank() || line.startsWith("#") || line.startsWith(";")) {
          continue;
        }

        if (line.startsWith("@")) {
          String tableId = line.substring(1).trim();
          if (tableId.isEmpty()) {
            throw new IOException("Invalid .tbl line " + lineNumber + ": empty table id");
          }
          if (!tableId.matches("[0-9A-Za-z]+")) {
            throw new IOException(
                "Invalid .tbl line "
                    + lineNumber
                    + ": invalid table id '"
                    + tableId
                    + "'");
          }
          if (tableName.isBlank()) {
            tableName = tableId;
          }
          continue;
        }

        int separator = line.indexOf('=');
        if (separator < 0) {
          throw new IOException("Invalid .tbl line " + lineNumber + ": missing '='");
        }

        String hexPart = line.substring(0, separator).trim();
        String valuePart = line.substring(separator + 1);
        EntryKind entryKind = parseEntryKind(hexPart, lineNumber);

        byte[] key = parseHexKey(entryKind.hexPart(), lineNumber);
        String keyId = bytesToHex(key);
        if (!seenKeys.add(keyId)) {
          throw new IOException(
              "Invalid .tbl line " + lineNumber + ": duplicate key '" + keyId + "'");
        }

        entries.add(new TblTableEntry(key, parseValue(entryKind, valuePart, lineNumber)));
      }
    }

    if (entries.isEmpty()) {
      throw new IOException("Table is empty");
    }

    return new TblTable(tableName, entries);
  }

  /**
   * Serializes this table's entries as {@code .tbl} text.
   *
   * <p>The table name is intentionally not emitted: it is metadata supplied by the caller or
   * registry, not part of the common {@code .tbl} entry format.
   *
   * @return table entries formatted as {@code .tbl} text
   */
  public String toTblString() {
    StringBuilder result = new StringBuilder();

    for (TblTableEntry entry : entries) {
      result.append(bytesToHex(entry.getKey()));
      result.append('=');
      result.append(escapeValue(entry.getValue()));
      result.append('\n');
    }

    return result.toString();
  }

  private static EntryKind parseEntryKind(String hexPart, int lineNumber) throws IOException {
    if (hexPart.isEmpty()) {
      return EntryKind.normal(hexPart);
    }

    char prefix = hexPart.charAt(0);
    if (prefix == '$' || prefix == '/') {
      return new EntryKind(prefix, hexPart.substring(1));
    }

    if (prefix == '!') {
      throw new IOException(
          "Invalid .tbl line " + lineNumber + ": table switching is not supported");
    }

    return EntryKind.normal(hexPart);
  }

  private static String parseValue(EntryKind entryKind, String valuePart, int lineNumber)
      throws IOException {
    if (entryKind.isNormal()) {
      return parseNormalValue(valuePart, lineNumber);
    }

    return parseNonNormalValue(entryKind, valuePart, lineNumber);
  }

  private static String parseNormalValue(String valuePart, int lineNumber) throws IOException {
    if (valuePart.indexOf('[') >= 0 || valuePart.indexOf(']') >= 0) {
      throw new IOException(
          "Invalid .tbl line "
              + lineNumber
              + ": normal entries cannot contain '[' or ']' characters");
    }

    return unescapeValue(valuePart);
  }

  private static String parseNonNormalValue(EntryKind entryKind, String valuePart, int lineNumber)
      throws IOException {
    int labelStart = valuePart.indexOf('[');
    int labelEnd = valuePart.indexOf(']');
    if (labelStart != 0 || labelEnd <= labelStart + 1) {
      throw new IOException(
          "Invalid .tbl line " + lineNumber + ": invalid non-normal entry label");
    }

    String label = valuePart.substring(labelStart + 1, labelEnd);
    if (!label.matches("[0-9A-Za-z]+")) {
      throw new IOException(
          "Invalid .tbl line " + lineNumber + ": invalid non-normal entry label '" + label + "'");
    }

    String suffix = valuePart.substring(labelEnd + 1);
    if (entryKind.prefix() == '/' && !suffix.matches("(?:\\\\n)*")) {
      throw new IOException(
          "Invalid .tbl line " + lineNumber + ": end tokens cannot have parameters");
    }

    if (entryKind.prefix() == '$') {
      int parameterStart = suffix.indexOf(',');
      if (parameterStart >= 0) {
        suffix = suffix.substring(0, parameterStart);
      }
    }

    return "[" + label + "]" + unescapeValue(suffix);
  }

  private record EntryKind(char prefix, String hexPart) {
    private static EntryKind normal(String hexPart) {
      return new EntryKind('\0', hexPart);
    }

    private boolean isNormal() {
      return prefix == '\0';
    }
  }

  private static String stripBom(String line) {
    if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
      return line.substring(1);
    }
    return line;
  }

  private static byte[] parseHexKey(String hex, int lineNumber) throws IOException {
    String normalized = hex.replaceAll("\\s+", "");

    if (normalized.isEmpty()) {
      throw new IOException("Invalid .tbl line " + lineNumber + ": empty key");
    }

    if ((normalized.length() % 2) != 0) {
      throw new IOException(
          "Invalid .tbl line " + lineNumber + ": hex key must have an even number of digits");
    }

    byte[] bytes = new byte[normalized.length() / 2];
    for (int i = 0; i < bytes.length; i++) {
      int offset = i * 2;
      String pair = normalized.substring(offset, offset + 2);

      try {
        bytes[i] = (byte) Integer.parseInt(pair, 16);
      } catch (NumberFormatException e) {
        throw new IOException(
            "Invalid .tbl line " + lineNumber + ": invalid hex byte '" + pair + "'", e);
      }
    }

    return bytes;
  }

  private static String unescapeValue(String value) {
    StringBuilder result = new StringBuilder();

    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c != '\\' || i + 1 >= value.length()) {
        result.append(c);
        continue;
      }

      char next = value.charAt(++i);
      switch (next) {
        case 'n':
          result.append('\n');
          break;
        case 'r':
          result.append('\r');
          break;
        case 't':
          result.append('\t');
          break;
        case '\\':
          result.append('\\');
          break;
        default:
          result.append(next);
          break;
      }
    }

    return result.toString();
  }

  private static String escapeValue(String value) {
    StringBuilder result = new StringBuilder(value.length());

    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '\n':
          result.append("\\n");
          break;
        case '\r':
          result.append("\\r");
          break;
        case '\t':
          result.append("\\t");
          break;
        case '\\':
          result.append("\\\\");
          break;
        default:
          result.append(c);
          break;
      }
    }

    return result.toString();
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);

    for (byte b : bytes) {
      int value = b & 0xff;
      result.append(HEX_DIGITS[(value >>> 4) & 0xf]);
      result.append(HEX_DIGITS[value & 0xf]);
    }

    return result.toString();
  }
}
