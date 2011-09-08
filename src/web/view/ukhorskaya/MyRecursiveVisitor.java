package web.view.ukhorskaya;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileImpl;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import web.view.ukhorskaya.handlers.MyBaseHandler;
import web.view.ukhorskaya.providers.BaseHighlighterProvider;

import java.awt.*;
import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/12/11
 * Time: 10:20 AM
 */
public class MyRecursiveVisitor extends PsiRecursiveElementVisitor {
    private StringBuffer result = new StringBuffer();

    private BaseHighlighterProvider provider;

    private VirtualFile currentFile;
    private IterationState iterationState;
    private int intPositionState;

    //Link TextAttributes with css className
    private HashMap<MyTextAttributes, Integer> mapAttributes = new HashMap<MyTextAttributes, Integer>();
    //Link TextRange of link in current file with PsiReferense
    private HashMap<TextRange, PsiReference> mapLinks = new HashMap<TextRange, PsiReference>();

    private PsiReference ref = null;
    //private MyTextAttributes defaultTextAttributes = new MyTextAttributes();

    //Counter for generate css className
    private int id = 0;

    //Boolean for check is <a> tag open
    private boolean isOpenedATag = false;

    //Position of Iteration state in injection
    private int relPosIS = 0;

    public MyRecursiveVisitor(VirtualFile currentFile, IterationState iterationState, int intPosition, BaseHighlighterProvider provider) {
        this.currentFile = currentFile;

        this.iterationState = iterationState;
        this.intPositionState = intPosition;
        this.provider = provider;
    }

