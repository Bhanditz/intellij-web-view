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
    private int relPosIS = 0;

    public MyRecursiveVisitor(VirtualFile currentFile, Project currentProject, IterationState iterationState, int intPosition, BaseHighlighterProvider provider) {
        this.currentFile = currentFile;
        this.currentProject = currentProject;

        this.iterationState = iterationState;
        this.intPositionState = intPosition;
        this.provider = provider;
    }

    @Override
    public void visitElement(final PsiElement element) {

        //Check for Referense
        ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
                PsiReference localRef = element.getReference();
                if ((localRef != null) && (localRef.resolve() != null) && (localRef.resolve().getContainingFile() != null) && (isInProject(localRef.resolve().getContainingFile()))) {
                    mapLinks.put(new TextRange(element.getTextRange().getStartOffset() + localRef.getRangeInElement().getStartOffset(),
                            element.getTextRange().getStartOffset() + localRef.getRangeInElement().getEndOffset()), localRef);
                }
            }
        });

        //if (!getOnlyLinks) {
        PsiElement injection = getInjection(element);

        if ((injection != null) && (element.getTextRange().getStartOffset() != 0)) {
            addInjectionForElement(element, injection);
        } else {
            //Check for length for optimize work with empty elements
            if (isLeaf(element) && (element.getText().length() > 0)) {
                //Check for injection
                addTextToResult(element);
            } else {
                super.visitElement(element);
            }
        }
    }

    private void addInjectionForElement(final PsiElement element, final PsiElement injection) {
        if (element.getTextRange().getStartOffset() != 0) {

            MyRecursiveVisitor visitor = new MyRecursiveVisitor(currentFile, currentProject, provider.getIterationStateFromPsiFile(injection.getContainingFile(), currentProject, element.getTextRange().getStartOffset()),
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
            while (iterationState.getEndOffset() != element.getTextRange().getEndOffset()) iterationState.advance();
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
        //final PsiReference localRef = mapLinks.get(new TextRange(textRange.get().getStartOffset() + relPosIS, textRange.get().getEndOffset() + relPosIS));

        if (localRef != null) {
            final Ref<String> resolvedRefFile = new Ref<String>();
            final Ref<Integer> resolvedRefStartOffset = new Ref<Integer>();

            ApplicationManager.getApplication().runReadAction(new Runnable() {
                @Override
                public void run() {
                    resolvedRefFile.set(localRef.resolve().getContainingFile().getVirtualFile().getPath());
                    resolvedRefStartOffset.set(localRef.resolve().getTextOffset());
                }
            });

            // For don't add a link for it self
            if (!resolvedRefStartOffset.get().equals(textOffset.get())) {
                result.append(addLinkStart(resolvedRefFile.get(), "anch" + resolvedRefStartOffset.get() + localRef.resolve().getContainingFile()));
                ref = localRef;
                isOpenedATag = true;
            }
        }

        if (isOpenedATag) {
            result.append(" name=\"anch").append(textOffset.get()).append(element.getContainingFile());
            result.append("\">");
            isOpenedATag = false;
        } else if (!(element instanceof PsiWhiteSpace)) {
            result.append("<a name=\"anch").append(textOffset.get()).append(element.getContainingFile());
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
                //relPosIS added to differ class from injection
                mapAttributes.put(myTextAttributes, id + relPosIS);
                className = id + relPosIS;
            }
            result.append(addHighlightingStart(className, textOffset.get().toString() + element.getContainingFile()));
            //}
        }

        result.append(MyBaseHandler.processString(element.getText()));

        if ((ref != null) && (textRange.get().getEndOffset() == ref.getElement().getTextRange().getEndOffset())) {
            result.append(addLinkEnd());
            ref = null;
        }

        if ((iterationState.getStartOffset() == textRange.get().getStartOffset() + relPosIS) && ((iterationState.getEndOffset() - iterationState.getStartOffset()) < element.getTextLength())) {
            while ((iterationState.getEndOffset() != textRange.get().getEndOffset() + relPosIS) && (iterationState.getEndOffset() <= textRange.get().getEndOffset() + relPosIS)) {
                iterationState.advance();
            }
        }

        if ((textRange.get().getEndOffset() + relPosIS == iterationState.getEndOffset())) {
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

            if ((!injection.get().getContainingFile().equals(element.getContainingFile()))) {
                while (injection.get().getText().length() < element.getText().length()) {
                    injection.set(injection.get().getParent());
                }

                if (injection.get().getText().equals(element.getText())) {
                    return injection.get();
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
