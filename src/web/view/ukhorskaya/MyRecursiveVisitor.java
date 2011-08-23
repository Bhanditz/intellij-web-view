package web.view.ukhorskaya;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import web.view.ukhorskaya.handlers.MyBaseHandler;

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

    private VirtualFile currentFile;
    private Project currentProject;
    private IterationState iterationState;
    private int intPositionState;

    private HashMap<MyTextAttributes, Integer> mapAttributes = new HashMap<MyTextAttributes, Integer>();
    private HashMap<TextRange, PsiReference> mapLinks = new HashMap<TextRange, PsiReference>();

    private PsiReference ref = null;
    //private MyTextAttributes defaultTextAttributes = new MyTextAttributes();
    private int id = 0;

    private boolean isOpenedATag = false;

    //Position of Iteration state in injection
    private int relPositionIterationState = 0;

    public MyRecursiveVisitor(VirtualFile currentFile, Project currentProject, IterationState iterationState, int intPosition) {
        this.currentFile = currentFile;
        this.currentProject = currentProject;

        this.iterationState = iterationState;
        this.intPositionState = intPosition;
    }

    @Override
    public void visitElement(final PsiElement element) {

        //Check for Referense
        if (element instanceof PsiReference) {
            final PsiReference localRef = element.getReference();
            //if ((localRef.resolve() != null) && (localRef.resolve().getContainingFile() != null) && (localRef.getRangeInElement().getStartOffset() == 0)) {

            ApplicationManager.getApplication().runReadAction(new Runnable() {
                @Override
                public void run() {
                    if ((localRef.resolve() != null) && (localRef.resolve().getContainingFile() != null) && (isInProject(localRef.resolve().getContainingFile()))) {
                        mapLinks.put(new TextRange(element.getTextRange().getStartOffset() + localRef.getRangeInElement().getStartOffset(), element.getTextRange().getStartOffset() + localRef.getRangeInElement().getEndOffset()), localRef);
                    }
                }
            });
        }

        //Check for childs
        if (isLeaf(element) && !(element instanceof PsiReferenceParameterList)) {
            //Check for injection
            PsiFile injection = getInjection(element);
            if (injection != null) {
                addInjection(element, injection);
            } else {
                addTextToResult(element);
            }
        } else {
            super.visitElement(element);
        }
    }

    private void addInjection(final PsiElement element, PsiFile injection) {
        if (element.getTextRange().getStartOffset() != 0) {
            MyRecursiveVisitor visitor = new MyRecursiveVisitor(currentFile, currentProject, MyBaseHandler.getIterationStateByPsiFile(injection, element.getTextRange().getStartOffset()), intPositionState);

            visitor.setRelPositionIterationState(iterationState.getStartOffset());
            visitor.visitFile(injection);
            HashMap<MyTextAttributes, Integer> map = visitor.getMapAttributes();

            //Goto to end of Iteration state in injection
            while (iterationState.getEndOffset() != element.getTextRange().getEndOffset()) iterationState.advance();
            iterationState.advance();

            //Copy results from injection
            for (MyTextAttributes attr : map.keySet()) {
                mapAttributes.put(attr, map.get(attr));
            }
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
            Pair<String, Integer> pair = MyBaseHandler.getFilePathAndTextOffsetByElement(localRef.resolve());

            if (!pair.getField2().equals(textOffset.get())) {
                result.append(addLinkStart(pair.getField1(), "anch" + pair.getField2()));
                ref = localRef;
                isOpenedATag = true;
            }
        }

        if (isOpenedATag) {
            result.append(" name=\"anch").append(textOffset.get());
            result.append("\">");
            isOpenedATag = false;
        } else if (!(element instanceof PsiWhiteSpace)) {
            result.append("<a name=\"anch").append(textOffset.get());
            result.append("\"></a>");
            isOpenedATag = false;
        }

        MyTextAttributes myTextAttributes = new MyTextAttributes(iterationState.getMergedAttributes());
        if ((iterationState.getEndOffset() < intPositionState) && (MyBaseHandler.getColor(myTextAttributes.getBackgroundColor()).equals("#ffffd7"))) {
            myTextAttributes.setBackgroundColor(Color.white);
        }
        if ((element.getTextLength() != 0) && (textRange.get().getStartOffset() + relPositionIterationState == iterationState.getStartOffset())) {
            //if (!myTextAttributes.equals(defaultTextAttributes)) {
            int className = 0;
            if (mapAttributes.containsKey(myTextAttributes)) {
                if (mapAttributes.get(myTextAttributes) != null) {
                    className = mapAttributes.get(myTextAttributes);
                }
            } else {
                //relPositionIterationState added to differ class from injection
                mapAttributes.put(myTextAttributes, id + relPositionIterationState);
                className = id + relPositionIterationState;
            }
            result.append(addHighlightingStart(className, textOffset.get()));
            //}
        }

        result.append(MyBaseHandler.processString(element.getText()));

        if ((ref != null) && (textRange.get().getEndOffset() == ref.getElement().getTextRange().getEndOffset())) {
            result.append(addLinkEnd());
            ref = null;
        }

        if ((iterationState.getStartOffset() == textRange.get().getStartOffset() + relPositionIterationState) && ((iterationState.getEndOffset() - iterationState.getStartOffset()) < element.getTextLength())) {
            while ((iterationState.getEndOffset() != textRange.get().getEndOffset() + relPositionIterationState) && (iterationState.getEndOffset() <= textRange.get().getEndOffset() + relPositionIterationState)) {
                iterationState.advance();
            }
        }

        if ((textRange.get().getEndOffset() + relPositionIterationState == iterationState.getEndOffset())) {
            //if (!myTextAttributes.equals(defaultTextAttributes)) {
            result.append(addHighlightingEnd());
            //}
            iterationState.advance();
            id++;
        }
    }

    private boolean isInProject(PsiFile file) {
        for (VirtualFile root : ProjectRootManager.getInstance(currentProject).getContentRoots()) {
            if (file.getVirtualFile() != null) {
                return ((file.getVirtualFile().getPath().contains(root.getPath())) || (currentFile.getPath().contains(file.getVirtualFile().getPath())));
            }
        }
        return false;
    }

    //If PsiElement contains characters form other language
    private PsiFile getInjection(final PsiElement element) {
        final PsiElement next = element.getNextSibling();
        if (next != null) {
            final Ref<PsiElement> injection = new Ref<PsiElement>();
            ApplicationManager.getApplication().runReadAction(new Runnable() {
                @Override
                public void run() {
                    PsiFile file = element.getContainingFile();
                    int pos = element.getTextRange().getStartOffset();
                    injection.set(InjectedLanguageUtil.findElementAtNoCommit(file, pos));
                }
            });

            if (injection.get() instanceof PsiFile) {
                return (PsiFile) injection.get();
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
        buffer.append("http://localhost/");
        String projectName = currentProject.getName();
        buffer.append(projectName);
        if (path.contains(projectName)) {
            buffer.append(getFileByRelPath(path));
        } else if (currentFile.getPath().contains(path)) {
            buffer.append(getFileByRelPath(currentFile.getPath()));
        }
        return buffer.toString();
    }

    private String getFileByRelPath(String path) {
        for (VirtualFile file : ProjectRootManager.getInstance(currentProject).getContentRoots()) {
            if (path.contains(file.getPath())) {
                return path.substring(path.indexOf(file.getPath()) + file.getPath().length());
            }
        }
        return null;
    }

    private String addLinkEnd() {
        return "</a>";
    }

    private String addHighlightingStart(int classId, int offset) {
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

    public void setRelPositionIterationState(int pos) {
        relPositionIterationState = pos;
    }
}
