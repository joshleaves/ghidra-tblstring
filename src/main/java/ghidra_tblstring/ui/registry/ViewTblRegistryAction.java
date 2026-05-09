/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.ui.registry;

import docking.ComponentProvider;
import docking.WindowPosition;
import docking.widgets.textfield.HintTextField;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.model.listing.Program;
import ghidra.util.Msg;
import ghidra_tblstring.ghidra.TblRegistry;
import ghidra_tblstring.tbl.TblTable;
import ghidra_tblstring.tbl.TblTableEntry;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import resources.ResourceManager;

public final class ViewTblRegistryAction {
  private static final String TBL_REGISTRY_WINDOW_NAME = "tbl Registry";

  private static final Icon MENU_ICON = ResourceManager.loadImage("images/tblstring-icon_16.png");
  private static final Icon ADD_TABLE_ICON = ResourceManager.loadImage("images/page_add.png");
  private static final Icon REMOVE_TABLE_ICON = ResourceManager.loadImage("images/page_delete.png");
  private static final Icon ADD_ENTRY_ICON = ResourceManager.loadImage("images/small_plus.png");
  private static final Icon REMOVE_ENTRY_ICON = ResourceManager.loadImage("images/list-remove.png");
  private static final Icon EXPORT_ICON = ResourceManager.loadImage("images/disk_save_as.png");
  private final TblRegistryProvider provider;

  public ViewTblRegistryAction(PluginTool tool, String owner, TblRegistry registry, Supplier<Program> programSupplier) {
    provider = new TblRegistryProvider(tool, owner, registry, programSupplier);
  }

  public ViewTblRegistryAction(PluginTool tool, String owner) {
    this(tool, owner, new TblRegistry(), () -> null);
  }

  private static final class TblRegistryProvider extends ComponentProvider {
    private static final Dimension DEFAULT_SIZE = new Dimension(920, 560);
    private static final int TREE_WIDTH = 260;

    private final PluginTool tool;
    private final TblRegistry registry;
    private final Supplier<Program> programSupplier;
    private final JPanel component;
    private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Tables");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    private final JTree tableTree = new JTree(treeModel);
    private final TblEntryTableModel entryModel;
    private final JTable entryTable;
    private final TableRowSorter<TblEntryTableModel> entrySorter;
    private final JTextField filterField = new HintTextField("Search");
    private final JLabel tableTitle = new JLabel("No table selected");
    private final JLabel tableSummary = new JLabel("No table");
    private final JLabel tableCount = new JLabel("0 table(s)");
    private final JLabel defaultTableLabel = new JLabel("Default table: <none>");
    private final JButton removeTableButton = new JButton("Remove", REMOVE_TABLE_ICON);
    private final JButton reloadSourceButton = new JButton("Reload from Source File");
    private final JButton overwriteSourceButton = new JButton("Overwrite Source File");
    private final JButton saveAsButton = new JButton("Save as", EXPORT_ICON);
    private final JButton setDefaultButton = new JButton("Set as Default");
    private final JButton addEntryButton = new JButton("Add Entry", ADD_ENTRY_ICON);
    private final JButton removeEntryButton = new JButton("Remove Entry", REMOVE_ENTRY_ICON);
    private String selectedTableId;
    private boolean refreshingControls;

    TblRegistryProvider(
        PluginTool tool, String owner, TblRegistry registry, Supplier<Program> programSupplier) {
      super(tool, TBL_REGISTRY_WINDOW_NAME, owner);

      this.tool = Objects.requireNonNull(tool, "tool");
      this.registry = Objects.requireNonNull(registry, "registry");
      this.programSupplier = Objects.requireNonNull(programSupplier, "programSupplier");
      this.entryModel = new TblEntryTableModel(this::updateEntry);
      this.entryTable = new JTable(entryModel);
      this.entrySorter = new TableRowSorter<>(entryModel);
      this.component = buildComponent();

      setIcon(MENU_ICON);
      addToToolbar();
      setWindowMenuGroup("tblString");
      setWindowGroup("tblString");
      setDefaultWindowPosition(WindowPosition.WINDOW);
      setDefaultFocusComponent(tableTree);
      addToTool();
    }

