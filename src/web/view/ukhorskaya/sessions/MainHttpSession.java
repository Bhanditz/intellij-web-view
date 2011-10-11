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

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 9/6/11
 * Time: 12:30 PM
 */

public class MainHttpSession extends HttpSession {
    @Override
    public void setVariables(final VirtualFile file) {
        final Ref<IterationState> stateRef = new Ref<IterationState>();
        final Ref<Integer> intPositionRef = new Ref<Integer>();
        final Ref<PsiFile> psiFileRef = new Ref<PsiFile>();
        final Ref<Editor> editorRef = new Ref<Editor>();
        final Ref<Document> documentRef = new Ref<Document>();

        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            public void run() {
                psiFileRef.set(PsiManager.getInstance(currentProject).findFile(file));
                Document document = PsiDocumentManager.getInstance(currentProject).getDocument(psiFileRef.get());

                if (document == null) {
                    return;
                }
                documentRef.set(document);
                Editor[] editors = EditorFactory.getInstance().getAllEditors();
                if (editors.length != 0) {
                    for (Editor e : editors) {
                        if (e.getDocument() == document) {
                            editorRef.set(e);
                        }
                    }
                }
                if (editorRef.isNull()) {
                    Editor editor = EditorFactory.getInstance().createEditor(document, currentProject, file, true);
                    editorRef.set(editor);
                }

                stateRef.set(new IterationState((EditorEx) editorRef.get(), 0, false));
                intPositionRef.set(editorRef.get().getCaretModel().getVisualLineEnd());
            }
        }, ModalityState.defaultModalityState());

        currentPsiFile = psiFileRef.get();
        if (stateRef.isNull()) {
            if (file.getFileType().isBinary()) {
                throw new IllegalArgumentException("This is binary file." + file.getUrl());
            } else {
                throw new IllegalArgumentException("Impossible to create an editor.");
            }
        }
        currentDocument = documentRef.get();
        currentEditor = editorRef.get();
        iterationState = stateRef.get();
        intPositionState = intPositionRef.get();
    }
}
