/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.tbl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable parsed representation of a {@code .tbl} character table.
 *
 * <p>The original entry order is preserved for display/export purposes. A second longest-key-first
 * view is prepared for decoding, because common {@code .tbl} files can contain overlapping keys
 * such as {@code 1F} and {@code 1F00}; the longest key must win at each input offset.
 */
public final class TblTable {
  private final String name;
  private final List<TblTableEntry> entries;
  private final List<TblTableEntry> longestFirstEntries;

  /**
   * Creates an immutable table from pre-parsed entries.
   *
   * @param name human-readable table name
   * @param entries non-empty entry list in source/display order
   * @throws NullPointerException if {@code name}, {@code entries}, or any entry is {@code null}
   * @throws IllegalArgumentException if {@code entries} is empty
   */
  public TblTable(String name, List<TblTableEntry> entries) {
    this.name = Objects.requireNonNull(name, "name");

    List<TblTableEntry> copiedEntries = new ArrayList<>(Objects.requireNonNull(entries, "entries"));
    if (copiedEntries.isEmpty()) {
      throw new IllegalArgumentException("entries cannot be empty");
    }
    for (TblTableEntry entry : copiedEntries) {
      Objects.requireNonNull(entry, "entry");
    }

    this.entries = Collections.unmodifiableList(copiedEntries);

    List<TblTableEntry> sorted = new ArrayList<>(copiedEntries);
    sorted.sort(Comparator.comparingInt((TblTableEntry entry) -> entry.getKeyLength()).reversed());
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
   * with {@code #} or {@code ;} are ignored. Whitespace is allowed inside hex keys, values are kept
   * verbatim after {@code =}, and the escape sequences {@code \n}, {@code \r}, {@code \t}, and
   * {@code \\} are decoded.
   *
   * @param name table name assigned to the parsed table
   * @param reader source reader
   * @return parsed table
   * @throws IOException if a line is malformed or the table has no entries
   * @throws NullPointerException if {@code name} or {@code reader} is {@code null}
   */
  public static TblTable parse(String name, Reader reader) throws IOException {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(reader, "reader");

    List<TblTableEntry> entries = new ArrayList<>();

    try (BufferedReader bufferedReader = new BufferedReader(reader)) {
      String line;
      int lineNumber = 0;

      while ((line = bufferedReader.readLine()) != null) {
        lineNumber++;
        line = stripBom(line).stripLeading();

        if (line.isBlank() || line.startsWith("#") || line.startsWith(";")) {
          continue;
        }

        int separator = line.indexOf('=');
        if (separator < 0) {
          throw new IOException("Invalid .tbl line " + lineNumber + ": missing '='");
        }

        String hexPart = line.substring(0, separator).trim();
        String valuePart = line.substring(separator + 1);

        byte[] key = parseHexKey(hexPart, lineNumber);
        entries.add(new TblTableEntry(key, unescapeValue(valuePart)));
      }
    }

    if (entries.isEmpty()) {
      throw new IOException("Table is empty");
    }

    return new TblTable(name, entries);
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
}
