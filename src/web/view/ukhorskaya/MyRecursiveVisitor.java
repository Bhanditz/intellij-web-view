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
import web.view.ukhorskaya.handlers.MyBaseHandler;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/12/11
 * Time: 10:20 AM
 */
public class MyRecursiveVisitor extends PsiRecursiveElementVisitor {
    private StringBuffer result = new StringBuffer();

    private Project currentProject;
    private IterationState iterationState;
    private int intPositionState;

    private HashMap<MyTextAttributes, Integer> mapAttributes = new HashMap<MyTextAttributes, Integer>();
    private HashMap<TextRange, PsiReference> mapLinks = new HashMap<TextRange, PsiReference>();
    private ArrayList<Pair> arrayLinks = new ArrayList<Pair>();

    private final Ref<MyRecursiveVisitor> visitorRef = new Ref<MyRecursiveVisitor>();

    private PsiReference ref = null;
    private MyTextAttributes defaultTextAttributes = new MyTextAttributes();
    private int id;

    private boolean isOpenedATag = false;

    public MyRecursiveVisitor(Project currentProject, IterationState iterationState, int intPosition) {
        this.currentProject = currentProject;

        this.iterationState = iterationState;
        this.intPositionState = intPosition;
        visitorRef.set(this);
    }


    @Override
    public void visitElement(final PsiElement element) {

        //Check for Referense
        if (element instanceof PsiReference) {
            final PsiReference localRef = element.getReference();
            //if ((localRef.resolve() != null) && (localRef.resolve().getContainingFile() != null) && (localRef.getRangeInElement().getStartOffset() == 0)) {
            if ((localRef.resolve() != null) && (localRef.resolve().getContainingFile() != null) && (isInProject(localRef.resolve().getContainingFile()))) {
                mapLinks.put(new TextRange(element.getTextRange().getStartOffset() + localRef.getRangeInElement().getStartOffset(), element.getTextRange().getStartOffset() + localRef.getRangeInElement().getEndOffset()), localRef);
            }
        }

        //Check for childs
        if (isLeaf(element) && !(element instanceof PsiReferenceParameterList)) {
            TextRange textRange = element.getTextRange();
            PsiReference localRef = mapLinks.get(textRange);

            if (localRef != null) {
                result.append(addLinkStart(localRef.resolve().getContainingFile().getVirtualFile().getPath(), "anch" + localRef.resolve().getTextOffset()));
                ref = localRef;
                isOpenedATag = true;
            }

            if (isOpenedATag) {
                result.append(" name=\"anch").append(element.getTextOffset());
                result.append("\">");
                isOpenedATag = false;
            } else if (!(element instanceof PsiWhiteSpace)) {
                result.append("<a name=\"anch").append(element.getTextOffset());
                result.append("\"></a>");
                isOpenedATag = false;
            }


            MyTextAttributes myTextAttributes = new MyTextAttributes(iterationState.getMergedAttributes());
            if ((iterationState.getEndOffset() < intPositionState) && (MyBaseHandler.getColor(myTextAttributes.getBackgroundColor()).equals("#ffffd7"))) {
                myTextAttributes.setBackgroundColor(Color.white);
            }
            if ((element.getTextLength() != 0) && (textRange.getStartOffset() == iterationState.getStartOffset())) {
                //if (!myTextAttributes.equals(defaultTextAttributes)) {
                int className = 0;
                if (mapAttributes.containsKey(myTextAttributes)) {
                    if (mapAttributes.get(myTextAttributes) != null) {
                        className = mapAttributes.get(myTextAttributes);
                    }
                } else {
                    mapAttributes.put(myTextAttributes, id);
                    className = id;
                }
                result.append(addHighlightingStart(className, element.getTextOffset()));
                //}
            }

            result.append(MyBaseHandler.processString(element.getText()));

            if ((ref != null) && (textRange.getEndOffset() == ref.getElement().getTextRange().getEndOffset())) {
                result.append(addLinkEnd());
                ref = null;
            }

            if ((iterationState.getStartOffset() == textRange.getStartOffset()) && ((iterationState.getEndOffset() - iterationState.getStartOffset()) < element.getTextLength())) {
                while (iterationState.getEndOffset() != textRange.getEndOffset()) {
                    iterationState.advance();
                }
            }

            if ((textRange.getEndOffset() == iterationState.getEndOffset())) {
                //if (!myTextAttributes.equals(defaultTextAttributes)) {
                result.append(addHighlightingEnd());
                //}
                iterationState.advance();
                id++;
            }

        } else {
            ApplicationManager.getApplication().runReadAction(new Runnable() {
                public void run() {
                    element.acceptChildren(visitorRef.get());
                }
            });
        }
    }

    private boolean isInProject(PsiFile file) {
        for (VirtualFile root : ProjectRootManager.getInstance(currentProject).getContentRoots()) {
            if (file.getVirtualFile() != null) {
                return file.getVirtualFile().getPath().contains(root.getPath());
            }
        }
        return false;
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

        } else {

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
}
