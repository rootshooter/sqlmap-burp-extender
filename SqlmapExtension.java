package burp;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.table.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.concurrent.TimeUnit;

public class SqlmapExtension implements IBurpExtender, IContextMenuFactory, ITab {
    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;
    private PrintWriter stdout;
    private PrintWriter stderr;
    
    private JPanel mainPanel;
    private JTextField sqlmapPathField;
    private JTextArea additionalArgsArea;
    private JCheckBox verboseCheckBox;
    private JCheckBox batchModeCheckBox;
    private JCheckBox randomAgentCheckBox;
    private JCheckBox flushSessionCheckBox;
    private JComboBox<String> riskLevelCombo;
    private JComboBox<String> testLevelCombo;
    private JTable resultsTable;
    private DefaultTableModel resultsTableModel;
    
    private ExecutorService executorService;
    private String sqlmapPath = "sqlmap";
    private static final String SETTING_SQLMAP_PATH = "sqlmap_path";
    private static final String SETTING_RISK_LEVEL = "risk_level";
    private static final String SETTING_TEST_LEVEL = "test_level";
    private static final String SETTING_VERBOSE = "verbose";
    private static final String SETTING_BATCH = "batch";
    private static final String SETTING_RANDOM_AGENT = "random_agent";
    private static final String SETTING_FLUSH_SESSION = "flush_session";
    private static final String SETTING_ADDITIONAL_ARGS = "additional_args";
    
    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
        this.helpers = callbacks.getHelpers();
        this.stdout = new PrintWriter(callbacks.getStdout(), true);
        this.stderr = new PrintWriter(callbacks.getStderr(), true);
        
        callbacks.setExtensionName("SQLMap Scanner");
        
        executorService = Executors.newFixedThreadPool(3);
        
        // Load saved settings
        loadSettings();
        
        SwingUtilities.invokeLater(() -> {
            buildUI();
            callbacks.addSuiteTab(SqlmapExtension.this);
        });
        
        callbacks.registerContextMenuFactory(this);
        
