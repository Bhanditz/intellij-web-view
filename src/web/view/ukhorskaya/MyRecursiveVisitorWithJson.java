package web.view.ukhorskaya;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import web.view.ukhorskaya.css.GlobalCssMap;
import web.view.ukhorskaya.handlers.BaseHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/12/11
 * Time: 10:20 AM
 */
public class MyRecursiveVisitorWithJson extends PsiRecursiveElementVisitor {
    private JSONArray jsonResult = new JSONArray();
    private StringBuffer result = new StringBuffer();
    private StringBuffer lineWithTags = new StringBuffer();
    private StringBuffer lineWithoutTags = new StringBuffer();

    private VirtualFile currentFile;
    private Document myDocument;

    private IterationState iterationState;
    private int intPositionState;

    private int lineNumber = -1;
    private int lastSpanLineIndex = 0;
    //Position of Iteration state in injection
    private int relPosIS = 0;

    //Link TextRange of link in current file with PsiReferense
    private HashMap<TextRange, PsiReference> mapLinks = new HashMap<TextRange, PsiReference>();


    private PsiReference ref = null;
    //private MyTextAttributes defaultTextAttributes = new MyTextAttributes();

    //Boolean for check is <a> tag open
    private boolean isOpenedATag = false;

    public MyRecursiveVisitorWithJson(VirtualFile currentFile, IterationState iterationState, int intPosition, int lineNumber) {
        this.currentFile = currentFile;
        this.iterationState = iterationState;
        this.intPositionState = intPosition;
        this.lineNumber = lineNumber;

    }

    public MyRecursiveVisitorWithJson(VirtualFile currentFile, IterationState iterationState, int intPosition, int lineNumber, int lastSpanLineIndex) {
        this.currentFile = currentFile;
        this.iterationState = iterationState;
        this.intPositionState = intPosition;
        this.lineNumber = lineNumber;
        this.lastSpanLineIndex = lastSpanLineIndex;
    }

    @Override
    public void visitElement(final PsiElement element) {
        if (element instanceof PsiFile) {
            myDocument = PsiDocumentManager.getInstance(ProjectUtil.guessProjectForFile(currentFile)).getDocument((PsiFile) element);
            lineNumber = myDocument.getLineCount();
        }
        if ((lineNumber != -1) && (myDocument.getLineNumber(element.getTextRange().getStartOffset()) > lineNumber)) {
            throw new LineOutOfUpdateException();
        }

        //Check for Reference
        PsiReference localRef = element.getReference();
        if (localRef != null) {

            PsiElement resolvedRef = localRef.resolve();
            if (resolvedRef != null) {
                if (resolvedRef.getTextOffset() != element.getTextOffset()) {
                    if (resolvedRef.getContainingFile() != null) {
                        int elStart = element.getTextRange().getStartOffset();
                        TextRange refRangeInElem = localRef.getRangeInElement();
                        mapLinks.put(new TextRange(elStart + refRangeInElem.getStartOffset(),
                                elStart + refRangeInElem.getEndOffset()), localRef);
                    }
                }
            }
        }

        //PsiElement injection = null;
        PsiElement injection = getInjection(element);

        //Check for injection
        if ((injection != null) && (element.getTextRange().getStartOffset() != 0)) {
            addInjectionForElement(element, injection);
        } else {
            //Check length to optimize work with empty elements
            if (isLeaf(element) && (element.getText().length() > 0)) {
                addTextToResult(element);
            } else {
                super.visitElement(element);

            }
        }
    }

    private void addInjectionForElement(final PsiElement element, final PsiElement injection) {
        final int elementStart = element.getTextRange().getStartOffset();
        if (elementStart != 0) {

            MyRecursiveVisitorWithJson visitor = new MyRecursiveVisitorWithJson(currentFile,
                    iterationState, intPositionState, lineNumber, lastSpanLineIndex);

            // "-" for add correct highlighting for elements witch starts not from begining of injected file
            visitor.setRelPosIS(iterationState.getStartOffset() - injection.getTextRange().getStartOffset());

            visitor.visitElement(injection);

            //Goto to end of Iteration state in injection
            while (iterationState.getEndOffset() < element.getTextRange().getEndOffset()) iterationState.advance();

            //result.append(visitor.getResult());
        }
    }

