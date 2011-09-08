package web.view.ukhorskaya.handlers;

import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import com.sun.net.httpserver.HttpExchange;
import org.jetbrains.annotations.Nullable;
import web.view.ukhorskaya.providers.TestHighlighterProvider;
import web.view.ukhorskaya.sessions.HttpSession;
import web.view.ukhorskaya.sessions.TestHttpSession;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/10/11
 * Time: 1:24 PM
 */
public class MyTestHandler extends MyBaseHandler {


    /*private BaseHighlighterProvider hProvider;

    public void setIterationState(IterationState state) {
        this.iterationState = state;
    }

    public void setHighlightingProvider(BaseHighlighterProvider provider) {
        this.hProvider = provider;
    }

    public void setIntPosition(int position) {
        this.intPositionState = position;
    }

    public void setPsiFile(PsiFile file) {
        this.psiFile = file;
    }

    @Override
    protected BaseHighlighterProvider getProvider() {
        return hProvider;
    }

    @Override
    public void setVariables(VirtualFile file) {
    }
*/

    TestHttpSession session = new TestHttpSession();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!parseRequest(exchange)) {
            session.handle(exchange);
            clearMaps();
        }
    }

    public void setVariables(@Nullable Ref<PsiFile> file,@Nullable Ref<IterationState> state,@Nullable Ref<Integer> pos) {
        session.setPsiFile(file);
        session.setIterationState(state);
        session.setIntPosition(pos);

    }

    public void setHighlightingProvider(TestHighlighterProvider provider) {
        session.setHighlightingProvider(provider);
    }

    public HttpSession getHttpSession() {
        return session;
    }

}
