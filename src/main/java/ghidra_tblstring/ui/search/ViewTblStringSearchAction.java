/* (C) Arnaud 'red' Rouyer 2026 */
package ghidra_tblstring.ui.search;

import docking.ActionContext;
import docking.DockingContextListener;
import docking.action.DockingAction;
import docking.action.ToggleDockingAction;
import docking.action.builder.ActionBuilder;
import docking.action.builder.ToggleActionBuilder;
import docking.menu.ButtonState;
import docking.menu.MultiStateButton;
import docking.widgets.OptionDialog;
import docking.widgets.checkbox.GCheckBox;
import docking.widgets.combobox.GComboBox;
import docking.widgets.combobox.GhidraComboBox;
import docking.widgets.label.GDLabel;
import docking.widgets.label.GLabel;
import docking.widgets.table.TableColumnDescriptor;
import docking.widgets.table.actions.DeleteTableRowAction;
import ghidra.app.context.NavigatableActionContext;
import ghidra.app.nav.Navigatable;
import ghidra.app.nav.NavigatableRemovalListener;
import ghidra.framework.model.DomainObject;
import ghidra.framework.model.DomainObjectClosedListener;
import ghidra.framework.plugintool.ComponentProviderAdapter;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressRange;
import ghidra.program.model.address.AddressSet;
import ghidra.program.model.address.AddressSetView;
import ghidra.program.model.address.AddressOverflowException;
import ghidra.program.model.listing.CodeUnit;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.listing.Program;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryAccessException;
import ghidra.program.model.mem.MemoryBlock;
import ghidra.program.util.BytesFieldLocation;
import ghidra.program.util.ProgramLocation;
import ghidra.program.util.ProgramSelection;
import ghidra.util.Msg;
import ghidra.util.Swing;
import ghidra.util.datastruct.Accumulator;
import ghidra.util.exception.CancelledException;
import ghidra.util.layout.PairLayout;
import ghidra.util.layout.VerticalLayout;
import ghidra.util.table.GhidraProgramTableModel;
import ghidra.util.table.GhidraTable;
import ghidra.util.table.GhidraTableFilterPanel;
import ghidra.util.table.GhidraThreadedTablePanel;
import ghidra.util.table.SelectionNavigationAction;
import ghidra.util.table.actions.MakeProgramSelectionAction;
import ghidra.util.task.Task;
import ghidra.util.task.TaskMonitor;
import ghidra_tblstring.ghidra.TblRegistry;
import ghidra_tblstring.tbl.TblHex;
import ghidra_tblstring.tbl.TblStringEncoder;
import ghidra_tblstring.tbl.TblTable;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import resources.Icons;
import resources.ResourceManager;

/**
 * Installs the {@code Search -> TableString...} action.
 *
 * <p>The provider mirrors Ghidra's memory search workflow, but uses a {@link TblTable} to encode
 * the user-entered text into every matching byte pattern before scanning memory.
 */
public final class ViewTblStringSearchAction {
  private static final Icon MENU_ICON = ResourceManager.loadImage("images/tblstring-icon_16.png");

  private final PluginTool tool;
  private final TblRegistry registry;
  private final Supplier<Program> programSupplier;

  /**
   * Creates and installs the table-string search action.
   *
   * @param tool tool that hosts the action
   * @param owner action owner, usually the plugin name
   * @param registry active program registry
   * @param programSupplier supplies the current program when UI actions run
   */
  public ViewTblStringSearchAction(
      PluginTool tool, String owner, TblRegistry registry, Supplier<Program> programSupplier) {
    this.tool = Objects.requireNonNull(tool, "tool");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.programSupplier = Objects.requireNonNull(programSupplier, "programSupplier");

    new ActionBuilder("TableString Search", owner)
        .menuPath("&Search", "TableString...")
        .menuGroup("search", "tblString")
        .description("Search memory for text encoded with a .tbl table")
        .withContext(NavigatableActionContext.class, true)
        .enabledWhen(context -> context.getProgram() != null)
        .onAction(context -> showProvider(owner, context))
        .buildAndInstall(tool);
  }

  private void showProvider(String owner, NavigatableActionContext context) {
    Program program = Optional.ofNullable(context.getProgram()).orElseGet(programSupplier);
    if (program == null) {
      Msg.showWarn(this, null, "TableString Search", "No active program.");
      return;
    }
    if (registry.isEmpty()) {
      Msg.showWarn(this, null, "TableString Search", "Import a .tbl table before searching.");
      return;
    }

    new TblStringSearchProvider(tool, owner, registry, program, context.getNavigatable());
  }

  private enum ResultCombiner {
    REPLACE,
    UNION,
    INTERSECT,
    XOR,
    A_MINUS_B,
    B_MINUS_A
  }

  private record TableChoice(String id, String name) {
    @Override
    public String toString() {
      return name.equals(id) ? name : name + " (" + id + ")";
    }
  }

