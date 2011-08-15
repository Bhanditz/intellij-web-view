package web.view.ukhorskaya;

import com.intellij.openapi.editor.markup.TextAttributes;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/9/11
 * Time: 5:18 PM
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

    public int getFontType() {
        return fontType;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof MyTextAttributes)) {
            return false;
        }
        MyTextAttributes newTextAttributes = (MyTextAttributes) object;
        return (this.fontType == newTextAttributes.fontType) &&
                (this.foregroundColor.equals(newTextAttributes.foregroundColor)) &&
                (this.backgroundColor.equals(newTextAttributes.backgroundColor));
    }

    @Override
    public int hashCode() {
        return (foregroundColor.hashCode() + backgroundColor.hashCode() + fontType);
    }

}
