package web.view.ukhorskaya;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ArrayUtil;
import org.json.JSONArray;
import web.view.ukhorskaya.css.GlobalCssMap;
import web.view.ukhorskaya.inspection.GlobalInspectionProfileEntry;
import web.view.ukhorskaya.inspection.MyLocalInspectionPass;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 9/29/11
 * Time: 4:25 PM
 */

public class JsonResponseForHighlighting {

    private final PsiFile currentPsiFile;
    private final Document currentDocument;
    private final Project currentProject;
    private final Editor currentEditor;

    long startTime = System.currentTimeMillis();

    public JsonResponseForHighlighting(PsiFile currentPsiFile, Project currentProject, Editor currentEditor) {
        this.currentPsiFile = currentPsiFile;
        this.currentProject = currentProject;
        this.currentEditor = currentEditor;
        this.currentDocument = PsiDocumentManager.getInstance(currentProject).getDocument(currentPsiFile);
    }

    public String getResult() {

        final DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl) DaemonCodeAnalyzer.getInstance(currentProject);

        final Ref<List<HighlightInfo>> infoListRef = new Ref<List<HighlightInfo>>();
        System.out.println("before getInfo " + (System.currentTimeMillis() - startTime));
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            @Override
            public void run() {
                //boolean toInitializeDaemon = !daemonCodeAnalyzer.isInitialized();
                //daemonCodeAnalyzer.prepareForTest(!daemonCodeAnalyzer.);
                daemonCodeAnalyzer.prepareForTest();
                InspectionManagerEx inspectionManager = (InspectionManagerEx) InspectionManager.getInstance(currentProject);
                System.out.println("before localinsppass " + (System.currentTimeMillis() - startTime));
                MyLocalInspectionPass localInspectionsPass = new MyLocalInspectionPass(
                        currentPsiFile, currentDocument, 0, currentDocument.getTextLength(),
                        new TextRange(0, currentDocument.getTextLength()),
                        false);
                localInspectionsPass.doInspectInBatch(inspectionManager, GlobalInspectionProfileEntry.getInstance().getInspectionProfileEntry());
                 System.out.println("after localinsppass " + (System.currentTimeMillis() - startTime));
                final TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(currentEditor);

                List<HighlightInfo> infoList = localInspectionsPass.getInfos();
                System.out.println("before runpasses " + (System.currentTimeMillis() - startTime));
                infoList.addAll(daemonCodeAnalyzer.runPasses(currentPsiFile, currentDocument, textEditor, ArrayUtil.EMPTY_INT_ARRAY, false, null));
                System.out.println("after runpasses " + (System.currentTimeMillis() - startTime));
                infoListRef.set(infoList);
            }
        }, ModalityState.defaultModalityState());
         System.out.println("after getInfo " + (System.currentTimeMillis() - startTime));
        //Line number and Map<JsonAttribute, JsonValue> for add only 1 error for line
        Map<Integer, Map<String, String>> errorsMap = new HashMap<Integer, Map<String, String>>();

        for (HighlightInfo info : infoListRef.get()) {
            if (info.getSeverity() != HighlightSeverity.INFORMATION) {
                PsiElement elementAtOffset = PsiUtilBase.getElementAtOffset(currentPsiFile, info.getActualStartOffset());
                TextAttributes attr = null;
                if (elementAtOffset != null) {
                    attr = info.getTextAttributes(elementAtOffset, EditorColorsManager.getInstance().getGlobalScheme());
                }
                String className = null;
                if (attr != null) {
                    className = GlobalCssMap.getInstance().getClassFromTextAttribute(attr);
                }
                if ((className == null) && (info.isAfterEndOfLine)) {
                    className = "redLine";
                }
                if ((className != null) && !("class0".equals(className))){
                    int lineNumber = currentDocument.getLineNumber(info.getActualStartOffset());
                    Map<String, String> map = getMapWithPositionsHighlighting(info, className);
                    if (errorsMap.containsKey(lineNumber)) {
                        if (info.getSeverity() == HighlightSeverity.ERROR) {
                            errorsMap.put(lineNumber, map);
                        }
                    } else {
                        errorsMap.put(lineNumber, map);
                    }
                }
            }
        }
        System.out.println("generate map for Json " + (System.currentTimeMillis() - startTime));
        final JSONArray jsonResult = new JSONArray();
        for (Map<String, String> map : errorsMap.values()) {
            jsonResult.put(map);
        }

        return jsonResult.toString();
    }

    public Map<String, String> getMapWithPositionsHighlighting(HighlightInfo info, String className) {
        int start = info.getActualStartOffset();
        int end = info.getActualEndOffset();
        int lineNumberForElementStart = currentDocument.getLineNumber(start);
        int lineNumberForElementEnd = currentDocument.getLineNumber(end);
        int charNumberForElementStart = start - currentDocument.getLineStartOffset(lineNumberForElementStart);
        int charNumberForElementEnd = end - currentDocument.getLineStartOffset(lineNumberForElementStart);
        if ((start == end)  && (lineNumberForElementStart == lineNumberForElementEnd)){
            charNumberForElementStart--;
        }
        Map<String, String> map = new HashMap<String, String>();
        map.put("x", "{line: " + lineNumberForElementStart + ", ch: " + charNumberForElementStart + "}");
        map.put("y", "{line: " + lineNumberForElementEnd + ", ch: " + charNumberForElementEnd + "}");
        String title = escape(info.description);
        map.put("titleName", title);
        map.put("className", className);
        map.put("severity", info.getSeverity().myName);
        return map;
    }

    private String escape(String str) {
        if ((str != null) && (str.contains("\""))) {
            str = str.replaceAll("\\\"", "'");
        }
        return str;
    }
}
