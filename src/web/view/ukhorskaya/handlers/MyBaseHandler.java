package web.view.ukhorskaya.handlers;

import com.intellij.ide.util.gotoByName.FilteringGotoByModel;
import com.intellij.ide.util.gotoByName.GotoClassModel2;
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.ide.util.gotoByName.GotoSymbolModel2;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import web.view.ukhorskaya.JSONResponse;
import web.view.ukhorskaya.MyRecursiveVisitor;
import web.view.ukhorskaya.MyTextAttributes;
import web.view.ukhorskaya.providers.BaseHighlighterProvider;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/10/11
 * Time: 1:16 PM
 */

public abstract class MyBaseHandler implements HttpHandler {
//private static final Logger LOG = Logger.getInstance("web.view.ukhorskaya.handlers.MyBaseHandler");

    public static HashMap<Icon, Integer> mapIconHashCode = new HashMap<Icon, Integer>();
    public static HashMap<Integer, BufferedImage> mapHashCodeBufferedImage = new HashMap<Integer, BufferedImage>();

    protected int intPositionState;
    protected PsiFile psiFile;
    protected IterationState iterationState;

    protected Project currentProject;
    protected VirtualFile currentFile;

    private final KeyModifier SHIFT = new KeyModifier(InputEvent.SHIFT_DOWN_MASK + InputEvent.SHIFT_MASK, 16);
    private final KeyModifier CTRL = new KeyModifier(InputEvent.CTRL_DOWN_MASK + InputEvent.CTRL_MASK, 17);
    private final KeyModifier ALT = new KeyModifier(InputEvent.ALT_DOWN_MASK + InputEvent.ALT_MASK, 18);
    private final KeyModifier META = new KeyModifier(InputEvent.META_DOWN_MASK + InputEvent.META_MASK, 19);

    private enum FileType {
        HL_JS,
        HTML,
        JS,
        DIALOG_JS
    }

    private HashMap<MyTextAttributes, Integer> mapAttributes = new HashMap<MyTextAttributes, Integer>();

    public void handle(HttpExchange exchange) {
        if (exchange.getRequestURI().toString().contains("____.css")) {
            sendCssFile(exchange);
        } else if (exchange.getRequestURI().toString().contains("fticons")) {
            sendIcon(exchange);
        } else if (exchange.getRequestURI().toString().contains(".png")) {
            sendImageFile(exchange);
        } else if (exchange.getRequestURI().toString().contains("jquery")) {
            sendResourceFile(exchange, false);
        } else if ((exchange.getRequestURI().toString().contains("highlighting.js"))) {
            sendResourceFile(exchange, false);
        } else if ((exchange.getRequestURI().toString().contains("dialog.js"))) {
            sendResourceFile(exchange, false);
        } else if (exchange.getRequestURI().toString().contains("autocomplete")) {
            sendJsonData(exchange);
        } else {
            sendOtherFile(exchange);
        }
    }

    private void sendIcon(HttpExchange exchange) {
        String hashcode = exchange.getRequestURI().getPath();
        hashcode = hashcode.substring(hashcode.indexOf("fticons/") + 8);
        BufferedImage bi = MyBaseHandler.mapHashCodeBufferedImage.get(Integer.parseInt(hashcode));
        writeImageToStream(bi, exchange);
    }

    private void sendImageFile(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        String projectName = getProjectName(path);
        path = path.substring(path.indexOf(projectName) + projectName.length());
        BufferedImage bi;
        try {
            bi = ImageIO.read(MyBaseHandler.class.getResourceAsStream(path));
            writeImageToStream(bi, exchange);
        } catch (IOException e) {
            //LOG.error("Error while reading image file", e);
            e.printStackTrace();
        }
    }

