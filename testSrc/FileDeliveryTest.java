import com.intellij.ide.util.gotoByName.GotoClassModel2;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NonNls;
import web.view.ukhorskaya.IdeaHttpServer;
import web.view.ukhorskaya.JSONResponse;
import web.view.ukhorskaya.handlers.MyTestHandler;
import web.view.ukhorskaya.providers.TestHighlighterProvider;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.NoSuchAlgorithmException;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/4/11
 * Time: 4:21 PM
 */
public class FileDeliveryTest extends IdeaTestCase {


    private final String LOCALHOST = "http://localhost/";
    private Editor myEditor;

    public void testServerStarted() {
        assertTrue("Server didn't start", IdeaHttpServer.getInstance().isServerRunning());
    }

    @Override
    protected void setUp() throws Exception {
        IdeaHttpServer.ourHandlerClass = MyTestHandler.class;
        super.setUp();
        //IdeaHttpServer.getInstance().isServerRunning();
    }

    @Override
    protected boolean isRunInWriteAction() {
        return false;
    }

    @Override
    protected Module createModule(@NonNls String moduleName) {
        final Module module = super.createModule(moduleName);
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
                ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
                model.addContentEntry(module.getModuleFile().getParent());
                model.commit();
            }
        });
        return module;
    }

    /*public void testIncorrectUrlFormat() throws IOException {
        String urlPath = "incorrectUrlFormat";
        String expectedResult = "Path to the file is incorrect.<br/>URL format is [localhost]/[project name]/[path to the file]";

        compareResults(urlPath, addHtmlHeader(expectedResult));
    }*/


    /*public void testAbsentProject() throws IOException {
        String absentProjectName = "myProject" + RandomUtils.nextInt();
        String urlPath = absentProjectName + "/Foo.java";
        String expectedResult = "Project " + absentProjectName + " not found. Check that the project is opened in Intellij IDEA.";

        compareResults(urlPath, addHtmlHeader(expectedResult));
    }*/

    public void testAbsentFile() throws IOException, InterruptedException {
        String urlPath = myProject.getName() + "/AbsentFile.java";
        String expectedResult = "File /AbsentFile.java not found at project " + myProject.getName();

        compareResults(urlPath, addHtmlHeader(expectedResult));
    }

    public void testFooRoot_java() throws IOException, InterruptedException {
        String fileName = this.getTestName(false).replace("_", ".");
        makeFileInProject(fileName, true);
        getFilesToCompare(fileName, true);
    }

    public void testFoo_java() throws IOException, InterruptedException {
        String fileName = this.getTestName(false).replace("_", ".");
        makeFileInProject(fileName);
        getFilesToCompare(fileName);
    }

    public void testMain_mxml() throws IOException, InterruptedException {
        String fileName = this.getTestName(false).replace("_", ".");
        makeFileInProject(fileName);
        getFilesToCompare(fileName);
    }

    private void getFilesToCompare(String fileName) throws IOException {
        getFilesToCompare(fileName, false);
    }

    private void getFilesToCompare(String fileName, boolean isRootDirectory) throws IOException {
        String urlPath;
        if (isRootDirectory) {
            urlPath = myProject.getName() + "/" + fileName;
        } else {
            urlPath = myProject.getName() + "/testData/" + fileName;
        }

        String expectedFilePath = getProjectDir().getPath() + "/testData/" + fileName + ".html";

        VirtualFile expectedFile = LocalFileSystem.getInstance().findFileByPath(expectedFilePath);
        String expectedResult = processString(VfsUtil.loadText(expectedFile)).replaceAll("PROJECT_NAME", myProject.getName());
        compareResults(urlPath, expectedResult);
    }

    private void compareResults(final String urlToInputFile, String expectedResult) throws IOException {
        String actualResult = getFileContentFromUrl(urlToInputFile);

        assertEquals("Wrong result", expectedResult, actualResult);
    }

    public void testJsonNullFormat() throws NoSuchAlgorithmException {
        String expectedResult = "[{\"label\":\"null\"}]";
        GotoClassModel2 model = new GotoClassModel2(myProject);
        JSONResponse response = new JSONResponse(model);
        String actualResult = response.getResponse(myProject, null, "qwerty");

        assertEquals("Wrong result", expectedResult, actualResult);
    }

    /* public void testJsonFormat() throws NoSuchAlgorithmException, IOException {
        String expectedResult = "[{\"label\":\"null\"}]";
        makeFileInProject("Foo.java", true);
        makeFileInProject("FooRoot.java", true);
        GotoClassModel2 model = new GotoClassModel2(myProject);
        JSONResponse response = new JSONResponse(model);
        String actualResult = response.getResponse(myProject, null, "myh");

        assertEquals("Wrong result", expectedResult, actualResult);
    }*/


    private void makeFileInProject(String fileName) throws IOException {
        makeFileInProject(fileName, false);
    }

    private void makeFileInProject(String fileName, boolean isRootDirectory) throws IOException {
        final VirtualFile baseDir;
        if (isRootDirectory) {
            baseDir = ProjectRootManager.getInstance(myProject).getContentRoots()[0];

        } else {
            baseDir = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
                public VirtualFile compute() {
                    try {
                        return ProjectRootManager.getInstance(myProject).getContentRoots()[0].createChildDirectory(this, "testData");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        String inputFilePath = getProjectDir().getPath() + "/testData/" + fileName;
        final VirtualFile inputFile = LocalFileSystem.getInstance().findFileByPath(inputFilePath);

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
                try {
                    VfsUtil.copyFile(this, inputFile, baseDir);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private String getFileContentFromUrl(String urlPathWoLocalhost) throws IOException {
        String urlPath = LOCALHOST + urlPathWoLocalhost;
        URL url = new URL(urlPath);
        HttpURLConnection urlConnection = null;
        BufferedReader in = null;
        urlConnection = (HttpURLConnection) url.openConnection();

        String relPath = "";
        if (urlPathWoLocalhost.contains("/")) {
            relPath = urlPathWoLocalhost.substring(urlPathWoLocalhost.indexOf("/"));
        } else {
            relPath = urlPathWoLocalhost;
        }

        final VirtualFile currentFile = getFileByRelPath(relPath);
        if (currentFile == null) {
            try {
                urlConnection.getInputStream();
            } catch (FileNotFoundException e) {
                in = new BufferedReader(new InputStreamReader(urlConnection.getErrorStream()));
            }
        } else {
            final Ref<IterationState> stateRef = new Ref<IterationState>();
            final Ref<PsiFile> psiFileRef = new Ref<PsiFile>();
            final Ref<Integer> intPositionRef = new Ref<Integer>();
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                    psiFileRef.set(PsiManager.getInstance(myProject).findFile(currentFile));
                    Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFileRef.get());
                    myEditor = EditorFactory.getInstance().createEditor(document, myProject, currentFile, true);
                    stateRef.set(new IterationState((EditorEx) myEditor, 0, false));
                    intPositionRef.set(myEditor.getCaretModel().getVisualLineEnd());
                }
            });

            ((MyTestHandler) IdeaHttpServer.getInstance().getMyHandler()).setVariables(psiFileRef.get(), stateRef.get(), intPositionRef.get());
            //((MyTestHandler) IdeaHttpServer.getInstance().getMyHandler()).setIterationState(stateRef.get());
            //((MyTestHandler) IdeaHttpServer.getInstance().getMyHandler()).setIntPosition(intPositionRef.get());

            if (currentFile.getName().contains("Main.mxml")) {
                setIterationStateForInjection(psiFileRef.get());
            }

            in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
        }

        String str;
        StringBuilder result = new StringBuilder();
        while ((str = in.readLine()) != null) {
            result.append(str);
        }
        in.close();

        return result.toString();
    }

    private void setIterationStateForInjection(PsiFile file) {
        TestHighlighterProvider provider = new TestHighlighterProvider();
        final Ref<PsiFile> injectionRef = new Ref<PsiFile>();

        file.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                final PsiElement next = element.getNextSibling();
                if (next != null) {
                    final Ref<PsiElement> injection = new Ref<PsiElement>();
                    ApplicationManager.getApplication().runReadAction(new Runnable() {
                        @Override
                        public void run() {
                            PsiFile file = next.getContainingFile();
                            int pos = next.getTextRange().getStartOffset();
                            injection.set(InjectedLanguageUtil.findElementAtNoCommit(file, pos));
                        }
                    });

                    if (injection.get() instanceof PsiFile) {
                        injectionRef.set((PsiFile) injection.get());
                    }
                }
                super.visitElement(element);
            }
        });

        final Ref<IterationState> stateRef = new Ref<IterationState>();
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
                Document document = PsiDocumentManager.getInstance(myProject).getDocument(injectionRef.get());
                EditorFactory.getInstance().releaseEditor(myEditor);
                myEditor = EditorFactory.getInstance().createEditor(document, myProject, injectionRef.get().getFileType(), true);
                stateRef.set(new IterationState((EditorEx) myEditor, 0, false));
            }
        });

        provider.setIterationState(stateRef.get());

        ((MyTestHandler) IdeaHttpServer.getInstance().getMyHandler()).setHighlightingProvider(provider);
    }

    @Override
    protected void tearDown() throws Exception {
        if (myEditor != null) {
            EditorFactory.getInstance().releaseEditor(myEditor);
        }
        super.tearDown();
    }

    private VirtualFile getFileByRelPath(String relPath) {
        for (VirtualFile file : ProjectRootManager.getInstance(myProject).getContentRoots()) {
            VirtualFile currentFile = file.findFileByRelativePath(relPath);
            if (currentFile != null) {
                return currentFile;
            }
        }
        return null;
    }

    private File getProjectDir() {
        File dir = new File(getClass().getResource("").getPath());
        while (dir != null && !isProjectDir(dir)) {
            dir = dir.getParentFile();
        }
        return dir;
    }

    private boolean isProjectDir(File dir) {
        String path = dir.getAbsolutePath().replace("%20", " ");
        return new File(path).isDirectory() && new File(path + "\\" + ProjectUtil.DIRECTORY_BASED_PROJECT_DIR).exists();
    }

    private String addHtmlHeader(String inputString) {
        StringBuilder response = new StringBuilder();
        response.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">");
        response.append("<html>");
        response.append("<head>");
        //TODO add sessionId
        //1response.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"____.css?type=css&sessionid=" + sessionId + "\"/>");
        response.append("<title>Web View</title>");

        response.append("<script src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.5/jquery.min.js\"></script>");
        response.append("<script src=\"/highlighting.js?file=highlighting.js\"></script>");
        /* PopUp Dialog for find classes */
        response.append("<script src=\"/dialog.js?file=dialog.js\"></script>");
        response.append("<script src=\"/jquery/development-bundle/jquery-1.6.2.js?type=jquery_lib\"></script>");
        response.append("<script src=\"/jquery/development-bundle/external/jquery.bgiframe-2.1.2.js?type=jquery_lib\"></script>");
        response.append("<script src=\"/jquery/development-bundle/ui/jquery.ui.core.js?type=jquery_lib\"></script>");
        response.append("<script src=\"/jquery/development-bundle/ui/jquery.ui.widget.js?type=jquery_lib\"></script>");
        response.append("<script src=\"/jquery/development-bundle/ui/jquery.ui.mouse.js?type=jquery_lib\"></script>");
        //response.append("<script src=\"/resources/jquery/development-bundle/ui/jquery.ui.draggable.js?type=jquery_lib\"></script>");
        //response.append("<script src=\"/resources/jquery/development-bundle/ui/jquery.ui.position.js?type=jquery_lib\"></script>");
        //response.append("<script src=\"/resources/jquery/development-bundle/ui/jquery.ui.resizable.js?type=jquery_lib\"></script>");
        response.append("<script src=\"/jquery/development-bundle/ui/jquery.ui.dialog.js?type=jquery_lib\"></script>");
        response.append("<link rel=\"stylesheet\" href=\"/jquery/development-bundle/themes/base/jquery.ui.all.css\">");
        response.append("<link type=\"text/css\" href=\"/jquery/css/ui-lightness/jquery-ui-1.8.16.custom.css?type=jquery_lib\" rel=\"stylesheet\"/>");
        response.append("<script src=\"/jquery/js/jquery-ui-1.8.16.custom.min.js?type=jquery_lib\" type=\"text/javascript\"></script>");
        response.append("</head>");
        //TODO add projectName
        response.append("<body onload=\"setGotoFileShortcut(16,17,78); setGotoClassShortcut(17,78); setGotoSymbolShortcut(16,18,17,78); setProjectName('PROJECT_NAME');\"><div id=\"fake-body\">");
        response.append("<div>");
        response.append(inputString);
        response.append("</div>");
        response.append("<div id=\"dialog\" style=\"min-height: 26px !important; height: 26px !important;\"><div class=\"ui-widget\"><input id=\"tags\" value=\"\" type=\"text\" style='width: 468px;'/></div></div></div><div id=\"dock\"><div> Go to file: <b>Ctrl+Shift+N</b>     Go to class: <b>Ctrl+N</b>     Go to symbol: <b>Ctrl+Alt+Shift+N</b>     </div></div>");
        response.append("</body>");
        response.append("</html>");
        return response.toString();
    }

    private String processString(String inputString) {
        inputString = inputString.replaceAll("    ", " ");
        return inputString.replaceAll("\\r\\n", "");
    }


}
