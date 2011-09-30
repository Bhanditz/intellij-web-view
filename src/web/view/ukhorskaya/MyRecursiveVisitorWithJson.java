package web.view.ukhorskaya;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.json.JSONArray;
import web.view.ukhorskaya.css.GlobalCssMap;
import web.view.ukhorskaya.handlers.BaseHandler;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/12/11
 * Time: 10:20 AM
 */
public class MyRecursiveVisitorWithJson extends PsiRecursiveElementVisitor {
    long startTime = System.currentTimeMillis();
    private JSONArray jsonResult = new JSONArray();

    private VirtualFile currentFile;
    private Document myDocument;

    private IterationState iterationState;

    public MyRecursiveVisitorWithJson(VirtualFile currentFile, IterationState iterationState, int intPosition, int lineNumber) {
        this.currentFile = currentFile;
        this.iterationState = iterationState;

    }


    @Override
    public void visitElement(final PsiElement element) {

        if (element instanceof PsiFile) {
            myDocument = PsiDocumentManager.getInstance(ProjectUtil.guessProjectForFile(currentFile)).getDocument((PsiFile) element);
        }

        //Check length to optimize work with empty elements
        if (isLeaf(element) && (element.getText().length() > 0)) {
            addTextToResult(element);
        } else {
            super.visitElement(element);
        }
    }

    private void addTextToResult(final PsiElement element) {
        //int textOffset = element.getTextOffset();
        TextRange textRange = element.getTextRange();

        if (myDocument != null) {
            if ((iterationState.getStartOffset() == textRange.getStartOffset()) && ((iterationState.getEndOffset() - iterationState.getStartOffset()) < element.getTextLength())) {
                while (!(iterationState.atEnd()) && (iterationState.getEndOffset() <= textRange.getEndOffset())) {
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
            System.out.println("getErrorAttributeName start = " + (System.currentTimeMillis() - startTime));
            String className = getErrorAttributeName();
            System.out.println("getErrorAttributeName end = " + (System.currentTimeMillis() - startTime));
            if (className != null) {
                System.out.println("getLineNumber start = " + (System.currentTimeMillis() - startTime));

                int lineNumberForElementStart = myDocument.getLineNumber(textRange.getStartOffset());
                int charNumberForElementStart = textRange.getStartOffset() - myDocument.getLineStartOffset(lineNumberForElementStart);
                int charNumberForElementEnd = textRange.getEndOffset() - myDocument.getLineStartOffset(lineNumberForElementStart);
                System.out.println("getLineNumber end = " + (System.currentTimeMillis() - startTime));

                System.out.println("put to map start = " + (System.currentTimeMillis() - startTime));

                Map<String, String> map = new HashMap<String, String>();
                map.put("className", className);
                map.put("x", "{line: " + lineNumberForElementStart + ", ch: " + charNumberForElementStart + "}");
                map.put("y", "{line: " + lineNumberForElementStart + ", ch: " + charNumberForElementEnd + "}");
                jsonResult.put(map);
                System.out.println("put to map end = " + (System.currentTimeMillis() - startTime));

            }
            while (!(iterationState.atEnd()) && (iterationState.getEndOffset() <= textRange.getEndOffset())) {
                iterationState.advance();
            }
        }

    }

    private boolean isErrorAttribute() {
        TextAttributes attributes = iterationState.getMergedAttributes();

        return ((attributes.getForegroundColor().equals(new Color(255, 0, 0)))
                || (attributes.getEffectType() == EffectType.WAVE_UNDERSCORE))
                || (attributes.getBackgroundColor().equals(new Color(246, 235, 188)));
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



    private boolean isLeaf(final PsiElement element) {
        return (element.getChildren().length == 0);
    }

    public String getResult() {
        System.out.println(jsonResult.toString());

        return jsonResult.toString();
    }
}
