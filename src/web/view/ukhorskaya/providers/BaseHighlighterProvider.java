package web.view.ukhorskaya.providers;

import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/24/11
 * Time: 3:10 PM
 */

public abstract class BaseHighlighterProvider {

    public abstract IterationState getIterationStateFromPsiFile(PsiFile file, Project project, int position);

}
