package web.view.ukhorskaya;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ui.EmptyIcon;
import org.json.JSONArray;
import web.view.ukhorskaya.autocomplete.IconHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 10/20/11
 * Time: 1:53 PM
 */

public class JsonResponseForCompletion {

    private final int lineNumber;
    private final int charNumber;
    private final Editor currentEditor;
    private final Project currentProject;

    public JsonResponseForCompletion(int lineNumber, int charNumber, Editor currentEditor, Project currentProject) {
        this.lineNumber = lineNumber;
        this.charNumber = charNumber;
        this.currentEditor = currentEditor;
        this.currentProject = currentProject;
    }

    public String getResult() {
        final VisualPosition visualPosition = new VisualPosition(lineNumber, charNumber);

        final Ref<LookupElement[]> lookupRef = new Ref<LookupElement[]>();

        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            @Override
            public void run() {
                currentEditor.getCaretModel().moveToVisualPosition(visualPosition);

                new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(currentProject, currentEditor, 1, false);

                LookupImpl lookup = (LookupImpl) LookupManager.getActiveLookup(currentEditor);

                LookupElement[] myItems = lookup == null ? null : lookup.getItems().toArray(new LookupElement[lookup.getItems().size()]);
                if (lookup != null) {
                    lookup.hide();
                }
                lookupRef.set(myItems);
            }
        }, ModalityState.defaultModalityState());

        JSONArray resultString = new JSONArray();
        if (lookupRef.get() != null) {
            for (final LookupElement item : lookupRef.get()) {
                final LookupElementPresentation presentation = new LookupElementPresentation();
                ApplicationManager.getApplication().invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        item.renderElement(presentation);
                    }
                }, ModalityState.defaultModalityState());
                Map<String, String> map = new HashMap<String, String>();
                if ((presentation.getIcon() != null) && !(presentation.getIcon() instanceof EmptyIcon)) {
                    map.put("icon", "/fticons/" + IconHelper.getInstance().addIconToMap(presentation.getIcon()));
                } else {
                    map.put("icon", "");
                }
                if (presentation.getItemText() != null) {
                    map.put("name", presentation.getItemText());
                } else {
                    map.put("name", "");
                }

                if (presentation.getTailText() != null) {
                    map.put("tail", presentation.getTailText());
                } else {
                    map.put("tail", "");
                }
                resultString.put(map);
            }
        }

        return resultString.toString();
        //writeResponse(resultString.toString(), HttpStatus.SC_OK, true);
    }
}
