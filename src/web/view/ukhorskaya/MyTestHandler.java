package web.view.ukhorskaya;

import com.intellij.openapi.editor.impl.IterationState;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/10/11
 * Time: 1:24 PM
 */
public class MyTestHandler extends MyBaseHandler {


    public MyTestHandler(IterationState iterationState, int position) {
        this.iterationState = iterationState;
        this.intPositionState = position;
    }

    @Override
    public void setVariables() {

    }

}
