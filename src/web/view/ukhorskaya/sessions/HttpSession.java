package web.view.ukhorskaya.sessions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.sun.net.httpserver.HttpExchange;
import org.apache.commons.lang.math.RandomUtils;
import web.view.ukhorskaya.MyRecursiveVisitor;
import web.view.ukhorskaya.MyTextAttributes;
import web.view.ukhorskaya.handlers.MyBaseHandler;
import web.view.ukhorskaya.providers.BaseHighlighterProvider;

import java.awt.event.InputEvent;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 9/6/11
 * Time: 12:14 PM
 */
public abstract class HttpSession {
    private static final Logger LOG = Logger.getInstance(HttpSession.class);

    protected final int sessionId = RandomUtils.nextInt();

    private Map<MyTextAttributes, Integer> mapAttributes = new HashMap<MyTextAttributes, Integer>();

    protected int intPositionState;
    protected PsiFile psiFile;
    protected IterationState iterationState;

    protected Project currentProject;
    protected VirtualFile currentFile;

    private final KeyModifier SHIFT = new KeyModifier(InputEvent.SHIFT_DOWN_MASK + InputEvent.SHIFT_MASK, 16);
    private final KeyModifier CTRL = new KeyModifier(InputEvent.CTRL_DOWN_MASK + InputEvent.CTRL_MASK, 17);
    private final KeyModifier ALT = new KeyModifier(InputEvent.ALT_DOWN_MASK + InputEvent.ALT_MASK, 18);
    private final KeyModifier META = new KeyModifier(InputEvent.META_DOWN_MASK + InputEvent.META_MASK, 19);

    public void handle(HttpExchange exchange) {
        String param = exchange.getRequestURI().toString();
        if (param.contains("path=")) {
            sendProjectSourceFile(exchange);
            return;
        }
        param = exchange.getRequestURI().getPath();
        if (param.contains("project=")) {
            sendIndexFile(exchange);
        }

        writeResponse(exchange, "Wrong request: " + exchange.getRequestURI().toString(), 404, true);
    }

    private void sendIndexFile(HttpExchange exchange) {
        StringBuilder response = new StringBuilder();

        String path = exchange.getRequestURI().getPath();
        String projectName = path.substring(path.indexOf("project=") + 8, path.length() - 1);

        currentProject = MyBaseHandler.getProjectByProjectName(projectName);

        response.append("For find file, class or file you can open a popup window.");
        writeResponse(exchange, response.toString(), 200);
    }

    private void sendProjectSourceFile(HttpExchange exchange) {
        String requestURI = exchange.getRequestURI().toString().substring(6);
        requestURI = requestURI.replace("%20", " ");
        String response;

        //TODO add navigation by id IndexInfrastructure.findFileById((PersistentFS)ManagingFS.getInstance(), myFileId)
        currentFile = VirtualFileManager.getInstance().findFileByUrl(requestURI);

        if (currentFile == null) {
            response = "File with path " + requestURI + " not found ";
            writeResponse(exchange, response, 404);
            return;
        }

        currentProject = ProjectUtil.guessProjectForFile(currentFile);
        response = getContentWithDecoration();

        response = response.replaceAll("    ", "&nbsp;&nbsp;&nbsp;&nbsp;");
        response = response.replaceAll("\\n", "<br/>");

        writeResponse(exchange, response, 200);
    }

    protected abstract BaseHighlighterProvider getProvider();

    //Set IterationState and intPosition
    public abstract void setVariables(VirtualFile file);

    private String getContentWithDecoration() {
        try {
            setVariables(currentFile);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }

        PsiElement mirrorFile = null;
        if (psiFile instanceof PsiCompiledElement) {
            mirrorFile = ((PsiCompiledElement) psiFile).getMirror();
        }
        final Ref<MyRecursiveVisitor> visitorRef = new Ref<MyRecursiveVisitor>();
        final PsiElement finalMirrorFile = mirrorFile;

        ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {

                final MyRecursiveVisitor visitor = new MyRecursiveVisitor(currentFile, iterationState, intPositionState, getProvider());
                if (finalMirrorFile instanceof PsiFile) {
                    visitor.visitFile((PsiFile) finalMirrorFile);
                } else {
                    visitor.visitFile(psiFile);
                }
                visitorRef.set(visitor);

            }
        });

        mapAttributes = visitorRef.get().getMapAttributes();
        MyBaseHandler.mapCss.put(sessionId, mapAttributes);

        return visitorRef.get().getResult();
    }

    private void writeResponse(HttpExchange exchange, String responseBody, int errorCode) {
        writeResponse(exchange, responseBody, errorCode, false);
    }

    //Send Response
    //addFeatures - for add popUp window with autocomplete action
    private void writeResponse(HttpExchange exchange, String responseBody, int errorCode, boolean addFeatures) {
        OutputStream os = null;
        StringBuilder response = new StringBuilder();

        String path;
        if (addFeatures) {
            path = "/resources/header-wo-dialog.html";
        } else {
            path = "/resources/header.html";
        }
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
            }
        } catch (NullPointerException e) {
            response.append("Resource file not found");
            writeResponse(exchange, response.toString(), 404);
        } catch (IOException e) {
            response.append("Error while reading from file");
            writeResponse(exchange, response.toString(), 400);
        }

        ShortcutSet gotofile = ActionManager.getInstance().getAction("GotoFile").getShortcutSet();
        ShortcutSet gotoclass = ActionManager.getInstance().getAction("GotoClass").getShortcutSet();
        ShortcutSet gotosymbol = ActionManager.getInstance().getAction("GotoSymbol").getShortcutSet();

        String finalResponse = response.toString();
        finalResponse = finalResponse.replaceFirst("SESSIONID", String.valueOf(sessionId));
        finalResponse = finalResponse.replaceFirst("GOTOFILESHORTCUT", getKeyboardShortcutFromShortcutSet(gotofile));
        finalResponse = finalResponse.replaceFirst("GOTOCLASSSHORTCUT", getKeyboardShortcutFromShortcutSet(gotoclass));
        finalResponse = finalResponse.replaceFirst("GOTOSYMBOLSHORTCUT", getKeyboardShortcutFromShortcutSet(gotosymbol));

        if (currentProject != null) {
            finalResponse = finalResponse.replaceFirst("PROJECTNAME", currentProject.getName());
        }

        finalResponse = finalResponse.replaceFirst("GOTOFILESHORTCUTSTRING", gotofile.getShortcuts()[0].toString());
        finalResponse = finalResponse.replaceFirst("GOTOCLASSSHORTCUTSTRING", gotoclass.getShortcuts()[0].toString());
        finalResponse = finalResponse.replaceFirst("GOTOSYMBOLSHORTCUTSTRING", gotosymbol.getShortcuts()[0].toString());

        finalResponse = finalResponse.replaceFirst("RESPONSEBODY", responseBody);

        try {
            exchange.sendResponseHeaders(errorCode, finalResponse.length());
            os = exchange.getResponseBody();
            os.write(finalResponse.getBytes());
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

    private String getKeyboardShortcutFromShortcutSet(ShortcutSet set) {
        StringBuilder result = new StringBuilder();
        int modifiers = ((KeyboardShortcut) (set.getShortcuts()[0])).getFirstKeyStroke().getModifiers();
        result.append(setModifiers(modifiers));
        int keyCode = ((KeyboardShortcut) (set.getShortcuts()[0])).getFirstKeyStroke().getKeyCode();
        result.append(keyCode);
        return result.toString();
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
            LOG.error("Error: there isn't a value for modifiers: " + modifiers);
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
