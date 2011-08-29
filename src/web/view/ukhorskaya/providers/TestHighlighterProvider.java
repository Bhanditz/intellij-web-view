package web.view.ukhorskaya.providers;

import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/24/11
 * Time: 3:11 PM
 */

public class TestHighlighterProvider extends BaseHighlighterProvider {
    private IterationState iterationState;

    @Override
    public IterationState getIterationStateFromPsiFile(final PsiFile file, final Project project, final int position) {
        return iterationState;
    }

    public void setIterationState(IterationState iterationState) {
        this.iterationState = iterationState;
    }
}
