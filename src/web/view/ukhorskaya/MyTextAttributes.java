package web.view.ukhorskaya;

import com.intellij.openapi.editor.markup.TextAttributes;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/9/11
 * Time: 5:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class MyTextAttributes {
    private Color foregroundColor;
    private Color backgroundColor;
    private int fontType;

    public MyTextAttributes() {
        foregroundColor = Color.black;
        backgroundColor = Color.white;
        fontType = 0;

    }

    public MyTextAttributes(TextAttributes attributes) {
        foregroundColor = attributes.getForegroundColor();
        backgroundColor = attributes.getBackgroundColor();
        fontType = attributes.getFontType();
    }


    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public Color getForegroundColor() {
        return foregroundColor;
    }

    public void setForegroundColor(Color foregroundColor) {
        this.foregroundColor = foregroundColor;
    }

    public int getFontType() {
        return fontType;
    }

    public void setFontType(int fontType) {
        this.fontType = fontType;
    }

    @Override
    //TODO add return false when o isn't MyTextAttributes
    public boolean equals(Object o) {
        MyTextAttributes newTextAttributes = (MyTextAttributes) o;
        if ((this.fontType == newTextAttributes.fontType) &&
                (this.foregroundColor.equals(newTextAttributes.foregroundColor)) &&
                (this.backgroundColor.equals(newTextAttributes.backgroundColor))) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (foregroundColor.hashCode() + backgroundColor.hashCode() + fontType);
    }
}
