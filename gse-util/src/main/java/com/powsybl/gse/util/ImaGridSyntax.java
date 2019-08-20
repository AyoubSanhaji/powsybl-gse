package com.powsybl.gse.util;

import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rsyntaxtextarea.modes.GroovyTokenMaker;

public class ImaGridSyntax extends GroovyTokenMaker {
    static TokenMap extraTokens;

    public ImaGridSyntax() {
        extraTokens = getKeywords();
    }

    @Override
    public void addToken(char[] array, int start, int end, int tokenType, int startOffset, boolean hyperlink) {
        int newType = tokenType;
        if (tokenType == TokenTypes.IDENTIFIER) {
            if (extraTokens.get(array, start, end) > -1) {
                newType = extraTokens.get(array, start, end);
            }
        }
        super.addToken(array, start, end, newType, startOffset, hyperlink);
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
