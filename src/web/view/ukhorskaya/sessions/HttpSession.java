package web.view.ukhorskaya.sessions;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.execution.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.EmptyIcon;
import com.sun.net.httpserver.HttpExchange;
import org.apache.commons.httpclient.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import web.view.ukhorskaya.autocomplete.IconHelper;
import web.view.ukhorskaya.css.GlobalCssMap;
import web.view.ukhorskaya.handlers.BaseHandler;

import java.awt.event.InputEvent;
import java.io.*;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private void setGlobalVariables() {
        String requestURI = exchange.getRequestURI().getPath().substring(6);
        requestURI = requestURI.replace("%20", " ");
        currentVirtualFile = VirtualFileManager.getInstance().findFileByUrl(requestURI);
        String response;
        if (currentVirtualFile == null) {
            response = "File with path " + requestURI + " not found ";
            writeResponse(response, HttpStatus.SC_NOT_FOUND);
            return;
        }
        currentProject = ProjectUtil.guessProjectForFile(currentVirtualFile);
    }


    private void sendCompletionResult() {
        setGlobalVariables();
        writeDataToFile();

        String param = exchange.getRequestURI().getQuery();
        String[] position = new String[0];
        if (param.contains("cursorAt")) {
            position = (param.substring(param.indexOf("cursorAt=") + 9)).split(",");
        }

        setVariables(currentVirtualFile);

        final VisualPosition visualPosition = new VisualPosition(Integer.parseInt(position[0]), Integer.parseInt(position[1]));

        final Ref<String> stringRef = new Ref<String>();
        final Ref<LookupElement[]> lookupRef = new Ref<LookupElement[]>();

        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            @Override
            public void run() {
                currentEditor.getCaretModel().moveToVisualPosition(visualPosition);
                System.out.println("begin getCompletion()  = " + (System.currentTimeMillis() - startTime));
                new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(currentProject, currentEditor, 1, false);
                System.out.println("end getCompletion()  = " + (System.currentTimeMillis() - startTime));

                LookupImpl lookup = (LookupImpl) LookupManager.getActiveLookup(currentEditor);

                LookupElement[] myItems = lookup == null ? null : lookup.getItems().toArray(new LookupElement[lookup.getItems().size()]);
                if (lookup != null) {
                    lookup.hide();
                }
                lookupRef.set(myItems);
            }
        }, ModalityState.defaultModalityState());

        JSONArray resultString = new JSONArray();
        if (lookupRef.get() != null) {
            for (final LookupElement item : lookupRef.get()) {
                final LookupElementPresentation presentation = new LookupElementPresentation();
                ApplicationManager.getApplication().invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        item.renderElement(presentation);
                    }
                }, ModalityState.defaultModalityState());
                Map<String, String> map = new HashMap<String, String>();
                if ((presentation.getIcon() != null) && !(presentation.getIcon() instanceof EmptyIcon)) {
                    map.put("icon", "/fticons/" + IconHelper.getInstance().addIconToMap(presentation.getIcon()));
                } else {
                    map.put("icon", "");
                }
                if (presentation.getItemText() != null) {
                    map.put("name", presentation.getItemText());
                } else {
                    map.put("name", "");
                }

                if (presentation.getTailText() != null) {
                    map.put("tail", presentation.getTailText());
                } else {
                    map.put("tail", "");
                }
                resultString.put(map);
            }
        }
        writeResponse(resultString.toString(), HttpStatus.SC_OK, true);
    }

    private void sendProjectSourceFile() {
        String param = exchange.getRequestURI().getQuery();

        setGlobalVariables();
        if ((param != null) && (param.contains("sendData=true"))) {
            writeDataToFile();
            String response = getContentWithDecoration();
            response = response.replaceAll("\\n", "");
            writeResponse(response, HttpStatus.SC_OK, true);
        } else {
            writeResponse("", HttpStatus.SC_OK);
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
                    try {
                        VfsUtil.saveText(currentVirtualFile, finalResponse1.substring(5));
                    } catch (IOException e) {
                        LOG.error("Impossible to write to file in UTF-8");
                    }
                }
            }, ModalityState.defaultModalityState());
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
        System.out.println("begin setVariables()  = " + (System.currentTimeMillis() - startTime));
        try {
            setVariables(currentVirtualFile);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        System.out.println("end setVariables()  = " + (System.currentTimeMillis() - startTime));

        /*PsiElement mirrorFile = null;
        if (currentPsiFile instanceof PsiCompiledElement) {
            mirrorFile = ((PsiCompiledElement) currentPsiFile).getMirror();
        }*/

        final Ref<String> stringRef = new Ref<String>();

        /*ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            @Override
            public void run() {
                JsonResponseForHighlighting responseForHighlighting = new JsonResponseForHighlighting(PsiDocumentManager.getInstance(currentProject).getDocument(currentPsiFile), iterationState);
                *//*if (jsonResult != null) {
                    responseForHighlighting.setJsonResult(jsonResult);
                }
                if (pairHashMap != null) {
                    responseForHighlighting.setPairHashMap(pairHashMap);
                }*//*
                System.out.println("3");
                try {
                    stringRef.set(responseForHighlighting.getResult());
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }, ModalityState.defaultModalityState());*/


        final DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl) DaemonCodeAnalyzer.getInstance(currentProject);
        final Ref<List<HighlightInfo>> infoListRef = new Ref<List<HighlightInfo>>();
        System.out.println("runPasses b = " + (System.currentTimeMillis() - startTime));

        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            @Override
            public void run() {
                final TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(currentEditor);
                List<HighlightInfo> infoList = codeAnalyzer.runPasses(currentPsiFile, currentDocument, textEditor, ArrayUtil.EMPTY_INT_ARRAY, false, null);
                infoListRef.set(infoList);
            }
        }, ModalityState.defaultModalityState());
        System.out.println("runPasses e = " + (System.currentTimeMillis() - startTime));

        final JSONArray jsonResult = new JSONArray();
        final Map<Integer, Pair<String, String>> pairHashMap = new HashMap<Integer, Pair<String, String>>();

        System.out.println("infoListRef b = " + (System.currentTimeMillis() - startTime));

        for (HighlightInfo info : infoListRef.get()) {
            if (!(info.getSeverity().myName.equals("INFORMATION"))) {
                //if (info.isAfterEndOfLine) {
                    TextAttributes attr = info.getTextAttributes(PsiUtilBase.getElementAtOffset(currentPsiFile, info.startOffset), EditorColorsManager.getInstance().getGlobalScheme());
                    String className = null;
                    if (attr != null) {
                        className = GlobalCssMap.getInstance().getClassFromTextAttribute(attr);
                    }
                    if (className != null) {
                        jsonResult.put(HttpSession.getMapWithPositionsHighlighting(currentDocument, info.startOffset, info.endOffset, className, info.description, info.getSeverity().myName));
                    }
                /*} else {
                    pairHashMap.put(currentDocument.getLineNumber(info.startOffset), new Pair<String, String>(info.description, info.getSeverity().myName));
                }*/
            }
        }
        System.out.println("infoListRef e = " + (System.currentTimeMillis() - startTime));
        System.out.println("JsonResponseForHighlighting = " + (System.currentTimeMillis() - startTime));


        /*ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            @Override
            public void run() {
                JsonResponseForHighlighting responseForHighlighting = new JsonResponseForHighlighting(PsiDocumentManager.getInstance(currentProject).getDocument(currentPsiFile), iterationState);
                if (jsonResult != null) {
                    responseForHighlighting.setJsonResult(jsonResult);
                }
                if (pairHashMap != null) {
                    responseForHighlighting.setPairHashMap(pairHashMap);
                }
                System.out.println("3");
                try {
                    stringRef.set(responseForHighlighting.getResult());
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }, ModalityState.defaultModalityState());*/


        System.out.println("end JsonResponseForHighlighting = " + (System.currentTimeMillis() - startTime));
        return stringRef.get();
    }


    private void writeResponse(String responseBody, int errorCode) {
        writeResponse(responseBody, errorCode, false);
    }

    public static Map<String, String> getMapWithPositionsHighlighting(@NotNull Document document, int start, int end, String className, String title, String severity) {
        if (!severity.equals("INFORMATION")) {
            int lineNumberForElementStart = document.getLineNumber(start);
            int lineNumberForElementEnd = document.getLineNumber(end);
            int charNumberForElementStart = start - document.getLineStartOffset(lineNumberForElementStart);
            int charNumberForElementEnd = end - document.getLineStartOffset(lineNumberForElementStart);
            if (start == end) {
                charNumberForElementStart--;
            }
            Map<String, String> map = new HashMap<String, String>();
            map.put("className", className);
            if ((title != null) && (title.contains("\""))) {
                title = title.replaceAll("\\\"", "'");
            }
            map.put("titleName", title);
            map.put("severity", severity);
            map.put("x", "{line: " + lineNumberForElementStart + ", ch: " + charNumberForElementStart + "}");
            map.put("y", "{line: " + lineNumberForElementEnd + ", ch: " + charNumberForElementEnd + "}");
            return map;
        }
        return null;
    }

    private void compileOrRunProject(final boolean isOnlyCompilation) {
        final StringBuilder builder = new StringBuilder();
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            @Override
            public void run() {
                CompilerManager.getInstance(currentProject).compile(ModuleManager.getInstance(currentProject).getModules()[0], new CompileStatusNotification() {
                    @Override
                    public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
                        for (CompilerMessage message : compileContext.getMessages(CompilerMessageCategory.INFORMATION)) {
                            builder.append("<p class=\"newLineClass\">").append("<img src=\"/icons/information.png\"/>");
                            builder.append(CompilerMessageCategory.INFORMATION.getPresentableText()).append(": <font color=\"red\">").append(message.getRenderTextPrefix()).append(" - ").append(message.getMessage()).append("</font></p>");
                        }
                        for (CompilerMessage message : compileContext.getMessages(CompilerMessageCategory.ERROR)) {
                            builder.append("<p class=\"newLineClass\">").append("<img src=\"/icons/error.png\"/>");
                            builder.append(CompilerMessageCategory.ERROR.getPresentableText()).append(": <font color=\"red\">").append(message.getRenderTextPrefix()).append(" - ").append(message.getMessage()).append("</font></p>");
                        }
                        for (CompilerMessage message : compileContext.getMessages(CompilerMessageCategory.STATISTICS)) {
                            builder.append("<p class=\"newLineClass\">").append(CompilerMessageCategory.STATISTICS.getPresentableText()).append(": <font color=\"red\">").append(message.getRenderTextPrefix()).append(" - ").append(message.getMessage()).append("</font></p>");
                        }
                        for (CompilerMessage message : compileContext.getMessages(CompilerMessageCategory.WARNING)) {
                            builder.append("<p class=\"newLineClass\">").append("<img src=\"/icons/warning.png\"/>");
                            builder.append(CompilerMessageCategory.WARNING.getPresentableText()).append(": <font color=\"red\">").append(message.getRenderTextPrefix()).append(" - ").append(message.getMessage()).append("</font></p>");
                        }
                        if (isOnlyCompilation) {
                            if (builder.length() == 0) {
                                builder.append("Compilation complete successfully");
                            }
                            writeResponse(builder.toString(), HttpStatus.SC_OK, true);
                        } else {
                            if (builder.length() != 0) {
                                writeResponse(builder.toString(), HttpStatus.SC_OK, true);
                            } else {
                                runProject();
                            }
                        }

                    }
                });

            }
        }, ModalityState.defaultModalityState());
    }


    private void runProject() {
        //RunConfiguration configuration = RunManager.getInstance(currentProject).createRunConfiguration("TestConf", Extensions.getExtensions(ConfigurationType.CONFIGURATION_TYPE_EP)[5].getConfigurationFactories()[0]);
        //RunManager.getInstance(currentProject).createRunConfiguration("TestConf", Extensions.getExtensions(ConfigurationType.CONFIGURATION_TYPE_EP)[5].getConfigurationFactories()[0])
        Executor executor = new DefaultRunExecutor();

        RunnerAndConfigurationSettings settings = ((RunManagerImpl) RunManager.getInstance(currentProject)).getSettings(RunManager.getInstance(currentProject).getAllConfigurations()[0]);
        //ProgramRunnerUtil.executeConfiguration(currentProject, settings, new DefaultRunExecutor());
        ProgramRunner runner = ProgramRunnerUtil.getRunner(executor.getId(), settings);
        try {
            runner.execute(new DefaultRunExecutor(), new ExecutionEnvironment(runner, settings, currentProject), new ProgramRunner.Callback() {
                @Override
                public void processStarted(RunContentDescriptor descriptor) {
                    if (descriptor != null) {
                        ProcessHandler handler = descriptor.getProcessHandler();
                        if (handler != null) {
                            handler.addProcessListener(new ProcessListener() {
                                StringBuilder result = new StringBuilder("Console: \n");

                                @Override
                                public void startNotified(ProcessEvent event) {

                                }

                                @Override
                                public void processTerminated(ProcessEvent event) {
                                    writeResponse(result.toString(), HttpStatus.SC_OK, true);
                                }

                                @Override
                                public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
                                    writeResponse(result.toString(), HttpStatus.SC_OK, true);
                                }

                                @Override
                                public void onTextAvailable(ProcessEvent event, Key outputType) {
                                    if (outputType == ProcessOutputTypes.STDOUT) {
                                        result.append(event.getText());
                                    } else if (outputType == ProcessOutputTypes.STDERR) {
                                        result.append("<p><font  color=\"red\">");
                                        result.append(event.getText());
                                        result.append("</font></p>");
                                    } else if (outputType == ProcessOutputTypes.SYSTEM) {
                                        result.append("<p><font  color=\"blue\">");
                                        result.append(event.getText());
                                        result.append("</font></p>");
                                    }
                                }
                            });
                        }
                    }
                }
            });
        } catch (ExecutionException e) {
            LOG.error("Impossible to run configuration");
        }
    }

    //Send Response
    //addFeatures - add html header or write only responseBody
    private void writeResponse(String responseBody, int errorCode, boolean addFeatures) {
        //EditorFactoryImpl.getInstance().releaseEditor(currentEditor);
        System.out.println("begin writeResponse() = " + (System.currentTimeMillis() - startTime));
        OutputStream os = null;
        StringBuilder response = new StringBuilder();

        String path;
        if (addFeatures) {
            path = "/header-wo-dialog.html";
        } else {
            path = "/header.html";
        }
        /*if (path.contains("resources")) {
            path = path.substring(path.indexOf("resources") + 9);
        } */
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
