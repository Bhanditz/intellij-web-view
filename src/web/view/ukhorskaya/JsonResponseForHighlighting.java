package web.view.ukhorskaya;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import web.view.ukhorskaya.css.GlobalCssMap;
import web.view.ukhorskaya.sessions.HttpSession;

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
    private Map<Integer, Pair<String, String>> pairHashMap = new HashMap<Integer, Pair<String, String>>();

    private IterationState iterationState;

    public JsonResponseForHighlighting(Document myDocument, IterationState iterationState) {
        this.myDocument = myDocument;
        this.iterationState = iterationState;

    }

    public void setJsonResult(@NotNull JSONArray jsonResult) {
        for (int i = 0; i < jsonResult.length(); ++i) {
            try {
                this.jsonResult.put(jsonResult.get(i));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void setPairHashMap(@NotNull Map<Integer, Pair<String, String>> pairHashMap) {
        this.pairHashMap = pairHashMap;
    }

    public String getResult() {
        System.out.println("aaa");
        while (!iterationState.atEnd()) {
            if (myDocument != null) {
                String className = GlobalCssMap.getInstance().getClassFromTextAttribute(iterationState.getMergedAttributes());
                //System.out.println(iterationState.getMergedAttributes() + " className " + className);


                if ((className != null) && (!className.equals("class0"))) {
                    Pair<String, String> pair = pairHashMap.get(myDocument.getLineNumber(iterationState.getStartOffset()));
                    if (pair != null) {
                        String tooltip = pair.getFirst();
                        String severity = pair.getSecond();
                        jsonResult.put(HttpSession.getMapWithPositionsHighlighting(myDocument, iterationState.getStartOffset(), iterationState.getEndOffset(), className, tooltip, severity));
                    }
                    jsonResult.put(HttpSession.getMapWithPositionsHighlighting(myDocument, iterationState.getStartOffset(), iterationState.getEndOffset(), className, "tooltip", "severity"));

                }
                iterationState.advance();
            }
        }
        return jsonResult.toString();
    }
}