    private void writeImageToStream(BufferedImage image, HttpExchange exchange) {
        OutputStream out = null;
        try {
            ByteArrayOutputStream tmp = new ByteArrayOutputStream();
            ImageIO.write(image, "png", tmp);
            tmp.close();
            Integer contentLength = tmp.size();
            exchange.sendResponseHeaders(200, contentLength);
            out = exchange.getResponseBody();
            out.write(tmp.toByteArray());
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private void sendJsonData(HttpExchange exchange) {
        StringBuilder response = new StringBuilder();
        String requestUri = exchange.getRequestURI().toString();
        String term = requestUri.substring(requestUri.indexOf("term=") + 5);
        String type = requestUri.substring(requestUri.indexOf("=") + 1, requestUri.indexOf("?term="));
        response.append(getAllFilesInProjectWithTerm(term, type));

        writeResponse(exchange, response.toString(), 200, true);
    }

    private void sendResourceFile(HttpExchange exchange, boolean isRoot) {
        StringBuilder response = new StringBuilder();

        InputStreamReader reader = null;
        if (isRoot) {
            reader = new InputStreamReader(MyBaseHandler.class.getResourceAsStream("/index.html"));

        } else {
            String path = exchange.getRequestURI().getPath();
            String projectName = getProjectName(path);
            path = path.substring(path.indexOf(projectName) + projectName.length());
            reader = new InputStreamReader(MyBaseHandler.class.getResourceAsStream(path));
        }
        try {
            BufferedReader bufferedReader = new BufferedReader(reader);

            String tmp;
            while ((tmp = bufferedReader.readLine()) != null) {
                response.append(tmp);
                response.append("\n");
            }
        } catch (NullPointerException e) {
            response.append("Resource file not found");
            writeResponse(exchange, response.toString(), 404);
        } catch (IOException e) {
            response.append("Error while reading from file");
            writeResponse(exchange, response.toString(), 400);
        }
        writeResponse(exchange, response.toString(), 200, true);
    }

    private void sendOtherFile(HttpExchange exchange) {
        String requestURI = exchange.getRequestURI().toString();
        String response;

        String projectName = getProjectName(requestURI);
        if (projectName == null) {
            response = "Path to the file is incorrect.<br/>URL format is [localhost]/[project name]/[path to the file]";
            writeResponse(exchange, response, 404);
            return;
        }

        currentProject = getProjectByProjectName(projectName);
        if (currentProject == null) {
            response = "Project " + projectName + " not found. Check that the project is opened in Intellij IDEA.";
            writeResponse(exchange, response, 404);
            return;
        }

        String relPath = requestURI.substring(requestURI.indexOf(projectName) + projectName.length());
        if (relPath.length() <= 1) {
            sendResourceFile(exchange, true);
            return;
        }
        currentFile = getFileByRelPath(relPath);
        if (currentFile == null) {
            response = "File " + relPath + " not found at project " + projectName;
            writeResponse(exchange, response, 404);
            return;
        }

        response = getContentWithDecoration();

        response = response.replaceAll("    ", "&nbsp;&nbsp;&nbsp;&nbsp;");
        response = response.replaceAll("\\n", "<br/>");

        writeResponse(exchange, response, 200);
    }

    private String getAllFilesInProjectWithTerm(final String term, String type) {
        FilteringGotoByModel model;
        if (type.equals("class")) {
            model = new GotoClassModel2(currentProject);
        } else if (type.equals("symbol")) {
            model = new GotoSymbolModel2(currentProject);
        } else {
            model = new GotoFileModel(currentProject);
        }

        JSONResponse response = new JSONResponse(model);
        return response.getResponse(currentProject, psiFile, term);
    }

    private String getContentWithDecoration() {
        setVariables(currentFile);

        MyRecursiveVisitor visitor = new MyRecursiveVisitor(currentFile, currentProject, iterationState, intPositionState, getProvider());
        visitor.visitFile(psiFile);
        mapAttributes = visitor.getMapAttributes();
        return visitor.getResult();
    }

    protected abstract BaseHighlighterProvider getProvider();

    private void sendCssFile(HttpExchange exchange) {
        String response = generateCssStyles();
        writeResponse(exchange, response, 200, true);
    }

    //Generate css-file
    private String generateCssStyles() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("body { font-family: monospace; font-size: 12px; color: #000000; background-color: #FFFFFF;} ");
        buffer.append(" a {text-decoration: none; color: #000000;} span.highlighting { background-color: yellow !important;}");
        buffer.append(" a span {text-decoration: none; color: #000000;} a:hover span {color: blue; text-decoration: underline;}");
        for (MyTextAttributes attr : mapAttributes.keySet()) {
            buffer.append("\nspan.class");
            buffer.append(mapAttributes.get(attr)).append("{");
            String tmp = getColor(attr.getForegroundColor());
            if (!tmp.equals("#000000")) {
                buffer.append("color: ").append(tmp).append("; ");
            }
            tmp = getColor(attr.getBackgroundColor());
            if (!tmp.equals("#ffffff")) {
                buffer.append("background-color: ").append(tmp).append("; ");
            }
            buffer.append(getFontType(attr.getFontType())).append(" ");
            if (attr.getEffectType().equals(EffectType.LINE_UNDERSCORE)) {
                buffer.append("text-decoration: underline; ").append("; ");
            }
            buffer.append("}");

            //Cut empty styles
            tmp = "\nspan.class" + mapAttributes.get(attr) + "{ }";
            int position = buffer.toString().indexOf(tmp);
            if (position != -1) {
                buffer = buffer.delete(position, buffer.length());
            }
        }
        return buffer.toString();
    }

    //Get Color as String
    public static String getColor(Color color) {
        ArrayList<String> colors = new ArrayList<String>();
        colors.add(Long.toHexString(color.getRed()));
        colors.add(Long.toHexString(color.getGreen()));
        colors.add(Long.toHexString(color.getBlue()));

        StringBuilder buffer = new StringBuilder();
        for (String c : colors) {
            if (c.length() == 1) {
                buffer.append("0");
            }
            buffer.append(c);
        }
        return ("#" + buffer.toString());
    }

    //Get fontType as String
    public static String getFontType(int fontType) {
        switch (fontType) {
            default:
                return "";
            case 1:
                return "font-weight: bold;";
            case 2:
                return "font-style: italic;";
        }
    }

    //Set IterationState and intPosition
    public abstract void setVariables(VirtualFile file);

    private void writeResponse(HttpExchange exchange, String responseBody, int errorCode) {
        writeResponse(exchange, responseBody, errorCode, false);
    }

    //Send Response
    private void writeResponse(HttpExchange exchange, String responseBody, int errorCode, boolean isResourceFile) {
        OutputStream os = null;
        StringBuilder response = new StringBuilder();
        if (!isResourceFile) {

            response.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">\n");
            response.append("<html>\n");
            response.append("<head>\n");
            response.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"____.css\"/>");
            response.append("<title>Web View</title>\n");

            response.append("<script src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.5/jquery.min.js\"></script>\n");
            response.append("<script src=\"/resources/highlighting.js\"></script>\n");
            /* PopUp Dialog for find classes */
            response.append("<script src=\"/resources/dialog.js\"></script>\n");
            response.append("<script src=\"/resources/jquery/development-bundle/jquery-1.6.2.js\"></script>\n");
            response.append("<script src=\"/resources/jquery/development-bundle/external/jquery.bgiframe-2.1.2.js\"></script>\n");
            response.append("<script src=\"/resources/jquery/development-bundle/ui/jquery.ui.core.js\"></script>\n");
            response.append("<script src=\"/resources/jquery/development-bundle/ui/jquery.ui.widget.js\"></script>\n");
            response.append("<script src=\"/resources/jquery/development-bundle/ui/jquery.ui.mouse.js\"></script>\n");
            response.append("<script src=\"/resources/jquery/development-bundle/ui/jquery.ui.draggable.js\"></script>\n");
            response.append("<script src=\"/resources/jquery/development-bundle/ui/jquery.ui.position.js\"></script>\n");
            response.append("<script src=\"/resources/jquery/development-bundle/ui/jquery.ui.resizable.js\"></script>\n");
            response.append("<script src=\"/resources/jquery/development-bundle/ui/jquery.ui.dialog.js\"></script>\n");
            response.append("<link rel=\"stylesheet\" href=\"/resources/jquery/development-bundle/themes/base/jquery.ui.all.css\">\n");
            response.append("<link type=\"text/css\" href=\"/resources/jquery/css/ui-lightness/jquery-ui-1.8.16.custom.css\" rel=\"stylesheet\"/>\n");
            response.append("<script src=\"/resources/jquery/js/jquery-ui-1.8.16.custom.min.js\" type=\"text/javascript\"></script>\n");
            response.append("</head>\n");

            response.append("<body onload=\"setGotoFileShortcut(" + getGotoFileShortcut()
                    + "); setGotoClassShortcut(" + getGotoClassShortcut() + ");" +
                    " setGotoSymbolShortcut(" + getGotoSymbolShortcut() + ");" +
                    "\">\n");
            response.append("<div>\n");
            response.append(responseBody);
            response.append("</div>\n");
            response.append("<div id=\"dialog\" style=\"min-height: 26px !important; height: 26px !important;\">\n").append("<div class=\"ui-widget\">\n").append("<input id=\"tags\" value=\"\" type=\"text\" style='width: 468px;'/>\n").append("</div>\n").append("</div>");
            response.append("</body>\n");
            response.append("");
            response.append("</html>\n");
        } else {
            response.append(responseBody);
        }