        stdout.println("========================================");
        stdout.println("SQLMap Scanner");
        stdout.println("========================================");
        stdout.println("[+] Extension loaded successfully");
        if (!sqlmapPath.isEmpty()) {
            stdout.println("[*] Saved SQLMap path: " + sqlmapPath);
            stdout.println("[!] Remember to click 'Test' to verify the path works");
        } else {
            stdout.println("[!] Please configure SQLMap path in the SQLMap tab");
            stdout.println("[!] On Windows, use: python C:\\path\\to\\sqlmap.py");
        }
        stdout.println("========================================");
    }
    
    private void loadSettings() {
        sqlmapPath = callbacks.loadExtensionSetting(SETTING_SQLMAP_PATH);
        if (sqlmapPath == null || sqlmapPath.isEmpty()) {
            sqlmapPath = "sqlmap";
        }
    }
    
    private void saveSettings() {
        callbacks.saveExtensionSetting(SETTING_SQLMAP_PATH, sqlmapPathField.getText().trim());
        callbacks.saveExtensionSetting(SETTING_RISK_LEVEL, String.valueOf(riskLevelCombo.getSelectedIndex()));
        callbacks.saveExtensionSetting(SETTING_TEST_LEVEL, String.valueOf(testLevelCombo.getSelectedIndex()));
        callbacks.saveExtensionSetting(SETTING_VERBOSE, String.valueOf(verboseCheckBox.isSelected()));
        callbacks.saveExtensionSetting(SETTING_BATCH, String.valueOf(batchModeCheckBox.isSelected()));
        callbacks.saveExtensionSetting(SETTING_RANDOM_AGENT, String.valueOf(randomAgentCheckBox.isSelected()));
        callbacks.saveExtensionSetting(SETTING_FLUSH_SESSION, String.valueOf(flushSessionCheckBox.isSelected()));
        callbacks.saveExtensionSetting(SETTING_ADDITIONAL_ARGS, additionalArgsArea.getText());
        
        stdout.println("[*] Settings saved");
    }
    
    private void loadUISettings() {
        String riskLevel = callbacks.loadExtensionSetting(SETTING_RISK_LEVEL);
        if (riskLevel != null) {
            riskLevelCombo.setSelectedIndex(Integer.parseInt(riskLevel));
        }
        
        String testLevel = callbacks.loadExtensionSetting(SETTING_TEST_LEVEL);
        if (testLevel != null) {
            testLevelCombo.setSelectedIndex(Integer.parseInt(testLevel));
        }
        
        String verbose = callbacks.loadExtensionSetting(SETTING_VERBOSE);
        if (verbose != null) {
            verboseCheckBox.setSelected(Boolean.parseBoolean(verbose));
        }
        
        String batch = callbacks.loadExtensionSetting(SETTING_BATCH);
        if (batch != null) {
            batchModeCheckBox.setSelected(Boolean.parseBoolean(batch));
        }
        
        String randomAgent = callbacks.loadExtensionSetting(SETTING_RANDOM_AGENT);
        if (randomAgent != null) {
            randomAgentCheckBox.setSelected(Boolean.parseBoolean(randomAgent));
        }
        
        String flushSession = callbacks.loadExtensionSetting(SETTING_FLUSH_SESSION);
        if (flushSession != null) {
            flushSessionCheckBox.setSelected(Boolean.parseBoolean(flushSession));
        }
        
        String additionalArgs = callbacks.loadExtensionSetting(SETTING_ADDITIONAL_ARGS);
        if (additionalArgs != null) {
            additionalArgsArea.setText(additionalArgs);
        }
    }
    
    private String autoDetectSqlmap() {
        // Try common locations
        String[] commonPaths = {
            "sqlmap",                                    // System PATH
            "sqlmap.py",                                 // System PATH with .py extension
            "/usr/bin/sqlmap",                          // Linux/Mac
            "/usr/local/bin/sqlmap",                    // Linux/Mac
            System.getProperty("user.home") + "/sqlmap-dev/sqlmap.py",  // Common dev location
            System.getProperty("user.home") + "/tools/sqlmap-dev/sqlmap.py",
            "C:\\sqlmap\\sqlmap.py",                    // Windows
            "C:\\tools\\sqlmap\\sqlmap.py"              // Windows
        };
        
        for (String path : commonPaths) {
            if (testSqlmapPathQuiet(path)) {
                return path;
            }
        }
        
        // Try to find sqlmap in PATH using 'which' or 'where' command
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String command = os.contains("win") ? "where" : "which";
            
            ProcessBuilder pb = new ProcessBuilder(command, "sqlmap");
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String path = reader.readLine();
            
            if (path != null && !path.isEmpty() && process.waitFor() == 0) {
                return path.trim();
            }
        } catch (Exception e) {
            // Silent fail - this is just auto-detection
        }
        
        return null;
    }
    
    private boolean testSqlmapPathQuiet(String path) {
        try {
            ProcessBuilder pb = new ProcessBuilder(path, "--version");
            pb.redirectErrorStream(true);
            
            // Redirect stdin to null device instead of closing
            try {
                pb.redirectInput(ProcessBuilder.Redirect.from(new File(
                    System.getProperty("os.name").toLowerCase().contains("win") ? "NUL" : "/dev/null"
                )));
            } catch (Exception e) {
                // Continue if redirect fails
            }
            
            Process process = pb.start();
            
            // Read and discard output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while (reader.readLine() != null) {
                // Consume output
            }
            
            // Wait with timeout (5 seconds)
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void buildUI() {
        mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Configuration Panel
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("SQLMap Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // SQLMap Path
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        configPanel.add(new JLabel("SQLMap Path:"), gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        sqlmapPathField = new JTextField(sqlmapPath);
        configPanel.add(sqlmapPathField, gbc);
        
        // Buttons panel for path controls
        JPanel pathButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        
        JButton testPathButton = new JButton("Test");
        testPathButton.addActionListener(e -> testSqlmapPathAsync());
        pathButtonsPanel.add(testPathButton);
        
        JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(e -> browseSqlmapPath());
        pathButtonsPanel.add(browseButton);
        
        JButton autoDetectButton = new JButton("Auto-detect");
        autoDetectButton.addActionListener(e -> autoDetectAndSet());
        pathButtonsPanel.add(autoDetectButton);
        
        gbc.gridx = 2; gbc.weightx = 0;
        configPanel.add(pathButtonsPanel, gbc);
        
        // Risk Level
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        configPanel.add(new JLabel("Risk Level:"), gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        String[] riskLevels = {"1 (Default)", "2", "3 (High)"};
        riskLevelCombo = new JComboBox<>(riskLevels);
        configPanel.add(riskLevelCombo, gbc);
        
        // Test Level
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        configPanel.add(new JLabel("Test Level:"), gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        String[] testLevels = {"1 (Default)", "2", "3", "4", "5 (Maximum)"};
        testLevelCombo = new JComboBox<>(testLevels);
        configPanel.add(testLevelCombo, gbc);
        
        // Checkboxes
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3;
        verboseCheckBox = new JCheckBox("Verbose Mode", true);
        configPanel.add(verboseCheckBox, gbc);
        
        gbc.gridy = 4;
        batchModeCheckBox = new JCheckBox("Batch Mode (Never Ask)", true);
        configPanel.add(batchModeCheckBox, gbc);
        
        gbc.gridy = 5;
        randomAgentCheckBox = new JCheckBox("Random User-Agent", true);
        configPanel.add(randomAgentCheckBox, gbc);
        
        gbc.gridy = 6;
        flushSessionCheckBox = new JCheckBox("Flush Session (Clear Cached Results)", false);
        configPanel.add(flushSessionCheckBox, gbc);
        
        // Additional Arguments
        gbc.gridy = 7; gbc.weightx = 0;
        configPanel.add(new JLabel("Additional Arguments:"), gbc);
        
        gbc.gridy = 8; gbc.weighty = 0.3; gbc.fill = GridBagConstraints.BOTH;
        additionalArgsArea = new JTextArea(3, 40);
        additionalArgsArea.setLineWrap(true);
        additionalArgsArea.setWrapStyleWord(true);
        JScrollPane argsScrollPane = new JScrollPane(additionalArgsArea);
        configPanel.add(argsScrollPane, gbc);
        
        // Results Table
        String[] columnNames = {"Time", "URL", "Parameter", "Status", "Injection Type", "DBMS"};
        resultsTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        resultsTable = new JTable(resultsTableModel);
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        
        // Add double-click handler to view full output
        resultsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    int row = resultsTable.getSelectedRow();
                    if (row >= 0) {
                        String url = resultsTable.getValueAt(row, 1).toString();
                        String status = resultsTable.getValueAt(row, 3).toString();
                        JOptionPane.showMessageDialog(mainPanel,
                            "URL: " + url + "\n" +
                            "Status: " + status + "\n\n" +
                            "Full SQLMap output is available in Burp's Output tab.\n" +
                            "Enable 'Verbose Mode' for detailed logs.",
                            "Scan Details",
                            JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }
        });
        
        JScrollPane resultsScrollPane = new JScrollPane(resultsTable);
        resultsScrollPane.setBorder(BorderFactory.createTitledBorder("Scan Results"));
        
        // Add components to main panel
        mainPanel.add(configPanel, BorderLayout.NORTH);
        mainPanel.add(resultsScrollPane, BorderLayout.CENTER);
        
        // Buttons Panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton saveSettingsButton = new JButton("Save Settings");
        saveSettingsButton.addActionListener(e -> {
            saveSettings();
            JOptionPane.showMessageDialog(mainPanel, 
                "Settings saved successfully!", 
                "Settings Saved", 
                JOptionPane.INFORMATION_MESSAGE);
        });
        buttonsPanel.add(saveSettingsButton);
        
        JButton clearButton = new JButton("Clear Results");
        clearButton.addActionListener(e -> resultsTableModel.setRowCount(0));
        buttonsPanel.add(clearButton);
        
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);
        
        // Load saved UI settings
        loadUISettings();
    }
    
    private void browseSqlmapPath() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select SQLMap executable or script");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        // Set current directory to home or current path
        String currentPath = sqlmapPathField.getText().trim();
        if (!currentPath.isEmpty() && !currentPath.equals("sqlmap")) {
            File currentFile = new File(currentPath);
            if (currentFile.exists()) {
                fileChooser.setSelectedFile(currentFile);
            } else if (currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
                fileChooser.setCurrentDirectory(currentFile.getParentFile());
            }
        }
        
        int result = fileChooser.showOpenDialog(mainPanel);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            sqlmapPathField.setText(selectedFile.getAbsolutePath());
            
            // Automatically test the selected path
            if (testSqlmapPathQuiet(selectedFile.getAbsolutePath())) {
                sqlmapPath = selectedFile.getAbsolutePath();
                JOptionPane.showMessageDialog(mainPanel, 
                    "SQLMap found and set successfully!", 
                    "Success", 
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(mainPanel, 
                    "Warning: The selected file does not appear to be SQLMap.\nPlease verify the path.", 
                    "Warning", 
                    JOptionPane.WARNING_MESSAGE);
            }
        }
    }
    
    private void autoDetectAndSet() {
        stdout.println("[*] Attempting to auto-detect SQLMap...");
        
        // Disable button during detection
        Component[] components = mainPanel.getComponents();
        JButton autoDetectButton = null;
        for (Component comp : components) {
            if (comp instanceof JPanel) {
                for (Component subComp : ((JPanel) comp).getComponents()) {
                    if (subComp instanceof JButton && ((JButton) subComp).getText().equals("Auto-detect")) {
                        autoDetectButton = (JButton) subComp;
                        break;
                    }
                }
            }
        }
        
        final JButton btn = autoDetectButton;
        if (btn != null) {
            btn.setEnabled(false);
            btn.setText("Detecting...");
        }
        
        // Run detection in background thread
        new Thread(() -> {
            String detectedPath = autoDetectSqlmap();
            
            SwingUtilities.invokeLater(() -> {
                // Re-enable button
                if (btn != null) {
                    btn.setEnabled(true);
                    btn.setText("Auto-detect");
                }
                
                if (detectedPath != null) {
                    sqlmapPathField.setText(detectedPath);
                    sqlmapPath = detectedPath;
                    JOptionPane.showMessageDialog(mainPanel, 
                        "SQLMap auto-detected at:\n" + detectedPath, 
                        "Success", 
                        JOptionPane.INFORMATION_MESSAGE);
                    stdout.println("[+] SQLMap auto-detected: " + detectedPath);
                } else {
                    JOptionPane.showMessageDialog(mainPanel, 
                        "Could not auto-detect SQLMap.\n\n" +
                        "On Windows, SQLMap requires Python:\n" +
                        "Try: python C:\\path\\to\\sqlmap.py\n\n" +
                        "Please specify the path manually.", 
                        "Not Found", 
                        JOptionPane.WARNING_MESSAGE);
                    stdout.println("[-] Could not auto-detect SQLMap");
                }
            });
        }).start();
    }
    
    private void testSqlmapPathAsync() {
        final String path = sqlmapPathField.getText().trim();
        
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel, 
                "Please enter a SQLMap path first", 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        stdout.println("[*] Testing SQLMap path: " + path);
        
        // Run test in background thread with proper timeout
        ExecutorService testExecutor = Executors.newSingleThreadExecutor();
        Future<?> future = testExecutor.submit(() -> {
            Process process = null;
            try {
                // Split the path properly to handle "python C:\path\to\sqlmap.py"
                String[] pathParts = path.split("\\s+");
                List<String> command = new ArrayList<>();
                for (String part : pathParts) {
                    command.add(part);
                }
                command.add("--version");
                
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                
                // Redirect stdin to null device to prevent SQLMap from waiting for input
                // Don't close it or SQLMap thinks we want stdin mode
                try {
                    pb.redirectInput(ProcessBuilder.Redirect.from(new File(
                        System.getProperty("os.name").toLowerCase().contains("win") ? "NUL" : "/dev/null"
                    )));
                } catch (Exception e) {
                    // If redirect fails, continue anyway
                }
                
                process = pb.start();
                
                final Process finalProcess = process;
                
                // Read output in separate thread to avoid blocking
                StringBuilder output = new StringBuilder();
                Thread outputReader = new Thread(() -> {
                    try {
                        BufferedReader reader = new BufferedReader(
                            new InputStreamReader(finalProcess.getInputStream()));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line).append("\n");
                        }
                    } catch (IOException e) {
                        // Process was killed, that's okay
                    }
                });
                outputReader.setDaemon(true);
                outputReader.start();
                
                // Wait for process with timeout
                boolean finished = process.waitFor(10, TimeUnit.SECONDS);
                
                if (!finished) {
                    // Process timed out - kill it forcefully
                    stdout.println("[-] Process timed out, killing...");
                    process.destroy();
                    Thread.sleep(1000);
                    if (process.isAlive()) {
                        process.destroyForcibly();
                    }
                    
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(mainPanel, 
                            "SQLMap test timed out after 10 seconds.\n\n" +
                            "This usually means:\n" +
                            "1. Path is incorrect\n" +
                            "2. On Windows, you need: python C:\\path\\to\\sqlmap.py\n" +
                            "3. Python is not installed or not in PATH\n" +
                            "4. SQLMap is waiting for input (shouldn't happen with --version)\n\n" +
                            "Your path: " + path, 
                            "Timeout", 
                            JOptionPane.ERROR_MESSAGE);
                        stderr.println("[-] SQLMap test timed out for: " + path);
                    });
                    return;
                }
                
                // Wait a bit for output reader
                outputReader.join(2000);
                
                final int exitCode = process.exitValue();
                final String outputStr = output.toString().trim();
                
                SwingUtilities.invokeLater(() -> {
                    if (exitCode == 0 && !outputStr.isEmpty()) {
                        sqlmapPath = path;
                        JOptionPane.showMessageDialog(mainPanel, 
                            "SQLMap found successfully!\n\n" + outputStr, 
                            "Success", 
                            JOptionPane.INFORMATION_MESSAGE);
                        stdout.println("[+] SQLMap test successful: " + outputStr);
                    } else if (exitCode == 0 && outputStr.isEmpty()) {
                        JOptionPane.showMessageDialog(mainPanel, 
                            "SQLMap ran but produced no output.\n\n" +
                            "Exit code was 0 (success) but no version info.\n\n" +
                            "Your path might be correct, but try:\n" +
                            "python C:\\path\\to\\sqlmap.py", 
                            "Unexpected Result", 
                            JOptionPane.WARNING_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(mainPanel, 
                            "SQLMap test failed.\n\n" +
                            "Exit code: " + exitCode + "\n\n" +
                            "Output:\n" + outputStr + "\n\n" +
                            "On Windows, use:\npython C:\\path\\to\\sqlmap.py", 
                            "Error", 
                            JOptionPane.ERROR_MESSAGE);
                        stderr.println("[-] SQLMap test failed with exit code: " + exitCode);
                    }
                });
                
            } catch (InterruptedException e) {
                stdout.println("[-] Test was interrupted");
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(mainPanel, 
                        "Error: Could not execute SQLMap at this path.\n\n" +
                        "Make sure:\n" +
                        "1. The path is correct\n" +
                        "2. On Windows, use: python C:\\path\\to\\sqlmap.py\n" +
                        "3. Python is installed (for sqlmap.py)\n\n" +
                        "Error: " + e.getMessage(), 
                        "Error", 
                        JOptionPane.ERROR_MESSAGE);
                    stderr.println("[-] Error testing SQLMap: " + e.getMessage());
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(mainPanel, 
                        "Error testing SQLMap: " + e.getMessage(), 
                        "Error", 
                        JOptionPane.ERROR_MESSAGE);
                    stderr.println("[-] Error testing SQLMap: " + e.getMessage());
                    e.printStackTrace(stderr);
                });
            }
        });
        
        // Monitor the test and ensure it completes
        new Thread(() -> {
            try {
                future.get(15, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                stdout.println("[-] Test thread timed out after 15 seconds, cancelling...");
                future.cancel(true);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(mainPanel, 
                        "Test operation timed out completely.\n\n" +
                        "This indicates a serious issue with the command.\n\n" +
                        "Please verify:\n" +
                        "1. Python is installed: python --version\n" +
                        "2. SQLMap exists at the path\n" +
                        "3. Command works in terminal:\n   " + path + " --version", 
                        "Critical Timeout", 
                        JOptionPane.ERROR_MESSAGE);
                });
            } catch (Exception e) {
                // Test completed or was cancelled
            } finally {
                testExecutor.shutdownNow();
            }
        }).start();
    }
    
    private void testSqlmapPath() {
        String path = sqlmapPathField.getText().trim();
        
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel, 
                "Please enter a SQLMap path first", 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            ProcessBuilder pb = new ProcessBuilder(path, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            // Wait with timeout (10 seconds for user-initiated test)
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                JOptionPane.showMessageDialog(mainPanel, 
                    "SQLMap test timed out after 10 seconds.\n\n" +
                    "This usually means:\n" +
                    "1. Path is incorrect\n" +
                    "2. On Windows, you need: python C:\\path\\to\\sqlmap.py\n" +
                    "3. SQLMap is waiting for input (shouldn't happen with --version)", 
                    "Timeout", 
                    JOptionPane.ERROR_MESSAGE);
                stderr.println("[-] SQLMap test timed out");
                return;
            }
            
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                sqlmapPath = path;
                String version = output.toString().trim();
                JOptionPane.showMessageDialog(mainPanel, 
                    "SQLMap found successfully!\n\n" + version, 
                    "Success", 
                    JOptionPane.INFORMATION_MESSAGE);
                stdout.println("[+] SQLMap test successful: " + version);
            } else {
                JOptionPane.showMessageDialog(mainPanel, 
                    "SQLMap returned an error.\nExit code: " + exitCode + "\n\nOutput:\n" + output.toString(), 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
                stderr.println("[-] SQLMap test failed with exit code: " + exitCode);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainPanel, 
                "Error: Could not execute SQLMap at this path.\n\n" +
                "Make sure:\n" +
                "1. The path is correct\n" +
                "2. On Windows, use: python C:\\path\\to\\sqlmap.py\n" +
                "3. Python is installed (for sqlmap.py)\n\n" +
                "Error: " + e.getMessage(), 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            stderr.println("[-] Error testing SQLMap: " + e.getMessage());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel, 
                "Error testing SQLMap: " + e.getMessage(), 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            stderr.println("[-] Error testing SQLMap: " + e.getMessage());
        }
    }
    
    @Override
    public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation) {
        List<JMenuItem> menuItems = new ArrayList<>();
        
        if (invocation.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_REQUEST ||
            invocation.getInvocationContext() == IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_REQUEST ||
            invocation.getInvocationContext() == IContextMenuInvocation.CONTEXT_PROXY_HISTORY ||
            invocation.getInvocationContext() == IContextMenuInvocation.CONTEXT_TARGET_SITE_MAP_TABLE) {
            
            JMenuItem scanWithSqlmap = new JMenuItem("Scan with SQLMap");
            scanWithSqlmap.addActionListener(e -> {
                IHttpRequestResponse[] messages = invocation.getSelectedMessages();
                if (messages != null && messages.length > 0) {
                    for (IHttpRequestResponse message : messages) {
                        executeSqlmap(message);
                    }
                }
            });
            menuItems.add(scanWithSqlmap);
        }
        
        return menuItems;
    }
    
    private void executeSqlmap(IHttpRequestResponse message) {
        // Validate sqlmap path before execution
        String currentPath = sqlmapPathField.getText().trim();
        if (currentPath.isEmpty()) {
            stderr.println("[-] SQLMap path is not configured");
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(mainPanel, 
                    "SQLMap path is not configured.\nPlease set the path in the SQLMap tab.", 
                    "Configuration Error", 
                    JOptionPane.ERROR_MESSAGE);
            });
            return;
        }
        
        executorService.submit(() -> {
            File tempRequestFile = null;
            try {
                IRequestInfo requestInfo = helpers.analyzeRequest(message);
                URL url = requestInfo.getUrl();
                
                stdout.println("[*] Starting SQLMap scan for: " + url.toString());
                
                // Save request to temporary file
                tempRequestFile = File.createTempFile("burp_request_", ".txt");
                
                // Get request bytes and convert HTTP/2 to HTTP/1.1 for SQLMap compatibility
                byte[] requestBytes = message.getRequest();
                String requestStr = new String(requestBytes);
                
                // SQLMap doesn't handle HTTP/2 well - convert to HTTP/1.1
                if (requestStr.contains(" HTTP/2")) {
                    stdout.println("[*] Converting HTTP/2 to HTTP/1.1 for SQLMap compatibility");
                    requestStr = requestStr.replace(" HTTP/2\r\n", " HTTP/1.1\r\n");
                    requestStr = requestStr.replace(" HTTP/2\n", " HTTP/1.1\n");
                }
                
                // Ensure proper line endings (CRLF) for SQLMap
                requestStr = requestStr.replace("\r\n", "\n").replace("\n", "\r\n");
                
                // Ensure request ends with double CRLF (blank line) for SQLMap
                if (!requestStr.endsWith("\r\n\r\n")) {
                    if (requestStr.endsWith("\r\n")) {
                        requestStr += "\r\n";
                    } else {
                        requestStr += "\r\n\r\n";
                    }
                }
                
                requestBytes = requestStr.getBytes();
                
                FileOutputStream fos = new FileOutputStream(tempRequestFile);
                fos.write(requestBytes);
                fos.close();
                
                stdout.println("[*] Request saved to: " + tempRequestFile.getAbsolutePath());
                
                // Debug: Show request file contents
                if (verboseCheckBox.isSelected()) {
                    try {
                        BufferedReader debugReader = new BufferedReader(new FileReader(tempRequestFile));
                        stdout.println("[DEBUG] Request file contents:");
                        String debugLine;
                        int lineCount = 0;
                        while ((debugLine = debugReader.readLine()) != null) {
                            lineCount++;
                            if (lineCount <= 15) {  // Show first 15 lines
                                if (debugLine.isEmpty()) {
                                    stdout.println("[DEBUG]   (blank line)");
                                } else {
                                    stdout.println("[DEBUG]   " + debugLine);
                                }
                            }
                        }
                        stdout.println("[DEBUG] Total lines: " + lineCount);
                        debugReader.close();
                    } catch (Exception e) {
                        stderr.println("[-] Error reading debug output: " + e.getMessage());
                    }
                }
                
                // Build SQLMap command
                List<String> command = buildSqlmapCommand(tempRequestFile.getAbsolutePath());
                
                stdout.println("[*] Executing: " + String.join(" ", command));
                
                // Execute SQLMap
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                
                // Redirect stdin but DON'T close it for -r mode
                // SQLMap checks stdin and if it's closed, it thinks we want stdin mode
                pb.redirectInput(ProcessBuilder.Redirect.from(new File(
                    System.getProperty("os.name").toLowerCase().contains("win") ? "NUL" : "/dev/null"
                )));
                
                Process process = pb.start();
                
                // Read output
                StringBuilder output = new StringBuilder();
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
                
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (verboseCheckBox.isSelected()) {
                        stdout.println("[SQLMap] " + line);
                    }
                }
                
                int exitCode = process.waitFor();
                stdout.println("[*] SQLMap finished with exit code: " + exitCode);
                
                if (exitCode != 0 && output.length() < 100) {
                    stderr.println("[-] SQLMap may have failed to execute. Check path configuration.");
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(mainPanel, 
                            "SQLMap execution failed.\nPlease verify the SQLMap path is correct.", 
                            "Execution Error", 
                            JOptionPane.ERROR_MESSAGE);
                    });
                    return;
                }
                
                // Parse results and create scanner issues
                parseSqlmapOutput(output.toString(), message);
                
            } catch (IOException e) {
                stderr.println("[-] Error executing SQLMap: " + e.getMessage());
                stderr.println("[-] This often means SQLMap path is incorrect or Python is not available");
                e.printStackTrace(stderr);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(mainPanel, 
                        "Error executing SQLMap:\n" + e.getMessage() + 
                        "\n\nPlease verify:\n" +
                        "1. SQLMap path is correct\n" +
                        "2. Python is installed (for .py files)\n" +
                        "3. SQLMap has execute permissions", 
                        "Execution Error", 
                        JOptionPane.ERROR_MESSAGE);
                });
            } catch (Exception e) {
                stderr.println("[-] Error executing SQLMap: " + e.getMessage());
                e.printStackTrace(stderr);
            } finally {
                if (tempRequestFile != null && tempRequestFile.exists()) {
                    tempRequestFile.delete();
                }
            }
        });
    }
    
    private List<String> buildSqlmapCommand(String requestFilePath) {
        List<String> command = new ArrayList<>();
        
        // Use current path from UI field and split on spaces
        // This handles: "python C:\path\to\sqlmap.py"
        String currentPath = sqlmapPathField.getText().trim();
        String[] pathParts = currentPath.split("\\s+");
        
        for (String part : pathParts) {
            command.add(part);
        }
        
        command.add("-r");
        command.add(requestFilePath);
        
        // Risk level
        int riskLevel = riskLevelCombo.getSelectedIndex() + 1;
        command.add("--risk=" + riskLevel);
        
        // Test level
        int testLevel = testLevelCombo.getSelectedIndex() + 1;
        command.add("--level=" + testLevel);
        
        // Batch mode
        if (batchModeCheckBox.isSelected()) {
            command.add("--batch");
        }
        
        // Random user agent
        if (randomAgentCheckBox.isSelected()) {
            command.add("--random-agent");
        }
        
        // Verbose
        if (verboseCheckBox.isSelected()) {
            command.add("-v");
            command.add("3");
        }
        
        // Flush session (clear cached results)
        if (flushSessionCheckBox.isSelected()) {
            command.add("--flush-session");
        }
        
        // Additional options - remove technique to let SQLMap use its defaults
        command.add("--answers=crack=N");
        
        // Parse additional arguments
        String additionalArgs = additionalArgsArea.getText().trim();
        if (!additionalArgs.isEmpty()) {
            String[] args = additionalArgs.split("\\s+");
            command.addAll(Arrays.asList(args));
        }
        
        return command;
    }
    
    private void parseSqlmapOutput(String output, IHttpRequestResponse message) {
        IRequestInfo requestInfo = helpers.analyzeRequest(message);
        URL url = requestInfo.getUrl();
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        
        // Check for explicit "NOT injectable" warnings first
        boolean notInjectable = output.contains("does not seem to be injectable") ||
                               output.contains("do not appear to be injectable") ||
                               output.contains("not injectable");
        
        // Check if injection was actually found (not just mentioned)
        boolean vulnerabilityFound = (output.contains("is vulnerable") ||
                                     output.contains("sqlmap identified the following injection point") ||
                                     output.contains("Parameter:") && output.contains("Type:") && output.contains("Payload:")) 
                                     && !notInjectable;
        
        if (vulnerabilityFound) {
            // Extract injection details
            String parameter = extractParameter(output);
            String injectionType = extractInjectionType(output);
            String payload = extractPayload(output);
            String dbms = extractDBMS(output);
            
            // Add to results table (use final variables for lambda)
            final String finalParameter = parameter;
            final String finalInjectionType = injectionType;
            final String finalDbms = dbms;
            
            SwingUtilities.invokeLater(() -> {
                resultsTableModel.addRow(new Object[]{
                    timestamp,
                    url.toString(),
                    finalParameter,
                    "VULNERABLE",
                    finalInjectionType,
                    finalDbms
                });
            });
            
            // Create scanner issue
            createScannerIssue(message, parameter, injectionType, payload, dbms, output);
            
            stdout.println("[+] SQL Injection found in parameter: " + parameter);
        } else if (output.contains("all tested parameters do not appear to be injectable") ||
                   output.contains("does not seem to be injectable") ||
                   output.contains("it looks like the back-end DBMS is")) {
            // No injection found - still log the scan
            String param = extractParameter(output);
            final String parameter = param.equals("Unknown") ? "all parameters" : param;
            
            SwingUtilities.invokeLater(() -> {
                resultsTableModel.addRow(new Object[]{
                    timestamp,
                    url.toString(),
                    parameter,
                    "NOT VULNERABLE",
                    "NONE DETECTED",
                    "-"
                });
            });
            
            stdout.println("[-] No SQL injection found in: " + url.toString());
        } else {
            // Scan completed but results unclear
            SwingUtilities.invokeLater(() -> {
                resultsTableModel.addRow(new Object[]{
                    timestamp,
                    url.toString(),
                    "Unknown",
                    "? Scan Completed",
                    "Check logs",
                    "-"
                });
            });
            
            stdout.println("[-] Scan completed for: " + url.toString() + " (check detailed logs)");
        }
    }
    
    private String extractParameter(String output) {
        // Try to find explicit parameter mentions
        Pattern pattern = Pattern.compile("Parameter: ([^\\s\\(]+)");
        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Try to find parameter in "testing" messages
        pattern = Pattern.compile("testing (?:GET|POST|Cookie|JSON) parameter '([^']+)'");
        matcher = pattern.matcher(output);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Try to find parameter in "appears to be" messages  
        pattern = Pattern.compile("(?:GET|POST|Cookie|JSON) parameter '([^']+)' (?:appears|is|seems)");
        matcher = pattern.matcher(output);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Look for any parameter mentions
        pattern = Pattern.compile("parameter[s]? '?([A-Za-z0-9_-]+)'?");
        matcher = pattern.matcher(output);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return "Unknown";
    }
    
    private String extractInjectionType(String output) {
        StringBuilder types = new StringBuilder();
        if (output.contains("boolean-based blind")) types.append("Boolean-based blind, ");
        if (output.contains("error-based")) types.append("Error-based, ");
        if (output.contains("time-based blind")) types.append("Time-based blind, ");
        if (output.contains("UNION query")) types.append("UNION query, ");
        if (output.contains("stacked queries")) types.append("Stacked queries, ");
        
        String result = types.toString();
        return result.isEmpty() ? "Unknown" : result.substring(0, result.length() - 2);
    }
    
    private String extractPayload(String output) {
        Pattern pattern = Pattern.compile("Payload: (.+)");
        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            String payload = matcher.group(1).trim();
            return payload.length() > 100 ? payload.substring(0, 100) + "..." : payload;
        }
        return "See details";
    }
    
    private String extractDBMS(String output) {
        Pattern pattern = Pattern.compile("back-end DBMS: (.+)");
        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            return matcher.group(1).split("\n")[0].trim();
        }
        return "Unknown";
    }
    
    private void createScannerIssue(IHttpRequestResponse message, String parameter, 
                                   String injectionType, String payload, String dbms, 
                                   String fullOutput) {
        
        IScanIssue issue = new CustomScanIssue(
            message.getHttpService(),
            helpers.analyzeRequest(message).getUrl(),
            new IHttpRequestResponse[] { message },
            "SQL Injection (SQLMap)",
            buildIssueDetail(parameter, injectionType, payload, dbms, fullOutput),
            "High",
            "Firm"
        );
        
        callbacks.addScanIssue(issue);
        stdout.println("[+] Added scanner issue for: " + parameter);
    }
    
    private String buildIssueDetail(String parameter, String injectionType, 
                                    String payload, String dbms, String fullOutput) {
        StringBuilder detail = new StringBuilder();
        detail.append("<b>SQL Injection detected by SQLMap</b><br><br>");
        detail.append("<b>Parameter:</b> ").append(helpers.urlEncode(parameter)).append("<br>");
        detail.append("<b>Injection Type:</b> ").append(helpers.urlEncode(injectionType)).append("<br>");
        detail.append("<b>Database:</b> ").append(helpers.urlEncode(dbms)).append("<br>");
        detail.append("<b>Example Payload:</b> <code>").append(helpers.urlEncode(payload)).append("</code><br><br>");
        
        detail.append("<b>Impact:</b><br>");
        detail.append("SQL injection vulnerabilities allow an attacker to interfere with database queries. ");
        detail.append("This can lead to unauthorized data access, data modification, or complete system compromise.<br><br>");
        
        detail.append("<b>Recommendation:</b><br>");
        detail.append("- Use parameterized queries (prepared statements)<br>");
        detail.append("- Implement input validation and sanitization<br>");
        detail.append("- Apply principle of least privilege to database accounts<br>");
        detail.append("- Use Web Application Firewall (WAF) as defense in depth<br><br>");
        
        detail.append("<b>SQLMap Full Output (truncated):</b><br>");
        detail.append("<pre>");
        String truncatedOutput = fullOutput.length() > 2000 ? 
            fullOutput.substring(0, 2000) + "\n... (output truncated)" : fullOutput;
        detail.append(helpers.urlEncode(truncatedOutput));
        detail.append("</pre>");
        
        return detail.toString();
    }
    
    @Override
    public String getTabCaption() {
        return "SQLMap";
    }
    
    @Override
    public Component getUiComponent() {
        return mainPanel;
    }
    
    // Custom Scanner Issue Implementation
    class CustomScanIssue implements IScanIssue {
        private IHttpService httpService;
        private URL url;
        private IHttpRequestResponse[] httpMessages;
        private String name;
        private String detail;
        private String severity;
        private String confidence;
        
        public CustomScanIssue(IHttpService httpService, URL url, 
                              IHttpRequestResponse[] httpMessages, String name,
                              String detail, String severity, String confidence) {
            this.httpService = httpService;
            this.url = url;
            this.httpMessages = httpMessages;
            this.name = name;
            this.detail = detail;
            this.severity = severity;
            this.confidence = confidence;
        }
        
        @Override
        public URL getUrl() { return url; }
        
        @Override
        public String getIssueName() { return name; }
        
        @Override
        public int getIssueType() { return 0x00100000; } // Custom issue type
        
        @Override
        public String getSeverity() { return severity; }
        
        @Override
        public String getConfidence() { return confidence; }
        
        @Override
        public String getIssueBackground() {
            return "SQL injection is a vulnerability that allows an attacker to interfere with " +
                   "database queries that an application makes. This can allow attackers to view, " +
                   "modify, or delete data they shouldn't have access to.";
        }
        
        @Override
        public String getRemediationBackground() {
            return "The most effective way to prevent SQL injection is to use parameterized queries " +
                   "(prepared statements) for all database access. Input validation and least privilege " +
                   "database permissions also help reduce risk.";
        }
        
        @Override
        public String getIssueDetail() { return detail; }
        
        @Override
        public String getRemediationDetail() {
            return "Review all database queries in the affected functionality and ensure parameterized " +
                   "queries are used. Validate and sanitize all user input. Apply principle of least " +
                   "privilege to database user accounts.";
        }
        
        @Override
        public IHttpRequestResponse[] getHttpMessages() { return httpMessages; }
        
        @Override
        public IHttpService getHttpService() { return httpService; }
    }
}
