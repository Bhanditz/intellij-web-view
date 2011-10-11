package web.view.ukhorskaya.sessions;

import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiFile;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 9/6/11
 * Time: 12:30 PM
 */

public class TestHttpSession extends HttpSession {

    Ref<IterationState> state;
    Ref<Integer> position;
    Ref<PsiFile> file;

    public void setIterationState(Ref<IterationState> state) {
        this.state = state;
    }

    public void setIntPosition(Ref<Integer> position) {
        this.position = position;
    }

    public void setPsiFile(Ref<PsiFile> file) {
        this.file = file;
    }


    @Override
    public void setVariables(VirtualFile myfile) {
        try {
            currentPsiFile = file.get();
            iterationState = state.get();
            intPositionState = position.get();

        } catch (NullPointerException e) {

        }
        if (iterationState == null) {
            if (currentPsiFile instanceof PsiBinaryFile) {
                throw new NullPointerException("This is binary file.");
            } else {
                throw new NullPointerException("Impossible to create an editor.");
            }
        }

    }

}
