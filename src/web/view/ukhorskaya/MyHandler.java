package web.view.ukhorskaya;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
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
 * Date: 8/3/11
 * Time: 2:10 PM
 */
class MyHandler implements HttpHandler {

    public void handle(HttpExchange exchange) {

        String requestURI = exchange.getRequestURI().toString();
        String response = "";

        String projectName = getProjectName(requestURI);
        if (projectName == null) {
            response = "Path to the file is incorrect.<br/>URL format is [localhost]/[project name]/[path to the file]";
            writeResponse(exchange, response, 404);
            return;
        }

        final Project currentProject = getProjectByProjectName(projectName);
        if (currentProject == null) {
            response = "project " + projectName + " not found. Check that the project is opened in Intellij IDEA.";
            writeResponse(exchange, response, 404);
            return;
        }

        String relPath = requestURI.substring(requestURI.indexOf(projectName) + projectName.length());

        final VirtualFile currentFile = getFileByRelPath(currentProject, relPath);

        if (currentFile == null) {
            response = "file " + relPath + " not found at project " + projectName;
            writeResponse(exchange, response, 404);
            return;
        }

        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(currentFile.getInputStream()));

            String tmp;

            while ((tmp = bufferedReader.readLine()) != null) {
                response += tmp + "~";
            }
        } catch (IOException e) {
            response = "Error while reading from file";
            writeResponse(exchange, response, 400);
        }

        response = addHighLighting(currentFile, currentProject, response);

        response = response.replaceAll("    ", "&nbsp;&nbsp;&nbsp;&nbsp;");
        response = response.replaceAll("~", "<br/>");

        writeResponse(exchange, response, 200);
    }

    private String addHighLighting(final VirtualFile currentFile, final Project currentProject, String response) {
        final Ref<IterationState> stateRef = new Ref<IterationState>();
        final Ref<Editor> editorRef = new Ref<Editor>();
        final Ref<Integer> intPositionRef = new Ref<Integer>();
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            public void run() {
                PsiFile psiFile = PsiManager.getInstance(currentProject).findFile(currentFile);
                Document document = PsiDocumentManager.getInstance(currentProject).getDocument(psiFile);
                Editor editor = EditorFactory.getInstance().createEditor(document, currentProject, currentFile, true);
                editorRef.set(editor);
                stateRef.set(new IterationState((EditorEx) editor, 0, false));
                intPositionRef.set(editor.getCaretModel().getVisualLineEnd());
            }
        }, ModalityState.defaultModalityState());


        HashMap<MyTextAttributes, Integer> attributesMap = new HashMap<MyTextAttributes, Integer>();

        MyTextAttributes defaultTextAttributes = new MyTextAttributes();
        //HashMap<Color, Integer> attributesMap = new HashMap<Color, Integer>();
        int id = 0;
        StringBuilder result = new StringBuilder();
        while (stateRef.get().getEndOffset() < response.length()) {
            MyTextAttributes attr = new MyTextAttributes(stateRef.get().getMergedAttributes());
            if ((stateRef.get().getEndOffset() < intPositionRef.get()) && (getColor(attr.getBackgroundColor()).equals("#ffffd7"))) {
                attr.setBackgroundColor(Color.white);
            }

            String tmp = response.substring(stateRef.get().getStartOffset(), stateRef.get().getEndOffset());
            if (!attr.equals(defaultTextAttributes)) {
                if (attributesMap.containsKey(attr)) {

                    int className;
                    if (attributesMap.get(attr) != null) {
                        className = attributesMap.get(attr);
                        tmp = addClassForElement(tmp, className);
                    }
                } else {
                    attributesMap.put(attr, id);
                }

            }
            //
            result.append(tmp);
            stateRef.get().advance();
            id++;
        }

        return generateCssStyles(attributesMap) + result.toString();
    }

    private String generateCssStyles(HashMap<MyTextAttributes, Integer> map) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("<style type=\"text/css\">");
        for (MyTextAttributes attr : map.keySet()) {
            buffer.append(" span.class");
            buffer.append(map.get(attr) + "{");
            String tmp = getColor(attr.getForegroundColor());
            if (!tmp.equals("#000000")) {
                buffer.append("color: " + tmp + "; ");
            }
            tmp = getColor(attr.getBackgroundColor());
            if (!tmp.equals("#ffffff")) {
                buffer.append("background-color: " + tmp + "; ");
            }
            buffer.append(getFontType(attr.getFontType()) + " ");
            buffer.append("}");

            //Cut empty styles
            tmp = " span.class" + map.get(attr) + "{ }";
            int position = buffer.toString().indexOf(tmp);
            if (position != -1) {
                buffer = buffer.delete(position, buffer.length());
            }
        }
        buffer.append("</style>");
        return buffer.toString();
    }

    private String addClassForElement(String string, int id) {

        StringBuffer buffer = new StringBuffer();
        buffer.append("<span class=\"class");
        buffer.append(id);
        buffer.append("\">");
        buffer.append(processString(string));
        buffer.append("</span>");
        return buffer.toString();
    }

    private String removeBackgroundColorForFirstLine(String string) {
        int position = string.indexOf("background-color: #ffffd7;");
        if (position == -1) {
            return string;
        }
        String str = string.substring(0, position);
        str += string.substring(position + 26);
        return str;
    }

    private String addHighLightingForElement(String string, TextAttributes attributes) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("<span style=\"");
        String tmp = getColor(attributes.getForegroundColor());
        if (!tmp.equals("#000000")) {
            buffer.append("color: " + tmp + "; ");
        }
        tmp = getColor(attributes.getBackgroundColor());
        if (!tmp.equals("#ffffff")) {
            buffer.append("background-color: " + tmp + "; ");
        }
        buffer.append(getFontType(attributes.getFontType()) + " ");
        buffer.append("\">");
        buffer.append(processString(string));
        buffer.append("</span>");
        if (buffer.toString().indexOf("<span style=\" \">") != -1) {
            return string;
        } else return buffer.toString();
    }

    private void writeResponse(HttpExchange exchange, String responseBody, int errorCode) {
        OutputStream os = null;
        String response = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">\n" +
                "<html>\n" +
                "<head>\n" +
                "<title>IDEA PLUGIN</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "<div style=\"font-family: monospace; font-size: 12px; color: #000000; background-color: #FFFFFF;\">\n";
        response += responseBody;
        response += "</div>\n" +
                "</body>\n" +
                "</html>\n";

        try {
            exchange.sendResponseHeaders(errorCode, response.length());
            os = exchange.getResponseBody();
            os.write(response.getBytes());
        } catch (IOException e) {
            System.err.println("Error while work with file system");
            e.printStackTrace();
        } finally {
            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

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

    private VirtualFile getFileByRelPath(Project currentProject, String relPath) {
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

    private String getColor(Color color) {
        ArrayList<String> colors = new ArrayList<String>();
        colors.add(Long.toHexString(color.getRed()));
        colors.add(Long.toHexString(color.getGreen()));
        colors.add(Long.toHexString(color.getBlue()));

        StringBuffer buffer = new StringBuffer();
        for (String c : colors) {
            if (c.equals("0")) {
                c = "00";
            }
            buffer.append(c);
        }
        return ("#" + buffer.toString());
    }

    private String getFontType(int fontStyle) {
        switch (fontStyle) {
            default:
                return "";
            case 1:
                return "font-weight: bold;";
            case 2:
                return "font-style: italic;";
        }
    }
}
