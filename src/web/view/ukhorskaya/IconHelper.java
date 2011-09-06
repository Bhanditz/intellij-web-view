package web.view.ukhorskaya;

import com.intellij.ui.RowIcon;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 9/6/11
 * Time: 4:34 PM
 */

public class IconHelper {
    private static IconHelper helper;

    private Map<Icon, Integer> mapIconHashCode = Collections.synchronizedMap(new HashMap<Icon, Integer>());
    private Map<Integer, BufferedImage> mapHashCodeBufferedImage = Collections.synchronizedMap(new HashMap<Integer, BufferedImage>());

    public static IconHelper getInstance() {
        if (helper == null) {
            helper = new IconHelper();
        }
        return helper;
    }

    private IconHelper() {
    }

    protected void clearMaps() {
        if (mapIconHashCode.size() > 20) {
            mapIconHashCode = Collections.synchronizedMap(new HashMap<Icon, Integer>());
            mapHashCodeBufferedImage = Collections.synchronizedMap(new HashMap<Integer, BufferedImage>());
        }
    }

    public String getHtmlImageTag(Icon inputIcon) throws NoSuchAlgorithmException {
        clearMaps();
        StringBuilder response = new StringBuilder();
        if (inputIcon instanceof RowIcon) {
            for (int j = 0; j < ((RowIcon) inputIcon).getIconCount(); ++j) {
                Icon icon = ((RowIcon) inputIcon).getIcon(j);
                if (icon != null) {
                    paintIcon(icon);
                    response.append(getIconUrl(((RowIcon) inputIcon).getIcon(j)));
                }
            }
        } else {
            paintIcon(inputIcon);
            response.append(getIconUrl(inputIcon));
        }
        return response.toString();
    }

    public BufferedImage getIconFromMap(int hashCode) {
        return mapHashCodeBufferedImage.get(hashCode);
    }

    private void paintIcon(Icon myIcon) throws NoSuchAlgorithmException {
        BufferedImage iconBufImage = new BufferedImage(myIcon.getIconWidth(), myIcon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = iconBufImage.createGraphics();
        graphics.setBackground(new Color(0, 0, 0, 0));

        final JPanel panel = new JPanel(new BorderLayout());
        myIcon.paintIcon(panel, graphics, 0, 0);
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_IN, 0.0f));
        int hashCode = setHashCodeWithMD5FromBufferedImage(iconBufImage);
        if (!mapIconHashCode.containsKey(myIcon)) {
            mapIconHashCode.put(myIcon, hashCode);
            mapHashCodeBufferedImage.put(hashCode, iconBufImage);
        }
    }

    private int setHashCodeWithMD5FromBufferedImage(BufferedImage image) throws NoSuchAlgorithmException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", outputStream);
            byte[] data = outputStream.toByteArray();
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data);
            byte[] hash = md.digest();
            return setHashcodeFromByteArray(hash);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private int setHashcodeFromByteArray(byte[] bytes) {
        int hashCode = 0;
        for (int i = 0; i < 4; i++) {
            hashCode = (hashCode << 8) - Byte.MIN_VALUE + (int) bytes[i];
        }
        return hashCode;
    }

    private String getIconUrl(Icon icon) {
        if (icon == null) {
            return "";
        }
        StringBuilder response = new StringBuilder();
        response.append("<img src='");
        response.append("/fticons/");
        response.append(mapIconHashCode.get(icon).toString());
        response.append("'/>");
        return response.toString();

    }

}
