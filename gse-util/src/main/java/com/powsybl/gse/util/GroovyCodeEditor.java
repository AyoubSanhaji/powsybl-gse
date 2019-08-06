/**
 * Copyright (c) 2017, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.gse.util;

import javafx.embed.swing.SwingNode;
import javafx.scene.Scene;
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
        // Drag n Drop
//        codeZone.setDragEnabled(false);
//        RTATextTransferHandler uh = new RTATextTransferHandler();
//        codeZone.setTransferHandler(uh);

        // Handling the ALT GR events
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
                if (e.getKeyCode() == 40 && (e.getModifiers() & KeyEvent.CTRL_MASK + KeyEvent.SHIFT_MASK) != 0) {
                    duplicateLine(codeZone.getSelectionEnd());
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
        bottomPane.add(new JLabel("Taille Tabulation: "));
        bottomPane.add(tabSize);
        bottomPane.add(statusBar);

        panelSwing.add(bottomPane, BorderLayout.SOUTH);

        swingNode.setContent(panelSwing);

        Action a = csp.addBottomComponent(KeyStroke.getKeyStroke(KeyEvent.VK_F, ActionEvent.CTRL_MASK), findToolBar);
        a.putValue(Action.NAME, "Rechercher");
        a = csp.addBottomComponent(KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.CTRL_MASK), replaceToolBar);
        a.putValue(Action.NAME, "Remplacer");

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

//        VBox vBox = new VBox();
//        vBox.getChildren().add(searchBar);
//        setDetailNode(vBox);
//        setDetailSide(Side.TOP);
//        setShowDetailNode(false);

//        setOnKeyPressed((KeyEvent ke) -> {
//            if (searchKeyCombination.match(ke)) {
//                setSearchBar(vBox, "search");
//                showDetailNode();
//                searchBar.requestFocus();
//            } else if (replaceWordKeyCombination.match(ke)) {
//                setShowDetailNode(false);
//                setSearchBar(vBox, "replace");
//                searchBar.setReplaceAllAction(event -> replaceAllOccurences(searchBar.getSearchedText(), codeArea.getText(), searchBar.isCaseSensitiveBoxSelected(), searchBar.isWordSensitiveBoxSelected()));
//                searchBar.setReplaceAction(event -> replaceCurrentOccurence(searchBar.getCurrentMatchStart(), searchBar.getCurrentMatchEnd()));
//                showDetailNode();
//                searchBar.requestFocus();
//            }
//
//        });
        //editor.getTextArea().setDr
//        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, this::setTabulationSpace);
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

    public void duplicateLine(int end) {
        if (getSelectedText() != null) {
            getCodeZone().insert(getSelectedText(), end);
        } else {
            try {
                int lineIndexStart = getCodeZone().getLineStartOffsetOfCurrentLine();
                int lineIndexEnd = getCodeZone().getLineEndOffsetOfCurrentLine();
                String lineText = getCodeZone().getText(lineIndexStart, lineIndexEnd - lineIndexStart);
                if (lineText.contains("\n")) {
                    getCodeZone().insert(lineText, lineIndexEnd);
                } else {
                    getCodeZone().insert("\n".concat(lineText), lineIndexEnd);
                }
            } catch (BadLocationException e) {
                return;
            }
        }
    }

    /**
     * Creates our Find and Replace toolbars.
     */
    public void initSearchDialogs() {
        // This ties the properties of the two dialogs together (match case,
        // regex, etc.).
        SearchContext context = new SearchContext();

        // Create tool bars and tie their search contexts together also.
        findToolBar = new FindToolBar(this);
        findToolBar.setSearchContext(context);
        replaceToolBar = new ReplaceToolBar(this);
        replaceToolBar.setSearchContext(context);
    }


    /**
     * Listens for events from our search dialogs and actually does the dirty
     * work.
     */
    @Override
    public void searchEvent(SearchEvent e) {
        SearchEvent.Type type = e.getType();
        SearchContext context = e.getSearchContext();
        SearchResult result;

        switch (type) {
            default: // Prevent FindBugs warning later
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
            text = "Texte trouvé; occurrences marquées: " + result.getMarkedCount();
        } else if (type == SearchEvent.Type.MARK_ALL) {
            if (result.getMarkedCount() > 0) {
                text = "Occurrences marquées: " + result.getMarkedCount();
            } else {
                text = "";
            }
        } else {
            text = "Texte non trouvé";
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
            label = new JLabel("Prêt");
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

//    private int getTabSize() {
//        return getCodeZone().getTabSize();
//    }
//
//    private static String generateTabSpace(int size) {
//        return StringUtils.repeat(" ", size);
//    }

//    private int tabSpacesToAdd(int currentPosition) {
//        return getTabSize() - (currentPosition % getTabSize());
//    }

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
//            ClipboardContent content = new ClipboardContent();
//            content.putString(getCodeZone().getSelectedText());
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

//    private static String styleClass ( int tokenType){
//        switch (tokenType) {
//            case GroovyTokenTypes.LCURLY:
//            case GroovyTokenTypes.RCURLY:
//                return "brace";
//
//            case GroovyTokenTypes.LBRACK:
//            case GroovyTokenTypes.RBRACK:
//                return "bracket";
//
//            case GroovyTokenTypes.LPAREN:
//            case GroovyTokenTypes.RPAREN:
//                return "paren";
//
//            case GroovyTokenTypes.SEMI:
//                return "semicolon";
//
//            case GroovyTokenTypes.STRING_LITERAL:
//            case GroovyTokenTypes.REGEXP_LITERAL:
//            case GroovyTokenTypes.DOLLAR_REGEXP_LITERAL:
//                return "string";
//
//            case GroovyTokenTypes.ML_COMMENT:
//            case GroovyTokenTypes.SH_COMMENT:
//            case GroovyTokenTypes.SL_COMMENT:
//                return "comment";
//
//            case GroovyTokenTypes.ABSTRACT:
//            case GroovyTokenTypes.CLASS_DEF:
//            case GroovyTokenTypes.EXTENDS_CLAUSE:
//            case GroovyTokenTypes.IMPLEMENTS_CLAUSE:
//            case GroovyTokenTypes.IMPORT:
//            case GroovyTokenTypes.LITERAL_as:
//            case GroovyTokenTypes.LITERAL_assert:
//            case GroovyTokenTypes.LITERAL_boolean:
//            case GroovyTokenTypes.LITERAL_break:
//            case GroovyTokenTypes.LITERAL_byte:
//            case GroovyTokenTypes.LITERAL_case:
//            case GroovyTokenTypes.LITERAL_catch:
//            case GroovyTokenTypes.LITERAL_char:
//            case GroovyTokenTypes.LITERAL_class:
//            case GroovyTokenTypes.LITERAL_continue:
//            case GroovyTokenTypes.LITERAL_def:
//            case GroovyTokenTypes.LITERAL_default:
//            case GroovyTokenTypes.LITERAL_double:
//            case GroovyTokenTypes.LITERAL_else:
//            case GroovyTokenTypes.LITERAL_enum:
//            case GroovyTokenTypes.LITERAL_extends:
//            case GroovyTokenTypes.LITERAL_false:
//            case GroovyTokenTypes.LITERAL_finally:
//            case GroovyTokenTypes.LITERAL_float:
//            case GroovyTokenTypes.LITERAL_for:
//            case GroovyTokenTypes.LITERAL_if:
//            case GroovyTokenTypes.LITERAL_implements:
//            case GroovyTokenTypes.LITERAL_import:
//            case GroovyTokenTypes.LITERAL_in:
//            case GroovyTokenTypes.LITERAL_instanceof:
//            case GroovyTokenTypes.LITERAL_int:
//            case GroovyTokenTypes.LITERAL_interface:
//            case GroovyTokenTypes.LITERAL_long:
//            case GroovyTokenTypes.LITERAL_native:
//            case GroovyTokenTypes.LITERAL_new:
//            case GroovyTokenTypes.LITERAL_null:
//            case GroovyTokenTypes.LITERAL_package:
//            case GroovyTokenTypes.LITERAL_private:
//            case GroovyTokenTypes.LITERAL_protected:
//            case GroovyTokenTypes.LITERAL_public:
//            case GroovyTokenTypes.LITERAL_return:
//            case GroovyTokenTypes.LITERAL_short:
//            case GroovyTokenTypes.LITERAL_static:
//            case GroovyTokenTypes.LITERAL_super:
//            case GroovyTokenTypes.LITERAL_switch:
//            case GroovyTokenTypes.LITERAL_synchronized:
//            case GroovyTokenTypes.LITERAL_this:
//            case GroovyTokenTypes.LITERAL_threadsafe:
//            case GroovyTokenTypes.LITERAL_throw:
//            case GroovyTokenTypes.LITERAL_throws:
//            case GroovyTokenTypes.LITERAL_transient:
//            case GroovyTokenTypes.LITERAL_true:
//            case GroovyTokenTypes.LITERAL_try:
//            case GroovyTokenTypes.LITERAL_void:
//            case GroovyTokenTypes.LITERAL_volatile:
//            case GroovyTokenTypes.LITERAL_while:
//            case GroovyTokenTypes.PACKAGE_DEF:
//            case GroovyTokenTypes.UNUSED_CONST:
//            case GroovyTokenTypes.UNUSED_DO:
//            case GroovyTokenTypes.UNUSED_GOTO:
//            case GroovyTokenTypes.TYPE:
//                return "keyword";
//
//            default:
//                return null;
//        }
//    }

//    private int length (GroovySourceToken token){
//        int offset1 = codeArea.getDocument().position(token.getLine() - 1, token.getColumn() - 1).toOffset();
//        int offset2 = codeArea.getDocument().position(token.getLineLast() - 1, token.getColumnLast() - 1).toOffset();
//        return offset2 - offset1;
//    }

//    private void buildStyle (String styleClass, StyleSpansBuilder < Collection < String >> spansBuilder,
//                             int length, Token token){
//        if (styleClass != null) {
//            spansBuilder.add(Collections.singleton(styleClass), length);
//        } else if (!KEYWORDS_LOADER.getServices().isEmpty()) {
//            for (KeywordsProvider styleExtension : KEYWORDS_LOADER.getServices()) {
//                String style = styleExtension.styleClass(token.getText());
//                if (style != null) {
//                    spansBuilder.add(Collections.singleton(style), length);
//                } else {
//                    spansBuilder.add(Collections.emptyList(), length);
//                }
//            }
//        } else {
//            spansBuilder.add(Collections.emptyList(), length);
//        }
//    }

//    private StyleSpans<Collection<String>> computeHighlighting (String text){
//        Stopwatch stopwatch = Stopwatch.createStarted();
//
//        boolean added = false;
//        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
//        if (!text.isEmpty()) {
//            SourceBuffer sourceBuffer = new SourceBuffer();
//            try (UnicodeEscapingReader reader = new UnicodeEscapingReader(new StringReader(text), sourceBuffer)) {
//                GroovyLexer lexer = new GroovyLexer(new UnicodeLexerSharedInputState(reader));
//                lexer.setWhitespaceIncluded(true);
//                TokenStream tokenStream = lexer.plumb();
//                Token token = tokenStream.nextToken();
//                while (token.getType() != Token.EOF_TYPE) {
//                    String styleClass = styleClass(token.getType());
//                    int length = length((GroovySourceToken) token);
//                    buildStyle(styleClass, spansBuilder, length, token);
//                    added = true;
//                    token = tokenStream.nextToken();
//                }
//            } catch (IOException e) {
//                throw new UncheckedIOException(e);
//            } catch (TokenStreamException e) {
//                LOGGER.trace(e.getMessage());
//            }
//        }
//
//        if (!added) {
//            spansBuilder.add(Collections.emptyList(), 0);
//        }
//
//        stopwatch.stop();
//        LOGGER.trace("Highlighting of {} characters computed in {} ms", text.length(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
//
//        return spansBuilder.create();
//    }
}
