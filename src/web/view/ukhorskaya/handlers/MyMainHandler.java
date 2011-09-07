package web.view.ukhorskaya.handlers;

import com.sun.net.httpserver.HttpExchange;
import web.view.ukhorskaya.sessions.HttpSession;
import web.view.ukhorskaya.sessions.MainHttpSession;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/10/11
 * Time: 1:23 PM
 */
public class MyMainHandler extends MyBaseHandler {


    /*@Override
    protected BaseHighlighterProvider getProvider() {
        return new MainHighlighterProvider();
    }

    @Override
    public void setVariables(final VirtualFile file) {
        final Ref<IterationState> stateRef = new Ref<IterationState>();
        final Ref<Integer> intPositionRef = new Ref<Integer>();
        final Ref<PsiFile> psiFileRef = new Ref<PsiFile>();

        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            public void run() {

                psiFileRef.set(PsiManager.getInstance(currentProject).findFile(file));

                Document document = PsiDocumentManager.getInstance(currentProject).getDocument(psiFileRef.get());
                Editor editor = EditorFactory.getInstance().createEditor(document, currentProject, file, true);
                stateRef.set(new IterationState((EditorEx) editor, 0, false));
                intPositionRef.set(editor.getCaretModel().getVisualLineEnd());
            }
        }, ModalityState.defaultModalityState());

        psiFile = psiFileRef.get();
        iterationState = stateRef.get();
        intPositionState = intPositionRef.get();
    }*/

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        //if (!parseRequest(exchange)) {
        clearMaps();
        parseRequest(exchange);
        HttpSession session = new MainHttpSession();
        session.handle(exchange);
        // }
    }
}
