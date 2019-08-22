/**
 * Copyright (c) 2017, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.gse.util;

import javafx.embed.swing.SwingNode;
import javafx.event.EventType;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import org.controlsfx.control.MasterDetailPane;
import org.fife.rsta.ui.CollapsibleSectionPanel;
import org.fife.rsta.ui.search.FindToolBar;
import org.fife.rsta.ui.search.ReplaceToolBar;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.SearchListener;
import org.fife.ui.autocomplete.*;
import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ResourceBundle;

/**
 * @author Ayoub SANHAJI <sanhaji.ayoub at gmail.com>
 */
public class GroovyCodeEditor extends MasterDetailPane implements SearchListener {
    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle("lang.GroovyCodeEditor");

    private SwingNode swingNode;
    private JPanel panelSwing = new JPanel(new BorderLayout());
    private final Integer[] tabSizes = new Integer[]{2, 4, 6, 8};
    private RTextScrollPane editor;

    private CollapsibleSectionPanel csp;
    private FindToolBar findToolBar;
    private ReplaceToolBar replaceToolBar;
    private StatusBar statusBar;

    /**
     * Init all the editor's functionalities.
     * @return the editor object
     */
    private RTextScrollPane initEditor() {
        RSyntaxTextArea codeZone;
        // Template are enabled
        RSyntaxTextArea.setTemplatesEnabled(true);
        // Highlight lines + lines's numbers
        codeZone = new TextEditorPane();
        codeZone.setRows(100);
        codeZone.setColumns(45);
        // Tabulation size
        codeZone.setTabSize(4);
        // Is called when the caret stops moving after a short period to show all the occurrences
        codeZone.setMarkOccurrences(true);
        codeZone.setMarkOccurrencesDelay(1);
        // Language syntax
        codeZone.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_GROOVY);
        // Line's folding parameter
        codeZone.setCodeFoldingEnabled(true);
        codeZone.setCurrentLineHighlightColor(new Color(15792383));
        // Font
        codeZone.setFont(new Font("Comic Sans MS", Font.BOLD, 14));