    @Override
    public JComponent getComponent() {
      return component;
    }

    @Override
    public void componentShown() {
      refreshFromProgram();
    }

    private JPanel buildComponent() {
      JPanel root = new JPanel(new BorderLayout());
      root.setPreferredSize(DEFAULT_SIZE);
      root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
      root.add(buildToolbar(), BorderLayout.NORTH);
      root.add(buildSplitPane(), BorderLayout.CENTER);

      JPanel footer = new JPanel(new BorderLayout());
      footer.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
      footer.add(tableCount, BorderLayout.WEST);
      footer.add(tableSummary, BorderLayout.EAST);
      root.add(footer, BorderLayout.SOUTH);

      return root;
    }

    private JToolBar buildToolbar() {
      JToolBar toolbar = new JToolBar();
      toolbar.setFloatable(false);

      JButton addButton = new JButton("Add .tbl...", ADD_TABLE_ICON);
      addButton.addActionListener(event -> importTable());
      toolbar.add(addButton);

      removeTableButton.addActionListener(event -> removeSelectedTable());
      toolbar.add(removeTableButton);

      saveAsButton.addActionListener(event -> saveSelectedTableAs());
      toolbar.add(saveAsButton);

      toolbar.addSeparator();

      reloadSourceButton.addActionListener(event -> reloadSelectedTableFromSourceFile());
      reloadSourceButton.setToolTipText("Reload the selected table from its imported source file");
      toolbar.add(reloadSourceButton);

      overwriteSourceButton.addActionListener(event -> overwriteSelectedTableSourceFile());
      overwriteSourceButton.setToolTipText(
          "Write the selected table over its imported source file");
      toolbar.add(overwriteSourceButton);


      toolbar.addSeparator();
      setDefaultButton.addActionListener(event -> setSelectedDefaultTable());
      toolbar.add(setDefaultButton);

      toolbar.addSeparator();
      toolbar.add(defaultTableLabel);

      return toolbar;
    }

    private JSplitPane buildSplitPane() {
      tableTree.setRootVisible(false);
      tableTree.setShowsRootHandles(false);
      tableTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      tableTree.setCellRenderer(new TableTreeCellRenderer());
      tableTree.addTreeSelectionListener(event -> updateSelectedTableFromTree());

      JPanel leftPanel = new JPanel(new BorderLayout());
      leftPanel.setBorder(BorderFactory.createTitledBorder("Tables"));
      leftPanel.add(new JScrollPane(tableTree), BorderLayout.CENTER);

      configureEntryTable();

      JPanel rightPanel = new JPanel(new BorderLayout());
      rightPanel.add(buildTableHeader(), BorderLayout.NORTH);
      rightPanel.add(new JScrollPane(entryTable), BorderLayout.CENTER);
      rightPanel.add(buildEntryControls(), BorderLayout.SOUTH);

      JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
      splitPane.setResizeWeight(0);
      splitPane.setDividerLocation(TREE_WIDTH);
      splitPane.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
      return splitPane;
    }

    private JPanel buildTableHeader() {
      JPanel header = new JPanel(new BorderLayout());
      header.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 0));
      tableTitle.setFont(tableTitle.getFont().deriveFont(java.awt.Font.BOLD, 18f));
      header.add(tableTitle, BorderLayout.WEST);

