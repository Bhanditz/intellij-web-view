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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.IdeaTestCase;
import org.apache.commons.lang.math.RandomUtils;
import org.jetbrains.annotations.NonNls;
import web.view.ukhorskaya.IdeaHttpServer;
import web.view.ukhorskaya.MyTestHandler;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

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
        IdeaHttpServer.getInstance().isServerRunning();
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

    public void testIncorrectUrlFormat() throws IOException {
        String urlPath = "incorrectUrlFormat";
        String expectedResult = "Path to the file is incorrect.<br/>URL format is [localhost]/[project name]/[path to the file]";

        compareResults(urlPath, addHtmlHeader(expectedResult));
    }

    public void testAbsentProject() throws IOException {
        String absentProjectName = "myProject" + RandomUtils.nextInt();
        String urlPath = absentProjectName + "/Foo.java";
        String expectedResult = "Project " + absentProjectName + " not found. Check that the project is opened in Intellij IDEA.";

        compareResults(urlPath, addHtmlHeader(expectedResult));
    }

    public void testAbsentFile() throws IOException, InterruptedException {
        String urlPath = myProject.getName() + "/AbsentFile.java";
        String expectedResult = "File /AbsentFile.java not found at project " + myProject.getName();

        compareResults(urlPath, addHtmlHeader(expectedResult));
    }

    public void testFooRoot() throws IOException, InterruptedException {
        String fileName = this.getTestName(false);
        makeFileInProject(fileName, true);
        getFilesToCompare(fileName, true);
    }

    public void testFoo() throws IOException, InterruptedException {
        String fileName = this.getTestName(false);
        makeFileInProject(fileName);
        getFilesToCompare(fileName);
    }

    private void getFilesToCompare(String fileName) throws IOException {
        getFilesToCompare(fileName, false);
    }

    private void getFilesToCompare(String fileName, boolean isRootDirectory) throws IOException {
        String urlPath;
        if (isRootDirectory) {
            urlPath = myProject.getName() + "/" + fileName + ".java";
        } else {
            urlPath = myProject.getName() + "/testData/" + fileName + ".java";

        }


        String expectedFilePath = getProjectDir().getPath() + "/testData/" + fileName + ".html";

        VirtualFile expectedFile = LocalFileSystem.getInstance().findFileByPath(expectedFilePath);
        String expectedResult = processString(VfsUtil.loadText(expectedFile));
        compareResults(urlPath, expectedResult);
    }

    private void compareResults(final String urlToInputFile, String expectedResult) throws IOException {
        String actualResult = getFileContentFromUrl(urlToInputFile);

        assertEquals("Wrong result", expectedResult, actualResult);
    }

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

        String inputFilePath = getProjectDir().getPath() + "/testData/" + fileName + ".java";
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
            final Ref<Integer> intPositionRef = new Ref<Integer>();
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(currentFile);
                    Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
                    myEditor = EditorFactory.getInstance().createEditor(document, myProject, currentFile, true);
                    stateRef.set(new IterationState((EditorEx) myEditor, 0, false));
                    intPositionRef.set(myEditor.getCaretModel().getVisualLineEnd());
                }
            });

            ((MyTestHandler) IdeaHttpServer.getInstance().getMyHandler()).setIterationState(stateRef.get());
            ((MyTestHandler) IdeaHttpServer.getInstance().getMyHandler()).setIntPosition(intPositionRef.get());
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
        response.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"____.css\"/>");
        response.append("<title>Web View</title>");
        response.append("</head>");
        response.append("<body>");
        response.append("<div>");
        response.append(inputString);
        response.append("</div>");
        response.append("</body>");
        response.append("</html>");
        return response.toString();
    }

    private String processString(String inputString) {
        inputString = inputString.replaceAll("    ", " ");
        return inputString.replaceAll("\\r\\n", "");
    }


}
