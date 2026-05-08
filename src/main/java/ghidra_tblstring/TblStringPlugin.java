/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring;

import docking.ActionContext;
import docking.action.DockingAction;
import docking.action.MenuData;
import docking.action.ToolBarData;
import docking.tool.ToolConstants;
import ghidra.MiscellaneousPluginPackage;
import ghidra.app.plugin.PluginCategoryNames;
import ghidra.app.plugin.ProgramPlugin;
import ghidra.app.services.CodeViewerService;
import ghidra.framework.plugintool.PluginInfo;
import ghidra.framework.plugintool.PluginTool;
import ghidra.framework.plugintool.util.PluginStatus;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressSetView;
import ghidra.program.model.data.CharsetSettingsDefinition;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Listing;
import ghidra.program.model.listing.Program;
import ghidra.program.model.mem.Memory;
import ghidra.program.util.ProgramSelection;
import ghidra_tblstring.ghidra.TblRegistry;
import ghidra_tblstring.ghidra.TblStringDataType;
import ghidra_tblstring.tbl.TblTable;
import ghidra_tblstring.ui.about.AboutAction;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

@PluginInfo(
  status = PluginStatus.RELEASED,
  packageName = MiscellaneousPluginPackage.NAME,
  category = PluginCategoryNames.ANALYSIS,
  shortDescription = "tblString plugin",
  description = "Apply and manage custom table-based string decoding")
public class TblStringPlugin extends ProgramPlugin {

  private final TblRegistry registry = new TblRegistry();

  public TblStringPlugin(PluginTool tool) {
    super(tool);

    new AboutAction(tool, getName());

    createActions();
  }

  private void showMessage(String title, String message) {
    System.out.println(title + ": " + message);
    tool.setStatusInfo(message);
    JOptionPane.showMessageDialog(
        tool.getToolFrame(), message, title, JOptionPane.INFORMATION_MESSAGE);
  }

  private void importDefaultTable() {
    Program program = getCurrentProgram();
    if (program == null) {
      showMessage("TableString", "No active program");
      return;
    }

    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Import .tbl as default TableString table");

    int result = chooser.showOpenDialog(tool.getToolFrame());
    if (result != JFileChooser.APPROVE_OPTION) {
      return;
    }

    Path path = chooser.getSelectedFile().toPath();

    try {
      String content = Files.readString(path, StandardCharsets.UTF_8);
      TblTable table = TblTable.parse("default", new StringReader(content));

      registry.load(program);

      int transactionId = program.startTransaction("Import TableString table");
      boolean commit = false;

      try {
        registry.register("default", table);
        registry.save(program);
        commit = true;
      } finally {
        program.endTransaction(transactionId, commit);
      }

      showMessage(
          "TableString",
          "Imported table as default:\n"
              + path.getFileName()
              + "\n\nEntries: "
              + table.getEntries().size());
    } catch (IOException e) {
      showMessage("TableString", "Failed to import table: " + e.getMessage());
    } catch (RuntimeException e) {
      showMessage("TableString", "Failed to save table: " + e.getMessage());
    }
  }

  private void createActions() {
    DockingAction importAction =
        new DockingAction("Import Default TableString Table", getName()) {
          @Override
          public void actionPerformed(ActionContext context) {
            importDefaultTable();
          }
        };

    importAction.setDescription("Import a .tbl file as the default TableString table");
    importAction.setEnabled(true);
    importAction.setMenuBarData(
        new MenuData(
            new String[] {ToolConstants.MENU_TOOLS, "Import default .tbl..."}, "TableString"));
    importAction.setToolBarData(new ToolBarData(null, "TableString"));

    tool.addAction(importAction);

    DockingAction applyAction =
        new DockingAction("Apply TableString", getName()) {
          @Override
          public void actionPerformed(ActionContext context) {
            Program program = getCurrentProgram();
            if (program == null) {
              showMessage("TableString", "No active program");
              return;
            }

            // Load tables from program (if any)
            registry.load(program);

            CodeViewerService codeViewer = tool.getService(CodeViewerService.class);
            if (codeViewer == null) {
              showMessage("TableString", "No CodeViewerService");
              return;
            }

            ProgramSelection selection = codeViewer.getCurrentSelection();
            if (selection == null || selection.isEmpty()) {
              showMessage("TableString", "No selection");
              return;
            }

            AddressSetView set = selection;
            Address start = set.getMinAddress();
            long length = set.getNumAddresses();

            if (length <= 0 || length > Integer.MAX_VALUE) {
              showMessage("TableString", "Invalid selection size");
              return;
            }

            Memory memory = program.getMemory();
            byte[] bytes = new byte[(int) length];

            try {
              memory.getBytes(start, bytes);
            } catch (Exception e) {
              showMessage("TableString", "Failed to read memory: " + e.getMessage());
              return;
            }

            // Decode (using a table id, for now hardcoded)
            String tableId = "default";

            TblTable table = registry.get(tableId).orElse(null);
            if (table == null) {
              showMessage(
                  "TableString",
                  "Missing table: " + tableId + "\n\nUse Tools → Import default .tbl... first.");
              return;
            }

            // Apply DataType on selection
            int transactionId = program.startTransaction("Apply TableString");
            boolean commit = false;

            try {
              Listing listing = program.getListing();

              TblStringDataType dt = new TblStringDataType(registry, (int) length, tableId);

              listing.clearCodeUnits(start, start.add(length - 1), false);
              Data data = listing.createData(start, dt, (int) length);
              CharsetSettingsDefinition.CHARSET.setCharset(data, tableId);
              commit = true;
            } catch (Exception e) {
              showMessage("TableString", "Failed to apply datatype: " + e.getMessage());
            } finally {
              program.endTransaction(transactionId, commit);
            }
          }
        };

    applyAction.setDescription("Apply a TableString to the current selection");
    applyAction.setEnabled(true);
    applyAction.setPopupMenuData(new MenuData(new String[] {"TableString", "Apply TableString"}));
    applyAction.setMenuBarData(
        new MenuData(
            new String[] {ToolConstants.MENU_TOOLS, "Apply TableString..."}, "TableString"));
    applyAction.setToolBarData(new ToolBarData(null, "TableString"));

    tool.addAction(applyAction);
  }

  @Override
  protected void programActivated(Program program) {
    super.programActivated(program);
  }
}