  private static final class TblStringSearchProvider extends ComponentProviderAdapter
      implements DockingContextListener, NavigatableRemovalListener, DomainObjectClosedListener {
    private static final int MAX_HISTORY = 10;
    // ponytail: fixed caps avoid runaway ambiguous tables; make them options if real tables hit them.
    private static final int MAX_ENCODINGS = 4096;
    private static final int MAX_RESULTS = 50_000;

    private final PluginTool tool;
    private final TblRegistry registry;
    private final Program program;
    private final Navigatable navigatable;
    private final List<String> searchHistory = new ArrayList<>();
    private final JPanel mainComponent = new JPanel(new BorderLayout());
    private final JPanel controlPanel = new JPanel(new VerticalLayout(0));
    private final SearchPanel searchPanel;
    private final OptionsPanel optionsPanel;
    private final TblStringSearchTableModel tableModel;
    private final GhidraThreadedTablePanel<TblStringSearchResult> threadedTablePanel;
    private final GhidraTableFilterPanel<TblStringSearchResult> filterPanel;
    private final GhidraTable table;
    private ToggleDockingAction toggleSearchPanelAction;
    private ToggleDockingAction toggleOptionsPanelAction;
    private DockingAction previousAction;
    private DockingAction nextAction;
    private DockingAction refreshAction;
    private boolean busy;
    private boolean hasUserChanges;
    private Address lastMatchingAddress;

    TblStringSearchProvider(
        PluginTool tool,
        String owner,
        TblRegistry registry,
        Program program,
        Navigatable navigatable) {
      super(tool, "TableString Search", owner);

      this.tool = Objects.requireNonNull(tool, "tool");
      this.registry = Objects.requireNonNull(registry, "registry");
      this.program = Objects.requireNonNull(program, "program");
      this.navigatable = Objects.requireNonNull(navigatable, "navigatable");
      this.tableModel = new TblStringSearchTableModel(tool, program, "TableString Search Results");
      this.threadedTablePanel = new GhidraThreadedTablePanel<>(tableModel);
      this.table = threadedTablePanel.getTable();
      this.filterPanel = new GhidraTableFilterPanel<>(table, tableModel);
      this.searchPanel = new SearchPanel();
      this.optionsPanel = new OptionsPanel();

      buildComponent();
      createActions(owner);
      setTransient();
      addToTool();
      setVisible(true);
      setDefaultFocusComponent(searchPanel.searchInput.getTextField());
      updateTitle();
      updateActionState();

      tool.addContextListener(this);
      navigatable.addNavigatableListener(this);
      program.addCloseListener(this);
    }

    @Override
    public JComponent getComponent() {
      return mainComponent;
    }

    @Override
    public ActionContext getActionContext(MouseEvent event) {
      ActionContext context = new NavigatableActionContext(this, navigatable);
      context.setSourceComponent(table);
      return context;
    }

    @Override
    public void contextChanged(ActionContext context) {
      searchPanel.updateSelectionOnlyEnabled();
    }

    @Override
    public void navigatableRemoved(Navigatable removed) {
      doClose(true);
    }

    @Override
    public void domainObjectClosed(DomainObject closed) {
      doClose(true);
    }

    @Override
    public void closeComponent() {
      doClose(false);
    }

    @Override
    public void removeFromTool() {
      disposeProvider();
      super.removeFromTool();
    }

    private void buildComponent() {
      mainComponent.setPreferredSize(new Dimension(900, 650));
      mainComponent.add(buildCenterPanel(), BorderLayout.CENTER);

      table.setActionsEnabled(true);
      table.installNavigation(tool, navigatable);
      tableModel.addTableModelListener(event -> updateSubTitle());
      table.getSelectionModel()
          .addListSelectionListener(
              event -> {
                if (!event.getValueIsAdjusting()) {
                  TblStringSearchResult result = selectedResult();
                  if (result != null) {
                    lastMatchingAddress = result.address();
                  }
                  tool.contextChanged(this);
                }
              });
    }

    private JComponent buildCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout());
      controlPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
      controlPanel.add(searchPanel);
      panel.add(controlPanel, BorderLayout.NORTH);
      panel.add(threadedTablePanel, BorderLayout.CENTER);
      panel.add(filterPanel, BorderLayout.SOUTH);
      return panel;
    }

    private void createActions(String owner) {
      nextAction =
          new ActionBuilder("TableString Search Next", owner)
              .toolBarIcon(Icons.DOWN_ICON)
              .toolBarGroup("A")
              .description("Search forward for one TableString result")
              .enabledWhen(context -> canSearch())
              .onAction(context -> searchOnce(true))
              .buildAndInstallLocal(this);

      previousAction =
          new ActionBuilder("TableString Search Previous", owner)
              .toolBarIcon(Icons.UP_ICON)
              .toolBarGroup("A")
              .description("Search backward for one TableString result")
              .enabledWhen(context -> canSearch())
              .onAction(context -> searchOnce(false))
              .buildAndInstallLocal(this);

      refreshAction =
          new ActionBuilder("Refresh TableString Results", owner)
              .toolBarIcon(Icons.REFRESH_ICON)
              .toolBarGroup("A")
              .description("Reload bytes for each TableString search result")
              .enabledWhen(context -> !busy && tableModel.getRowCount() > 0)
              .onAction(context -> refreshResults())
              .buildAndInstallLocal(this);

      toggleSearchPanelAction =
          new ToggleActionBuilder("Show TableString Search Controls", owner)
              .toolBarIcon(MENU_ICON)
              .toolBarGroup("Z")
              .description("Toggles showing the search controls")
              .selected(true)
              .onAction(context -> updateControlPanel())
              .buildAndInstallLocal(this);

      toggleOptionsPanelAction =
          new ToggleActionBuilder("Show TableString Search Options", owner)
              .toolBarIcon(Icons.CONFIGURE_FILTER_ICON)
              .toolBarGroup("Z")
              .description("Toggles showing the search options panel")
              .onAction(context -> toggleShowOptions())
              .buildAndInstallLocal(this);

      addLocalAction(new MakeProgramSelectionAction(navigatable, owner, table));
      addLocalAction(new SelectionNavigationAction(owner, table));
      addLocalAction(
          new DeleteTableRowAction(table, owner, "Remove Selected TableString Results") {
            @Override
            public void actionPerformed(ActionContext context) {
              super.actionPerformed(context);
              hasUserChanges = true;
              updateSubTitle();
            }
          });
    }

    private void updateControlPanel() {
      controlPanel.removeAll();
      if (toggleSearchPanelAction.isSelected()) {
        controlPanel.add(searchPanel);
      }
      controlPanel.revalidate();
      controlPanel.repaint();
    }

    private void toggleShowOptions() {
      if (toggleOptionsPanelAction.isSelected()) {
        mainComponent.add(optionsPanel, BorderLayout.EAST);
      } else {
        mainComponent.remove(optionsPanel);
      }
      mainComponent.revalidate();
      mainComponent.repaint();
    }

    private boolean canSearch() {
      return !busy && searchPanel != null && searchPanel.hasSearchParameters();
    }

    private void searchAll(ResultCombiner combiner) {
      SearchRequest request = createSearchRequest();
      if (request == null) {
        return;
      }
      addHistory(request.input());
      updateTitle();
      setBusy(true);
      tool.execute(
          new SearchTask(
              request,
              (results, cancelled) -> {
                if (cancelled) {
                  setBusy(false);
                  return;
                }
                applySearchResults(results, combiner);
              }));
    }

    private void searchOnce(boolean forward) {
      SearchRequest request = createSearchRequest();
      if (request == null) {
        return;
      }
      addHistory(request.input());
      updateTitle();
      setBusy(true);
      Address start = getSearchStartAddress(forward);
      tool.execute(
          new SearchTask(
              request,
              (results, cancelled) -> {
                if (cancelled) {
                  setBusy(false);
                  return;
                }
                TblStringSearchResult result = chooseSearchOnceResult(results, start, forward);
                if (result == null) {
                  setBusy(false);
                  Msg.showInfo(this, table, "TableString Search", "No match found.");
                  return;
                }
                applySearchResults(List.of(result), ResultCombiner.UNION);
                selectResult(result);
                navigatable.goTo(program, new BytesFieldLocation(program, result.address()));
              }));
    }

    private SearchRequest createSearchRequest() {
      String input = searchPanel.getSearchText();
      if (input.isBlank()) {
        Msg.showInfo(this, table, "TableString Search", "Enter text to search.");
        return null;
      }

      TableChoice choice = searchPanel.getSelectedTable();
      if (choice == null) {
        Msg.showInfo(this, table, "TableString Search", "Select a .tbl table.");
        return null;
      }

      List<byte[]> patterns;
      try {
        patterns = TblStringEncoder.encodeAll(input, registry.require(choice.id()), MAX_ENCODINGS);
      } catch (IllegalArgumentException e) {
        Msg.showInfo(this, table, "TableString Search", e.getMessage());
        return null;
      }

      if (patterns.isEmpty()) {
        Msg.showInfo(
            this,
            table,
            "TableString Search",
            "This text cannot be encoded with the selected table.");
        return null;
      }

      AddressSet addresses = getSearchAddresses();
      if (addresses.isEmpty()) {
        Msg.showInfo(this, table, "TableString Search", "Addresses to search are empty.");
        return null;
      }

      return new SearchRequest(program, input, choice.name(), patterns, addresses, optionsPanel.snapshot());
    }

    private AddressSet getSearchAddresses() {
      AddressSet addresses = new AddressSet();
      Set<String> selectedBlocks = optionsPanel.selectedBlockNames();
      for (MemoryBlock block : program.getMemory().getBlocks()) {
        if (block.isInitialized() && selectedBlocks.contains(block.getName())) {
          addresses.add(block.getStart(), block.getEnd());
        }
      }

      if (searchPanel.isSelectionOnly()) {
        ProgramSelection selection = navigatable.getSelection();
        if (selection == null || selection.isEmpty()) {
          return new AddressSet();
        }
        addresses = addresses.intersect(selection);
      }
      return addresses;
    }

    private Address getSearchStartAddress(boolean forward) {
      ProgramLocation location = navigatable.getLocation();
      Address startAddress = location == null ? null : location.getByteAddress();
      if (startAddress == null) {
        return forward ? program.getMinAddress() : program.getMaxAddress();
      }
      if (lastMatchingAddress == null) {
        return startAddress;
      }

      CodeUnit codeUnit = program.getListing().getCodeUnitContaining(startAddress);
      if (codeUnit != null && codeUnit.contains(lastMatchingAddress)) {
        Address next = forward ? lastMatchingAddress.next() : lastMatchingAddress.previous();
        return next == null ? startAddress : next;
      }
      return startAddress;
    }

    private TblStringSearchResult chooseSearchOnceResult(
        List<TblStringSearchResult> results, Address start, boolean forward) {
      TblStringSearchResult best = null;
      for (TblStringSearchResult result : results) {
        int comparison = result.address().compareTo(start);
        if ((forward && comparison < 0) || (!forward && comparison > 0)) {
          continue;
        }
        if (best == null) {
          best = result;
          continue;
        }
        int bestComparison = result.address().compareTo(best.address());
        if ((forward && bestComparison < 0) || (!forward && bestComparison > 0)) {
          best = result;
        }
      }
      return best;
    }

    private void applySearchResults(List<TblStringSearchResult> newResults, ResultCombiner combiner) {
      List<TblStringSearchResult> previous = tableModel.getModelData();
      List<TblStringSearchResult> combined = combine(previous, newResults, combiner);
      hasUserChanges = combiner != ResultCombiner.REPLACE && !previous.isEmpty();
      tableModel.setResults(combined);
      setBusy(false);
      if (combined.isEmpty()) {
        Msg.showInfo(this, table, "TableString Search", "No matches found.");
      }
    }

    private List<TblStringSearchResult> combine(
        List<TblStringSearchResult> previous,
        List<TblStringSearchResult> next,
        ResultCombiner combiner) {
      return switch (combiner) {
        case REPLACE -> next;
        case UNION -> union(previous, next);
        case INTERSECT -> intersect(previous, next);
        case XOR -> union(subtract(previous, next), subtract(next, previous));
        case A_MINUS_B -> subtract(previous, next);
        case B_MINUS_A -> subtract(next, previous);
      };
    }

    private List<TblStringSearchResult> union(
        List<TblStringSearchResult> first, List<TblStringSearchResult> second) {
      List<TblStringSearchResult> results = new ArrayList<>(first);
      Set<String> seen = keys(first);
      for (TblStringSearchResult result : second) {
        if (seen.add(result.key())) {
          results.add(result);
        }
      }
      return results;
    }

    private List<TblStringSearchResult> intersect(
        List<TblStringSearchResult> first, List<TblStringSearchResult> second) {
      Set<String> secondKeys = keys(second);
      List<TblStringSearchResult> results = new ArrayList<>();
      for (TblStringSearchResult result : first) {
        if (secondKeys.contains(result.key())) {
          results.add(result);
        }
      }
      return results;
    }

    private List<TblStringSearchResult> subtract(
        List<TblStringSearchResult> first, List<TblStringSearchResult> second) {
      Set<String> secondKeys = keys(second);
      List<TblStringSearchResult> results = new ArrayList<>();
      for (TblStringSearchResult result : first) {
        if (!secondKeys.contains(result.key())) {
          results.add(result);
        }
      }
      return results;
    }

    private Set<String> keys(List<TblStringSearchResult> results) {
      Set<String> keys = new HashSet<>();
      for (TblStringSearchResult result : results) {
        keys.add(result.key());
      }
      return keys;
    }

    private void refreshResults() {
      List<TblStringSearchResult> current = tableModel.getModelData();
      if (current.isEmpty()) {
        return;
      }
      setBusy(true);
      tool.execute(
          new RefreshTask(
              program,
              current,
              (refreshed, cancelled) -> {
                if (cancelled) {
                  setBusy(false);
                  return;
                }
                tableModel.setResults(refreshed);
                setBusy(false);
              }));
    }

    private void selectResult(TblStringSearchResult result) {
      int row = tableModel.getRowIndex(result);
      if (row >= 0) {
        table.selectRow(row);
        table.scrollToSelectedRow();
      }
    }

    private TblStringSearchResult selectedResult() {
      int row = table.getSelectedRow();
      return row < 0 ? null : tableModel.getRowObject(row);
    }

    private void addHistory(String input) {
      searchHistory.remove(input);
      searchHistory.add(0, input);
      while (searchHistory.size() > MAX_HISTORY) {
        searchHistory.remove(searchHistory.size() - 1);
      }
      searchPanel.updateHistory();
    }

    private void setBusy(boolean busy) {
      this.busy = busy;
      searchPanel.updateSearchButton();
      updateActionState();
    }

    private void updateActionState() {
      if (nextAction != null) {
        nextAction.setEnabled(canSearch());
      }
      if (previousAction != null) {
        previousAction.setEnabled(canSearch());
      }
      if (refreshAction != null) {
        refreshAction.setEnabled(!busy && tableModel.getRowCount() > 0);
      }
      tool.contextChanged(this);
    }

    private void updateTitle() {
      String input = searchPanel.getSearchText();
      String title = input.isBlank() ? "Search TableString" : "Search TableString: \"" + input + "\"";
      setTitle(title + "  (" + program.getDomainFile().getName() + ")");
    }

    private void updateSubTitle() {
      int count = tableModel.getRowCount();
      setSubTitle(count == 0 ? "" : " (" + count + (count == 1 ? " entry)" : " entries)"));
      searchPanel.updateSearchButton();
      updateActionState();
    }

    private void doClose(boolean force) {
      if (!force && hasUserChanges && tableModel.getRowCount() > 0) {
        int choice =
            OptionDialog.showYesNoDialog(
                mainComponent,
                "Close Results Window?",
                "Close dialog and lose custom search results?");
        if (choice != OptionDialog.YES_OPTION) {
          return;
        }
      }
      super.closeComponent();
    }

    private void disposeProvider() {
      filterPanel.dispose();
      table.dispose();
      tool.removeContextListener(this);
      navigatable.removeNavigatableListener(this);
      program.removeCloseListener(this);
    }

    /** Input panel that owns the search text, table choice, byte preview, and result combiner. */
    private final class SearchPanel extends JPanel {
      private final GhidraComboBox<String> searchInput = new GhidraComboBox<>();
      private final GComboBox<TableChoice> tableCombo = new GComboBox<>();
      private final GDLabel byteSequenceField = new GDLabel();
      private final MultiStateButton<ResultCombiner> searchButton =
          new MultiStateButton<>(List.of(new ButtonState<>("Search", "", ResultCombiner.REPLACE)));
      private final GCheckBox selectionOnlyCheckbox = new GCheckBox("Selection Only");
      private boolean updatingHistory;

      SearchPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        add(buildInputPanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.EAST);
        loadTables();
        updateSearchButton();
      }

      private JComponent buildInputPanel() {
        searchInput.setEditable(true);
        searchInput.setAutoCompleteEnabled(false);
        searchInput.addActionListener(
            event -> {
              if (!updatingHistory) {
                searchAll(ResultCombiner.REPLACE);
              }
            });
        searchInput.addDocumentListener(new ChangeDocumentListener(this::inputChanged));
        tableCombo.addActionListener(event -> inputChanged());

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(searchInput, BorderLayout.CENTER);
        inputPanel.add(tableCombo, BorderLayout.EAST);

        byteSequenceField.setName("TableString Byte Sequence Field");
        byteSequenceField.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createLoweredBevelBorder(),
                BorderFactory.createEmptyBorder(0, 4, 0, 4)));

        JPanel bytesPanel = new JPanel(new BorderLayout());
        bytesPanel.add(byteSequenceField, BorderLayout.CENTER);
        bytesPanel.add(Box.createHorizontalStrut(tableCombo.getPreferredSize().width), BorderLayout.EAST);

        JPanel panel = new JPanel(new PairLayout(2, 10));
        panel.add(new GLabel("Search Text:"));
        panel.add(inputPanel);
        panel.add(new GLabel("Byte Sequence:"));
        panel.add(bytesPanel);
        return panel;
      }

      private JComponent buildButtonPanel() {
        JPanel panel = new JPanel(new VerticalLayout(5));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        searchButton.setMnemonic('S');
        searchButton.addActionListener(event -> searchAll(selectedCombiner()));
        panel.add(searchButton);

        selectionOnlyCheckbox.setMnemonic('O');
        selectionOnlyCheckbox.setToolTipText("If selected, search will be restricted to selected addresses");
        selectionOnlyCheckbox.addActionListener(event -> updateSearchButton());
        panel.add(selectionOnlyCheckbox);
        updateSelectionOnlyEnabled();
        return panel;
      }

      private ResultCombiner selectedCombiner() {
        String text = searchButton.getText();
        for (ButtonState<ResultCombiner> state : buttonStates()) {
          if (state.getButtonText().equals(text)) {
            return state.getClientData();
          }
        }
        return ResultCombiner.REPLACE;
      }

      private List<ButtonState<ResultCombiner>> buttonStates() {
        if (tableModel.getRowCount() == 0) {
          return List.of(new ButtonState<>("Search", "", ResultCombiner.REPLACE));
        }
        return List.of(
            new ButtonState<>("New Search", "New Search", "Replace current results", ResultCombiner.REPLACE),
            new ButtonState<>("Add To Search", "A union B", "Add new results", ResultCombiner.UNION),
            new ButtonState<>("Intersect Search", "A intersect B", "Keep results in both searches", ResultCombiner.INTERSECT),
            new ButtonState<>("Xor Search", "A xor B", "Keep results unique to either search", ResultCombiner.XOR),
            new ButtonState<>("A-B Search", "A - B", "Subtract new results from current results", ResultCombiner.A_MINUS_B),
            new ButtonState<>("B-A Search", "B - A", "Subtract current results from new results", ResultCombiner.B_MINUS_A));
      }

      private void inputChanged() {
        updateByteSequencePreview();
        updateSearchButton();
      }

      private void updateByteSequencePreview() {
        String input = getSearchText();
        TableChoice table = getSelectedTable();
        if (input.isBlank() || table == null) {
          byteSequenceField.setText("");
          return;
        }
        try {
          List<byte[]> encodings = TblStringEncoder.encodeAll(input, registry.require(table.id()), MAX_ENCODINGS);
          byteSequenceField.setText(formatPreview(encodings));
        } catch (IllegalArgumentException e) {
          byteSequenceField.setText(e.getMessage());
        }
      }

      private String formatPreview(List<byte[]> encodings) {
        if (encodings.isEmpty()) {
          return "<not encodable>";
        }
        int limit = Math.min(encodings.size(), 3);
        List<String> hex = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
          hex.add(TblHex.toHex(encodings.get(i)));
        }
        String suffix = encodings.size() > limit ? " +" + (encodings.size() - limit) + " more" : "";
        return String.join(" | ", hex) + suffix;
      }

      private void loadTables() {
        tableCombo.removeAllItems();
        for (String id : registry.ids()) {
          TblTable table = registry.require(id);
          tableCombo.addItem(new TableChoice(id, table.getName()));
        }
        registry.getDefaultTableId().ifPresent(this::selectTable);
      }

      private void selectTable(String tableId) {
        for (int i = 0; i < tableCombo.getItemCount(); i++) {
          TableChoice choice = tableCombo.getItemAt(i);
          if (choice.id().equals(tableId)) {
            tableCombo.setSelectedIndex(i);
            return;
          }
        }
      }

      private void updateHistory() {
        String current = getSearchText();
        updatingHistory = true;
        try {
          searchInput.setModel(new DefaultComboBoxModel<>(searchHistory.toArray(String[]::new)));
          searchInput.setText(current);
        } finally {
          updatingHistory = false;
        }
      }

      private String getSearchText() {
        return searchInput.getText();
      }

      private TableChoice getSelectedTable() {
        return (TableChoice) tableCombo.getSelectedItem();
      }

      private boolean isSelectionOnly() {
        return selectionOnlyCheckbox.isSelected();
      }

      private boolean hasSearchParameters() {
        return !getSearchText().isBlank() && getSelectedTable() != null;
      }

      private void updateSelectionOnlyEnabled() {
        ProgramSelection selection = navigatable.getSelection();
        selectionOnlyCheckbox.setEnabled(selection != null && !selection.isEmpty());
      }

      private void updateSearchButton() {
        searchButton.setButtonStates(buttonStates());
        searchButton.setEnabled(!busy && hasSearchParameters());
      }
    }

    /** Optional filters applied after byte matching finds a candidate address. */
    private final class OptionsPanel extends JPanel {
      private final JTextField alignmentField = new JTextField("1", 5);
      private final GCheckBox includeInstructions = selectedCheckbox("Instructions");
      private final GCheckBox includeDefinedData = selectedCheckbox("Defined Data");
      private final GCheckBox includeUndefinedData = selectedCheckbox("Undefined Data");
      private final List<BlockChoice> blockChoices = new ArrayList<>();

      OptionsPanel() {
        super(new BorderLayout());
        JPanel panel = new JPanel(new VerticalLayout(12));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));
        panel.add(buildByteOptions());
        panel.add(buildCodeUnitOptions());
        panel.add(buildMemoryBlockOptions());

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scroll, BorderLayout.CENTER);
      }

      private GCheckBox selectedCheckbox(String text) {
        GCheckBox checkbox = new GCheckBox(text);
        checkbox.setSelected(true);
        return checkbox;
      }

      private JComponent buildByteOptions() {
        JPanel panel = new JPanel(new PairLayout(3, 2));
        panel.setBorder(BorderFactory.createTitledBorder("Byte Options"));
        panel.add(new GLabel("Alignment:"));
        panel.add(alignmentField);
        return panel;
      }

      private JComponent buildCodeUnitOptions() {
        JPanel panel = new JPanel(new VerticalLayout(5));
        panel.setBorder(BorderFactory.createTitledBorder("Code Type Filter"));
        panel.add(includeInstructions);
        panel.add(includeDefinedData);
        panel.add(includeUndefinedData);
        return panel;
      }

      private JComponent buildMemoryBlockOptions() {
        JPanel panel = new JPanel(new VerticalLayout(3));
        panel.setBorder(BorderFactory.createTitledBorder("Search Region Filter"));
        for (MemoryBlock block : program.getMemory().getBlocks()) {
          if (!block.isInitialized()) {
            continue;
          }
          GCheckBox checkbox = selectedCheckbox(block.getName());
          checkbox.setToolTipText(block.getStart() + " - " + block.getEnd());
          blockChoices.add(new BlockChoice(block.getName(), checkbox));
          panel.add(checkbox);
        }
        return panel;
      }

      private SearchOptions snapshot() {
        return new SearchOptions(
            alignment(),
            includeInstructions.isSelected(),
            includeDefinedData.isSelected(),
            includeUndefinedData.isSelected());
      }

      private int alignment() {
        try {
          return Math.max(1, Integer.parseInt(alignmentField.getText().trim()));
        } catch (NumberFormatException e) {
          return 1;
        }
      }

      private Set<String> selectedBlockNames() {
        Set<String> names = new LinkedHashSet<>();
        for (BlockChoice choice : blockChoices) {
          if (choice.checkbox().isSelected()) {
            names.add(choice.name());
          }
        }
        return names;
      }
    }
  }

  private record BlockChoice(String name, GCheckBox checkbox) {}

  /** Immutable search filters captured when the search starts. */
  private record SearchOptions(
      int alignment,
      boolean includeInstructions,
      boolean includeDefinedData,
      boolean includeUndefinedData) {}

  /** Immutable request passed from the UI provider to the background search task. */
  private record SearchRequest(
      Program program,
      String input,
      String tableName,
      List<byte[]> patterns,
      AddressSetView addresses,
      SearchOptions options) {
    SearchRequest {
      patterns = patterns.stream().map(byte[]::clone).toList();
    }
  }

  /** Callback used by background search tasks to report completion on the Swing thread. */
  private interface SearchCompletion {
    void complete(List<TblStringSearchResult> results, boolean cancelled);
  }

  /** Background task that searches initialized memory ranges for every encoded byte pattern. */
  private static final class SearchTask extends Task {
    private final SearchRequest request;
    private final SearchCompletion completion;

    SearchTask(SearchRequest request, SearchCompletion completion) {
      super("TableString Search", true, true, true);
      this.request = Objects.requireNonNull(request, "request");
      this.completion = Objects.requireNonNull(completion, "completion");
    }

    @Override
    public void run(TaskMonitor monitor) throws CancelledException {
      try {
        monitor.initialize(TblStringSearchProvider.MAX_RESULTS, "Searching TableString patterns...");
        List<TblStringSearchResult> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Memory memory = request.program().getMemory();

        for (byte[] pattern : request.patterns()) {
          monitor.checkCancelled();
          for (AddressRange range : request.addresses()) {
            monitor.checkCancelled();
            searchRange(memory, range, pattern, results, seen, monitor);
            if (results.size() >= TblStringSearchProvider.MAX_RESULTS) {
              Swing.runLater(() -> completion.complete(results, false));
              return;
            }
          }
        }

        Swing.runLater(() -> completion.complete(results, false));
      } catch (CancelledException e) {
        Swing.runLater(() -> completion.complete(List.of(), true));
        throw e;
      }
    }

    private void searchRange(
        Memory memory,
        AddressRange range,
        byte[] pattern,
        List<TblStringSearchResult> results,
        Set<String> seen,
        TaskMonitor monitor)
        throws CancelledException {
      Address start = range.getMinAddress();
      Address end = range.getMaxAddress();
      while (start != null && start.compareTo(end) <= 0) {
        monitor.checkCancelled();
        Address match = memory.findBytes(start, end, pattern, null, true, monitor);
        if (match == null) {
          return;
        }
        if (accept(match)) {
          TblStringSearchResult result =
              new TblStringSearchResult(
                  match, request.tableName(), request.input(), pattern, pattern);
          if (seen.add(result.key())) {
            results.add(result);
            monitor.incrementProgress();
          }
        }
        start = nextAddress(match);
      }
    }

    private boolean accept(Address address) {
      SearchOptions options = request.options();
      if (options.alignment() > 1 && address.getOffset() % options.alignment() != 0) {
        return false;
      }

      CodeUnit codeUnit = request.program().getListing().getCodeUnitContaining(address);
      if (codeUnit instanceof Instruction) {
        return options.includeInstructions();
      }
      if (codeUnit instanceof Data data && data.isDefined()) {
        return options.includeDefinedData();
      }
      return options.includeUndefinedData();
    }

    private Address nextAddress(Address address) {
      try {
        return address.addNoWrap(1);
      } catch (AddressOverflowException e) {
        return null;
      }
    }
  }

  /** Callback used by refresh tasks to report completion on the Swing thread. */
  private interface RefreshCompletion {
    void complete(List<TblStringSearchResult> results, boolean cancelled);
  }

  /** Background task that reloads current bytes for existing result rows. */
  private static final class RefreshTask extends Task {
    private final Program program;
    private final List<TblStringSearchResult> results;
    private final RefreshCompletion completion;

    RefreshTask(
        Program program, List<TblStringSearchResult> results, RefreshCompletion completion) {
      super("Refresh TableString Results", true, true, true);
      this.program = Objects.requireNonNull(program, "program");
      this.results = List.copyOf(results);
      this.completion = Objects.requireNonNull(completion, "completion");
    }

    @Override
    public void run(TaskMonitor monitor) throws CancelledException {
      try {
        monitor.initialize(results.size(), "Refreshing TableString results...");
        List<TblStringSearchResult> refreshed = new ArrayList<>();
        for (TblStringSearchResult result : results) {
          monitor.checkCancelled();
          refreshed.add(refresh(result));
          monitor.incrementProgress();
        }
        Swing.runLater(() -> completion.complete(refreshed, false));
      } catch (CancelledException e) {
        Swing.runLater(() -> completion.complete(List.of(), true));
        throw e;
      }
    }

    private TblStringSearchResult refresh(TblStringSearchResult result) {
      byte[] bytes = new byte[result.length()];
      try {
        program.getMemory().getBytes(result.address(), bytes);
      } catch (MemoryAccessException e) {
        bytes = result.currentBytes();
      }
      return new TblStringSearchResult(
          result.address(), result.tableName(), result.input(), result.patternBytes(), bytes);
    }
  }

  /** One row in the TableString search result table. */
  private record TblStringSearchResult(
      Address address, String tableName, String input, byte[] patternBytes, byte[] currentBytes) {
    TblStringSearchResult {
      Objects.requireNonNull(address, "address");
      Objects.requireNonNull(tableName, "tableName");
      Objects.requireNonNull(input, "input");
      patternBytes = Objects.requireNonNull(patternBytes, "patternBytes").clone();
      currentBytes = Objects.requireNonNull(currentBytes, "currentBytes").clone();
    }

    @Override
    public byte[] patternBytes() {
      return patternBytes.clone();
    }

    @Override
    public byte[] currentBytes() {
      return currentBytes.clone();
    }

    int length() {
      return patternBytes.length;
    }

    String patternHex() {
      return TblHex.toHex(patternBytes);
    }

    String currentHex() {
      return TblHex.toHex(currentBytes);
    }

    String key() {
      return address + ":" + patternHex();
    }
  }

  /** Ghidra table model that exposes result rows as navigable program addresses. */
  private static final class TblStringSearchTableModel
      extends GhidraProgramTableModel<TblStringSearchResult> {
    private List<TblStringSearchResult> results = List.of();

    TblStringSearchTableModel(PluginTool tool, Program program, String name) {
      super(name, tool, program, TaskMonitor.DUMMY);
    }

    void setResults(List<TblStringSearchResult> results) {
      this.results = List.copyOf(results);
      reload();
    }

    @Override
    protected void doLoad(Accumulator<TblStringSearchResult> accumulator, TaskMonitor monitor)
        throws CancelledException {
      accumulator.addAll(results);
    }

    @Override
    protected TableColumnDescriptor<TblStringSearchResult> createTableColumnDescriptor() {
      TableColumnDescriptor<TblStringSearchResult> descriptor = new TableColumnDescriptor<>();
      descriptor.addVisibleColumn("Address", Address.class, TblStringSearchResult::address);
      descriptor.addVisibleColumn("Bytes", String.class, TblStringSearchResult::patternHex);
      descriptor.addVisibleColumn("Current Bytes", String.class, TblStringSearchResult::currentHex);
      descriptor.addVisibleColumn("Length", Integer.class, TblStringSearchResult::length);
      descriptor.addVisibleColumn("Table", String.class, TblStringSearchResult::tableName);
      descriptor.addVisibleColumn("Text", String.class, TblStringSearchResult::input);
      return descriptor;
    }

    @Override
    public Address getAddress(int row, int column) {
      return getAddress(row);
    }

    @Override
    public Address getAddress(int row) {
      return getRowObject(row).address();
    }

    @Override
    public ProgramLocation getProgramLocation(int row, int column) {
      return new BytesFieldLocation(program, getAddress(row));
    }
  }

  /** Small adapter that turns Swing document callbacks into one change callback. */
  private static final class ChangeDocumentListener implements javax.swing.event.DocumentListener {
    private final Runnable changeCallback;

    ChangeDocumentListener(Runnable changeCallback) {
      this.changeCallback = Objects.requireNonNull(changeCallback, "changeCallback");
    }

    @Override
    public void insertUpdate(javax.swing.event.DocumentEvent event) {
      changeCallback.run();
    }

    @Override
    public void removeUpdate(javax.swing.event.DocumentEvent event) {
      changeCallback.run();
    }

    @Override
    public void changedUpdate(javax.swing.event.DocumentEvent event) {
      changeCallback.run();
    }
  }
}
