package com.powsybl.gse.util;

import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rsyntaxtextarea.modes.GroovyTokenMaker;

public class imaGridSyntax extends GroovyTokenMaker {
    static TokenMap extraTokens;

    public imaGridSyntax() {
        extraTokens = getKeywords();
    }

    @Override
    public void addToken(char[] array, int start, int end, int tokenType, int startOffset, boolean hyperlink) {
        if (tokenType == TokenTypes.IDENTIFIER) {
            int newType = extraTokens.get(array, start, end);
            if (newType > -1) {
                tokenType = newType;
            }
        }
        super.addToken(array, start, end, tokenType, startOffset, hyperlink);
    }

    public void clear() {
        extraTokens = new TokenMap();
    }

    static TokenMap getKeywords() {
        if (extraTokens == null) {
            try {
                extraTokens = new TokenMap(false);

                extraTokens.put("mapToLoad",  Token.RESERVED_WORD);
                extraTokens.put("timeSeries",  Token.RESERVED_WORD);
                extraTokens.put("ts",  Token.RESERVED_WORD);
                extraTokens.put("filter", Token.FUNCTION);

            } catch (Exception e) {
                System.err.println("Problem loading keywords");
                System.exit(1);
            }
        }
        return extraTokens;
    }
}
