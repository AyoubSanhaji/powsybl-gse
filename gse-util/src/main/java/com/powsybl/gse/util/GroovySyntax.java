package com.powsybl.gse.util;

import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rsyntaxtextarea.modes.GroovyTokenMaker;

public class GroovySyntax extends GroovyTokenMaker {
    static TokenMap extraTokens;

    public GroovySyntax() {
        extraTokens = getKeywords();
    }

    @Override
    public void addToken(char[] array, int start, int end, int tokenType, int startOffset, boolean hyperlink) {
        if (tokenType == TokenTypes.IDENTIFIER) {
            int newType = extraTokens.get(array, start, end);
            if (newType > -1) {
                super.addToken(array, start, end, newType, startOffset, hyperlink);
            }
        }
        super.addToken(array, start, end, tokenType, startOffset, hyperlink);
    }

    public void clear() {
        extraTokens = new TokenMap();
    }

    static TokenMap getKeywords() {
//        if (extraTokens == null) {
//            try {
//                extraTokens = new TokenMap(false);
//
//                HashMap<String, Integer> keywords = PdeKeywords.get();
//                Set<String> keys = keywords.keySet();
//                for (String key : keys) {
//                    extraTokens.put(key, keywords.get(key));
//                }
//
//            } catch (Exception e) {
//                System.err.println("Problem loading keywords");
//                System.exit(1);
//            }
//        }
//        return extraTokens;
        TokenMap tokenMap = new TokenMap();

        tokenMap.put("mapToGenerator",  Token.RESERVED_WORD);

        tokenMap.put("lol", Token.FUNCTION);
//        tokenMap.put("scanf",  Token.FUNCTION);
//        tokenMap.put("fopen",  Token.FUNCTION);

        return tokenMap;
    }
}
