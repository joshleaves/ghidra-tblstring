![](src/main/resources/images/tblstring-banner.png)

`ghidra-tblString` adds a Ghidra `tblString` data type for decoding game text that uses project-
specific `.tbl` character tables.

Instead of leaving encoded bytes as raw data:

```text
f9 23 e1 23 ff e8 23 f0 23 e3 23
```

the Listing can render them as decoded text:

```text
"[Copyright]BIRD<FF>IRD"
```

## Current Features

- Import one or more `.tbl` files into the current Ghidra program.
- Store imported tables in program options, so decoding does not depend on the original file path.
- Edit table entries from a floating `tbl Registry` window while browsing the Listing.
- Apply the `tblString` data type through Ghidra's native data-type workflow.
- Select the table id on each applied `tblString` data instance through the `.tbl Table` setting.
- Re-render existing `tblString` data when the registry changes.
- Parse common `HEX=value` tables, longest-match multi-byte keys, simple dump control-code entries,
  and end-token entries.

## Quick Usage

1. Enable the `tblString` plugin in the Ghidra tool if it is not already enabled.
2. Open a program.
3. Open `Window -> tbl Registry`.
4. Click `Add .tbl...` and import a table.
5. Apply the `tblString` data type to the bytes for one encoded string using Ghidra's normal data
   type application workflow.
6. If needed, edit the applied data settings and choose the table under `.tbl Table`.

## Registry Window

`Window -> tbl Registry` opens a normal Ghidra window, not a modal tool. It can stay open while you
move through the Listing.

Available actions:

- `Add .tbl...`: import a `.tbl` file into the current program.
- `Remove`: remove the selected table from the current program.
- `Save as`: export the selected table to a `.tbl` file.
- `Reload from Source File`: reload the selected table from the source path recorded at import time.
- `Overwrite Source File`: write the current table contents over that source path.
- `Set as Default`: make the selected table the default for new `tblString` applications.
- `Add Entry` / `Remove Entry`: edit entries directly in the table grid.

Imported tables get stable internal ids and unique display names. The left-side tree is sorted by
display name.

In the entry grid, leading and trailing whitespace/control characters are shown as tokens so they
are visible. Interior spaces stay as normal spaces. For example, ` ma maison ` is displayed as
`<SP>ma maison<SP>`.

## Data Type Behavior

Applied `tblString` data can store its selected table id in Ghidra data settings under `.tbl Table`.
The data type resolves tables in this order:

1. Table id stored on the data instance.
2. Legacy charset setting, for older data created before `.tbl Table`.
3. Table id passed when the data type was created.
4. Default table from the active plugin registry.
5. Default table loaded from the current program through the memory buffer.

If the referenced table id does not exist, the Listing shows `<missing-table:id>`. If no table can
be resolved at all, it shows `<no-table>`.

## Table Format

The parser supports the major [Text Table](https://datacrystal.tcrf.net/wiki/Text_Table) specifications, MINUS some exotic stuff from the [original format descriptions](https://transcorp.romhacking.net/scratchpad/Table%20File%20Format.txt) like switching files and stuff.

In short:

```text
@CREDITS
41=A
8140=あ
$FD=[linebreak]\n
/FF=[END]\n\n
```

Values are preserved after `=`, including a literal single-space value such as `20=<space>`.

Decoding is greedy: at each byte offset, the longest matching key wins.

Unknown bytes render as uppercase hex angle tokens by default:

```text
<00><FF>
```

## Storage

Tables are stored inside the current program using program options:

```text
tblString.defaultTableId
tblString.tables.order
tblString.tables.{id}.tbl
tblString.tables.{id}.name
tblString.tables.{id}.sourcePath
```

The original source path is metadata for reload/overwrite workflows. The imported table contents
remain embedded in the Ghidra program.

## Development

Set `GHIDRA_INSTALL_DIR` to a compatible Ghidra installation, then run:

```sh
./gradlew test
./gradlew spotlessCheck
./gradlew buildExtension
```

The extension zip is written to `dist/`.

Useful docs:

- [References](REFERENCES.md)

## Current Limitations

- No automatic text discovery.
- No batch apply.
- No terminator scanning.
- No word-le/word-be mode yet; keys are byte sequences.
- No compression support.
- No patch/re-encode workflow.

## Credits

- The font in the icon/logo is `Super Mario Bros. NES.ttf` by
  [TheWolfBunny64](https://thewolfbunny64.itch.io/super-mario-bros-nes).