    @Override
    public void visitElement(final PsiElement element) {

        //Check for Reference
        ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
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
            }
        });

        //PsiElement injection = null;
        PsiElement injection = getInjection(element);
        //getLineMarkerInfo(element);

        //Check for injection
        if ((injection != null) && (element.getTextRange().getStartOffset() != 0)) {
            addInjectionForElement(element, injection);
        } else {
            //Check for length for optimize work with empty elements
            if (isLeaf(element) && (element.getText().length() > 0)) {
                addTextToResult(element);
            } else {
                super.visitElement(element);
            }
        }
    }

    private void addInjectionForElement(final PsiElement element, final PsiElement injection) {
        int elementStart =  element.getTextRange().getStartOffset();
        if (elementStart != 0) {
            MyRecursiveVisitor visitor = new MyRecursiveVisitor(currentFile,
                    provider.getIterationStateFromPsiFile(injection.getContainingFile(), elementStart),
                    intPositionState, provider);

            final Ref<Integer> startOffsetInj = new Ref<Integer>();
            ApplicationManager.getApplication().runReadAction(new Runnable() {
                @Override
                public void run() {
                    startOffsetInj.set(injection.getTextRange().getStartOffset());
                }
            });

            // "-" for add correct highlighting for elements witch starts not from begining of injected file
            visitor.setRelPosIS(iterationState.getStartOffset() - startOffsetInj.get());
            visitor.setMapAttributes(mapAttributes);

            visitor.visitElement(injection);

            //Goto to end of Iteration state in injection
            while (iterationState.getEndOffset() < element.getTextRange().getEndOffset()) iterationState.advance();
            iterationState.advance();

            //Copy results from injection
            mapAttributes = visitor.getMapAttributes();
            result.append(visitor.getResult());
        }
    }

    private void addTextToResult(final PsiElement element) {
        final Ref<Integer> textOffset = new Ref<Integer>();
        final Ref<TextRange> textRange = new Ref<TextRange>();

        ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
                textOffset.set(element.getTextOffset());
                textRange.set(element.getTextRange());
            }
        });

        final PsiReference localRef = mapLinks.get(textRange.get());

        if (localRef != null) {
            final Ref<Integer> resolvedRefFile = new Ref<Integer>();
            final Ref<Integer> resolvedRefStart = new Ref<Integer>();
            final Ref<PsiElement> resolvedRef = new Ref<PsiElement>();

            ApplicationManager.getApplication().runReadAction(new Runnable() {
                @Override
                public void run() {
                    PsiElement resolve = localRef.resolve();
                    resolvedRef.set(resolve);
                    VirtualFile file = resolve.getContainingFile().getVirtualFile();
                    if (file != null) {
                        resolvedRefFile.set(((VirtualFileImpl) resolve.getContainingFile().getVirtualFile()).getId());
                    } else {
                        resolvedRefFile.set(0);
                    }
                    resolvedRefStart.set(resolve.getTextOffset());
                }
            });


            result.append(addLinkStart(resolvedRefFile.get().toString(), "anch" + resolvedRefStart.get() + resolvedRef.get().getContainingFile()));
            ref = localRef;
            isOpenedATag = true;

        }

        String elemContainingFileName = "" ;
                //String.valueOf((VirtualFileImpl) element.getContainingFile().getVirtualFile()).getId());

        //Add anchors
        if (isOpenedATag) {
            result.append(" name=\"anch").append(textOffset.get()).append(elemContainingFileName);
            result.append("\">");
            isOpenedATag = false;
        } else if (!(element instanceof PsiWhiteSpace)) {
            result.append("<a name=\"anch").append(textOffset.get()).append(elemContainingFileName);
            result.append("\"></a>");
            isOpenedATag = false;
        }

        MyTextAttributes myTextAttributes = new MyTextAttributes(iterationState.getMergedAttributes());
        if ((iterationState.getEndOffset() < intPositionState) && (MyBaseHandler.getColor(myTextAttributes.getBackgroundColor()).equals("#ffffd7"))) {
            myTextAttributes.setBackgroundColor(Color.white);
        }
        if ((element.getTextLength() != 0) && (textRange.get().getStartOffset() + relPosIS == iterationState.getStartOffset())) {
            //if (!myTextAttributes.equals(defaultTextAttributes)) {
            int className = 0;
            if (mapAttributes.containsKey(myTextAttributes)) {
                if (mapAttributes.get(myTextAttributes) != null) {
                    className = mapAttributes.get(myTextAttributes);
                }
            } else {
                //relPosIS added to differ css classes for injection
                mapAttributes.put(myTextAttributes, id + relPosIS);
                className = id + relPosIS;
            }
            result.append(addHighlightingStart(className, textOffset.get().toString() + elemContainingFileName));
            //}
        }

        //Advance IterationState for case when PsiElement is bigger then element for IterationState
        if ((iterationState.getStartOffset() == textRange.get().getStartOffset() + relPosIS) && ((iterationState.getEndOffset() - iterationState.getStartOffset()) < element.getTextLength())) {
            result.append(addHighlightingEnd());
            result.append(getResultForBigElement(element));
            if ((textRange.get().getEndOffset() + relPosIS == iterationState.getEndOffset())) {
                result.append("<span>");
            }
        } else {
            result.append(MyBaseHandler.processString(element.getText()));
        }

        //Add </a> tag if there is a referense for element
        if ((ref != null) && (textRange.get().getEndOffset() == ref.getElement().getTextRange().getEndOffset())) {
            result.append(addLinkEnd());
            ref = null;
        }

        if (iterationState.getEndOffset() < textRange.get().getStartOffset() + relPosIS) {
            System.err.println("WARNING: iteration state and element have different end and start position. " + element.getText()
                    + " " + element.getTextRange().toString() +
                    ", (" + iterationState.getStartOffset() + ", " + iterationState.getEndOffset() + ")");
            result.append(addHighlightingEnd());
            while (iterationState.getStartOffset() < textRange.get().getStartOffset() + relPosIS) iterationState.advance();
        }

        //Add </span> tag for end of element for IterationState
        if ((textRange.get().getEndOffset() + relPosIS == iterationState.getEndOffset())) {
            //if (!myTextAttributes.equals(defaultTextAttributes)) {
            result.append(addHighlightingEnd());
            //}
            iterationState.advance();
            id++;
        }
    }

    private String getResultForBigElement(PsiElement element) {
        StringBuilder result = new StringBuilder();
        int iterStateBegin = iterationState.getStartOffset();

        while (iterationState.getEndOffset() <= iterStateBegin + element.getTextLength()) {
            MyTextAttributes myTextAttributes = new MyTextAttributes(iterationState.getMergedAttributes());
            if ((iterationState.getEndOffset() < intPositionState) && (MyBaseHandler.getColor(myTextAttributes.getBackgroundColor()).equals("#ffffd7"))) {
                myTextAttributes.setBackgroundColor(Color.white);
            }

            int className = 0;
            if (mapAttributes.containsKey(myTextAttributes)) {
                if (mapAttributes.get(myTextAttributes) != null) {
                    className = mapAttributes.get(myTextAttributes);
                }
            } else {
                //relPosIS added to differ css classes for injection
                mapAttributes.put(myTextAttributes, id + relPosIS);
                className = id + relPosIS;
            }
            result.append(addHighlightingStart(className, String.valueOf(iterationState.getStartOffset()) + element.getContainingFile()));
            result.append(MyBaseHandler.processString(element.getText().substring(iterationState.getStartOffset() - iterStateBegin, iterationState.getEndOffset() - iterStateBegin)));
            result.append(addHighlightingEnd());
            iterationState.advance();
            id++;
        }


        return result.toString();
    }

    //If PsiElement contains characters from other language
    private PsiElement getInjection(final PsiElement element) {

        if (element != null) {
            final Ref<PsiElement> injection = new Ref<PsiElement>();
            ApplicationManager.getApplication().runReadAction(new Runnable() {
                @Override
                public void run() {
                    PsiFile file = element.getContainingFile();
                    int pos = element.getTextRange().getStartOffset();
                    injection.set(InjectedLanguageUtil.findElementAtNoCommit(file, pos));
                }
            });
            if (injection.get() != null) {
                if ((!injection.get().getContainingFile().equals(element.getContainingFile()))) {
                    while (injection.get().getText().length() < element.getText().length()) {
                        injection.set(injection.get().getParent());
                    }

                    if (injection.get().getText().equals(element.getText())) {
                        return injection.get();
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

    private String addHighlightingStart(int classId, String offset) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("<span class=\"class");
        buffer.append(classId);
        buffer.append("\"");
        buffer.append(" id=\"");
        buffer.append(offset);
        buffer.append("\">");
        return buffer.toString();
    }

    private String addHighlightingEnd() {
        return "</span>";
    }

    private boolean isLeaf(final PsiElement element) {
        return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
            public Boolean compute() {
                return (element.getChildren().length == 0);
            }
        });
    }

    public String getResult() {
        return result.toString();
    }

    public HashMap<MyTextAttributes, Integer> getMapAttributes() {
        return mapAttributes;
    }

    public void setMapAttributes(HashMap<MyTextAttributes, Integer> map) {
        this.mapAttributes = map;
    }

    public void setRelPosIS(int pos) {
        relPosIS = pos;
    }
}
