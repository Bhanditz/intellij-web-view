package web.view.ukhorskaya;

import com.intellij.openapi.editor.markup.EffectType;
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
    private EffectType effectType;

    public MyTextAttributes() {
        foregroundColor = Color.black;
        backgroundColor = Color.white;
        fontType = 0;
        effectType = EffectType.BOXED;
    }


    public MyTextAttributes(TextAttributes attributes) {
        foregroundColor = attributes.getForegroundColor();
        backgroundColor = attributes.getBackgroundColor();
        fontType = attributes.getFontType();
        effectType = attributes.getEffectType();
    }


    public Color getBackgroundColor() {
        return backgroundColor;
    }

     public EffectType getEffectType() {
        return effectType;
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
                (this.effectType.equals(newTextAttributes.effectType)) &&
                (this.backgroundColor.equals(newTextAttributes.backgroundColor)) ;
    }

    @Override
    public int hashCode() {
        return (foregroundColor.hashCode() + backgroundColor.hashCode() + fontType + effectType.hashCode());
    }

    public void setEffectType(EffectType effectType) {
        this.effectType = effectType;
    }
}
