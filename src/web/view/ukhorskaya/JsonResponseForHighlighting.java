package web.view.ukhorskaya;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.json.JSONArray;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 9/29/11
 * Time: 4:25 PM
 */
public class JsonResponseForHighlighting {

    private JSONArray jsonResult = new JSONArray();
    private Document myDocument;

    private IterationState iterationState;

    public JsonResponseForHighlighting(Document myDocument, IterationState iterationState) {
        this.myDocument = myDocument;
        this.iterationState = iterationState;
    }

    public String getResult() {
        while (!iterationState.atEnd()) {
            if (myDocument != null) {
                String className = getErrorAttributeName();
                if (className != null) {
                    int lineNumberForElementStart = myDocument.getLineNumber(iterationState.getStartOffset());
                    int charNumberForElementStart = iterationState.getStartOffset() - myDocument.getLineStartOffset(lineNumberForElementStart);
                    int charNumberForElementEnd = iterationState.getEndOffset() - myDocument.getLineStartOffset(lineNumberForElementStart);

                    Map<String, String> map = new HashMap<String, String>();
                    map.put("className", className);
                    map.put("x", "{line: " + lineNumberForElementStart + ", ch: " + charNumberForElementStart + "}");
                    map.put("y", "{line: " + lineNumberForElementStart + ", ch: " + charNumberForElementEnd + "}");
                    jsonResult.put(map);
                }
                iterationState.advance();
            }
        }
       return jsonResult.toString();
    }

    private String getErrorAttributeName() {
        TextAttributes attributes = iterationState.getMergedAttributes();
        if (attributes.getEffectColor() != null) {
            if (attributes.getEffectColor().equals(new Color(255, 0, 0))) {
                return "redLine";
            } else {
                return "greenLine";
            }
        }
        if (attributes.getForegroundColor().equals(new Color(255, 0, 0))) {
            return "error";
        }

        if (attributes.getBackgroundColor().equals(new Color(246, 235, 188))) {
            return "warning";
        }
        return null;
    }
}
