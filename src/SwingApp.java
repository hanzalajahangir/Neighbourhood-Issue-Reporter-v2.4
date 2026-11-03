import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class SwingApp extends JFrame {
    private static final Color PAGE_BACKGROUND = new Color(245, 248, 245);
    private static final Color CARD_BACKGROUND = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(30, 41, 34);
    private static final Color TEXT_MUTED = new Color(94, 106, 97);
    private static final Color PRIMARY = new Color(22, 101, 52);
    private static final Color PRIMARY_DARK = new Color(15, 61, 36);
    private static final Color PRIMARY_HOVER = new Color(28, 119, 62);
    private static final Color SOFT_GREEN = new Color(232, 242, 234);
    private static final Color BORDER = new Color(213, 224, 215);
    private static final Color ROW_STRIPE = new Color(249, 251, 249);
    private static final Color SELECTION = new Color(216, 235, 221);
    private static final Color DANGER = new Color(145, 45, 45);
    private static final Color ON_DARK_MUTED = new Color(198, 219, 204);
    private static final Font BODY_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font TABLE_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FIELD_LABEL_FONT = new Font("Segoe UI", Font.BOLD, 12);
    private static final Dimension AUTH_FIELD_SIZE = new Dimension(320, 34);
    private static final DateTimeFormatter ISSUE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final DataStore store;
    private final IssueManager issueManager;
    private final UserManager userManager;
    private final IssueFilter issueFilter = new IssueFilter();

    private User currentUser;
    private JTable issueTable;
    private DefaultTableModel issueTableModel;
    private JLabel titleLabel;
    private JLabel statsLabel;
    private JTextField searchField;
    private JComboBox<String> statusFilterBox;
    private JComboBox<String> categoryFilterBox;
    private JComboBox<String> priorityFilterBox;
    private JComboBox<String> departmentFilterBox;
    private JComboBox<String> timeFilterBox;
    private boolean updatingFilterControls;

    public SwingApp(DataStore store) {
        if (store == null) {
            throw new IllegalArgumentException("DataStore is required.");
        }
        configureTheme();
        this.store = store;
        this.userManager = new UserManager(store);
        this.issueManager = new IssueManager(store);

        setTitle("Neighbourhood Issue Reporter - Civic Services");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 620);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(820, 520));
        setIconImage(createSealImage(64));
        showAuthPanel();
    }

    /**
     * Shows any problems recorded while loading data (for example, a corrupt
     * file that was backed up). Called once after the window becomes visible.
     */
    public void showStartupWarnings() {
        List<String> warnings = store.getWarnings();
        if (warnings.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder("Some saved data could not be loaded:\n\n");
        for (String warning : warnings) {
            message.append("- ").append(warning).append("\n\n");
        }
        JOptionPane.showMessageDialog(
                this,
                message.toString().trim(),
                "Data Warning",
                JOptionPane.WARNING_MESSAGE);
    }

    private void showAuthPanel() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(PAGE_BACKGROUND);
        root.add(createMasthead(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setUI(new CivicTabbedPaneUI());
        tabs.setFont(BODY_FONT.deriveFont(Font.BOLD, 13));
        tabs.setBackground(CARD_BACKGROUND);
        tabs.setForeground(PRIMARY_DARK);
        tabs.setBorder(BorderFactory.createLineBorder(BORDER));
        tabs.addTab("Login", createLoginPanel());
        tabs.addTab("Register", createRegisterPanel());
        tabs.setPreferredSize(new Dimension(560, 360));

        JPanel body = new JPanel(new GridBagLayout());
        body.setBackground(PAGE_BACKGROUND);
        body.setBorder(new EmptyBorder(14, 24, 18, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        body.add(tabs, gbc);

        root.add(body, BorderLayout.CENTER);
        setContentPane(root);
        revalidate();
        repaint();
    }

    private JPanel createLoginPanel() {
        JPanel panel = createFormPanel("Citizen sign-in", "Sign in to manage neighbourhood reports.");
        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JButton loginButton = new CivicButton("Login", CivicButton.FILLED);

        addAuthField(panel, "Username", usernameField, 2);
        addAuthField(panel, "Password", passwordField, 3);
        addAuthButton(panel, loginButton, 4);

        getRootPane().setDefaultButton(loginButton);
        loginButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            currentUser = userManager.login(username, password);

            if (currentUser == null) {
                showError("Invalid username or password.");
                return;
            }
            showDashboard();
            promptForDefaultAdminPassword();
        });
        return panel;
    }

    private JPanel createRegisterPanel() {
        JPanel panel = createFormPanel("Citizen registration", "Register as a citizen to report local issues.");
        JTextField nameField = new JTextField();
        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JButton registerButton = new CivicButton("Register", CivicButton.FILLED);

        addAuthField(panel, "Full name", nameField, 2);
        addAuthField(panel, "Username", usernameField, 3);
        addAuthField(panel, "Password", passwordField, 4);
        addAuthButton(panel, registerButton, 5);

        registerButton.addActionListener(e -> {
            String name = nameField.getText().trim();
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());

            String error = userManager.validateRegistration(username, password, name);
            if (error != null) {
                showError(error);
                return;
            }
            try {
                if (!userManager.register(username, password, name)) {
                    showError("Registration failed. Please review your details.");
                    return;
                }
            } catch (DataStoreException ex) {
                showSaveError(ex);
                return;
            }

            nameField.setText("");
            usernameField.setText("");
            passwordField.setText("");
            JOptionPane.showMessageDialog(this, "Registered successfully. You can now log in.");
        });
        return panel;
    }

    private JPanel createFormPanel(String heading, String subheading) {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(CARD_BACKGROUND);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(CARD_BACKGROUND);
        form.setBorder(new EmptyBorder(18, 32, 24, 32));

        JLabel headingLabel = new JLabel(heading);
        headingLabel.setFont(new Font("Georgia", Font.BOLD, 22));
        headingLabel.setForeground(PRIMARY_DARK);
        headingLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel subheadingLabel = new JLabel(subheading);
        subheadingLabel.setFont(BODY_FONT);
        subheadingLabel.setForeground(TEXT_MUTED);
        subheadingLabel.setHorizontalAlignment(SwingConstants.CENTER);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 4, 0);
        form.add(headingLabel, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 16, 0);
        form.add(subheadingLabel, gbc);

        wrapper.putClientProperty("formPanel", form);
        wrapper.add(form);
        return wrapper;
    }

    private void addAuthField(JPanel panel, String label, JComponent field, int row) {
        JPanel form = getAuthForm(panel);
        JLabel jLabel = new JLabel(label);
        jLabel.setFont(FIELD_LABEL_FONT);
        jLabel.setForeground(TEXT_PRIMARY);

        styleInput(field);
        field.setPreferredSize(AUTH_FIELD_SIZE);
        field.setMinimumSize(AUTH_FIELD_SIZE);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 7, 16);
        form.add(jLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.insets = new Insets(0, 0, 7, 0);
        form.add(field, gbc);
    }

    private void addAuthButton(JPanel panel, JButton button, int row) {
        JPanel form = getAuthForm(panel);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(12, 0, 0, 0);
        form.add(button, gbc);
    }

    private JPanel getAuthForm(JPanel panel) {
        Object form = panel.getClientProperty("formPanel");
        return form instanceof JPanel ? (JPanel) form : panel;
    }

    private void addField(JPanel panel, String label, JComponent field) {
        JLabel jLabel = new JLabel(label);
        jLabel.setFont(FIELD_LABEL_FONT);
        jLabel.setForeground(TEXT_PRIMARY);
        styleInput(field);
        panel.add(jLabel);
        panel.add(field);
    }

    private void showDashboard() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(PAGE_BACKGROUND);
        root.add(createDashboardMasthead(), BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(PAGE_BACKGROUND);
        content.setBorder(new EmptyBorder(12, 14, 12, 14));

        issueTableModel = new DefaultTableModel(
                new String[]{"ID", "Title", "Category", "Priority", "Status", "Department", "Location", "Reported By", "Date"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        issueTable = new JTable(issueTableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component component = super.prepareRenderer(renderer, row, column);
                if (component instanceof JLabel) {
                    JLabel label = (JLabel) component;
                    boolean selected = isRowSelected(row);
                    label.setBorder(new EmptyBorder(0, 8, 0, 8));
                    label.setFont(TABLE_FONT);
                    label.setForeground(TEXT_PRIMARY);
                    label.setBackground(selected
                            ? SELECTION
                            : (row % 2 == 0 ? CARD_BACKGROUND : ROW_STRIPE));
                    if (!selected && column == 3) {
                        label.setForeground(priorityColor(String.valueOf(getValueAt(row, column))));
                        label.setFont(TABLE_FONT.deriveFont(Font.BOLD));
                    } else if (!selected && column == 4) {
                        label.setForeground(statusColor(String.valueOf(getValueAt(row, column))));
                        label.setFont(TABLE_FONT.deriveFont(Font.BOLD));
                    }
                }
                return component;
            }

            @Override
            public String getToolTipText(java.awt.event.MouseEvent event) {
                int row = rowAtPoint(event.getPoint());
                int column = columnAtPoint(event.getPoint());
                if (row >= 0 && column >= 0) {
                    Object value = getValueAt(row, column);
                    return value == null ? null : value.toString();
                }
                return super.getToolTipText(event);
            }
        };
        issueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(issueTableModel);
        sorter.setComparator(8, (left, right) -> {
            LocalDateTime leftDate = parseIssueDate(left);
            LocalDateTime rightDate = parseIssueDate(right);
            if (leftDate != null && rightDate != null) {
                return leftDate.compareTo(rightDate);
            }
            return String.valueOf(left).compareTo(String.valueOf(right));
        });
        issueTable.setRowSorter(sorter);
        issueTable.setRowHeight(26);
        issueTable.setShowVerticalLines(false);
        issueTable.setShowHorizontalLines(true);
        issueTable.setGridColor(new Color(235, 241, 236));
        issueTable.setIntercellSpacing(new Dimension(0, 1));
        issueTable.setSelectionBackground(SELECTION);
        issueTable.setSelectionForeground(TEXT_PRIMARY);
        issueTable.setFillsViewportHeight(true);
        issueTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        int[] widths = {42, 160, 90, 74, 94, 112, 118, 98, 142};
        for (int i = 0; i < widths.length; i++) {
            issueTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        styleIssueTable(issueTable);

        JScrollPane scrollPane = new JScrollPane(issueTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
        scrollPane.getViewport().setBackground(CARD_BACKGROUND);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);

        content.add(createFilterBar(), BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(createActionPanel(), BorderLayout.SOUTH);
        root.add(content, BorderLayout.CENTER);

        setContentPane(root);
        getRootPane().setDefaultButton(null);
        installDashboardShortcuts(root);
        refreshDashboard();
        revalidate();
        repaint();
    }

    private void styleIssueTable(JTable table) {
        JTableHeader tableHeader = table.getTableHeader();
        tableHeader.setReorderingAllowed(false);
        tableHeader.setPreferredSize(new Dimension(0, 30));
        tableHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, PRIMARY_DARK));
        tableHeader.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus,
                    int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                label.setOpaque(true);
                label.setBackground(PRIMARY_DARK);
                label.setForeground(Color.WHITE);
                label.setFont(new Font("Segoe UI", Font.BOLD, 11));
                label.setBorder(new EmptyBorder(0, 8, 0, 8));
                label.setHorizontalAlignment(SwingConstants.LEFT);
                return label;
            }
        });
    }

    private static Color statusColor(String status) {
        if ("Resolved".equalsIgnoreCase(status)) {
            return new Color(21, 94, 51);
        }
        if ("In Progress".equalsIgnoreCase(status)) {
            return new Color(33, 82, 120);
        }
        return new Color(138, 85, 12);
    }

    private static Color priorityColor(String priority) {
        if ("Critical".equalsIgnoreCase(priority) || "High".equalsIgnoreCase(priority)) {
            return DANGER;
        }
        if ("Medium".equalsIgnoreCase(priority)) {
            return new Color(138, 85, 12);
        }
        return TEXT_MUTED;
    }

    private JPanel createFilterBar() {
        JPanel bar = new JPanel(new GridLayout(0, 1, 0, 6));
        bar.setBackground(CARD_BACKGROUND);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(9, 12, 9, 12)
        ));

        searchField = new JTextField();
        styleFilterField(searchField, 230);
        searchField.setToolTipText("Search title, description, location, or reporter (Ctrl+F)");

        statusFilterBox = new JComboBox<>(withAll(Issue.STATUSES));
        categoryFilterBox = new JComboBox<>(withAll(Issue.CATEGORIES));
        priorityFilterBox = new JComboBox<>(withAll(Issue.PRIORITIES));
        departmentFilterBox = new JComboBox<>(departmentFilterOptions());
        timeFilterBox = new JComboBox<>(IssueFilter.TIME_RANGES);
        styleFilterField(statusFilterBox, 110);
        styleFilterField(categoryFilterBox, 120);
        styleFilterField(priorityFilterBox, 110);
        styleFilterField(departmentFilterBox, 145);
        styleFilterField(timeFilterBox, 115);

        searchField.setText(issueFilter.getSearchText());
        statusFilterBox.setSelectedItem(issueFilter.getStatus());
        categoryFilterBox.setSelectedItem(issueFilter.getCategory());
        priorityFilterBox.setSelectedItem(issueFilter.getPriority());
        departmentFilterBox.setSelectedItem(issueFilter.getDepartment());
        timeFilterBox.setSelectedItem(issueFilter.getTimeRange());

        JPanel firstRow = createFilterRow();
        addFilterControl(firstRow, "Search", searchField);
        addFilterControl(firstRow, "Status", statusFilterBox);
        addFilterControl(firstRow, "Category", categoryFilterBox);

        JButton clearButton = new CivicButton("Clear Filters", CivicButton.OUTLINED);
        clearButton.addActionListener(e -> clearFilters());

        JPanel secondRow = createFilterRow();
        addFilterControl(secondRow, "Priority", priorityFilterBox);
        addFilterControl(secondRow, "Department", departmentFilterBox);
        addFilterControl(secondRow, "Reported", timeFilterBox);
        secondRow.add(clearButton);

        bar.add(firstRow);
        bar.add(secondRow);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { filterChanged(); }

            @Override
            public void removeUpdate(DocumentEvent e) { filterChanged(); }

            @Override
            public void changedUpdate(DocumentEvent e) { filterChanged(); }
        });
        statusFilterBox.addActionListener(e -> filterChanged());
        categoryFilterBox.addActionListener(e -> filterChanged());
        priorityFilterBox.addActionListener(e -> filterChanged());
        departmentFilterBox.addActionListener(e -> filterChanged());
        timeFilterBox.addActionListener(e -> filterChanged());

        return bar;
    }

    private JPanel createFilterRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        return row;
    }

    private void addFilterControl(JPanel row, String label, JComponent component) {
        JLabel filterLabel = new JLabel(label);
        filterLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        filterLabel.setForeground(TEXT_MUTED);
        row.add(filterLabel);
        row.add(component);
    }

    private void styleFilterField(JComponent component, int width) {
        styleInput(component);
        component.setPreferredSize(new Dimension(width, 28));
    }

    private String[] withAll(String[] options) {
        String[] result = new String[options.length + 1];
        result[0] = IssueFilter.ALL;
        System.arraycopy(options, 0, result, 1, options.length);
        return result;
    }

    private String[] departmentFilterOptions() {
        String[] result = new String[Issue.DEPARTMENTS.length + 2];
        result[0] = IssueFilter.ALL;
        result[1] = IssueFilter.UNASSIGNED;
        System.arraycopy(Issue.DEPARTMENTS, 0, result, 2, Issue.DEPARTMENTS.length);
        return result;
    }

    private void filterChanged() {
        if (updatingFilterControls) {
            return;
        }
        issueFilter.setSearchText(searchField.getText());
        issueFilter.setStatus((String) statusFilterBox.getSelectedItem());
        issueFilter.setCategory((String) categoryFilterBox.getSelectedItem());
        issueFilter.setPriority((String) priorityFilterBox.getSelectedItem());
        issueFilter.setDepartment((String) departmentFilterBox.getSelectedItem());
        issueFilter.setTimeRange((String) timeFilterBox.getSelectedItem());
        refreshDashboard();
    }

    private void clearFilters() {
        updatingFilterControls = true;
        resetFilterModel();
        searchField.setText("");
        statusFilterBox.setSelectedItem(IssueFilter.ALL);
        categoryFilterBox.setSelectedItem(IssueFilter.ALL);
        priorityFilterBox.setSelectedItem(IssueFilter.ALL);
        departmentFilterBox.setSelectedItem(IssueFilter.ALL);
        timeFilterBox.setSelectedItem(IssueFilter.ANY_TIME);
        updatingFilterControls = false;
        refreshDashboard();
        searchField.requestFocusInWindow();
    }

    private void resetFilterModel() {
        issueFilter.setSearchText("");
        issueFilter.setStatus(IssueFilter.ALL);
        issueFilter.setCategory(IssueFilter.ALL);
        issueFilter.setPriority(IssueFilter.ALL);
        issueFilter.setDepartment(IssueFilter.ALL);
        issueFilter.setTimeRange(IssueFilter.ANY_TIME);
    }

    private void installDashboardShortcuts(JPanel root) {
        InputMap inputMap = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = root.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK),
                "focusIssueSearch");
        actionMap.put("focusIssueSearch", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                searchField.requestFocusInWindow();
                searchField.selectAll();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearIssueFilters");
        actionMap.put("clearIssueFilters", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                clearFilters();
            }
        });
    }

    private JPanel createActionPanel() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setBackground(CARD_BACKGROUND);
        actions.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(7, 8, 7, 8)
        ));

        if (!isAdmin()) {
            JButton reportButton = new CivicButton("Report Issue", CivicButton.FILLED);
            reportButton.addActionListener(e -> showIssueForm());
            actions.add(reportButton);
        }

        JButton detailsButton = new CivicButton("View Details", CivicButton.OUTLINED);
        JButton refreshButton = new CivicButton("Refresh", CivicButton.OUTLINED);
        detailsButton.addActionListener(e -> showSelectedIssueDetails());
        refreshButton.addActionListener(e -> refreshDashboard());

        actions.add(detailsButton);
        actions.add(refreshButton);

        if (isAdmin()) {
            JButton statusButton = new CivicButton("Update Status", CivicButton.OUTLINED);
            JButton deptButton = new CivicButton("Assign Department", CivicButton.OUTLINED);
            JButton priorityButton = new CivicButton("Update Priority", CivicButton.OUTLINED);
            JButton deleteButton = new CivicButton("Delete", CivicButton.DANGER_STYLE);
            JButton usersButton = new CivicButton("Users", CivicButton.OUTLINED);

            statusButton.addActionListener(e -> updateSelectedIssue("Status", Issue.STATUSES));
            deptButton.addActionListener(e -> updateSelectedIssue("Department", Issue.DEPARTMENTS));
            priorityButton.addActionListener(e -> updateSelectedIssue("Priority", Issue.PRIORITIES));
            deleteButton.addActionListener(e -> deleteSelectedIssue());
            usersButton.addActionListener(e -> showUsers());

            actions.add(statusButton);
            actions.add(deptButton);
            actions.add(priorityButton);
            actions.add(deleteButton);
            actions.add(usersButton);
        }

        return actions;
    }

    private void showIssueForm() {
        if (isAdmin()) {
            showError("Only citizens can report issues.");
            return;
        }

        JTextField titleField = new JTextField();
        JTextArea descriptionArea = new JTextArea(4, 24);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        JTextField locationField = new JTextField();
        JComboBox<String> categoryBox = new JComboBox<>(Issue.CATEGORIES);
        JComboBox<String> priorityBox = new JComboBox<>(Issue.PRIORITIES);

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        form.setBackground(CARD_BACKGROUND);
        form.setBorder(new EmptyBorder(8, 10, 8, 10));
        addField(form, "Title", titleField);
        addField(form, "Description", new JScrollPane(descriptionArea));
        addField(form, "Location", locationField);
        addField(form, "Category", categoryBox);
        addField(form, "Priority", priorityBox);

        String title = "";
        String description = "";
        String location = "";
        while (true) {
            int choice = JOptionPane.showConfirmDialog(
                    this, form, "Report New Issue", JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION) return;

            title = titleField.getText().trim();
            description = descriptionArea.getText().trim();
            location = locationField.getText().trim();

            String error = issueManager.validateIssue(
                    title, description, location, currentUser.getUsername());
            if (error == null) break;
            showError(error);
        }

        Issue issue = new Issue(
                title,
                description,
                location,
                (String) categoryBox.getSelectedItem(),
                (String) priorityBox.getSelectedItem(),
                currentUser.getUsername()
        );
        try {
            if (!issueManager.addIssue(issue)) {
                showError("Unable to report this issue.");
                return;
            }
        } catch (DataStoreException ex) {
            showSaveError(ex);
            return;
        }
        refreshDashboard();
        JOptionPane.showMessageDialog(this, "Issue #" + issue.getId() + " reported.");
    }

    private void refreshDashboard() {
        if (currentUser == null) {
            return;
        }
        titleLabel.setText("Welcome, " + currentUser.getName() + " (" + currentUser.getRole() + ")");
        List<Issue> issues = isAdmin()
                ? issueManager.getAllIssues()
                : issueManager.getByUser(currentUser.getUsername());
        List<Issue> filtered = issueFilter.apply(issues);

        issueTableModel.setRowCount(0);
        for (Issue issue : filtered) {
            issueTableModel.addRow(new Object[]{
                    issue.getId(),
                    issue.getTitle(),
                    issue.getCategory(),
                    issue.getPriority(),
                    issue.getStatus(),
                    issue.getDepartment(),
                    issue.getLocation(),
                    issue.getReportedBy(),
                    issue.getDateReported()
            });
        }

        statsLabel.setText("Showing " + filtered.size() + " of " + issues.size()
                + "    Pending: " + countStatus(filtered, "Pending")
                + "    In Progress: " + countStatus(filtered, "In Progress")
                + "    Resolved: " + countStatus(filtered, "Resolved"));
    }

    private int countStatus(List<Issue> issues, String status) {
        int count = 0;
        for (Issue issue : issues) {
            if (issue.getStatus().equalsIgnoreCase(status)) {
                count++;
            }
        }
        return count;
    }

    private void showSelectedIssueDetails() {
        Issue issue = getSelectedIssue();
        if (issue == null) return;

        JTextArea details = new JTextArea(formatIssue(issue), 12, 42);
        details.setEditable(false);
        details.setFont(new Font("Consolas", Font.PLAIN, 13));
        JOptionPane.showMessageDialog(this, new JScrollPane(details), "Issue #" + issue.getId(), JOptionPane.INFORMATION_MESSAGE);
    }

    private String formatIssue(Issue issue) {
        return "Title       : " + issue.getTitle() + "\n"
                + "Description : " + issue.getDescription() + "\n"
                + "Location    : " + issue.getLocation() + "\n"
                + "Category    : " + issue.getCategory() + "\n"
                + "Priority    : " + issue.getPriority() + "\n"
                + "Status      : " + issue.getStatus() + "\n"
                + "Department  : " + issue.getDepartment() + "\n"
                + "Reported By : " + issue.getReportedBy() + "\n"
                + "Date        : " + issue.getDateReported();
    }

    private static LocalDateTime parseIssueDate(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, ISSUE_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void updateSelectedIssue(String label, String[] options) {
        Issue issue = getSelectedIssue();
        if (issue == null) return;

        String selected = (String) JOptionPane.showInputDialog(
                this,
                "Select " + label.toLowerCase() + ":",
                "Update " + label,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]
        );
        if (selected == null) return;

        try {
            boolean updated;
            if (label.equals("Status")) {
                updated = issueManager.updateStatus(issue.getId(), selected);
            } else if (label.equals("Department")) {
                updated = issueManager.assignDepartment(issue.getId(), selected);
            } else {
                updated = issueManager.updatePriority(issue.getId(), selected);
            }
            if (!updated) {
                showError("Unable to update issue #" + issue.getId() + ".");
                return;
            }
        } catch (DataStoreException ex) {
            showSaveError(ex);
        }
        refreshDashboard();
    }

    private void deleteSelectedIssue() {
        Issue issue = getSelectedIssue();
        if (issue == null) return;

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete issue #" + issue.getId() + "?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                if (!issueManager.deleteIssue(issue.getId())) {
                    showError("Unable to delete issue #" + issue.getId() + ".");
                    return;
                }
            } catch (DataStoreException ex) {
                showSaveError(ex);
                return;
            }
            refreshDashboard();
        }
    }

    private void showUsers() {
        JDialog dialog = new JDialog(this, "Registered Users - Civic Services", true);
        dialog.setIconImage(getIconImage());
        JTextArea usersArea = new JTextArea(formatUsers(), 12, 42);
        usersArea.setEditable(false);
        usersArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        usersArea.setBackground(CARD_BACKGROUND);
        usersArea.setForeground(TEXT_PRIMARY);
        usersArea.setBorder(new EmptyBorder(8, 10, 8, 10));

        JButton createAdminButton = new CivicButton("Create Admin", CivicButton.FILLED);
        JButton removeAdminButton = new CivicButton("Remove Admin", CivicButton.DANGER_STYLE);
        JButton closeButton = new CivicButton("Close", CivicButton.OUTLINED);
        createAdminButton.addActionListener(e -> {
            if (showCreateAdminDialog(dialog)) {
                usersArea.setText(formatUsers());
            }
        });
        removeAdminButton.addActionListener(e -> {
            if (showRemoveAdminDialog(dialog)) {
                usersArea.setText(formatUsers());
            }
        });
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(createAdminButton);
        buttons.add(removeAdminButton);
        buttons.add(closeButton);

        JPanel headingBox = new JPanel(new GridLayout(0, 1, 2, 2));
        headingBox.setOpaque(false);
        JLabel heading = new JLabel("Account Directory");
        heading.setFont(new Font("Georgia", Font.BOLD, 20));
        heading.setForeground(PRIMARY_DARK);
        JLabel subheading = new JLabel("Registered citizens and administrators");
        subheading.setFont(BODY_FONT);
        subheading.setForeground(TEXT_MUTED);
        headingBox.add(heading);
        headingBox.add(subheading);

        JScrollPane scrollPane = new JScrollPane(usersArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
        scrollPane.getViewport().setBackground(CARD_BACKGROUND);

        JPanel content = new JPanel(new BorderLayout(10, 12));
        content.setBackground(PAGE_BACKGROUND);
        content.setBorder(new EmptyBorder(16, 18, 16, 18));
        content.add(headingBox, BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(closeButton);
        dialog.setSize(600, 450);
        dialog.setMinimumSize(new Dimension(480, 340));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private String formatUsers() {
        StringBuilder builder = new StringBuilder();
        for (User user : userManager.getAllUsers()) {
            builder.append(user.getUsername())
                    .append("    [")
                    .append(user.getRole())
                    .append("]    ")
                    .append(user.getName())
                    .append("\n");
        }
        return builder.toString();
    }

    private boolean showCreateAdminDialog(Component parent) {
        JTextField nameField = new JTextField();
        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JPasswordField confirmField = new JPasswordField();

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        form.setBackground(CARD_BACKGROUND);
        form.setBorder(new EmptyBorder(8, 10, 8, 10));
        addField(form, "Full name", nameField);
        addField(form, "Username", usernameField);
        addField(form, "Password", passwordField);
        addField(form, "Confirm password", confirmField);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(
                    parent, form, "Create Admin Account", JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION) {
                return false;
            }

            String name = nameField.getText().trim();
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            String confirm = new String(confirmField.getPassword());

            String error = userManager.validateAdminCreation(username, password, name);
            if (error == null && !password.equals(confirm)) {
                error = "Passwords do not match.";
            }
            if (error != null) {
                showError(parent, error);
                continue;
            }

            try {
                if (!userManager.createAdmin(username, password, name)) {
                    showError(parent, "Unable to create the admin account.");
                    return false;
                }
            } catch (DataStoreException ex) {
                showSaveError(parent, ex);
                return false;
            }

            JOptionPane.showMessageDialog(
                    parent,
                    "Admin account \"" + username + "\" created.",
                    "Admin Created",
                    JOptionPane.INFORMATION_MESSAGE);
            return true;
        }
    }

    private boolean showRemoveAdminDialog(Component parent) {
        List<String> removableAdmins = new ArrayList<>();
        for (User user : userManager.getAllUsers()) {
            if ("Admin".equalsIgnoreCase(user.getRole())
                    && !user.getUsername().equalsIgnoreCase(currentUser.getUsername())) {
                removableAdmins.add(user.getUsername());
            }
        }

        if (removableAdmins.isEmpty()) {
            showError(parent, "There are no other admin accounts to remove.");
            return false;
        }

        String selected = (String) JOptionPane.showInputDialog(
                parent,
                "Select the admin account to remove:",
                "Remove Admin",
                JOptionPane.WARNING_MESSAGE,
                null,
                removableAdmins.toArray(new String[0]),
                removableAdmins.get(0));
        if (selected == null) {
            return false;
        }

        String error = userManager.validateAdminRemoval(selected, currentUser.getUsername());
        if (error != null) {
            showError(parent, error);
            return false;
        }

        int confirm = JOptionPane.showConfirmDialog(
                parent,
                "Remove admin account \"" + selected + "\"?",
                "Confirm Remove Admin",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return false;
        }

        try {
            if (!userManager.removeAdmin(selected, currentUser.getUsername())) {
                showError(parent, "Unable to remove that admin account.");
                return false;
            }
        } catch (DataStoreException ex) {
            showSaveError(parent, ex);
            return false;
        }

        JOptionPane.showMessageDialog(
                parent,
                "Admin account \"" + selected + "\" removed.",
                "Admin Removed",
                JOptionPane.INFORMATION_MESSAGE);
        return true;
    }

    private void showChangePasswordDialog() {
        if (currentUser == null) {
            return;
        }

        JPasswordField currentField = new JPasswordField();
        JPasswordField newField = new JPasswordField();
        JPasswordField confirmField = new JPasswordField();

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        form.setBackground(CARD_BACKGROUND);
        form.setBorder(new EmptyBorder(8, 10, 8, 10));
        addField(form, "Current password", currentField);
        addField(form, "New password", newField);
        addField(form, "Confirm new password", confirmField);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(
                    this, form, "Change Password", JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }

            String current = new String(currentField.getPassword());
            String newPassword = new String(newField.getPassword());
            String confirm = new String(confirmField.getPassword());

            if (!newPassword.equals(confirm)) {
                showError("New passwords do not match.");
                continue;
            }

            String error;
            try {
                error = userManager.changePassword(currentUser, current, newPassword);
            } catch (DataStoreException ex) {
                showSaveError(ex);
                return;
            }
            if (error != null) {
                showError(error);
                continue;
            }

            JOptionPane.showMessageDialog(
                    this,
                    "Your password was changed.",
                    "Password Changed",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
    }

    private void promptForDefaultAdminPassword() {
        if (!userManager.isUsingDefaultAdminPassword(currentUser)) {
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
                this,
                "You are signed in with the default admin password, which is publicly documented.\n\n"
                        + "Change it now?",
                "Default Admin Password",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            showChangePasswordDialog();
        }
    }

    private Issue getSelectedIssue() {
        int row = issueTable.getSelectedRow();
        if (row < 0) {
            showError("Please select an issue first.");
            return null;
        }
        int modelRow = issueTable.convertRowIndexToModel(row);
        int id = (int) issueTableModel.getValueAt(modelRow, 0);
        return issueManager.getById(id);
    }

    private static void configureTheme() {
        UIManager.put("OptionPane.background", CARD_BACKGROUND);
        UIManager.put("Panel.background", CARD_BACKGROUND);
        UIManager.put("OptionPane.messageForeground", TEXT_PRIMARY);
        UIManager.put("OptionPane.messageFont", BODY_FONT);
        UIManager.put("OptionPane.buttonFont", BODY_FONT);
        UIManager.put("Label.foreground", TEXT_PRIMARY);
        UIManager.put("ToolTip.background", PRIMARY_DARK);
        UIManager.put("ToolTip.foreground", Color.WHITE);
        UIManager.put("ToolTip.font", BODY_FONT.deriveFont(Font.PLAIN, 12));
        UIManager.put("ToolTip.border", new EmptyBorder(6, 8, 6, 8));
        UIManager.put("ComboBox.selectionBackground", SELECTION);
        UIManager.put("ComboBox.selectionForeground", TEXT_PRIMARY);
        UIManager.put("List.selectionBackground", SELECTION);
        UIManager.put("List.selectionForeground", TEXT_PRIMARY);
        UIManager.put("TextField.selectionBackground", SELECTION);
        UIManager.put("TextField.selectionForeground", TEXT_PRIMARY);
        UIManager.put("PasswordField.selectionBackground", SELECTION);
        UIManager.put("PasswordField.selectionForeground", TEXT_PRIMARY);
    }

    private void styleInput(JComponent component) {
        component.setFont(BODY_FONT);

        if (component instanceof JTextComponent) {
            JTextComponent textComponent = (JTextComponent) component;
            textComponent.setBackground(CARD_BACKGROUND);
            textComponent.setForeground(TEXT_PRIMARY);
            textComponent.setCaretColor(PRIMARY_DARK);
            textComponent.setSelectionColor(SELECTION);
            textComponent.setSelectedTextColor(TEXT_PRIMARY);
            if (component instanceof JTextField) {
                JTextField field = (JTextField) component;
                field.setBorder(inputBorder(BORDER));
                addFocusHighlight(field);
            } else {
                textComponent.setBorder(new EmptyBorder(6, 8, 6, 8));
            }
        } else if (component instanceof JComboBox) {
            component.setBackground(CARD_BACKGROUND);
            component.setForeground(TEXT_PRIMARY);
            component.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    new EmptyBorder(1, 6, 1, 2)
            ));
        } else if (component instanceof JScrollPane) {
            JScrollPane scrollPane = (JScrollPane) component;
            scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
            scrollPane.getViewport().setBackground(CARD_BACKGROUND);
            Component view = scrollPane.getViewport().getView();
            if (view instanceof JComponent) {
                view.setFont(BODY_FONT);
                view.setBackground(CARD_BACKGROUND);
                view.setForeground(TEXT_PRIMARY);
                if (view instanceof JTextComponent) {
                    ((JTextComponent) view).setCaretColor(PRIMARY_DARK);
                }
            }
        } else {
            component.setBackground(CARD_BACKGROUND);
        }
    }

    private static Border inputBorder(Color color) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color),
                new EmptyBorder(4, 9, 4, 9)
        );
    }

    private void addFocusHighlight(final JTextField field) {
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.setBorder(inputBorder(PRIMARY));
            }

            @Override
            public void focusLost(FocusEvent e) {
                field.setBorder(inputBorder(BORDER));
            }
        });
    }

    private static JPanel createGreenPanel() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(
                        0, 0, PRIMARY_DARK,
                        getWidth(), getHeight(), PRIMARY));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 255, 255, 16));
                g2.fillOval(getWidth() - 130, -70, 220, 220);
                g2.setColor(new Color(255, 255, 255, 10));
                g2.fillOval(getWidth() - 210, 30, 120, 120);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        return panel;
    }

    private JPanel createMasthead() {
        JPanel masthead = createGreenPanel();
        masthead.setLayout(new BorderLayout(12, 0));
        masthead.setBorder(new EmptyBorder(13, 26, 13, 26));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        brand.setOpaque(false);
        brand.add(new SealView(54, PRIMARY_DARK, new Color(247, 251, 247), PRIMARY));

        JPanel words = new JPanel(new GridLayout(0, 1, 0, 1));
        words.setOpaque(false);
        JLabel eyebrow = new JLabel("CIVIC SERVICES");
        eyebrow.setFont(new Font("Segoe UI", Font.BOLD, 10));
        eyebrow.setForeground(ON_DARK_MUTED);
        JLabel title = new JLabel("Neighbourhood Issue Reporter");
        title.setFont(new Font("Georgia", Font.BOLD, 26));
        title.setForeground(Color.WHITE);
        JLabel subtitle = new JLabel("OFFICIAL REPORTING PORTAL");
        subtitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        subtitle.setForeground(ON_DARK_MUTED);
        words.add(eyebrow);
        words.add(title);
        words.add(subtitle);
        brand.add(words);

        JPanel badgeWrap = new JPanel(new GridBagLayout());
        badgeWrap.setOpaque(false);
        badgeWrap.add(new BadgeLabel("COMMUNITY ISSUE MANAGEMENT"));

        masthead.add(brand, BorderLayout.WEST);
        masthead.add(badgeWrap, BorderLayout.EAST);
        return masthead;
    }

    private JPanel createDashboardMasthead() {
        JPanel masthead = createGreenPanel();
        masthead.setLayout(new BorderLayout(12, 2));
        masthead.setBorder(new EmptyBorder(9, 16, 9, 16));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brand.setOpaque(false);
        brand.add(new SealView(34, PRIMARY_DARK, new Color(247, 251, 247), PRIMARY));

        JPanel brandWords = new JPanel(new GridLayout(0, 1, 0, 0));
        brandWords.setOpaque(false);
        JLabel eyebrow = new JLabel("CIVIC SERVICES  |  OFFICIAL ISSUE REGISTER");
        eyebrow.setFont(new Font("Segoe UI", Font.BOLD, 9));
        eyebrow.setForeground(ON_DARK_MUTED);
        JLabel brandTitle = new JLabel("Neighbourhood Issue Reporter");
        brandTitle.setFont(new Font("Georgia", Font.BOLD, 17));
        brandTitle.setForeground(Color.WHITE);
        brandWords.add(eyebrow);
        brandWords.add(brandTitle);
        brand.add(brandWords);

        JButton changePasswordButton = new CivicButton("Change Password", CivicButton.ON_DARK);
        changePasswordButton.addActionListener(e -> showChangePasswordDialog());
        JButton logoutButton = new CivicButton("Logout", CivicButton.ON_DARK);
        logoutButton.addActionListener(e -> {
            resetFilterModel();
            currentUser = null;
            showAuthPanel();
        });
        JPanel accountActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        accountActions.setOpaque(false);
        accountActions.add(changePasswordButton);
        accountActions.add(logoutButton);

        JPanel topRow = new JPanel(new BorderLayout(12, 0));
        topRow.setOpaque(false);
        topRow.add(brand, BorderLayout.WEST);
        topRow.add(accountActions, BorderLayout.EAST);

        titleLabel = new JLabel();
        titleLabel.setFont(new Font("Georgia", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);
        statsLabel = new JLabel();
        statsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statsLabel.setForeground(ON_DARK_MUTED);

        JPanel infoRow = new JPanel(new BorderLayout(12, 0));
        infoRow.setOpaque(false);
        infoRow.setBorder(new EmptyBorder(4, 0, 0, 0));
        infoRow.add(titleLabel, BorderLayout.WEST);
        infoRow.add(statsLabel, BorderLayout.EAST);

        masthead.add(topRow, BorderLayout.NORTH);
        masthead.add(infoRow, BorderLayout.CENTER);
        return masthead;
    }

    private static Image createSealImage(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paintSeal(g, size, PRIMARY_DARK, new Color(247, 251, 247), PRIMARY);
        g.dispose();
        return image;
    }

    private static void paintSeal(Graphics2D g, int size, Color ring, Color fill, Color glyph) {
        double outerInset = size * 0.06;
        double diameter = size - outerInset * 2;
        g.setColor(fill);
        g.fill(new Ellipse2D.Double(outerInset, outerInset, diameter, diameter));

        float outerStroke = Math.max(1.0f, size * 0.045f);
        g.setColor(ring);
        g.setStroke(new BasicStroke(outerStroke));
        g.draw(new Ellipse2D.Double(
                outerInset + outerStroke / 2.0,
                outerInset + outerStroke / 2.0,
                diameter - outerStroke,
                diameter - outerStroke));

        double innerInset = size * 0.17;
        double innerDiameter = size - innerInset * 2;
        g.setStroke(new BasicStroke(Math.max(0.8f, size * 0.018f)));
        g.draw(new Ellipse2D.Double(innerInset, innerInset, innerDiameter, innerDiameter));

        int dots = 12;
        double dotRadius = Math.max(0.7, size * 0.012);
        double ringRadius = size * 0.385;
        for (int i = 0; i < dots; i++) {
            double angle = Math.PI * 2 * i / dots - Math.PI / 2;
            double centerX = size / 2.0 + Math.cos(angle) * ringRadius;
            double centerY = size / 2.0 + Math.sin(angle) * ringRadius;
            g.fill(new Ellipse2D.Double(
                    centerX - dotRadius, centerY - dotRadius,
                    dotRadius * 2, dotRadius * 2));
        }

        drawStar(g, size / 2.0, size * 0.5, size * 0.20, size * 0.082, glyph);
    }

    private static void drawStar(
            Graphics2D g, double centerX, double centerY,
            double outerRadius, double innerRadius, Color color) {
        int[] xs = new int[10];
        int[] ys = new int[10];
        for (int i = 0; i < 10; i++) {
            double radius = i % 2 == 0 ? outerRadius : innerRadius;
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            xs[i] = (int) Math.round(centerX + Math.cos(angle) * radius);
            ys[i] = (int) Math.round(centerY + Math.sin(angle) * radius);
        }
        g.setColor(color);
        g.fill(new Polygon(xs, ys, 10));
    }

    private static final class SealView extends JComponent {
        private final int size;
        private final Color ringColor;
        private final Color fillColor;
        private final Color glyphColor;

        SealView(int size, Color ringColor, Color fillColor, Color glyphColor) {
            this.size = size;
            this.ringColor = ringColor;
            this.fillColor = fillColor;
            this.glyphColor = glyphColor;
            setOpaque(false);
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int paintSize = Math.min(getWidth(), getHeight());
            g2.translate((getWidth() - paintSize) / 2, (getHeight() - paintSize) / 2);
            paintSeal(g2, paintSize, ringColor, fillColor, glyphColor);
            g2.dispose();
        }
    }

    private static final class BadgeLabel extends JLabel {
        BadgeLabel(String text) {
            super(text);
            setFont(new Font("Segoe UI", Font.BOLD, 10));
            setForeground(new Color(235, 246, 238));
            setBorder(new EmptyBorder(6, 10, 6, 10));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(255, 255, 255, 26));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.setColor(new Color(255, 255, 255, 70));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class CivicButton extends JButton {
        private static final int FILLED = 0;
        private static final int OUTLINED = 1;
        private static final int DANGER_STYLE = 2;
        private static final int ON_DARK = 3;

        private final int variant;
        private final Color fillColor;
        private final Color hoverColor;
        private final Color pressedColor;
        private final Color borderColor;
        private final Color labelColor;

        CivicButton(String text, int variant) {
            super(text);
            this.variant = variant;
            switch (variant) {
                case FILLED:
                    fillColor = PRIMARY;
                    hoverColor = PRIMARY_HOVER;
                    pressedColor = PRIMARY_DARK;
                    borderColor = PRIMARY_DARK;
                    labelColor = Color.WHITE;
                    break;
                case DANGER_STYLE:
                    fillColor = CARD_BACKGROUND;
                    hoverColor = new Color(250, 238, 238);
                    pressedColor = new Color(245, 225, 225);
                    borderColor = DANGER;
                    labelColor = DANGER;
                    break;
                case ON_DARK:
                    fillColor = new Color(255, 255, 255, 20);
                    hoverColor = new Color(255, 255, 255, 46);
                    pressedColor = new Color(255, 255, 255, 68);
                    borderColor = new Color(255, 255, 255, 115);
                    labelColor = Color.WHITE;
                    break;
                default:
                    fillColor = CARD_BACKGROUND;
                    hoverColor = SOFT_GREEN;
                    pressedColor = new Color(216, 234, 221);
                    borderColor = new Color(180, 202, 184);
                    labelColor = PRIMARY_DARK;
                    break;
            }
            setFont(BODY_FONT.deriveFont(Font.BOLD, 12));
            setForeground(labelColor);
            setBackground(fillColor);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(6, 14, 6, 14));
            setMargin(new Insets(0, 0, 0, 0));
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension preferred = super.getPreferredSize();
            return new Dimension(preferred.width, Math.max(32, preferred.height));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Color currentFill = !isEnabled()
                    ? new Color(240, 242, 240)
                    : getModel().isPressed()
                    ? pressedColor
                    : getModel().isRollover()
                    ? hoverColor
                    : fillColor;
            g2.setColor(currentFill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            if (variant != FILLED || !isEnabled()) {
                g2.setColor(isEnabled() ? borderColor : BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            }
            if (isFocusOwner() && isEnabled()) {
                g2.setColor(variant == FILLED || variant == ON_DARK
                        ? new Color(255, 255, 255, 140)
                        : new Color(22, 101, 52, 120));
                g2.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 5, 5);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class CivicTabbedPaneUI extends BasicTabbedPaneUI {
        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabInsets = new Insets(10, 26, 10, 26);
            tabAreaInsets = new Insets(6, 8, 0, 8);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
            contentBorderInsets = new Insets(0, 0, 0, 0);
        }

        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                          int x, int y, int w, int h, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(isSelected ? CARD_BACKGROUND : SOFT_GREEN);
            g2.fillRect(x, y, w, h);
            if (isSelected) {
                g2.setColor(PRIMARY);
                g2.fillRect(x, y + h - 3, w, 3);
            }
            if (tabIndex < tabPane.getTabCount() - 1) {
                g2.setColor(BORDER);
                g2.drawLine(x + w - 1, y + 8, x + w - 1, y + h - 8);
            }
            g2.dispose();
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                      int x, int y, int w, int h, boolean isSelected) {
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects,
                                           int tabIndex, Rectangle iconRect, Rectangle textRect,
                                           boolean isSelected) {
        }
    }

    private boolean isAdmin() {
        return currentUser != null && currentUser.getRole().equals("Admin");
    }

    private void showError(String message) {
        showError(this, message);
    }

    private void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void showSaveError(DataStoreException ex) {
        showSaveError(this, ex);
    }

    private void showSaveError(Component parent, DataStoreException ex) {
        JOptionPane.showMessageDialog(
                parent,
                "Your change could not be saved, so it was not applied.\n\n" + ex.getMessage(),
                "Save Error",
                JOptionPane.ERROR_MESSAGE);
    }
}
