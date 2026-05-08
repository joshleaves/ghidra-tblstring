# Ghidra sources

## Documentation

* [StringDataType](https://ghidra.re/ghidra_docs/api/ghidra/program/model/data/StringDataType.html)

## Source code

* [AbstractStringDataType.java](https://github.com/NationalSecurityAgency/ghidra/blob/master/Ghidra/Framework/SoftwareModeling/src/main/java/ghidra/program/model/data/AbstractStringDataType.java)

* [DataTypeListingHover.java](https://github.com/NationalSecurityAgency/ghidra/blob/master/Ghidra/Features/Base/src/main/java/ghidra/app/plugin/core/codebrowser/hover/DataTypeListingHover.java)

## References (aka: they do it right)

* [ghidra_psx_ldr](https://github.com/lab313ru/ghidra_psx_ldr/blob/master/src/main/java/psx/PsxPlugin.java#L64)
  They know how to properly set up a plugin.

* [snes-loader](https://github.com/achan1989/ghidra-snes-loader/tree/master/SnesLoader)
  Proper loader method to set up memory map.

