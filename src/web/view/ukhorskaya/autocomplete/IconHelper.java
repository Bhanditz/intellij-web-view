package web.view.ukhorskaya.autocomplete;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 9/6/11
 * Time: 4:34 PM
 */

public class IconHelper {
    private static final IconHelper HELPER = new IconHelper();

    public static IconHelper getInstance() {
        return HELPER;
    }

    private Map<Icon, Integer> mapIconHashCode = Collections.synchronizedMap(new HashMap<Icon, Integer>());
    private Map<Integer, Icon> mapHashCodeIcon = Collections.synchronizedMap(new HashMap<Integer, Icon>());

    private Map<Integer, BufferedImage> mapHashCodeBufferedImage = Collections.synchronizedMap(new WeakHashMap<Integer, BufferedImage>());

    private IconHelper() {
    }

    public int addIconToMap(Icon myIcon) {
        clearMaps();
        BufferedImage iconBufImage = new BufferedImage(myIcon.getIconWidth(), myIcon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = iconBufImage.createGraphics();
        graphics.setBackground(new Color(0, 0, 0, 0));

        final JPanel panel = new JPanel(new BorderLayout());
        myIcon.paintIcon(panel, graphics, 0, 0);
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_IN, 0.0f));
        int hashCode = setHashCodeWithMD5FromBufferedImage(iconBufImage);
        mapIconHashCode.put(myIcon, hashCode);
        mapHashCodeIcon.put(hashCode, myIcon);
        mapHashCodeBufferedImage.put(hashCode, iconBufImage);
        return hashCode;
    }

    private void clearMaps() {
        if (mapHashCodeBufferedImage.size() > 50) {
            mapHashCodeBufferedImage = Collections.synchronizedMap(new WeakHashMap<Integer, BufferedImage>());
        }
    }

    public BufferedImage getIconFromMap(int hashCode) {
        BufferedImage image = mapHashCodeBufferedImage.get(hashCode);
        if (image == null) {
            Icon icon = mapHashCodeIcon.get(hashCode);
            hashCode = addIconToMap(icon);
            return getIconFromMap(hashCode);
        }
        return image;
    }

    private int setHashCodeWithMD5FromBufferedImage(BufferedImage image) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", outputStream);
            byte[] data = outputStream.toByteArray();
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data);
            return Arrays.hashCode(md.digest());
        } catch (IOException e) {
            throw new RuntimeException("ByteArrayOutputStreamException: impossible to save an image", e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Impossible to generate MD5", e);
        }
    }

}