    private void addTextToResult(final PsiElement element) {
        int textOffset = element.getTextOffset();
        TextRange textRange = element.getTextRange();

        if (lastSpanLineIndex == 0) {
            lineWithTags.append(addLinePStart());
            lastSpanLineIndex++;
        }

        if ((myDocument != null) && (lastSpanLineIndex == myDocument.getLineCount())) {
            lineWithTags.append(addLinePEnd());
            lastSpanLineIndex++;
        }

        if ((myDocument != null) && (myDocument.getLineNumber(textRange.getEndOffset()) != lastSpanLineIndex)) {
            while (lastSpanLineIndex < myDocument.getLineNumber(textRange.getEndOffset())) {
                if (((lineNumber == -1) || (lastSpanLineIndex < lineNumber))) {
                    lineWithTags.append(addLinePEnd());
                    result.append(lastSpanLineIndex);
                    //result.append(lineWithTags);
                    result.append(lineWithoutTags);

                    try {
                        Map map = new HashMap<String, String>();
                        map.put("lineNumber", String.valueOf(lastSpanLineIndex));
                        map.put("text", lineWithoutTags);
                        map.put("html", lineWithTags);
                        jsonResult.put(map);

                        //jsonResult = jsonResult.put("lineNumber", lastSpanLineIndex);
                        //jsonResult = jsonResult.put("text", lineWithoutTags);
                        //jsonResult = jsonResult.put("html", lineWithTags);
                    } catch (Exception e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }


                    lineWithTags = new StringBuffer();
                    lineWithoutTags = new StringBuffer();

                    lineWithTags.append(addLinePStart());
                }
                lastSpanLineIndex++;
            }
        }

        final PsiReference localRef = mapLinks.get(textRange);

        if (localRef != null) {
            final Ref<String> resolvedRefFile = new Ref<String>();
            final Ref<Integer> resolvedRefStart = new Ref<Integer>();
            final Ref<PsiElement> resolvedRef = new Ref<PsiElement>();

            PsiElement resolve = localRef.resolve();
            resolvedRef.set(resolve);
            VirtualFile file = resolve.getContainingFile().getVirtualFile();
            if (file != null) {
                resolvedRefFile.set(file.getUrl());
            } else {
                resolvedRefFile.set("");
            }
            resolvedRefStart.set(resolve.getTextOffset());


            lineWithTags.append(addLinkStart(resolvedRefFile.get(), "anch" + resolvedRefStart.get() + resolvedRef.get().getContainingFile()));
            ref = localRef;
            isOpenedATag = true;
        }

        String elemContainingFileName = element.getContainingFile().toString();
        if (currentFile.getUrl().contains(".class")) {
            elemContainingFileName = elemContainingFileName.replace(".java", ".class");
            elemContainingFileName = elemContainingFileName.replace("PsiJavaFile", "PsiFile");
        }

        //Add anchors
        if (isOpenedATag) {
            lineWithTags.append(" name=\"anch").append(textOffset).append(elemContainingFileName);
            lineWithTags.append("\">");
            isOpenedATag = false;
        } else if (!(element instanceof PsiWhiteSpace)) {
            lineWithTags.append("<a name=\"anch").append(textOffset).append(elemContainingFileName);
            lineWithTags.append("\"></a>");
            isOpenedATag = false;
        }

        /*MyTextAttributes textAttributes = new MyTextAttributes(iterationState.getMergedAttributes());
        if ((iterationState.getEndOffset() < intPositionState) && (BaseHandler.getColor(textAttributes.getTextAttributes().getBackgroundColor()).equals("#ffffd7"))) {
            textAttributes.setBackgroundColor(Color.white);
        }*/
        if ((element.getTextLength() != 0) && (textRange.getStartOffset() + relPosIS == iterationState.getStartOffset())) {
            String className = GlobalCssMap.getInstance().getClassFromTextAttribute(iterationState.getMergedAttributes());
            if (className == null) {
                System.err.println("WARNING: Absent style in css\n" + iterationState.getMergedAttributes().getForegroundColor().toString() + iterationState.getMergedAttributes().getBackgroundColor().toString() + iterationState.getMergedAttributes().getEffectType().toString() + iterationState.getMergedAttributes().getFontType());
                className = "undefinedClass";
            }
            lineWithTags.append(addHighlightingStart(className, textOffset + elemContainingFileName));
        }

        //Advance IterationState for case when PsiElement is bigger then element for IterationState
        if ((iterationState.getStartOffset() == textRange.getStartOffset() + relPosIS) && ((iterationState.getEndOffset() - iterationState.getStartOffset()) < element.getTextLength())) {
            lineWithTags.append(addHighlightingEnd());
            lineWithTags.append(getResultForBigElement(element));
            if ((textRange.getEndOffset() + relPosIS == iterationState.getEndOffset())) {
                result.append("<span>");
            }
        } else {
            String text = element.getText().replaceAll("\\n", "");
            lineWithTags.append(BaseHandler.escapeString(text));
            lineWithoutTags.append(BaseHandler.escapeString(text));
        }

        if (iterationState.getEndOffset() < textRange.getStartOffset() + relPosIS) {
            System.err.println("WARNING: iteration state and element have different end and start position. " + element.getText()
                    + " " + element.getTextRange().toString() +
                    ", (" + iterationState.getStartOffset() + ", " + iterationState.getEndOffset() + ")");
            lineWithTags.append(addHighlightingEnd());
            while (iterationState.getStartOffset() < textRange.getStartOffset() + relPosIS)
                iterationState.advance();
        }

        //Add </span> tag for end of element for IterationState
        if ((textRange.getEndOffset() + relPosIS == iterationState.getEndOffset())) {
            lineWithTags.append(addHighlightingEnd());
            iterationState.advance();
        }
        //Add </a> tag if there is a referense for element
        if ((ref != null) && (textRange.getEndOffset() == ref.getElement().getTextRange().getEndOffset())) {
            lineWithTags.append(addLinkEnd());
            ref = null;
        }
    }

