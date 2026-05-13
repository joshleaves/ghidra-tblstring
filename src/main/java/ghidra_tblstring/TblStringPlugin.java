/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring;

import ghidra.MiscellaneousPluginPackage;
import ghidra.app.plugin.PluginCategoryNames;
import ghidra.app.plugin.ProgramPlugin;
import ghidra.app.services.CodeViewerService;
import ghidra.framework.plugintool.PluginInfo;
import ghidra.framework.plugintool.PluginTool;
import ghidra.framework.plugintool.util.PluginStatus;
import ghidra.program.model.listing.Program;
import ghidra.util.Msg;
import ghidra.util.Swing;
import ghidra_tblstring.ghidra.TblRegistry;
import ghidra_tblstring.ghidra.TblStringTableSettingsDefinition;
import ghidra_tblstring.ui.about.AboutAction;
import ghidra_tblstring.ui.registry.ViewTblRegistryAction;

/**
 * Main Ghidra plugin entry point for table-based string decoding.
 *
 * <p>The plugin owns one {@link TblRegistry} for the active program, installs the About and
 * Registry window actions, and refreshes the Code Browser when registry mutations can affect
 * rendered {@code tblString} data.
 */
@PluginInfo(
  status = PluginStatus.RELEASED,
  packageName = MiscellaneousPluginPackage.NAME,
  category = PluginCategoryNames.ANALYSIS,
  shortDescription = "tblString plugin",
  description = "Apply and manage custom .tbl-based string decoding")
public class TblStringPlugin extends ProgramPlugin {
  private final TblRegistry registry = new TblRegistry();
  private final Runnable registryChangeListener = this::updateCodeViewerDisplay;
  private volatile boolean codeViewerUpdatePending;

  /**
   * Creates the plugin and installs its actions into the supplied tool.
   *
   * @param tool plugin tool that hosts this extension
   */
  public TblStringPlugin(PluginTool tool) {
    super(tool);

    TblStringTableSettingsDefinition.setRegistrySupplier(() -> registry);
    registry.addChangeListener(registryChangeListener);

    new AboutAction(tool, getName());
    new ViewTblRegistryAction(tool, getName(), registry, this::getCurrentProgram);
  }

  @Override
  protected void dispose() {
    registry.removeChangeListener(registryChangeListener);
    TblStringTableSettingsDefinition.setRegistrySupplier(null);
    super.dispose();
  }

  @Override
  protected void programActivated(Program program) {
    super.programActivated(program);

    if (program == null) {
      registry.clear();
      return;
    }

    try {
      registry.load(program);
    } catch (Exception e) {
      Msg.showError(
          this,
          null,
          "Load .tbl Registry",
          "Unable to load .tbl registry for program: " + program.getName(),
          e);
    }
  }

  private void updateCodeViewerDisplay() {
    if (codeViewerUpdatePending) {
      return;
    }

    codeViewerUpdatePending = true;
    Swing.runLater(
        () -> {
          codeViewerUpdatePending = false;
          CodeViewerService codeViewer = tool.getService(CodeViewerService.class);
          if (codeViewer != null) {
            codeViewer.updateDisplay();
          }
        });
  }
}
