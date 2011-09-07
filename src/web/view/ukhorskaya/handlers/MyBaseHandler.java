package web.view.ukhorskaya.handlers;

import com.intellij.ide.util.gotoByName.FilteringGotoByModel;
import com.intellij.ide.util.gotoByName.GotoClassModel2;
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.ide.util.gotoByName.GotoSymbolModel2;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import web.view.ukhorskaya.IconHelper;
import web.view.ukhorskaya.JSONResponse;
import web.view.ukhorskaya.MyTextAttributes;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/10/11
 * Time: 1:16 PM
 */

public abstract class MyBaseHandler implements HttpHandler {
    private static final Logger LOG = Logger.getInstance(MyBaseHandler.class);

    public static IconHelper iconHelper = IconHelper.getInstance();

    //Link map for TextAttributes with session id for download correct css styles
    public static Map<Integer, Map<MyTextAttributes, Integer>> mapCss = Collections.synchronizedMap(new HashMap<Integer, Map<MyTextAttributes, Integer>>());

    protected boolean parseRequest(HttpExchange exchange) {
        String param = exchange.getRequestURI().getQuery();
        if (param != null) {
            if (param.contains("file=highlighting.js")) {
                sendResourceFile(exchange);
            } else if (param.contains("file=dialog.js")) {
                sendResourceFile(exchange);
            } else if (param.contains("type=jquery_lib")) {
                sendResourceFile(exchange);
            } else if (param.contains("type=css")) {
                writeResponse(exchange, generateCssStyles(param), 200);
            } else if (param.contains("file_type=autocomplete")) {
                iconHelper.clearMaps();
                sendJsonData(exchange);
            }
            return true;
        } else {
            param = exchange.getRequestURI().toString();
            if (param.contains("fticons")) {
                sendIcon(exchange);
                return  true;
            } else if (param.contains(".png")) {
                sendImageFile(exchange);
                return  true;
            } else if (param.equals("/")) {
                sendModuleList(exchange);
                return  true;
            } else if (param.contains(".css") && param.contains("jquery.ui")) {
                sendResourceFile(exchange);
                return  true;
            }
        }
        return false;
    }

    private void sendModuleList(HttpExchange exchange) {
        StringBuilder response = new StringBuilder();
        response.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">\n");
        response.append("<html>\n");
        response.append("<head>\n");
        response.append("<title>Web View</title>\n");
        response.append("</head>\n");
        response.append("<body>\n");
        response.append("<div>\n");

        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        if (projects.length > 0) {
            response.append("Please, choose the project:<br/>");
            for (Project project : projects) {
                String projectName = project.getName();
                response.append("<a href=\"/");
                response.append(projectName);
                response.append("/\">");
                response.append(projectName);
                response.append("</a><br/>");
            }
        } else {
            response.append("There is no open project in Intellij IDEA");
        }

