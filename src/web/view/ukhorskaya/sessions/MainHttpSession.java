package web.view.ukhorskaya.sessions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import web.view.ukhorskaya.providers.BaseHighlighterProvider;
import web.view.ukhorskaya.providers.MainHighlighterProvider;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 9/6/11
 * Time: 12:30 PM
 */

public class MainHttpSession extends HttpSession {
    @Override
    protected BaseHighlighterProvider getProvider() {
        return new MainHighlighterProvider();
    }

    @Override
    public void setVariables(final VirtualFile file) {
        final Ref<IterationState> stateRef = new Ref<IterationState>();
        final Ref<Integer> intPositionRef = new Ref<Integer>();
        final Ref<PsiFile> psiFileRef = new Ref<PsiFile>();
        final Ref<Editor> editorRef = new Ref<Editor>();

        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            public void run() {

                psiFileRef.set(PsiManager.getInstance(currentProject).findFile(file));

                Document document = PsiDocumentManager.getInstance(currentProject).getDocument(psiFileRef.get());
                if (document == null) {
                    return;
                }
                Editor editor = EditorFactory.getInstance().createEditor(document, currentProject, file, true);
                editorRef.set(editor);
                stateRef.set(new IterationState((EditorEx) editor, 0, false));
                intPositionRef.set(editor.getCaretModel().getVisualLineEnd());
            }
        }, ModalityState.defaultModalityState());

        psiFile = psiFileRef.get();
        if (stateRef.isNull()) {
            if (file.getFileType().isBinary()) {
                throw new IllegalArgumentException("This is binary file." + file.getUrl());
            }
            throw new IllegalArgumentException("Impossible to create an editor.");
        }

        iterationState = stateRef.get();
        intPositionState = intPositionRef.get();
    }
}
