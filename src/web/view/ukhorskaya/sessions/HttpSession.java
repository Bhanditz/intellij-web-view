package web.view.ukhorskaya.sessions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.sun.net.httpserver.HttpExchange;
import org.apache.commons.lang.math.RandomUtils;
import web.view.ukhorskaya.MyRecursiveVisitor;
import web.view.ukhorskaya.MyRecursiveVisitor2;
import web.view.ukhorskaya.MyTextAttributes;
import web.view.ukhorskaya.handlers.MyBaseHandler;
import web.view.ukhorskaya.providers.BaseHighlighterProvider;

import java.awt.event.InputEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
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

    private final int sessionId = RandomUtils.nextInt();

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
        if (param.contains("url=")) {
            sendOtherFile2(exchange);
        } else {
            sendOtherFile(exchange);
        }
    }


    private void sendResourceFile(HttpExchange exchange) {
        StringBuilder response = new StringBuilder();

        InputStreamReader reader = null;
        reader = new InputStreamReader(MyBaseHandler.class.getResourceAsStream("/index.html"));

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
            response = "Impossible to find a project by request URI: " + requestURI;
            writeResponse(exchange, response, 404);
            return;
        }

        currentProject = MyBaseHandler.getProjectByProjectName(projectName);
        if (currentProject == null) {
            response = "Project " + projectName + " not found. Check that the project is opened in Intellij IDEA.";
            writeResponse(exchange, response, 404);
            return;
        }

        String relPath = requestURI.substring(requestURI.indexOf(projectName) + projectName.length());
        if (relPath.length() <= 1) {
            sendResourceFile(exchange);
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

    private void sendOtherFile2(HttpExchange exchange) {
        String requestURI = exchange.getRequestURI().toString().substring(5);
        requestURI = requestURI.replace("%20", " ");
        String response;

        //TODO add navigation by id IndexInfrastructure.findFileById((PersistentFS)ManagingFS.getInstance(), myFileId)
        currentFile = VirtualFileManager.getInstance().findFileByUrl(requestURI);
        if (currentFile == null) {
            response = "File with url " + requestURI + " not found ";
            writeResponse(exchange, response, 404);
            return;
        }

        currentProject = ProjectUtil.guessProjectForFile(currentFile);
        response = getContentWithDecoration2();

        response = response.replaceAll("    ", "&nbsp;&nbsp;&nbsp;&nbsp;");
        response = response.replaceAll("\\n", "<br/>");

        writeResponse(exchange, response, 200);
    }

    private String getContentWithDecoration() {
        setVariables(currentFile);
        MyRecursiveVisitor visitor = new MyRecursiveVisitor(currentFile, currentProject, iterationState, intPositionState, getProvider());
        visitor.visitFile(psiFile);

        mapAttributes = visitor.getMapAttributes();
        MyBaseHandler.mapCss.put(sessionId, mapAttributes);
        return visitor.getResult();
    }

    protected abstract BaseHighlighterProvider getProvider();

    //Set IterationState and intPosition
    public abstract void setVariables(VirtualFile file);

    private String getContentWithDecoration2() {
        setVariables(currentFile);

        PsiElement mirrorFile = null;
        if (psiFile instanceof ClsFileImpl) {
            mirrorFile = ((ClsFileImpl) psiFile).getMirror();
        }

        MyRecursiveVisitor2 visitor = new MyRecursiveVisitor2(currentFile, iterationState, intPositionState, getProvider());
        //MyRecursiveVisitor visitor = new MyRecursiveVisitor(currentFile, currentProject, iterationState, intPositionState, getProvider());
        if (mirrorFile instanceof PsiFile) {
            visitor.visitFile((PsiFile) mirrorFile);
        } else {
            visitor.visitFile(psiFile);
        }

        mapAttributes = visitor.getMapAttributes();
        MyBaseHandler.mapCss.put(sessionId, mapAttributes);
        return visitor.getResult();
    }


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
            response.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"____.css?type=css&sessionid=" + sessionId + "\"/>");
            response.append("<title>Web View</title>\n");

            response.append("<script src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.5/jquery.min.js\"></script>\n");
            response.append("<script src=\"/highlighting.js?file=highlighting.js\"></script>\n");
            /* PopUp Dialog for find classes */
            response.append("<script src=\"/dialog.js?file=dialog.js\"></script>\n");
            response.append("<script src=\"/jquery/development-bundle/jquery-1.6.2.js?type=jquery_lib\"></script>\n");
            response.append("<script src=\"/jquery/development-bundle/external/jquery.bgiframe-2.1.2.js?type=jquery_lib\"></script>\n");
            response.append("<script src=\"/jquery/development-bundle/ui/jquery.ui.core.js?type=jquery_lib\"></script>\n");
            response.append("<script src=\"/jquery/development-bundle/ui/jquery.ui.widget.js?type=jquery_lib\"></script>\n");
            response.append("<script src=\"/jquery/development-bundle/ui/jquery.ui.mouse.js?type=jquery_lib\"></script>\n");
            //response.append("<script src=\"/resources/jquery/development-bundle/ui/jquery.ui.draggable.js?type=jquery_lib\"></script>\n");
            //response.append("<script src=\"/resources/jquery/development-bundle/ui/jquery.ui.position.js?type=jquery_lib\"></script>\n");
            //response.append("<script src=\"/resources/jquery/development-bundle/ui/jquery.ui.resizable.js?type=jquery_lib\"></script>\n");
            response.append("<script src=\"/jquery/development-bundle/ui/jquery.ui.dialog.js?type=jquery_lib\"></script>\n");
            response.append("<link rel=\"stylesheet\" href=\"/jquery/development-bundle/themes/base/jquery.ui.all.css\">\n");
            response.append("<link type=\"text/css\" href=\"/jquery/css/ui-lightness/jquery-ui-1.8.16.custom.css?type=jquery_lib\" rel=\"stylesheet\"/>\n");
            response.append("<script src=\"/jquery/js/jquery-ui-1.8.16.custom.min.js?type=jquery_lib\" type=\"text/javascript\"></script>\n");
            response.append("</head>\n");

            ShortcutSet gotofile = ActionManager.getInstance().getAction("GotoFile").getShortcutSet();
            ShortcutSet gotoclass = ActionManager.getInstance().getAction("GotoClass").getShortcutSet();
            ShortcutSet gotosymbol = ActionManager.getInstance().getAction("GotoSymbol").getShortcutSet();

            response.append("<body onload=\"setGotoFileShortcut(");
            response.append(getKeyboardShortcutFromShortcutSet(gotofile));
            response.append("); setGotoClassShortcut(");
            response.append(getKeyboardShortcutFromShortcutSet(gotoclass));
            response.append(");");
            response.append(" setGotoSymbolShortcut(");
            response.append(getKeyboardShortcutFromShortcutSet(gotosymbol));
            response.append(");");
            response.append(" setProjectName('");
            response.append(currentProject.getName());
            response.append("');");
            response.append("\">\n");

            response.append("<div id=\"fake-body\">\n");
            response.append("<div>\n");
            response.append(responseBody);
            response.append("</div>\n");
            response.append("<div id=\"dialog\" style=\"min-height: 26px !important; height: 26px !important;\">\n").append("<div class=\"ui-widget\">\n").append("<input id=\"tags\" value=\"\" type=\"text\" style='width: 468px;'/>\n").append("</div>\n").append("</div>");
            response.append("</div>\n");
            response.append("<div id=\"dock\"><div>\n");
            response.append(" Go to file: <b>");
            response.append(gotofile.getShortcuts()[0].toString());
            response.append("</b>     ");
            response.append("Go to class: <b>");
            response.append(gotoclass.getShortcuts()[0].toString());
            response.append("</b>     ");
            response.append("Go to symbol: <b>");
            response.append(gotosymbol.getShortcuts()[0].toString());
            response.append("</b>     ");
            response.append("</div></div>\n");

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

    private VirtualFile getFileByRelPath(String relPath) {
        for (VirtualFile file : ProjectRootManager.getInstance(currentProject).getContentRoots()) {
            VirtualFile currentFile = file.findFileByRelativePath(relPath);
            if (currentFile != null) {
                return currentFile;
            }
        }
        return null;
    }

    /*private VirtualFile getFileById(int id) {
        VirtualFile searchResult = findFileInContentRootById(ProjectRootManager.getInstance(currentProject).getContentRootsFromAllModules(), id);
        if (searchResult == null) {
            searchResult = findFileInContentRootById(ProjectRootManager.getInstance(currentProject).getProjectSdk().getHomeDirectory().getChildren(), id);
        }
        return searchResult;
    }

    private VirtualFile findFileInContentRootById(VirtualFile[] dir, int id) {
        VirtualFile curFile = null;
        label:
        {
            for (VirtualFile file : dir) {
                if (file instanceof VirtualDirectoryImpl) {
                    curFile = ((VirtualDirectoryImpl) file).findChildById(id);
                }
                if (curFile != null) {
                    break label;
                }
                VirtualFile[] childrens = file.getChildren();
                if (childrens.length > 0) {
                    findFileInContentRootById(file.getChildren(), id);
                }
            }
        }
        return curFile;
    }      */

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