        response.append("</div>\n");
        response.append("</body>\n");
        response.append("</html>\n");
        writeResponse(exchange, response.toString(), 200);
    }

     private void sendIcon(HttpExchange exchange) {
        String hashCode = exchange.getRequestURI().getPath();
        hashCode = hashCode.substring(hashCode.indexOf("fticons/") + 8);
        BufferedImage bi = iconHelper.getIconFromMap(Integer.parseInt(hashCode));
        writeImageToStream(bi, exchange);
    }

    private void sendImageFile(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        if (path.contains("resources")) {
            path = path.substring(path.indexOf("resources") + 9);
        }
        BufferedImage bi;
        try {
            bi = ImageIO.read(MyBaseHandler.class.getResourceAsStream(path));
            writeImageToStream(bi, exchange);
        } catch (IOException e) {
            LOG.error(e);
        }
    }

    private void writeImageToStream(BufferedImage image, HttpExchange exchange) {
        OutputStream out = null;
        try {
            //ByteArrayOutputStream exist because it is necessary to send headers before write to out
            ByteArrayOutputStream tmp = new ByteArrayOutputStream();
            ImageIO.write(image, "png", tmp);
            tmp.close();
            Integer contentLength = tmp.size();
            exchange.sendResponseHeaders(200, contentLength);
            out = exchange.getResponseBody();
            out.write(tmp.toByteArray());
        } catch (IOException e) {
            LOG.error(e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    LOG.error(e);
                }
            }
        }
    }

    private void sendJsonData(HttpExchange exchange) {
        StringBuilder response = new StringBuilder();
        String requestUri = exchange.getRequestURI().getQuery();
        if (requestUri == null) {
            writeResponse(exchange, "There isn't parametrs for request", 200);
            return;
        }
        String type = requestUri.substring(requestUri.indexOf("&type=") + 6, requestUri.indexOf("&project="));
        String projectName = requestUri.substring(requestUri.indexOf("&project=") + 9, requestUri.indexOf("&term"));
        String term = requestUri.substring(requestUri.indexOf("&term=") + 6);

        try {
            response.append(getAllFilesInProjectWithTerm(term, type, projectName));
            writeResponse(exchange, response.toString(), 200);
        } catch (NoSuchAlgorithmException e) {
            response.append("Server can't generate MD5 for icon image");
            response.append(e.getMessage());
            writeResponse(exchange, response.toString(), 400);
        }

    }

    private String getAllFilesInProjectWithTerm(final String term, String type, String projectName) throws NoSuchAlgorithmException {
        Project currentProject = getProjectByProjectName(projectName);
        if (currentProject == null) {
            return "Impossible to find a project by project name: " + projectName + "Check that the project is open in Intellij Idea." ;
        }

        FilteringGotoByModel model;
        if (type.equals("class")) {
            model = new GotoClassModel2(currentProject);
        } else if (type.equals("symbol")) {
            model = new GotoSymbolModel2(currentProject);
        } else {
            model = new GotoFileModel(currentProject);
        }

        JSONResponse response = new JSONResponse(model);
        return response.getResponse(currentProject, null, term);
    }

    private void sendResourceFile(HttpExchange exchange) {
        StringBuilder response = new StringBuilder();

        String path = exchange.getRequestURI().getPath();
        if (path.contains("resources")) {
            path = path.substring(path.indexOf("resources") + 9);
        }
        InputStream is = MyBaseHandler.class.getResourceAsStream(path);
        if (is == null) {
            writeResponse(exchange, "File not found", 404);
            return;
        }
        InputStreamReader reader = new InputStreamReader(is);

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
        writeResponse(exchange, response.toString(), 200);
    }

    //Send Response
    private void writeResponse(HttpExchange exchange, String responseBody, int errorCode) {
        OutputStream os = null;
        StringBuilder response = new StringBuilder();
        response.append(responseBody);
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
                LOG.error(e);
            }
        }
    }

    //Generate css-file
    private String generateCssStyles(String param) {
        String sessionId;
        if (!param.contains("sessionid")) {
            return "";
        }
        sessionId = param.substring(param.indexOf("sessionid=") + 10);

        Map<MyTextAttributes, Integer> mapAttributes = mapCss.get(Integer.parseInt(sessionId));
        StringBuffer buffer = new StringBuffer();
        buffer.append("body { font-family: monospace; font-size: 12px; color: #000000; background-color: #FFFFFF;} ");
        buffer.append(" a {text-decoration: none; color: #000000;} span.highlighting { background-color: yellow !important;}");
        buffer.append(" a span {text-decoration: none; color: #000000;} a:hover span {color: blue; text-decoration: underline;}");
        for (MyTextAttributes attr : mapAttributes.keySet()) {
            buffer.append("\nspan.class");
            buffer.append(mapAttributes.get(attr)).append("{");
            String tmp = MyBaseHandler.getColor(attr.getForegroundColor());
            if (!tmp.equals("#000000")) {
                buffer.append("color: ").append(tmp).append("; ");
            }
            tmp = MyBaseHandler.getColor(attr.getBackgroundColor());
            if (!tmp.equals("#ffffff")) {
                buffer.append("background-color: ").append(tmp).append("; ");
            }
            buffer.append(MyBaseHandler.getFontType(attr.getFontType())).append(" ");
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

    protected void clearMaps() {
        if (mapCss.size() > 30) {
            mapCss = Collections.synchronizedMap(new HashMap<Integer, Map<MyTextAttributes, Integer>>());
        }
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
            case 3:
                return "font-style: italic; font-weight: bold;";
        }
    }

    public static String processString(String string) {
        if (string.contains("<")) {
            string = string.replaceAll("<", "&lt;");
        }
        if (string.contains(">")) {
            string = string.replaceAll(">", "&gt;");
        }
        return string;
    }

    public static Project getProjectByProjectName(String projectName) {
        for (Project p : ProjectManager.getInstance().getOpenProjects()) {
            if (p.getName().equals(projectName)) {
                return p;
            }
        }
        return null;
    }

}

