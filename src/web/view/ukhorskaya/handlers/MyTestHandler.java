package web.view.ukhorskaya.handlers;

import com.intellij.openapi.editor.impl.IterationState;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/10/11
 * Time: 1:24 PM
 */
public class MyTestHandler extends MyBaseHandler {

    public void setIterationState(IterationState state) {
        this.iterationState = state;
    }

    public void setIntPosition(int position) {
        this.intPositionState = position;
    }

    @Override
    public void setVariables() {

    }

}
