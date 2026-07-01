/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.tbl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Encodes decoded text into every byte sequence accepted by a {@link TblTable}. */
public final class TblStringEncoder {
  private TblStringEncoder() {}

  /**
   * Returns every table-backed byte sequence that decodes exactly to {@code input}.
   *
   * <p>Empty table values are ignored so ambiguous or malformed tables cannot create zero-length
   * recursion. Duplicate byte sequences are returned once.
   *
   * @param input decoded text to encode
   * @param table table used for encoding
   * @param maxResults maximum number of byte sequences to return
   * @return matching byte sequences in table order
   * @throws IllegalArgumentException if {@code maxResults} is less than one, or if the table can
   *     encode {@code input} in more than {@code maxResults} unique ways
   * @throws NullPointerException if {@code input} or {@code table} is {@code null}
   */
  public static List<byte[]> encodeAll(String input, TblTable table, int maxResults) {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(table, "table");
    if (maxResults < 1) {
      throw new IllegalArgumentException("maxResults must be positive");
    }
    if (input.isEmpty()) {
      return List.of();
    }

    List<TblTableEntry> entries = encodableEntries(table);
    List<byte[]> results = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    encodeAt(input, 0, entries, new ArrayList<>(), results, seen, maxResults);
    return List.copyOf(results);
  }

  private static List<TblTableEntry> encodableEntries(TblTable table) {
    List<TblTableEntry> entries = new ArrayList<>();
    for (TblTableEntry entry : table.getEntries()) {
      if (!entry.getValue().isEmpty()) {
        entries.add(entry);
      }
    }
    return entries;
  }

  private static void encodeAt(
      String input,
      int offset,
      List<TblTableEntry> entries,
      List<byte[]> parts,
      List<byte[]> results,
      Set<String> seen,
      int maxResults) {
    if (offset == input.length()) {
      addResult(parts, results, seen, maxResults);
      return;
    }

    for (TblTableEntry entry : entries) {
      String value = entry.getValue();
      if (!input.startsWith(value, offset)) {
        continue;
      }

      parts.add(entry.getKey());
      encodeAt(input, offset + value.length(), entries, parts, results, seen, maxResults);
      parts.remove(parts.size() - 1);
    }
  }

  private static void addResult(
      List<byte[]> parts, List<byte[]> results, Set<String> seen, int maxResults) {
    byte[] bytes = flatten(parts);
    if (!seen.add(TblHex.toHex(bytes))) {
      return;
    }
    if (results.size() >= maxResults) {
      throw new IllegalArgumentException(
          "Too many possible encodings; refine the search text or table");
    }
    results.add(bytes);
  }

  private static byte[] flatten(List<byte[]> parts) {
    int length = 0;
    for (byte[] part : parts) {
      length += part.length;
    }

    byte[] bytes = new byte[length];
    int offset = 0;
    for (byte[] part : parts) {
      System.arraycopy(part, 0, bytes, offset, part.length);
      offset += part.length;
    }
    return bytes;
  }
}