    private String getResultForBigElement(PsiElement element) {
        StringBuilder result = new StringBuilder();
        int iterStateBegin = iterationState.getStartOffset();

        while (iterationState.getEndOffset() <= iterStateBegin + element.getTextLength()) {
            /* MyTextAttributes textAttributes = new MyTextAttributes(iterationState.getMergedAttributes());
            if ((iterationState.getEndOffset() < intPositionState) && (BaseHandler.getColor(textAttributes.getTextAttributes().getBackgroundColor()).equals("#ffffd7"))) {
                textAttributes.setBackgroundColor(Color.white);
            }*/

            String className = GlobalCssMap.getInstance().getClassFromTextAttribute(iterationState.getMergedAttributes());
            if (className == null) {
                System.err.println("WARNING: Absent style in css\n" + iterationState.getMergedAttributes().getForegroundColor().toString() + iterationState.getMergedAttributes().getBackgroundColor().toString() + iterationState.getMergedAttributes().getEffectType().toString() + iterationState.getMergedAttributes().getFontType());
                className = "undefinedClass";
            }
            result.append(addHighlightingStart(className, String.valueOf(iterationState.getStartOffset()) + element.getContainingFile()));
            result.append(BaseHandler.escapeString(element.getText().substring(iterationState.getStartOffset() - iterStateBegin, iterationState.getEndOffset() - iterStateBegin)));
            result.append(addHighlightingEnd());
            iterationState.advance();
        }
        return result.toString();
    }

    //If PsiElement contains characters from other language
    private PsiElement getInjection(final PsiElement element) {
        if (element != null) {
            PsiFile file = element.getContainingFile();
            int pos = element.getTextRange().getStartOffset();
            PsiElement injection = InjectedLanguageUtil.findElementAtNoCommit(file, pos);

            if (injection != null) {
                if ((!injection.getContainingFile().equals(element.getContainingFile()))) {
                    while (injection.getText().length() < element.getText().length()) {
                        injection = injection.getParent();
                    }
                    if (injection.getText().equals(element.getText())) {
                        return injection;
                    }
                }
            }
        }
        return null;
    }

    private String addLinkStart(String href, String anchorName) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("<a href=\"");
        buffer.append(getUrlFromFilePath(href));
        buffer.append("#");
        buffer.append(anchorName);
        buffer.append("\"");
        return buffer.toString();
    }

    private String getUrlFromFilePath(String path) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("/path=");
        buffer.append(path);
        return buffer.toString();
    }

    private String addLinkEnd() {
        return "</a>";
    }

    private String addHighlightingStart(String className, String offset) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("<span id=\"");
        buffer.append(offset);
        buffer.append("\" class=\"");
        buffer.append(className);
        buffer.append("\">");
        return buffer.toString();
    }

    private String addHighlightingEnd() {
        return "</span>";
    }

    private String addLinePStart() {
        return "<p class=\"newLineClass\">";
    }

    private String addLinePEnd() {
        return "</p>";
    }

    private boolean isLeaf(final PsiElement element) {
        return (element.getChildren().length == 0);
    }

    public String getResult() {

        System.out.println(jsonResult.toString());
        return jsonResult.toString();
    }

    public void setRelPosIS(int pos) {
        relPosIS = pos;
    }
}
