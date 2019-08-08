/**
 * Copyright (c) 2017, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.gse.util;

import com.sun.org.apache.xerces.internal.util.SynchronizedSymbolTable;
import javafx.embed.swing.SwingNode;
import javafx.scene.Scene;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import org.controlsfx.control.MasterDetailPane;
import org.fife.rsta.ui.CollapsibleSectionPanel;
import org.fife.rsta.ui.search.FindToolBar;
import org.fife.rsta.ui.search.ReplaceToolBar;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.SearchListener;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rtextarea.*;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.dnd.*;
import java.awt.event.*;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class GroovyCodeEditor extends MasterDetailPane implements SearchListener {
    private boolean allowedDrag = false;

    private SwingNode swingNode;
    private JPanel panelSwing = new JPanel(new BorderLayout());
    private final Integer[] tabSizes = new Integer[]{4, 6, 8};
    private RTextScrollPane editor;

    private CollapsibleSectionPanel csp;
    private FindToolBar findToolBar;
    private ReplaceToolBar replaceToolBar;
    private StatusBar statusBar;

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
        codeZone.setCurrentLineHighlightColor(new Color(15132390));
        // Font size
        codeZone.setFont(new Font("Comic Sans MS", Font.BOLD, 14));

//        codeZone.setDropMode(DropMode.INSERT);
//        editor.getTextArea().setDragEnabled(true);

        codeZone.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                String input = Character.toString(e.getKeyChar());
                if ("\"'(-_çà)e$".contains(input) && (e.getModifiers() & java.awt.event.KeyEvent.ALT_MASK) != 0) {
                    codeZone.insert(String.valueOf("#{[|\\^@]€¤".charAt("\"'(-_çà)e$".indexOf(e.getKeyChar()))), codeZone.getCaretPosition());
                }
                if (e.getKeyChar() == '/' && (e.getModifiers() & KeyEvent.CTRL_MASK) != 0) {
                    commentLines(codeZone.getSelectionStart(), codeZone.getSelectionEnd());
                }
                if (e.getKeyCode() == 127 && (e.getModifiers() & KeyEvent.CTRL_MASK) != 0) {
                    e.consume();
                    deleteWord(codeZone.getCaretPosition());
                }
                int down = KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK;
                if ((e.getKeyCode() == 40 || e.getKeyCode() == 38) && (e.getModifiersEx() & down) == down) {
                    duplicateLine(codeZone.getSelectionStart(), codeZone.getSelectionEnd(), e.getKeyCode());
                }
            }
        });

        // Adding keywords highlighting
        AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
        atmf.putMapping("text/myLanguage", "com.powsybl.gse.util.GroovySyntax");
        codeZone.setSyntaxEditingStyle("text/myLanguage");

        // Autocompletion treatment (By default)
//        LanguageSupportFactory lsf = LanguageSupportFactory.get();
//        lsf.register(codeZone);
//        GroovyLanguageSupport support = (GroovyLanguageSupport) lsf.getSupportFor(SyntaxConstants.SYNTAX_STYLE_GROOVY);
//        support.setAutoActivationEnabled(true);
//        support.setParameterAssistanceEnabled(true);
//        support.setShowDescWindow(true);
//        support.setAutoActivationEnabled(true);
//        support.setAutoActivationDelay(0);
//        support.install(codeZone);

        // Autocompletion treatment (Static)
        CompletionProvider provider = createCompletionProvider();
        AutoCompletion ac = new AutoCompletion(provider);
        //ac.setDescriptionWindowColor(null);
        ac.install(codeZone);
        ac.setShowDescWindow(true);
        ac.setAutoCompleteEnabled(true);
        ac.setAutoActivationEnabled(true); //removes the need to input ctrl+space for autocomplete
        ac.setAutoCompleteSingleChoices(false); //single choices are not automatically inputted
        ac.setAutoActivationDelay(0);

        // Editor Pane
        editor = new RTextScrollPane(codeZone);
        return editor;
    }

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
        tabSize.setSelectedItem(new Integer(4));
        tabSize.addActionListener(e -> setTabSize((int) tabSize.getSelectedItem()));

        JPanel bottomPane = new JPanel(new FlowLayout(FlowLayout.LEADING));
        bottomPane.add(new JLabel("Tabulation Size: "));
        bottomPane.add(tabSize);
        bottomPane.add(statusBar);

        panelSwing.add(bottomPane, BorderLayout.SOUTH);

        swingNode.setContent(panelSwing);

        Action a = csp.addBottomComponent(KeyStroke.getKeyStroke(KeyEvent.VK_F, ActionEvent.CTRL_MASK), findToolBar);
        a.putValue(Action.NAME, "Find");
        a = csp.addBottomComponent(KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.CTRL_MASK), replaceToolBar);
        a.putValue(Action.NAME, "Replace");

        return swingNode;
    }

    public GroovyCodeEditor(Scene scene) {
//        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
//        codeArea.richChanges()
//                .filter(ch -> !ch.getInserted().equals(ch.getRemoved()))
//                .subscribe(change -> codeArea.setStyleSpans(0, computeHighlighting(codeArea.getText())));
//        searchBar = new SearchBar(codeArea);
//        searchBar.setCloseAction(e -> {
//            setShowDetailNode(false);
//            codeArea.requestFocus();
//        });
        //setMasterNode(new VirtualizedScrollPane(codeArea));
        setMasterNode(initSwing());
        setShowDetailNode(false);
        // Drag n Drop
//        getCodeZone().setDragEnabled(false);
//        getCodeZone().setDropMode(DropMode.INSERT);
//        RTATextTransferHandler uh = new RTATextTransferHandler();
//        getCodeZone().setTransferHandler(uh);
//        editor.getTextArea().setOnDragEntered(event -> codeArea.setShowCaret(Caret.CaretVisibility.ON));
//        codeArea.setOnDragExited(event -> codeArea.setShowCaret(Caret.CaretVisibility.AUTO));
//        getTextEditorPane().setOnDragDetected(this::onDragDetected);
//        editor.getTextArea().setOnDragOver(this::onDragOver);
//        editor.getTextArea().setOnDragDropped(this::onDragDropped);
//        editor.getTextArea().setOnSelectionDrag(p -> allowedDrag = true);
    }

    /**
     * Create a simple provider that adds some Java-related completions.
     */
    private CompletionProvider createCompletionProvider() {
        DefaultCompletionProvider provider = new DefaultCompletionProvider();
        provider.setAutoActivationRules(true, ".");
        // A BasicCompletion is just a straightforward word completion.
        //new FunctionCompletion()
        //new VariableCompletion()
        //new ShorthandCompletion()

        provider.addCompletion(new BasicCompletion(provider, "mapToGenerator"));
        provider.addCompletion(new BasicCompletion(provider, "mapToLoad"));
        provider.addCompletion(new BasicCompletion(provider, "network.test1"));
        provider.addCompletion(new BasicCompletion(provider, "network.test2"));

        return provider;
    }

    @Override
    public String getSelectedText() {
        return getCodeZone().getSelectedText();
    }

    public void commentLines(int start, int end) {
        try {
            int lineNumberStart = getCodeZone().getLineOfOffset(start);
            int lineNumberEnd = getCodeZone().getLineOfOffset(end);
            for (int i = lineNumberStart; i <= lineNumberEnd; i++) {
                int lineIndexStart = getCodeZone().getLineStartOffset(i);
                if (getCodeZone().getText(lineIndexStart, 2).equals("//")) {
                    getCodeZone().replaceRange("", lineIndexStart, lineIndexStart + 2);
                } else {
                    getCodeZone().replaceRange("//", lineIndexStart, lineIndexStart);
                }
            }
        } catch (BadLocationException e) {
            return;
        }
    }

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
     * Listens for events from the search dialogs and execute the chosen action
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
                showMessageDialog(null, result.getCount() + " occurrences remplacées.");
                break;
        }

        String text;
        if (result.wasFound()) {
            text = "Text found; marked occurrences: " + result.getMarkedCount();
        } else if (type == SearchEvent.Type.MARK_ALL) {
            if (result.getMarkedCount() > 0) {
                text = "Marked occurrences: " + result.getMarkedCount();
            } else {
                text = "";
            }
        } else {
            text = "Text not found";
        }
        statusBar.setLabel(text);
    }

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
            label = new JLabel("Ready");
            setLayout(new BorderLayout());
            add(label);
        }

        void setLabel(String label) {
            this.label.setText(label);
        }
    }

    public boolean hasUnsavedChanges() {
        return getTextEditorPane().isDirty();
    }

    public TextEditorPane getTextEditorPane() {
        return (TextEditorPane) getCodeZone();
    }

    public RSyntaxTextArea getCodeZone() {
        return (RSyntaxTextArea) editor.getTextArea();
    }

    private void showDetailNode() {
        if (!isShowDetailNode()) {
            setShowDetailNode(true);
        }
    }

    private void deleteSelection() {
        if (!getCodeZone().getSelectedText().isEmpty()) {
            getCodeZone().getSelectedText().substring(0, 0);
        }
    }

    public void setTabSize(int size) {
        getCodeZone().setTabSize(size);
    }