        codeZone.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                String input = Character.toString(e.getKeyChar());
                if ("é\"'(-è_çà)e$".contains(input) && (e.isAltDown() || e.isAltGraphDown())) {
                    codeZone.insert(String.valueOf("~#{[|`\\^@]€¤".charAt("é\"'(-è_çà)e$".indexOf(e.getKeyChar()))), codeZone.getCaretPosition());
                }
                if (e.getKeyChar() == KeyEvent.VK_SLASH && e.isControlDown()) {
                    commentLines(codeZone.getSelectionStart(), codeZone.getSelectionEnd());
                }
                if (e.getKeyCode() == KeyEvent.VK_DELETE && e.isControlDown()) {
                    e.consume();
                    deleteWord(codeZone.getCaretPosition());
                }
                int down = KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK;
                if ((e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_UP) && (e.getModifiersEx() & down) == down) {
                    duplicateLine(codeZone.getSelectionStart(), codeZone.getSelectionEnd(), e.getKeyCode());
                }
            }
        });

        // Adding keywords highlighting
        AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
        atmf.putMapping("text/myLanguage", "com.powsybl.gse.util.ImaGridSyntax");
        codeZone.setSyntaxEditingStyle("text/myLanguage");

        // Autocompletion treatment
        CompletionProvider provider = createCompletionProvider();
        AutoCompletion ac = new AutoCompletion(provider);
        ac.install(codeZone);
        ac.setShowDescWindow(true);
        ac.setAutoCompleteEnabled(true);
        ac.setAutoActivationEnabled(true); //removes the need to input ctrl+space for autocomplete
        ac.setAutoCompleteSingleChoices(false); //single choices are not automatically inputted
        ac.setAutoActivationDelay(0);
        // Just for the moment since we have some pbs with the autocompletion window
        ac.uninstall();

        // Editor Pane
        editor = new RTextScrollPane(codeZone);
        return editor;
    }

    /**
     * Create the swingNode that contains all of the editor, search toolbars and the tab size bar
     * @return SwingNode object
     */
    public SwingNode initSwing() {
        swingNode = new SwingNode();
        csp = new CollapsibleSectionPanel();
        panelSwing.add(csp, BorderLayout.CENTER);

        initSearchDialogs();

        csp.add(initEditor());

        ErrorStrip errorStrip = new ErrorStrip(getCodeZone());
        csp.add(errorStrip, BorderLayout.LINE_END);
        statusBar = new StatusBar();

        JComboBox<Integer> tabSize = new JComboBox<>(tabSizes);
        tabSize.setPreferredSize(new Dimension(80, 25));
        setTabSize(2);
        tabSize.addActionListener(e -> setTabSize((int) tabSize.getSelectedItem()));

        JPanel bottomPane = new JPanel(new BorderLayout());
        bottomPane.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

        JPanel tabulationManager = new JPanel();
        tabulationManager.add(new JLabel(RESOURCE_BUNDLE.getString("TabulationSize")));
        tabulationManager.add(tabSize);

        bottomPane.add(tabulationManager, BorderLayout.WEST);
        JLabel caretPosition = new JLabel(getCaretPosition());
        caretPosition.setHorizontalAlignment(JTextField.CENTER);
        bottomPane.add(caretPosition, BorderLayout.CENTER);
        getCodeZone().addCaretListener(e -> caretPosition.setText(getCaretPosition()));
        bottomPane.add(statusBar, BorderLayout.EAST);
        panelSwing.add(bottomPane, BorderLayout.SOUTH);

        swingNode.setContent(panelSwing);

        Action a = csp.addBottomComponent(KeyStroke.getKeyStroke(KeyEvent.VK_F, ActionEvent.CTRL_MASK), findToolBar);
        a.putValue(Action.NAME, "Find");
        a = csp.addBottomComponent(KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.CTRL_MASK), replaceToolBar);
        a.putValue(Action.NAME, "Replace");

        // Find the selected text after pressing CTRL+F
        getCodeZone().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if ((e.getKeyCode() == KeyEvent.VK_F || e.getKeyCode() == KeyEvent.VK_R) && e.isControlDown()) {
                    if (getCodeZone().getSelectedText() != null) {
                        findToolBar.getSearchContext().setSearchFor(getCodeZone().getSelectedText());
                    } else {
                        findToolBar.getSearchContext().setSearchFor("");
                        replaceToolBar.getSearchContext().setSearchFor("");
                    }
                }
            }
        });

        // Handle the ESC pressing used to close the search toolbars
        swingNode.setOnKeyPressed(event -> {
            if (event.getCode().compareTo(KeyCode.ESCAPE) == 0) {
                findToolBar.getSearchContext().setSearchFor("");
                SearchEngine.markAll(getCodeZone(), findToolBar.getSearchContext());
                statusBar.setLabel(RESOURCE_BUNDLE.getString("Ready"));
            }
        });

        // Dragging Bug: resolved in version 10 openJDK
        swingNode.addEventFilter(EventType.ROOT, event -> {
            String type = event.getEventType().getName();
            if (type.equals("DRAG_ENTERED") || type.equals("DRAG_OVER")) {
                event.consume();
            }
        });
        return swingNode;
    }

    public GroovyCodeEditor(Scene scene) {
        setMasterNode(initSwing());
        setShowDetailNode(false);
    }

    /**
     * Add words to the autocomplete list
     * @return provider object
     */
    private CompletionProvider createCompletionProvider() {
        DefaultCompletionProvider provider = new DefaultCompletionProvider();
        provider.setAutoActivationRules(true, "");
        //new FunctionCompletion()
        //new VariableCompletion()
        //new ShorthandCompletion()
        provider.addCompletion(new BasicCompletion(provider, "mapToGenerators"));
        provider.addCompletion(new BasicCompletion(provider, "mapToLoads"));
        provider.addCompletion(new BasicCompletion(provider, "mapToHvdcLines"));
        provider.addCompletion(new BasicCompletion(provider, "mapToBoundaryLines"));
        provider.addCompletion(new BasicCompletion(provider, "mapToBreakers"));
        provider.addCompletion(new BasicCompletion(provider, "unmappedGenerators"));
        provider.addCompletion(new BasicCompletion(provider, "unmappedLoads"));
        provider.addCompletion(new BasicCompletion(provider, "unmappedHvdcLines"));
        provider.addCompletion(new BasicCompletion(provider, "unmappedBoundaryLines"));
        provider.addCompletion(new BasicCompletion(provider, "unmappedBreakers"));
        provider.addCompletion(new BasicCompletion(provider, "variable"));
        provider.addCompletion(new BasicCompletion(provider, "timeSeriesName"));
        provider.addCompletion(new BasicCompletion(provider, "filter"));
        provider.addCompletion(new BasicCompletion(provider, "distributionKey"));
        provider.addCompletion(new BasicCompletion(provider, "timeSeries"));
        provider.addCompletion(new BasicCompletion(provider, "ts"));
        provider.addCompletion(new BasicCompletion(provider, "ignoreLimits"));

        return provider;
    }

    @Override
    public String getSelectedText() {
        return getCodeZone().getSelectedText();
    }

    /**
     * Get the caret position 'rowNumber : columnNumber'
     * @return
     */
    private String getCaretPosition() {
        return (getCodeZone().getCaretLineNumber() + 1) + ":" + (getCodeZone().getCaretOffsetFromLineStart() + 1);
    }

    /**
     * Comment the lines which their
     * @param start : selected text's start position
     * @param end : selected text's end position
     */
    public void commentLines(int start, int end) {
        try {
            int lineNumberStart = getCodeZone().getLineOfOffset(start);
            int lineNumberEnd = getCodeZone().getLineOfOffset(end);
            boolean sameType = isSameType(start, end);
            for (int i = lineNumberStart; i <= lineNumberEnd; i++) {
                int lineIndexStart = getCodeZone().getLineStartOffset(i);
                int lineIndexEnd = getCodeZone().getLineEndOffset(i);
                int beginWithSlashes = beginsWith(getCodeZone().getText(lineIndexStart, lineIndexEnd - lineIndexStart));
                if (beginWithSlashes != -1 && sameType) {
                    getCodeZone().replaceRange("", lineIndexStart + beginWithSlashes, lineIndexStart + beginWithSlashes + 2);
                } else {
                    getCodeZone().replaceRange("//", lineIndexStart, lineIndexStart);
                }
            }
        } catch (BadLocationException e) {
            return;
        }
    }

    /**
     * Return true if all the lines are either commented or not
     * @param start : selected text's start position
     * @param end : selected text's end position
     * @return
     */
    private boolean isSameType(int start, int end) {
        try {
            int count = -1;
            int lineNumberStart = getCodeZone().getLineOfOffset(start);
            int lineNumberEnd = getCodeZone().getLineOfOffset(end);
            for (int i = lineNumberStart; i <= lineNumberEnd; i++) {
                int lineIndexStart = getCodeZone().getLineStartOffset(i);
                int lineIndexEnd = getCodeZone().getLineEndOffset(i);
                int beginWithSlashes = beginsWith(getCodeZone().getText(lineIndexStart, lineIndexEnd - lineIndexStart));
                if (beginWithSlashes != -1) {
                    count++;
                }
            }
            return count == lineNumberEnd - lineNumberStart;
        } catch (BadLocationException e) {
            return false;
        }
    }

    /**
     * Either the line begins with // or not
     * @param line
     * @return the index of the first slash if exists else return -1
     */
    private int beginsWith(String line) {
        for (int i = 0; i < line.length() - 1; i++) {
            if (line.charAt(i) == ' ' || line.charAt(i) == '\t' || line.charAt(i) == '/') {
                if ("//".equals(line.substring(i, i + 2))) {
                    return i;
                }
            } else {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Delete the word following the cursor position
     * @param start : cursor position
     */
    public void deleteWord(int start) {
        try {
            int lineIndexEnd = getCodeZone().getLineEndOffsetOfCurrentLine();
            String[] words = getCodeZone().getText(start, lineIndexEnd - start).split("[\\s|.|(|)|/|\"|\'|=|+|-|*|{|}]");
            if (words.length != 0 && words[0].length() != 0) {
                getCodeZone().replaceRange("", start, start + words[0].length());
            } else {
                getCodeZone().replaceRange("", start, start + 1);
            }
        } catch (BadLocationException e) {
            return;
        }
    }

    /**
     * Duplicate the selected lines above or below
     * @param start : selected text's start position
     * @param end : selected text's end position
     * @param key : up / down
     */
    public void duplicateLine(int start, int end, int key) {
        if (getSelectedText() != null) {
            if (key == 40) {
                getCodeZone().insert(getSelectedText(), end);
            }
            if (key == 38) {
                getCodeZone().insert(getSelectedText(), start);
            }
        } else {
            try {
                int lineIndexStart = getCodeZone().getLineStartOffsetOfCurrentLine();
                int lineIndexEnd = getCodeZone().getLineEndOffsetOfCurrentLine();
                String lineText = getCodeZone().getText(lineIndexStart, lineIndexEnd - lineIndexStart);
                if (lineText.contains("\n")) {
                    if (key == 40) {
                        getCodeZone().insert(lineText, lineIndexEnd);
                    }
                    if (key == 38) {
                        getCodeZone().insert(lineText, lineIndexStart);
                    }
                } else {
                    if (key == 40) {
                        getCodeZone().insert("\n".concat(lineText), lineIndexEnd);
                    }
                    if (key == 38) {
                        getCodeZone().insert("\n".concat(lineText), lineIndexStart);
                    }
                }
            } catch (BadLocationException e) {
                return;
            }
        }
    }

    /**
     * Creates the Find and Replace toolbars.
     */
    public void initSearchDialogs() {
        // This ties the properties of the two dialogs together (match case, regex, etc.).
        SearchContext context = new SearchContext();
        // Create tool bars and tie their search contexts together also.
        findToolBar = new FindToolBar(this);
        findToolBar.setSearchContext(context);
        replaceToolBar = new ReplaceToolBar(this);
        replaceToolBar.setSearchContext(context);
    }

    /**
     * Uses by the search toolbars to execute the chosen action
     * @param e : search event
     */
    @Override
    public void searchEvent(SearchEvent e) {
        SearchEvent.Type type = e.getType();
        SearchContext context = e.getSearchContext();
        SearchResult result;
        switch (type) {
            default:
            case MARK_ALL:
                result = SearchEngine.markAll(getCodeZone(), context);
                break;
            case FIND:
                result = SearchEngine.find(getCodeZone(), context);
                if (!result.wasFound()) {
                    getCodeZone().setCaretPosition(0);
                    UIManager.getLookAndFeel().provideErrorFeedback(getCodeZone());
                }
                break;
            case REPLACE:
                result = SearchEngine.replace(getCodeZone(), context);
                if (!result.wasFound()) {
                    UIManager.getLookAndFeel().provideErrorFeedback(getCodeZone());
                }
                break;
            case REPLACE_ALL:
                result = SearchEngine.replaceAll(getCodeZone(), context);
                showMessageDialog(null, result.getCount() + RESOURCE_BUNDLE.getString("Replaced"));
                break;
        }
        String text;
        if (result.wasFound()) {
            text = RESOURCE_BUNDLE.getString("Marked1") + result.getMarkedCount();
        } else if (type == SearchEvent.Type.MARK_ALL) {
            if (result.getMarkedCount() > 0) {
                text = RESOURCE_BUNDLE.getString("Marked2") + result.getMarkedCount();
            } else {
                text = "";
            }
        } else {
            text = RESOURCE_BUNDLE.getString("NotFound");
        }
        statusBar.setLabel(text);
    }

    /**
     * Show a message in a dialog
     * @param title : title of the dialog
     * @param message : displayed message
     * @return
     */
    public static int showMessageDialog(String title, Object message) {
        JOptionPane pane = new JOptionPane(message, JOptionPane.INFORMATION_MESSAGE);
        JDialog dialog = pane.createDialog(null, title);
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true);
        dialog.dispose();
        Object selectedValue = pane.getValue();
        if (selectedValue == null) {
            return JOptionPane.CLOSED_OPTION;
        }
        if (selectedValue instanceof Integer) {
            return ((Integer) selectedValue).intValue();
        }
        return JOptionPane.CLOSED_OPTION;
    }

    /**
     * The status bar for this application.
     */
    private static class StatusBar extends JPanel {
        private JLabel label;

        StatusBar() {
            label = new JLabel(RESOURCE_BUNDLE.getString("Ready"));
            setLayout(new BorderLayout());
            add(label);
        }

        void setLabel(String label) {
            this.label.setText(label);
        }
    }

    /**
     * Either the text area has some unsaved changes or not.
     * @return bool
     */
    public boolean hasUnsavedChanges() {
        return getTextEditorPane().isDirty();
    }

    public TextEditorPane getTextEditorPane() {
        return (TextEditorPane) getCodeZone();
    }

    public RSyntaxTextArea getCodeZone() {
        return (RSyntaxTextArea) editor.getTextArea();
    }

    public void setTabSize(int size) {
        getCodeZone().setTabSize(size);
    }

    public void setCode(String code) {
        getCodeZone().setText(code);
    }

    public String getCode() {
        return getCodeZone().getText();
    }
}