        try {
            exchange.sendResponseHeaders(errorCode, response.length());
            os = exchange.getResponseBody();
            os.write(response.toString().getBytes());
        } catch (IOException e) {
            //This is an exception we can't to send data to client
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getGotoClassShortcut() {
        String result = "";
        ShortcutSet gotoFile = ActionManager.getInstance().getAction("GotoClass").getShortcutSet();
        int modifiers = ((KeyboardShortcut) (gotoFile.getShortcuts()[0])).getFirstKeyStroke().getModifiers();
        result += setModifiers(modifiers);
        int keyCode = ((KeyboardShortcut) (gotoFile.getShortcuts()[0])).getFirstKeyStroke().getKeyCode();
        result += keyCode;
        return result;
    }

    private String getGotoFileShortcut() {
        String result = "";
        ShortcutSet gotoFile = ActionManager.getInstance().getAction("GotoFile").getShortcutSet();
        int modifiers = ((KeyboardShortcut) (gotoFile.getShortcuts()[0])).getFirstKeyStroke().getModifiers();
        result += setModifiers(modifiers);
        int keyCode = ((KeyboardShortcut) (gotoFile.getShortcuts()[0])).getFirstKeyStroke().getKeyCode();
        result += keyCode;
        return result;
    }

    private String getGotoSymbolShortcut() {
        String result = "";
        ShortcutSet gotoFile = ActionManager.getInstance().getAction("GotoSymbol").getShortcutSet();
        int modifiers = ((KeyboardShortcut) (gotoFile.getShortcuts()[0])).getFirstKeyStroke().getModifiers();
        result += setModifiers(modifiers);
        int keyCode = ((KeyboardShortcut) (gotoFile.getShortcuts()[0])).getFirstKeyStroke().getKeyCode();
        result += keyCode;
        return result;
    }


    //Change some special symbols from file to show in browser
    public static String processString(String string) {
        if (string.contains("<")) {
            string = string.replaceAll("<", "&lt;");
        }
        if (string.contains(">")) {
            string = string.replaceAll(">", "&gt;");
        }
        return string;
    }

    private Project getProjectByProjectName(String projectName) {
        for (Project p : ProjectManager.getInstance().getOpenProjects()) {
            if (p.getName().equals(projectName)) {
                return p;
            }
        }
        return null;
    }

    private VirtualFile getFileByRelPath(String relPath) {
        for (VirtualFile file : ProjectRootManager.getInstance(currentProject).getContentRoots()) {
            VirtualFile currentFile = file.findFileByRelativePath(relPath);
            if (currentFile != null) {
                return currentFile;
            }
        }
        return null;
    }

    private String getProjectName(String uri) {
        int position = uri.indexOf('/');
        if (position == -1) {
            return null;
        } else if (position == 0) {
            return getProjectName(uri.substring(1));
        } else {
            return uri.substring(0, position);
        }
    }

    private String setModifiers(int modifiers) {
        String result = "";
        if (modifiers == SHIFT.modifier) {
            result += SHIFT.key + ",";
        } else if (modifiers == CTRL.modifier) {
            result += CTRL.key + ",";
        } else if (modifiers == ALT.modifier) {
            result += ALT.key + ",";
        } else if (modifiers == META.modifier) {
            result += META.key + ",";
        } else if (modifiers == SHIFT.modifier + CTRL.modifier) {
            result += SHIFT.key + "," + CTRL.key + ",";
        } else if (modifiers == SHIFT.modifier + ALT.modifier) {
            result += SHIFT.key + "," + ALT.key;
        } else if (modifiers == CTRL.modifier + ALT.modifier) {
            result += CTRL.key + "," + ALT.key + ",";
        } else if (modifiers == SHIFT.modifier + ALT.modifier + CTRL.modifier) {
            result += SHIFT.key + "," + ALT.key + "," + CTRL.key + ",";
        } else if (modifiers == SHIFT.modifier + META.modifier) {
            result += SHIFT.key + "," + META.key;
        } else if (modifiers == CTRL.modifier + META.modifier) {
            result += CTRL.key + "," + META.key + ",";
        } else if (modifiers == SHIFT.modifier + META.modifier + CTRL.modifier) {
            result += SHIFT.key + "," + META.key + "," + CTRL.key + ",";
        } else if (modifiers != 0) {
            System.err.println("Error: there isn't a value for modifiers: " + modifiers);
        }
        return result;
    }

    class KeyModifier {
        int modifier;
        int key;

        private KeyModifier(int modifier, int key) {
            this.modifier = modifier;
            this.key = key;
        }
    }


}

