package web.view.ukhorskaya.handlers;

import com.intellij.ide.util.gotoByName.FilteringGotoByModel;
import com.intellij.ide.util.gotoByName.GotoClassModel2;
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.ide.util.gotoByName.GotoSymbolModel2;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.commons.httpclient.HttpStatus;
import web.view.ukhorskaya.autocomplete.IconHelper;
import web.view.ukhorskaya.autocomplete.JsonResponse;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/10/11
 * Time: 1:16 PM
 */

public abstract class BaseHandler implements HttpHandler {
    private static final Logger LOG = Logger.getInstance(BaseHandler.class);

    //Method send all data except source file
    protected boolean sendNonSourceFile(HttpExchange exchange) {

        String param = exchange.getRequestURI().getQuery();
        if (param != null) {
            if (param.contains("file=highlighting.js")) {
                sendResourceFile(exchange);
                return true;
            } else if (param.contains("file=dialog.js")) {
                sendResourceFile(exchange);
                return true;
            } else if (param.contains("type=jquery_lib") || param.contains("type=css")) {
                sendResourceFile(exchange);
                return true;
            } else if (param.contains("file_type=autocomplete")) {
                sendJsonData(exchange);
                return true;
            }
        } else {
            param = exchange.getRequestURI().toString();
            if (param.contains("fticons")) {
                sendIcon(exchange);
                return true;
            } else if ((param.contains(".png")) || (param.contains(".gif"))) {
                sendImageFile(exchange);
                return true;
            } else if (param.equals("/")) {
                sendModuleList(exchange);
                return true;
            } else if (param.contains(".css") && (param.contains("jquery.ui") || param.contains("jquery-ui"))) {
                sendResourceFile(exchange);
                return true;
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
                response.append("<a href=\"/project=");
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
        writeResponse(exchange, response.toString(), HttpStatus.SC_OK);
    }

    private void sendIcon(HttpExchange exchange) {
        String hashCode = exchange.getRequestURI().getPath();
        hashCode = hashCode.substring(hashCode.indexOf("fticons/") + 8);
        BufferedImage bi = IconHelper.getInstance().getIconFromMap(Integer.parseInt(hashCode));
        writeImageToStream(bi, exchange);
    }

    private void sendImageFile(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        if (path.contains("resources")) {
            path = path.substring(path.indexOf("resources") + 9);
        }
        try {
            BufferedImage bi = ImageIO.read(BaseHandler.class.getResourceAsStream(path));
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
            exchange.sendResponseHeaders(HttpStatus.SC_OK, contentLength);
            out = exchange.getResponseBody();
            out.write(tmp.toByteArray());
        } catch (IOException e) {
            LOG.error(e);
        } finally {
            close(out);
        }
    }

    private void sendJsonData(HttpExchange exchange) {
        StringBuilder response = new StringBuilder();
        String requestUri = exchange.getRequestURI().getQuery();
        if (requestUri == null) {
            writeResponse(exchange, "There isn't parameters for request", HttpStatus.SC_OK);
            return;
        }
        String type = requestUri.substring(requestUri.indexOf("&type=") + 6, requestUri.indexOf("&project="));
        String projectName = requestUri.substring(requestUri.indexOf("&project=") + 9, requestUri.indexOf("&term"));
        String term = requestUri.substring(requestUri.indexOf("&term=") + 6);

        response.append(getAllFilesInProjectWithTerm(term, type, projectName));
        writeResponse(exchange, response.toString(), HttpStatus.SC_OK);
    }

    private String getAllFilesInProjectWithTerm(final String term, String type, String projectName) {
        Project currentProject = getProjectByName(projectName);
        if (currentProject == null) {
            return "Impossible to find a project by project name: " + projectName + ". Check that the project is open in Intellij Idea.";
        }

        FilteringGotoByModel model;
        if (type.equals("class")) {
            model = new GotoClassModel2(currentProject);
        } else if (type.equals("symbol")) {
            model = new GotoSymbolModel2(currentProject);
        } else {
            model = new GotoFileModel(currentProject);
        }

        JsonResponse response = new JsonResponse(model);
        return response.getResponse(currentProject, null, term);
    }

    private void sendResourceFile(HttpExchange exchange) {
        StringBuilder response = new StringBuilder();

        String path = exchange.getRequestURI().getPath();
        if (path.contains("resources")) {
            path = path.substring(path.indexOf("resources") + 9);
        }

        InputStream is = BaseHandler.class.getResourceAsStream(path);
        if (is == null) {
            writeResponse(exchange, "File not found", HttpStatus.SC_NOT_FOUND);
            return;
        }

        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));

            String tmp;
            while ((tmp = bufferedReader.readLine()) != null) {
                response.append(tmp);
                response.append("\n");
            }
        } catch (IOException e) {
            response.append("Error while reading from file");
            writeResponse(exchange, response.toString(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        writeResponse(exchange, response.toString(), HttpStatus.SC_OK);
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
            close(os);
        }
    }

    private void close(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            LOG.error(e);
        }
    }


    //Get Color as String
    public static String getColor(Color color) {
        List<String> colors = new ArrayList<String>();
        colors.add(Long.toHexString(color.getRed()));
        colors.add(Long.toHexString(color.getGreen()));
        colors.add(Long.toHexString(color.getBlue()));

        StringBuilder buffer = new StringBuilder("#");
        for (String c : colors) {
            if (c.length() == 1) {
                buffer.append("0");
            }
            buffer.append(c);
        }
        return (buffer.toString());
    }

    //Get fontType as String
    public static String getFontType(int fontType) {
        switch (fontType) {
            case 1:
                return "font-weight: bold;";
            case 2:
                return "font-style: italic;";
            case 3:
                return "font-style: italic; font-weight: bold;";
            default:
                return "";

        }
    }

    public static String escapeString(String string) {
        if (string.contains("<")) {
            string = string.replaceAll("<", "&lt;");
        }
        if (string.contains(">")) {
            string = string.replaceAll(">", "&gt;");
        }
        return string;
    }

    public static Project getProjectByName(String projectName) {
        for (Project p : ProjectManager.getInstance().getOpenProjects()) {
            if (p.getName().equals(projectName)) {
                return p;
            }
        }
        return null;
    }

}

