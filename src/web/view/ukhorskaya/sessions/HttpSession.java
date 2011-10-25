package web.view.ukhorskaya.sessions;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.sun.net.httpserver.HttpExchange;
import org.apache.commons.httpclient.HttpStatus;
import web.view.ukhorskaya.*;
import web.view.ukhorskaya.handlers.BaseHandler;

import java.awt.event.InputEvent;
import java.io.*;
import java.net.URLDecoder;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 9/6/11
 * Time: 12:14 PM
 */

public abstract class HttpSession {
    private static final Logger LOG = Logger.getInstance(HttpSession.class);

    private static final KeyModifier SHIFT = new KeyModifier(InputEvent.SHIFT_DOWN_MASK + InputEvent.SHIFT_MASK, 16);
    private static final KeyModifier CTRL = new KeyModifier(InputEvent.CTRL_DOWN_MASK + InputEvent.CTRL_MASK, 17);
    private static final KeyModifier ALT = new KeyModifier(InputEvent.ALT_DOWN_MASK + InputEvent.ALT_MASK, 18);
    private static final KeyModifier META = new KeyModifier(InputEvent.META_DOWN_MASK + InputEvent.META_MASK, 19);

    protected int intPositionState;
    protected IterationState iterationState;
    protected Project currentProject;
    protected VirtualFile currentVirtualFile;
    protected PsiFile currentPsiFile;
    protected Document currentDocument;
    protected Editor currentEditor;

    private HttpExchange exchange;

    private long startTime;

    public void handle(HttpExchange exchange) {
        startTime = System.currentTimeMillis();
        System.out.println(startTime);
        this.exchange = exchange;
        String param = exchange.getRequestURI().toString();
        if (param.contains("path=")) {
            if (param.contains("compile=true") || param.contains("run=true")) {
                sendExecutorResult();
                return;
            } else if (param.contains("complete=true")) {
                sendCompletionResult();
                return;
            } else if (param.contains("stop=true")) {
                stopSession();
                return;
            } else {
                sendProjectSourceFile();
                return;
            }
        }
        param = exchange.getRequestURI().getPath();
        if (param.contains("project=")) {
            sendIndexFile();
            return;
        }

        writeResponse("Wrong request: " + exchange.getRequestURI().toString(), HttpStatus.SC_NOT_FOUND, true);
    }