      filterField.getDocument().addDocumentListener(
          new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
              updateFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
              updateFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
              updateFilter();
            }
          });
      filterField.setColumns(24);
      filterField.setToolTipText("Filter by hex key or decoded character");
      header.add(filterField, BorderLayout.EAST);

      return header;
    }

    private JPanel buildEntryControls() {
      JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
      addEntryButton.addActionListener(event -> addEntry());
      removeEntryButton.addActionListener(event -> removeSelectedEntries());
      panel.add(addEntryButton);
      panel.add(removeEntryButton);
      return panel;
    }

    private void configureEntryTable() {
      entryTable.setRowSorter(entrySorter);
      entryTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      entryTable.setFillsViewportHeight(true);
      entryTable.getColumnModel().getColumn(0).setPreferredWidth(160);
      entryTable.getColumnModel().getColumn(1).setPreferredWidth(520);
      entryTable.getColumnModel().getColumn(1).setCellRenderer(new ValueCellRenderer());
      entryTable.getTableHeader().setDefaultRenderer(new HeaderCellRenderer(entryTable));
    }

    private void refreshFromProgram() {
      stopEntryEditing();

      Program program = programSupplier.get();
      if (program == null) {
        registry.clear();
        selectedTableId = null;
        setSubTitle("No active program");
        refreshTableList(null);
        setStatus("No active program");
        return;
      }

      try {
        registry.load(program);
        setSubTitle(program.getName());
        refreshTableList(selectedTableId);
        setStatus("Loaded .tbl registry from " + program.getName());
      } catch (RuntimeException e) {
        showError(TBL_REGISTRY_WINDOW_NAME, "Failed to load .tbl registry: " + e.getMessage(), e);
      }
    }

    private void refreshTableList(String preferredTableId) {
      refreshingControls = true;
      try {
        rootNode.removeAllChildren();

        List<TableChoice> tableChoices = sortedTableChoices();
        for (TableChoice choice : tableChoices) {
          rootNode.add(new DefaultMutableTreeNode(choice));
        }

        treeModel.reload();
      } finally {
        refreshingControls = false;
      }

      tableCount.setText(registry.size() + " table(s)");
      updateDefaultTableLabel();
      selectTable(resolveTableToSelect(preferredTableId));
      updateActionState();
    }

    private List<TableChoice> sortedTableChoices() {
      List<TableChoice> choices = new ArrayList<>();
      for (String id : registry.ids()) {
        TblTable table = registry.require(id);
        choices.add(new TableChoice(id, table.getName()));
      }
      choices.sort(Comparator.comparing(choice -> choice.name.toLowerCase(Locale.ROOT)));
      return choices;
    }

    private String resolveTableToSelect(String preferredTableId) {
      if (preferredTableId != null && registry.contains(preferredTableId)) {
        return preferredTableId;
      }

      return registry
          .getDefaultTableId()
          .orElseGet(() -> registry.ids().stream().findFirst().orElse(null));
    }

    private void selectTable(String tableId) {
      if (tableId == null) {
        tableTree.clearSelection();
        selectedTableId = null;
        updateTableDetails();
        return;
      }

      for (int i = 0; i < rootNode.getChildCount(); i++) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) rootNode.getChildAt(i);
        TableChoice choice = (TableChoice) node.getUserObject();
        if (choice.id.equals(tableId)) {
          TreePath path = new TreePath(node.getPath());
          tableTree.setSelectionPath(path);
          tableTree.scrollPathToVisible(path);
          return;
        }
      }
    }

    private void updateDefaultTableLabel() {
      String defaultTableName =
          registry
              .getDefaultTableId()
              .map(id -> registry.require(id).getName())
              .orElse("<none>");
      defaultTableLabel.setText("Default table: " + defaultTableName);
    }

    private void updateSelectedTableFromTree() {
      if (refreshingControls) {
        return;
      }

      DefaultMutableTreeNode node =
          (DefaultMutableTreeNode) tableTree.getLastSelectedPathComponent();
      Object userObject = node == null ? null : node.getUserObject();
      selectedTableId = userObject instanceof TableChoice ? ((TableChoice) userObject).id : null;
      updateTableDetails();
      updateActionState();
    }

    private void updateTableDetails() {
      TblTable table = selectedTable();
      if (table == null) {
        tableTitle.setText("No table selected");
        tableSummary.setText("No table");
        entryModel.setEntries(List.of());
        return;
      }

      tableTitle.setText(table.getName());
      tableSummary.setText("Entries: " + table.getEntries().size());
      entryModel.setEntries(table.getEntries());
      updateFilter();
    }

    private void updateActionState() {
      boolean hasProgram = programSupplier.get() != null;
      boolean hasSelection = selectedTable() != null;
      boolean hasSourcePath = selectedTableSourcePath().isPresent();

      removeTableButton.setEnabled(hasProgram && hasSelection);
      reloadSourceButton.setEnabled(hasProgram && hasSelection && hasSourcePath);
      overwriteSourceButton.setEnabled(hasProgram && hasSelection && hasSourcePath);
      saveAsButton.setEnabled(hasSelection);
      setDefaultButton.setEnabled(hasProgram && hasSelection);
      addEntryButton.setEnabled(hasProgram && hasSelection);
      removeEntryButton.setEnabled(hasProgram && hasSelection);
      entryTable.setEnabled(hasProgram && hasSelection);
    }

    private TblTable selectedTable() {
      if (selectedTableId == null || !registry.contains(selectedTableId)) {
        return null;
      }

      return registry.require(selectedTableId);
    }

    private void importTable() {
      Program program = programSupplier.get();
      if (program == null) {
        showInfo(TBL_REGISTRY_WINDOW_NAME, "No active program");
        return;
      }

      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle("Import .tbl table");
      chooser.setFileFilter(new FileNameExtensionFilter(".tbl files", "tbl"));

      if (chooser.showOpenDialog(component) != JFileChooser.APPROVE_OPTION) {
        return;
      }

      Path path = chooser.getSelectedFile().toPath();
      try {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        TblTable table =
            withUniqueImportName(
                TblTable.parse(path.getFileName().toString(), new StringReader(content)));
        String id = registry.register(table);
        registry.setSourcePath(id, normalizedPath(path));

        if (!saveRegistry(program, "Import .tbl table")) {
          registry.remove(id);
          refreshTableList(selectedTableId);
          return;
        }

        refreshTableList(id);
        setStatus("Imported " + path.getFileName() + " as " + table.getName());
      } catch (IOException | RuntimeException e) {
        showError(TBL_REGISTRY_WINDOW_NAME, "Failed to import .tbl table: " + e.getMessage(), e);
      }
    }

    private TblTable withUniqueImportName(TblTable table) {
      String uniqueName = uniqueTableName(table.getName());
      if (uniqueName.equals(table.getName())) {
        return table;
      }

      return new TblTable(uniqueName, table.getEntries());
    }

    private String uniqueTableName(String name) {
      Set<String> existingNames = new HashSet<>();
      for (TblTable existingTable : registry.tables()) {
        existingNames.add(existingTable.getName().toLowerCase(Locale.ROOT));
      }

      if (!existingNames.contains(name.toLowerCase(Locale.ROOT))) {
        return name;
      }

      int extensionOffset = name.lastIndexOf('.');
      String baseName = extensionOffset > 0 ? name.substring(0, extensionOffset) : name;
      String extension = extensionOffset > 0 ? name.substring(extensionOffset) : "";
      for (int suffix = 2; ; suffix++) {
        String candidate = baseName + " (" + suffix + ")" + extension;
        if (!existingNames.contains(candidate.toLowerCase(Locale.ROOT))) {
          return candidate;
        }
      }
    }

    private void removeSelectedTable() {
      TblTable table = selectedTable();
      if (table == null) {
        return;
      }

      int answer =
          JOptionPane.showConfirmDialog(
              component,
              "Remove table '" + table.getName() + "' from this program?",
              TBL_REGISTRY_WINDOW_NAME,
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.WARNING_MESSAGE);
      if (answer != JOptionPane.OK_OPTION) {
        return;
      }

      Program program = programSupplier.get();
      String removedId = selectedTableId;
      registry.remove(removedId);
      if (!saveRegistry(program, "Remove .tbl table")) {
        refreshFromProgram();
        return;
      }

      refreshTableList(null);
      setStatus("Removed " + table.getName());
    }

    private void reloadSelectedTableFromSourceFile() {
      TblTable table = selectedTable();
      if (table == null) {
        return;
      }

      Path path = selectedTableSourcePath().orElse(null);
      if (path == null) {
        return;
      }
      if (!Files.exists(path)) {
        showInfo(TBL_REGISTRY_WINDOW_NAME, "Source file does not exist: " + path);
        return;
      }

      int answer =
          JOptionPane.showConfirmDialog(
              component,
              "Reload table '" + table.getName() + "' from " + path + "?",
              TBL_REGISTRY_WINDOW_NAME,
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.WARNING_MESSAGE);
      if (answer != JOptionPane.OK_OPTION) {
        return;
      }

      try {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        TblTable reloadedTable = TblTable.parse(table.getName(), new StringReader(content));
        registry.register(selectedTableId, reloadedTable);
        registry.setSourcePath(selectedTableId, normalizedPath(path));

        if (!saveRegistry(programSupplier.get(), "Reload .tbl from source file")) {
          registry.register(selectedTableId, table);
          refreshTableList(selectedTableId);
          return;
        }

        refreshTableList(selectedTableId);
        setStatus("Reloaded " + table.getName() + " from " + path.getFileName());
      } catch (IOException | RuntimeException e) {
        showError(
            TBL_REGISTRY_WINDOW_NAME,
            "Failed to reload .tbl source file: " + e.getMessage(),
            e);
      }
    }

    private void overwriteSelectedTableSourceFile() {
      TblTable table = selectedTable();
      if (table == null) {
        return;
      }

      Path path = selectedTableSourcePath().orElse(null);
      if (path == null) {
        return;
      }
      if (!Files.exists(path)) {
        showInfo(TBL_REGISTRY_WINDOW_NAME, "Source file does not exist: " + path);
        return;
      }

      int answer =
          JOptionPane.showConfirmDialog(
              component,
              "Overwrite " + path + " with table '" + table.getName() + "'?",
              TBL_REGISTRY_WINDOW_NAME,
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.WARNING_MESSAGE);
      if (answer != JOptionPane.OK_OPTION) {
        return;
      }

      try {
        Files.writeString(path, table.toTblString(), StandardCharsets.UTF_8);
        registry.setSourcePath(selectedTableId, normalizedPath(path));

        if (!saveRegistry(programSupplier.get(), "Overwrite .tbl source file")) {
          refreshTableList(selectedTableId);
          return;
        }

        refreshTableList(selectedTableId);
        setStatus("Overwrote " + path.getFileName());
      } catch (IOException | RuntimeException e) {
        showError(
            TBL_REGISTRY_WINDOW_NAME,
            "Failed to overwrite .tbl source file: " + e.getMessage(),
            e);
      }
    }

    private void saveSelectedTableAs() {
      TblTable table = selectedTable();
      if (table == null) {
        return;
      }

      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle("Save .tbl table as");
      chooser.setSelectedFile(new File(table.getName()));
      chooser.setFileFilter(new FileNameExtensionFilter(".tbl files", "tbl"));

      if (chooser.showSaveDialog(component) != JFileChooser.APPROVE_OPTION) {
        return;
      }

      Path path = chooser.getSelectedFile().toPath();
      if (Files.exists(path)) {
        int answer =
            JOptionPane.showConfirmDialog(
                component,
                "Overwrite " + path.getFileName() + "?",
                TBL_REGISTRY_WINDOW_NAME,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
          return;
        }
      }

      try {
        Files.writeString(path, table.toTblString(), StandardCharsets.UTF_8);
        registry.setSourcePath(selectedTableId, normalizedPath(path));

        if (!saveRegistry(programSupplier.get(), "Save .tbl table as")) {
          refreshTableList(selectedTableId);
          return;
        }

        refreshTableList(selectedTableId);
        setStatus("Saved " + path.getFileName());
      } catch (IOException | RuntimeException e) {
        showError(TBL_REGISTRY_WINDOW_NAME, "Failed to save .tbl table: " + e.getMessage(), e);
      }
    }

    private Optional<Path> selectedTableSourcePath() {
      if (selectedTableId == null) {
        return Optional.empty();
      }

      String sourcePath = registry.getSourcePath(selectedTableId).orElse(null);
      if (sourcePath == null) {
        return Optional.empty();
      }

      try {
        return Optional.of(Path.of(sourcePath));
      } catch (InvalidPathException e) {
        return Optional.empty();
      }
    }

    private String normalizedPath(Path path) {
      return path.toAbsolutePath().normalize().toString();
    }

    private void setSelectedDefaultTable() {
      TableChoice choice = selectedTableChoice();
      if (choice == null) {
        return;
      }

      Program program = programSupplier.get();
      registry.setDefaultTableId(choice.id);
      if (!saveRegistry(program, "Set default .tbl table")) {
        refreshFromProgram();
        return;
      }

      refreshTableList(selectedTableId);
      setStatus("Default .tbl table: " + choice.name);
    }

    private TableChoice selectedTableChoice() {
      TblTable table = selectedTable();
      if (selectedTableId == null || table == null) {
        return null;
      }

      return new TableChoice(selectedTableId, table.getName());
    }

    private void addEntry() {
      TblTable table = selectedTable();
      if (table == null) {
        return;
      }

      List<TblTableEntry> entries = new ArrayList<>(table.getEntries());
      entries.add(new TblTableEntry(nextAvailableKey(entries), ""));
      replaceSelectedTable(table, entries, "Add .tbl entry");
      selectEntry(entries.size() - 1);
    }

    private void removeSelectedEntries() {
      TblTable table = selectedTable();
      if (table == null) {
        return;
      }

      int[] selectedRows = entryTable.getSelectedRows();
      if (selectedRows.length == 0) {
        return;
      }

      List<Integer> modelRows = new ArrayList<>();
      for (int row : selectedRows) {
        modelRows.add(entryTable.convertRowIndexToModel(row));
      }
      modelRows.sort(Comparator.reverseOrder());

      List<TblTableEntry> entries = new ArrayList<>(table.getEntries());
      if (entries.size() == modelRows.size()) {
        showInfo(TBL_REGISTRY_WINDOW_NAME, "A table must keep at least one entry.");
        return;
      }

      for (int row : modelRows) {
        entries.remove(row);
      }

      replaceSelectedTable(table, entries, "Remove .tbl entry");
    }

    private boolean updateEntry(int row, int column, String value) {
      TblTable table = selectedTable();
      if (table == null || row < 0 || row >= table.getEntries().size()) {
        return false;
      }

      TblTableEntry current = table.getEntries().get(row);
      byte[] key = current.getKey();
      String decodedValue = current.getValue();

      try {
        if (column == 0) {
          key = parseHexKey(value);
        } else {
          decodedValue = unescapeCellValue(value);
        }

        List<TblTableEntry> entries = new ArrayList<>(table.getEntries());
        entries.set(row, new TblTableEntry(key, decodedValue));
        return replaceSelectedTable(table, entries, "Edit .tbl entry");
      } catch (RuntimeException e) {
        showError(TBL_REGISTRY_WINDOW_NAME, "Invalid entry: " + e.getMessage(), e);
        return false;
      }
    }

    private boolean replaceSelectedTable(
        TblTable previousTable, List<TblTableEntry> entries, String actionName) {
      Program program = programSupplier.get();
      if (program == null || selectedTableId == null) {
        showInfo(TBL_REGISTRY_WINDOW_NAME, "No active program");
        return false;
      }

      try {
        TblTable updatedTable = new TblTable(previousTable.getName(), entries);
        registry.register(selectedTableId, updatedTable);

        if (!saveRegistry(program, actionName)) {
          registry.register(selectedTableId, previousTable);
          refreshTableList(selectedTableId);
          return false;
        }

        refreshTableList(selectedTableId);
        return true;
      } catch (RuntimeException e) {
        registry.register(selectedTableId, previousTable);
        showError(TBL_REGISTRY_WINDOW_NAME, "Failed to update table: " + e.getMessage(), e);
        return false;
      }
    }

    private boolean saveRegistry(Program program, String actionName) {
      if (program == null) {
        showInfo(TBL_REGISTRY_WINDOW_NAME, "No active program");
        return false;
      }

      int transactionId = program.startTransaction(actionName);
      boolean commit = false;
      try {
        registry.save(program);
        commit = true;
        return true;
      } catch (RuntimeException e) {
        showError(TBL_REGISTRY_WINDOW_NAME, "Failed to save .tbl registry: " + e.getMessage(), e);
        return false;
      } finally {
        program.endTransaction(transactionId, commit);
      }
    }

    private void updateFilter() {
      String filter = filterField.getText().trim().toLowerCase(Locale.ROOT);
      if (filter.isEmpty()) {
        entrySorter.setRowFilter(null);
        return;
      }

      entrySorter.setRowFilter(
          new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends TblEntryTableModel, ? extends Integer> entry) {
              String hex = entry.getStringValue(0).toLowerCase(Locale.ROOT);
              String value = entry.getStringValue(1).toLowerCase(Locale.ROOT);
              return hex.contains(filter) || value.contains(filter);
            }
          });
    }

    private void selectEntry(int modelRow) {
      int viewRow = entryTable.convertRowIndexToView(modelRow);
      if (viewRow >= 0) {
        entryTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
        entryTable.scrollRectToVisible(entryTable.getCellRect(viewRow, 0, true));
      }
    }

    private void stopEntryEditing() {
      if (entryTable.isEditing()) {
        entryTable.getCellEditor().stopCellEditing();
      }
    }

    private void setStatus(String message) {
      tool.setStatusInfo(message);
    }

    private void showInfo(String title, String message) {
      setStatus(message);
      JOptionPane.showMessageDialog(component, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String title, String message, Throwable throwable) {
      tool.setStatusInfo(message, true);
      Msg.showError(ViewTblRegistryAction.class, component, title, message, throwable);
    }
  }

  private interface EntryUpdateHandler {
    boolean updateEntry(int row, int column, String value);
  }

  private static final class TblEntryTableModel extends AbstractTableModel {
    private final EntryUpdateHandler updateHandler;
    private List<TblTableEntry> entries = List.of();

    TblEntryTableModel(EntryUpdateHandler updateHandler) {
      this.updateHandler = updateHandler;
    }

    void setEntries(List<TblTableEntry> entries) {
      this.entries = List.copyOf(entries);
      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return entries.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public String getColumnName(int column) {
      return column == 0 ? "Hex (BE)" : "Character";
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return true;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      TblTableEntry entry = entries.get(rowIndex);
      return columnIndex == 0 ? bytesToHex(entry.getKey()) : displayValue(entry.getValue());
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
      if (updateHandler.updateEntry(rowIndex, columnIndex, value == null ? "" : value.toString())) {
        fireTableRowsUpdated(rowIndex, rowIndex);
      }
    }
  }

  private static final class TableChoice {
    private final String id;
    private final String name;

    TableChoice(String id, String name) {
      this.id = id;
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private static final class HeaderCellRenderer extends DefaultTableCellRenderer {
    HeaderCellRenderer(JTable table) {
      JTableHeader header = table.getTableHeader();
      setFont(header.getFont());
      setBorder(UIManager.getBorder("TableHeader.cellBorder"));
      setHorizontalAlignment(LEADING);
      setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(
        JTable table,
        Object value,
        boolean isSelected,
        boolean hasFocus,
        int row,
        int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      Color background = UIManager.getColor("Panel.background");
      Color foreground = UIManager.getColor("Label.foreground");
      setBackground(background != null ? background : table.getBackground());
      setForeground(foreground != null ? foreground : table.getForeground());
      return this;
    }
  }

  private static final class TableTreeCellRenderer extends DefaultTreeCellRenderer {
    @Override
    public java.awt.Component getTreeCellRendererComponent(
        javax.swing.JTree tree,
        Object value,
        boolean isSelected,
        boolean expanded,
        boolean leaf,
        int row,
        boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, isSelected, expanded, leaf, row, hasFocus);
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      Object userObject = node.getUserObject();
      if (userObject instanceof TableChoice) {
        setText(((TableChoice) userObject).name);
        setIcon(MENU_ICON);
      }
      return this;
    }
  }

  private static final class ValueCellRenderer extends DefaultTableCellRenderer {
    @Override
    protected void setValue(Object value) {
      String text = value == null ? "" : value.toString();
      super.setValue(text.isEmpty() ? "<empty>" : text);
    }
  }

  private static byte[] parseHexKey(String hex) {
    String normalized = hex == null ? "" : hex.replaceAll("\\s+", "");
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("hex key cannot be empty");
    }
    if ((normalized.length() % 2) != 0) {
      throw new IllegalArgumentException("hex key must have an even number of digits");
    }

    byte[] bytes = new byte[normalized.length() / 2];
    for (int i = 0; i < bytes.length; i++) {
      int offset = i * 2;
      String pair = normalized.substring(offset, offset + 2);
      try {
        bytes[i] = (byte) Integer.parseInt(pair, 16);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("invalid hex byte '" + pair + "'", e);
      }
    }
    return bytes;
  }

  private static byte[] nextAvailableKey(List<TblTableEntry> entries) {
    Set<String> existingKeys = new HashSet<>();
    for (TblTableEntry entry : entries) {
      existingKeys.add(bytesToHex(entry.getKey()));
    }

    for (int value = 0; value <= 0xff; value++) {
      byte[] key = new byte[] {(byte) value};
      if (!existingKeys.contains(bytesToHex(key))) {
        return key;
      }
    }

    for (int value = 0; value <= 0xffff; value++) {
      byte[] key = new byte[] {(byte) ((value >>> 8) & 0xff), (byte) (value & 0xff)};
      if (!existingKeys.contains(bytesToHex(key))) {
        return key;
      }
    }

    throw new IllegalArgumentException("no available one- or two-byte key");
  }

  private static String bytesToHex(byte[] bytes) {
    char[] digits = "0123456789ABCDEF".toCharArray();
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      int value = b & 0xff;
      result.append(digits[(value >>> 4) & 0xf]);
      result.append(digits[value & 0xf]);
    }
    return result.toString();
  }

  private static String unescapeCellValue(String value) {
    StringBuilder result = new StringBuilder();
    String input = value == null ? "" : value;

    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c == '<') {
        int tokenEnd = input.indexOf('>', i + 1);
        if (tokenEnd > i) {
          String decodedToken = decodeDisplayToken(input.substring(i + 1, tokenEnd));
          if (decodedToken != null) {
            result.append(decodedToken);
            i = tokenEnd;
            continue;
          }
        }
      }

      if (c != '\\' || i + 1 >= input.length()) {
        result.append(c);
        continue;
      }

      char next = input.charAt(++i);
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

  private static String decodeDisplayToken(String token) {
    String normalized = token.toUpperCase(Locale.ROOT);
    switch (normalized) {
      case "EMPTY":
        return "";
      case "SPACE":
        return " ";
      case "NEWLINE":
      case "LF":
        return "\n";
      case "CR":
        return "\r";
      case "TAB":
        return "\t";
      default:
        return decodeHexDisplayToken(normalized);
    }
  }

  private static String decodeHexDisplayToken(String token) {
    if (token.length() == 3 && token.charAt(0) == 'X') {
      return decodeCharacterToken(token.substring(1), 0xff);
    }
    if (token.length() == 5 && token.charAt(0) == 'U') {
      return decodeCharacterToken(token.substring(1), 0xffff);
    }
    return null;
  }

  private static String decodeCharacterToken(String hex, int maxValue) {
    try {
      int value = Integer.parseInt(hex, 16);
      if (value < 0 || value > maxValue) {
        return null;
      }
      return Character.toString((char) value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String displayValue(String value) {
    if (value.isEmpty()) {
      return "<EMPTY>";
    }

    StringBuilder result = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case ' ':
          result.append("<SPACE>");
          break;
        case '\n':
          result.append("<NEWLINE>");
          break;
        case '\r':
          result.append("<CR>");
          break;
        case '\t':
          result.append("<TAB>");
          break;
        default:
          appendVisibleCharacter(result, c);
          break;
      }
    }
    return result.toString();
  }

  private static void appendVisibleCharacter(StringBuilder result, char c) {
    if (Character.isISOControl(c)) {
      if (c <= 0xff) {
        result.append(String.format(Locale.ROOT, "<x%02X>", (int) c));
      } else {
        result.append(String.format(Locale.ROOT, "<u%04X>", (int) c));
      }
      return;
    }

    result.append(c);
  }
}
