package web.view.ukhorskaya.providers;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/24/11
 * Time: 3:11 PM
 */

public class MainHighlighterProvider extends BaseHighlighterProvider {

    @Override
    public IterationState getIterationStateFromPsiFile(final PsiFile file, final Project project, final int position) {
        final Ref<IterationState> stateRef = new Ref<IterationState>();
        final Ref<Integer> intPositionRef = new Ref<Integer>();
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            public void run() {
                Document document = PsiDocumentManager.getInstance(project).getDocument(file);
                Editor editor = EditorFactory.getInstance().createEditor(document, project, file.getFileType(), true);
                stateRef.set(new IterationState((EditorEx) editor, position, false));
                intPositionRef.set(editor.getCaretModel().getVisualLineEnd());
            }
        }, ModalityState.defaultModalityState());

        return stateRef.get();
    }
}
