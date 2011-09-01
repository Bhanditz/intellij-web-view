package web.view.ukhorskaya;

import web.view.ukhorskaya.handlers.MyBaseHandler;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/30/11
 * Time: 1:33 PM
 */
public class MyIcon {

    private Icon myIcon;

    public MyIcon(Icon icon) {
        myIcon = icon;
        if (myIcon != null) {
            paintIcon();
        }
    }

    private void paintIcon() {
        BufferedImage iconBufImage = new BufferedImage(myIcon.getIconWidth(), myIcon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = iconBufImage.createGraphics();
        graphics.setBackground(new Color(0, 0, 0, 0));

        final JPanel panel = new JPanel(new BorderLayout());
        myIcon.paintIcon(panel, graphics, 0, 0);
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_IN, 0.0f));
        int hashCode = getMD5FromBufferedImage(iconBufImage);
        if (!MyBaseHandler.mapIconHashCode.containsKey(hashCode)) {
            MyBaseHandler.mapIconHashCode.put(myIcon, hashCode);
            MyBaseHandler.mapHashCodeBufferedImage.put(hashCode, iconBufImage);
        }
    }

    private int getMD5FromBufferedImage(BufferedImage image) {
        int hashCode = 0;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", outputStream);
            byte[] data = outputStream.toByteArray();
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data);
            byte[] hash = md.digest();
            hashCode = byteArrayToInt(hash);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Impossible to generate MD5");
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return hashCode;
    }

    public static int byteArrayToInt(byte[] bytes) {
        int result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) - Byte.MIN_VALUE + (int) bytes[i];
        }
        return result;
    }

    public String getIconUrl(Icon icon) {
        if (icon == null) {
            return "";
        }
        StringBuilder response = new StringBuilder();
        response.append("<img src='");
        response.append("/fticons/");
        response.append(MyBaseHandler.mapIconHashCode.get(icon).toString());
        response.append("'/>");
        return response.toString();
    }
}