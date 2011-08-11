package web.view.ukhorskaya;

import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/10/11
 * Time: 1:16 PM
 */

public abstract class MyBaseHandler implements HttpHandler {
    protected IterationState iterationState;
    protected int intPositionState;

    protected Project currentProject;
    protected VirtualFile currentFile;

    HashMap<MyTextAttributes, Integer> mapAttributes;

    public void handle(HttpExchange exchange) {
        if (!exchange.getRequestURI().toString().contains("____.css")) {
            sendOtherFile(exchange);
        } else {
            sendCssFile(exchange);
        }
    }

    private void sendOtherFile(HttpExchange exchange) {
        String requestURI = exchange.getRequestURI().toString();
        String response = "";

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

        currentFile = getFileByRelPath(relPath);

        if (currentFile == null) {
            response = "File " + relPath + " not found at project " + projectName;
            writeResponse(exchange, response, 404);
            return;
        }

        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(currentFile.getInputStream()));
            String tmp;
            while ((tmp = bufferedReader.readLine()) != null) {
                response += tmp + "\n";
            }
        } catch (IOException e) {
            response = "Error while reading from file";
            writeResponse(exchange, response, 400);
        }

        response = addHighLighting(response);

        response = response.replaceAll("    ", "&nbsp;&nbsp;&nbsp;&nbsp;");
        response = response.replaceAll("\\n", "<br/>");

        writeResponse(exchange, response, 200);
    }

    private void sendCssFile(HttpExchange exchange) {
        String response = generateCssStyles();
        writeResponse(exchange, response, 200, true);
    }

    //Add highlighting for file
    private String addHighLighting(String response) {
        setVariables();

        mapAttributes = new HashMap<MyTextAttributes, Integer>();

        MyTextAttributes defaultTextAttributes = new MyTextAttributes();
        int id = 0;
        StringBuilder result = new StringBuilder();
        while (iterationState.getEndOffset() != iterationState.getStartOffset()) {
            MyTextAttributes myTextAttributes = new MyTextAttributes(iterationState.getMergedAttributes());
            if ((iterationState.getEndOffset() < intPositionState) && (getColor(myTextAttributes.getBackgroundColor()).equals("#ffffd7"))) {
                myTextAttributes.setBackgroundColor(Color.white);
            }

            String tmp = response.substring(iterationState.getStartOffset(), iterationState.getEndOffset());
            if (!myTextAttributes.equals(defaultTextAttributes)) {
                int className = 0;
                if (mapAttributes.containsKey(myTextAttributes)) {
                    if (mapAttributes.get(myTextAttributes) != null) {
                        className = mapAttributes.get(myTextAttributes);
                    }
                } else {
                    mapAttributes.put(myTextAttributes, id);
                    className = id;
                }
                tmp = addClassForElement(tmp, className);
            }
            result.append(tmp);
            iterationState.advance();
            id++;
        }
        //return generateCssStyles(attributesMap) + result.toString();
        return result.toString();
    }

    //Generate css-file
    private String generateCssStyles() {
        StringBuffer buffer = new StringBuffer();
        //buffer.append("<style type=\"text/css\">");
        buffer.append("body { font-family: monospace; font-size: 12px; color: #000000; background-color: #FFFFFF;}");
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
            buffer.append("}");

            //Cut empty styles
            tmp = "\nspan.class" + mapAttributes.get(attr) + "{ }";
            int position = buffer.toString().indexOf(tmp);
            if (position != -1) {
                buffer = buffer.delete(position, buffer.length());
            }
        }
        //buffer.append("</style>");
        return buffer.toString();
    }

    //Add <span> tag with class name for the element
    private String addClassForElement(String string, int id) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("<span class=\"class");
        buffer.append(id);
        buffer.append("\">");
        buffer.append(processString(string));
        buffer.append("</span>");
        return buffer.toString();
    }

    private void writeResponse(HttpExchange exchange, String responseBody, int errorCode) {
        writeResponse(exchange, responseBody, errorCode, false);
    }

    //Send Response
    private void writeResponse(HttpExchange exchange, String responseBody, int errorCode, boolean isCssFile) {
        OutputStream os = null;
        StringBuilder response = new StringBuilder();
        if (!isCssFile) {
            response.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">\n");
            response.append("<html>\n");
            response.append("<head>\n");
            response.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"____.css\"/>");
            response.append("<title>Web View</title>\n");
            response.append("</head>\n");
            response.append("<body>\n");
            response.append("<div>\n");
            response.append(responseBody);
            response.append("</div>\n");
            response.append("</body>\n");
            response.append("</html>\n");
        } else {
            response.append(responseBody);
        }

        try {
            exchange.sendResponseHeaders(errorCode, response.length());
            os = exchange.getResponseBody();
            os.write(response.toString().getBytes());
        } catch (IOException e) {
            System.err.println("Error while work with file system");
            e.printStackTrace();
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

    //Change some special symbols from file to show in browser
    private String processString(String string) {
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

    //Get Color as String
    private String getColor(Color color) {
        ArrayList<String> colors = new ArrayList<String>();
        colors.add(Long.toHexString(color.getRed()));
        colors.add(Long.toHexString(color.getGreen()));
        colors.add(Long.toHexString(color.getBlue()));

        StringBuilder buffer = new StringBuilder();
        for (String c : colors) {
            if (c.equals("0")) {
                c = "00";
            }
            buffer.append(c);
        }
        return ("#" + buffer.toString());
    }

    //Get fontType as String
    private String getFontType(int fontType) {
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
    public abstract void setVariables();

}