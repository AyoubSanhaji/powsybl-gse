package com.powsybl.gse.util;

import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rsyntaxtextarea.modes.GroovyTokenMaker;

/**
 * @author Ayoub SANHAJI <sanhaji.ayoub at gmail.com>
 */
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
            extraTokens = new TokenMap(false);

            // Functions
            extraTokens.put("mapToGenerators",  Token.FUNCTION);
            extraTokens.put("mapToLoads",  Token.FUNCTION);
            extraTokens.put("mapToHvdcLines",  Token.FUNCTION);
            extraTokens.put("mapToBoundaryLines",  Token.FUNCTION);
            extraTokens.put("mapToBreakers",  Token.FUNCTION);
            extraTokens.put("unmappedGenerators",  Token.FUNCTION);
            extraTokens.put("unmappedLoads",  Token.FUNCTION);
            extraTokens.put("unmappedHvdcLines",  Token.FUNCTION);
            extraTokens.put("unmappedBoundaryLines",  Token.FUNCTION);
            extraTokens.put("unmappedBreakers",  Token.FUNCTION);

            // Reserved Words
            extraTokens.put("variable",  Token.RESERVED_WORD);
            extraTokens.put("timeSeriesName",  Token.RESERVED_WORD);
            extraTokens.put("filter", Token.RESERVED_WORD);
            extraTokens.put("distributionKey",  Token.RESERVED_WORD);
            extraTokens.put("timeSeries",  Token.RESERVED_WORD);
            extraTokens.put("ts",  Token.RESERVED_WORD);
            extraTokens.put("ignoreLimits",  Token.RESERVED_WORD);
        }
        return extraTokens;
    }
}
