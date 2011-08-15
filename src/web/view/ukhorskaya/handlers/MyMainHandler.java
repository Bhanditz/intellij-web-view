package web.view.ukhorskaya.handlers;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/10/11
 * Time: 1:23 PM
 */
public class MyMainHandler extends MyBaseHandler {

    @Override
    public void setVariables() {
        final Ref<IterationState> stateRef = new Ref<IterationState>();
        final Ref<Integer> intPositionRef = new Ref<Integer>();
        final Ref<PsiFile> psiFileRef = new Ref<PsiFile>();

        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            public void run() {

                psiFileRef.set(PsiManager.getInstance(currentProject).findFile(currentFile));

                Document document = PsiDocumentManager.getInstance(currentProject).getDocument(psiFileRef.get());
                Editor editor = EditorFactory.getInstance().createEditor(document, currentProject, currentFile, true);
                stateRef.set(new IterationState((EditorEx) editor, 0, false));
                intPositionRef.set(editor.getCaretModel().getVisualLineEnd());
            }
        }, ModalityState.defaultModalityState());

        psiFile = psiFileRef.get();
        iterationState = stateRef.get();
        intPositionState = intPositionRef.get();
    }

}
