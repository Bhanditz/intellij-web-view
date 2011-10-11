package web.view.ukhorskaya;

import com.intellij.codeInsight.daemon.impl.DefaultHighlightVisitor;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/12/11
 * Time: 10:20 AM
 */
public class MyRecursiveVisitor extends PsiRecursiveElementVisitor {
    DefaultHighlightVisitor visitor;
    HighlightInfoHolder holder;

    public JSONArray getJsonResult() {
        return jsonResult;
    }

    JSONArray jsonResult = new JSONArray();
    Document myDocument;

    public MyRecursiveVisitor(@NotNull PsiFile pFile) {
        VirtualFile vFile = pFile.getVirtualFile();
        Project project = ProjectUtil.guessProjectForFile(vFile);
        if (project != null) {
            myDocument = PsiDocumentManager.getInstance(project).getDocument(pFile);

            holder = new HighlightInfoHolder(pFile, new HighlightInfoFilter() {

                @Override
                public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
                    int lineNumberForElementStart = myDocument.getLineNumber(highlightInfo.getStartOffset());
                    int lineNumberForElementEnd = lineNumberForElementStart;
                    int charNumberForElementStart = highlightInfo.getStartOffset() - myDocument.getLineStartOffset(lineNumberForElementStart);
                    int charNumberForElementEnd = highlightInfo.getEndOffset() - myDocument.getLineStartOffset(lineNumberForElementStart);
                    /*if (charNumberForElementStart == charNumberForElementEnd) {
                        charNumberForElementEnd = 0;
                       lineNumberForElementEnd++;
                    }*/
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("className", "redLine");
                    map.put("x", "{line: " + lineNumberForElementStart + ", ch: " + charNumberForElementStart + "}");
                    map.put("y", "{line: " + lineNumberForElementEnd + ", ch: " + charNumberForElementEnd + "}");
                    jsonResult.put(map);
                    return true;
                }
            });

            visitor = new DefaultHighlightVisitor(project);
        }
    }

    @Override
    public void visitElement(final PsiElement element) {
        visitor.visit(element, holder);
        if (!isLeaf(element)) {
            super.visitElement(element);
        }
    }

    private boolean isLeaf(final PsiElement element) {
        return (element.getChildren().length == 0);
    }
}