//    private void onDragDetected(MouseEvent event) {
////        if (allowedDrag) {
////            Dragboard db = codeArea.startDragAndDrop(TransferMode.COPY_OR_MOVE);
////            ClipboardContent content = new ClipboardContent();
////            content.putString(codeArea.getSelectedText());
////            db.setContent(content);
////            event.consume();
////            allowedDrag = false;
////        }
//        if (allowedDrag) {
//            DropTarget db = editor.getDropTarget();
//            Clipboard content = new Clipboard(getCodeZone().getSelectedText());
//            db.setComponent(content);
//            event.consume();
//            allowedDrag = false;
//        }
//    }

    private void onDragOver(DragEvent event) {
        Dragboard db = event.getDragboard();
        if ((db.hasContent(EquipmentInfo.DATA_FORMAT) && db.getContent(EquipmentInfo.DATA_FORMAT) instanceof EquipmentInfo) || db.hasString()) {
            if (event.getGestureSource() == getCodeZone()) {
                event.acceptTransferModes(TransferMode.MOVE);
            } else {
                event.acceptTransferModes(TransferMode.COPY);
            }
//            CharacterHit hit = getCodeZone().getcahit(event.getX(), event.getY());
//            codeArea.displaceCaret(hit.getInsertionIndex());
        }
    }

    private void onDragDropped(DragEvent event) {
        //codeArea.setShowCaret(Caret.CaretVisibility.AUTO);
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasContent(EquipmentInfo.DATA_FORMAT)) {
            List<EquipmentInfo> equipmentInfoList = (List<EquipmentInfo>) db.getContent(EquipmentInfo.DATA_FORMAT);
            getCodeZone().insert(equipmentInfoList.get(0).getIdAndName().getId(), getCodeZone().getCaretPosition());
            //codeArea.insertText(codeArea.getCaretPosition(), equipmentInfoList.get(0).getIdAndName().getId());
            success = true;
        } else if (db.hasString() && event.getGestureSource() != getCodeZone()) {
            getCodeZone().insert(db.getString(), getCodeZone().getCaretPosition());
            success = true;
        }
//        else if (event.getGestureSource() == getCodeZone()) {
//            CharacterHit hit = getCodeZone().hit(event.getX(), event.getY());
//            codeArea.moveSelectedText(hit.getInsertionIndex());
//            success = true;
//        }
        event.setDropCompleted(success);
        event.consume();
    }

    public void setCode(String code) {
        getCodeZone().setText(code);
    }

    public String getCode() {
        return getCodeZone().getText();
    }

//    public ObservableValue<String> codeProperty() {
//        return codeArea.textProperty();
//    }
//
//    public ObservableValue<Integer> caretPositionProperty() {
//        return codeArea.caretPositionProperty();
//    }

    public String currentPosition() {
        int caretColumn = getCodeZone().getCaretPosition() + 1;
        int paragraphIndex = getCodeZone().getCaretLineNumber() + 1;
        return paragraphIndex + ":" + caretColumn;
    }
}