    private void stopSession() {
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            @Override
            public void run() {
                IdeaInitializer.tearDown();

                IdeaHttpServer.stopServer();

                FileDocumentManager.getInstance().saveAllDocuments();

                Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
                for (Project openProject : openProjects) {
                    ProjectEx project = (ProjectEx) openProject;
                    project.save();
                }
            }
        }, ModalityState.defaultModalityState());

        System.exit(0);
    }

    private void setGlobalVariables() {
        String requestURI = exchange.getRequestURI().getPath().substring(6);
        requestURI = requestURI.replace("%20", " ");
        if (requestURI.contains("directoryId")) {
            requestURI = requestURI.replace("directoryId", IdeaInitializer.directoryId);
        }

        currentVirtualFile = VirtualFileManager.getInstance().findFileByUrl(requestURI);
        String response;
        if (currentVirtualFile == null) {
            response = "File with path " + requestURI + " not found ";
            writeResponse(response, HttpStatus.SC_NOT_FOUND);
            return;
        }
        currentProject = ProjectUtil.guessProjectForFile(currentVirtualFile);
        /*final Module currentModule = ModuleManager.getInstance(currentProject).getModules()[0];

        final LibraryTable libraryTable = ProjectLibraryTable.getInstance(currentProject);
        final Ref<Library> lib = new Ref<Library>();
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            @Override
            public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override
                    public void run() {
                        lib.set(libraryTable.createLibrary("LIB"));
                        //lib.get().getModifiableModel().addJarDirectory();
                        final ModifiableRootModel rootModel = ModuleRootManager.getInstance(currentModule).getModifiableModel();
                        rootModel.addLibraryEntry(lib.get());
                        rootModel.commit();
                    }
                });

                //final File file = new File(PathManagerEx.getTestDataPath() + "/psi/repositoryUse/cls");
                final File file = new File("c:/idea-src/java/java-tests/testData/psi/repositoryUse/cls/java/");
                final VirtualFile root = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
                    @Override
                    public VirtualFile compute() {
                        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
                    }
                });

                final Library.ModifiableModel modifyableModel = lib.get().getModifiableModel();
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override
                    public void run() {
                        modifyableModel.addRoot(root, OrderRootType.CLASSES);
                        modifyableModel.commit();
                    }
                });
            }
        }, ModalityState.defaultModalityState());


        //final ModifiableRootModel rootModel = ModuleRootManager.getInstance(currentModule).getModifiableModel();
        //rootModel.addLibraryEntry(lib.get());
        //ApplicationManager.getApplication().runWriteAction(new Runnable() {
        //    @Override
        //    public void run() {
        //        rootModel.commit();
        //    }
        //});
        //final JavaPsiFacade manager = getJavaFacade();
        //assertNull(manager.findClass("pack.MyClass", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(currentModule)));

        //assertNotNull(root);
         if (JavaPsiFacade.getInstance(currentProject).findClass("java.lang.String", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(currentModule)) != null) {
             System.out.println("Class String found");
         }*/
        int i = 0;
    }


    private void sendCompletionResult() {
        setGlobalVariables();
        setVariables(currentVirtualFile);
        writeDataToFile();

        String param = exchange.getRequestURI().getQuery();
        String[] position = new String[0];
        if (param.contains("cursorAt")) {
            position = (param.substring(param.indexOf("cursorAt=") + 9)).split(",");
        }

        JsonResponseForCompletion jsonResponseForCompletion = new JsonResponseForCompletion(
                Integer.parseInt(position[0]), Integer.parseInt(position[1]),
                currentEditor, currentProject);
        writeResponse(jsonResponseForCompletion.getResult(), HttpStatus.SC_OK, true);
    }

    private void sendProjectSourceFile() {
        String param = exchange.getRequestURI().getQuery();

        setGlobalVariables();
        try {
            setVariables(currentVirtualFile);
        } catch (IllegalArgumentException e) {

        }
        if ((param != null) && (param.contains("sendData=true"))) {
            writeDataToFile();
            System.out.println("before decoration " + (System.currentTimeMillis() - startTime));
            String response = getContentWithDecoration();
            System.out.println("afret decoration " + (System.currentTimeMillis() - startTime));
            response = response.replaceAll("\\n", "");
            writeResponse(response, HttpStatus.SC_OK, true);
        } else {
            ApplicationManager.getApplication().invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    try {
                        currentVirtualFile.refresh(false, false);
                        writeResponse(VfsUtil.loadText(currentVirtualFile), HttpStatus.SC_OK);
                    } catch (IOException e) {
                        writeResponse("Impossible to read file", HttpStatus.SC_BAD_GATEWAY);
                    }
                }
            }, ModalityState.defaultModalityState());
        }
    }

    private void writeDataToFile() {
        System.out.println("begin writeDataToFile()  = " + (System.currentTimeMillis() - startTime));
        StringBuilder reqResponse = new StringBuilder();
        InputStreamReader reader = null;

        try {
            reader = new InputStreamReader(exchange.getRequestBody(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            LOG.error("Impossible to write to file in UTF-8");
        }

        try {
            BufferedReader bufferedReader = new BufferedReader(reader);

            String tmp;
            while ((tmp = bufferedReader.readLine()) != null) {
                //while (tmp.contains("+")) tmp = tmp.replace("+", " ");
                reqResponse.append(tmp);
            }
        } catch (NullPointerException e) {
            reqResponse.append("Resource file not found");
            writeResponse(reqResponse.toString(), HttpStatus.SC_NOT_FOUND);
        } catch (IOException e) {
            reqResponse.append("Error while reading from file sendProjectSourceFile()");
            writeResponse(reqResponse.toString(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }

        String finalResponse = null;
        try {
            finalResponse = URLDecoder.decode(reqResponse.toString(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            LOG.error("Impossible to write to file in UTF-8");
        }

        //System.out.println("RequestBody (with br)  = " + finalResponse);
        finalResponse = finalResponse.replaceAll("<br>", "\n");
        // System.out.println("RequestBody (with n)  = " + finalResponse);

        System.out.println("begin VfsUtil.saveText()  = " + (System.currentTimeMillis() - startTime));
        if (finalResponse.length() > 5) {
            final String finalResponse1 = finalResponse;
            ApplicationManager.getApplication().invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    ApplicationManager.getApplication().runWriteAction(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                //currentVirtualFile.refresh(false, false);
                                currentDocument.setText(finalResponse1.substring(5));
                                //VfsUtil.saveText(currentVirtualFile, finalResponse1.substring(5));
                            } catch (Throwable e) {
                                LOG.error("Impossible write to file");
                            }
                        }
                    });
                }
            }, ModalityState.defaultModalityState());
//                    ApplicationManager.getApplication().runWriteAction(new Runnable() {
//                        @Override
//                        public void run() {
//                            try {
//                                currentVirtualFile.refresh(false, false);
//                                VfsUtil.saveText(currentVirtualFile, finalResponse1.substring(5));
//                            } catch (Throwable e) {
//                                LOG.error("Impossible to write to file in UTF-8");
//                            }
//                        }
//                    });
        }
        System.out.println("write writeDataToFile() = " + (System.currentTimeMillis() - startTime));
    }

    private void sendIndexFile() {
        StringBuilder response = new StringBuilder();

        String path = exchange.getRequestURI().getPath();
        String projectName = path.substring(path.indexOf("project=") + 8, path.length() - 1);

        currentProject = BaseHandler.getProjectByName(projectName);

        response.append("To find file, class or symbol you can open a popup window.");
        writeResponse(response.toString(), HttpStatus.SC_OK);
    }

    private void sendExecutorResult() {
        setGlobalVariables();

        if (exchange.getRequestURI().getQuery().contains("compile")) {
            compileOrRunProject(true);
        } else if (exchange.getRequestURI().getQuery().contains("run")) {
            compileOrRunProject(false);
        } else {
            writeResponse("Incorrect url: absent run or compile parameter", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    //Set IterationState and intPosition
    public abstract void setVariables(VirtualFile file);


    private String getContentWithDecoration() {
        JsonResponseForHighlighting responseForHighlighting = new JsonResponseForHighlighting(currentPsiFile, currentProject, currentEditor);
        return responseForHighlighting.getResult();
    }

    private void compileOrRunProject(final boolean isOnlyCompilation) {
        ResponseForCompilation responseForCompilation = new ResponseForCompilation(isOnlyCompilation, currentProject);
        writeResponse(responseForCompilation.getResult(), HttpStatus.SC_OK);
    }

    private void writeResponse(String responseBody, int errorCode) {
        writeResponse(responseBody, errorCode, false);
    }

    //Send Response
    //disableHeaders - disable html header for answer
    private void writeResponse(String responseBody, int errorCode, boolean disableHeaders) {
        //EditorFactoryImpl.getInstance().releaseEditor(currentEditor);
        System.out.println("begin writeResponse() = " + (System.currentTimeMillis() - startTime));
        OutputStream os = null;
        StringBuilder response = new StringBuilder();

        String path;
        if (!disableHeaders) {
            path = "/header.html";
            InputStream is = BaseHandler.class.getResourceAsStream(path);
            if (is == null) {
                writeResponse("File not found", HttpStatus.SC_NOT_FOUND);
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
                writeResponse(response.toString(), HttpStatus.SC_NOT_FOUND);
            } catch (IOException e) {
                response.append("Error while reading from file in writeResponse()");
                writeResponse(response.toString(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
            } catch (RuntimeException e) {
                writeResponse(e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
        } else {
            response.append("RESPONSEBODY");
        }

        //ShortcutSet gotofile = ActionManager.getInstance().getAction("GotoFile").getShortcutSet();
        //ShortcutSet gotoclass = ActionManager.getInstance().getAction("GotoClass").getShortcutSet();
        //ShortcutSet gotosymbol = ActionManager.getInstance().getAction("GotoSymbol").getShortcutSet();

        String finalResponse = response.toString();
        //finalResponse = finalResponse.replaceFirst("GOTOFILESHORTCUT", getKeyboardShortcutFromShortcutSet(gotofile));
        //finalResponse = finalResponse.replaceFirst("GOTOCLASSSHORTCUT", getKeyboardShortcutFromShortcutSet(gotoclass));
        //finalResponse = finalResponse.replaceFirst("GOTOSYMBOLSHORTCUT", getKeyboardShortcutFromShortcutSet(gotosymbol));

        //if (currentProject != null) {
        //   finalResponse = finalResponse.replaceFirst("PROJECTNAME", currentProject.getName());
        //}

        //finalResponse = finalResponse.replaceFirst("GOTOFILESHORTCUTSTRING", gotofile.getShortcuts()[0].toString());
        // finalResponse = finalResponse.replaceFirst("GOTOCLASSSHORTCUTSTRING", gotoclass.getShortcuts()[0].toString());
        // finalResponse = finalResponse.replaceFirst("GOTOSYMBOLSHORTCUTSTRING", gotosymbol.getShortcuts()[0].toString());

        finalResponse = finalResponse.replace("RESPONSEBODY", responseBody);

        try {
            exchange.sendResponseHeaders(errorCode, finalResponse.length());
            os = exchange.getResponseBody();
            os.write(finalResponse.getBytes());
            System.out.println("end writeResponse() = " + (System.currentTimeMillis() - startTime));
        } catch (IOException e) {
            //This is an exception we can't send data to client
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


    private static class KeyModifier {
        public final int modifier;
        public final int key;

        private KeyModifier(int modifier, int key) {
            this.modifier = modifier;
            this.key = key;
        }
    }


}
