import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.apache.commons.lang.math.RandomUtils;
import org.jetbrains.annotations.NonNls;
import web.view.ukhorskaya.IdeaHttpServer;

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

    public void testServerStarted() {
        assertTrue("Server didn't start", IdeaHttpServer.getInstance().isServerRunning());
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

        compareResults(urlPath, expectedResult);
    }

    public void testAbsentProject() throws IOException {
        String absentProjectName = "myProject" + RandomUtils.nextInt();
        String urlPath = absentProjectName + "/Foo.java";
        String expectedResult = "Project " + absentProjectName + " not found. Check that the project is opened in Intellij IDEA.";

        compareResults(urlPath, expectedResult);
    }

    public void testAbsentFile() throws IOException, InterruptedException {
        String urlPath = myProject.getName() + "/AbsentFile.java";
        String expectedResult = "File /AbsentFile.java not found at project " + myProject.getName();

        compareResults(urlPath, expectedResult);
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
        String expectedResult = substringHtml(VfsUtil.loadText(expectedFile));
        compareResults(urlPath, expectedResult);
    }

    private void compareResults(String urlToInputFile, String expectedResult) throws IOException {
        String actualResult = getFileContentFromUrl(urlToInputFile);
        actualResult = substringHtml(actualResult);
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
            baseDir = ProjectRootManager.getInstance(myProject).getContentRoots()[0].createChildDirectory(this, "testData");
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

    private String getFileContentFromUrl(String urlPath) throws IOException {
        urlPath = LOCALHOST + urlPath;
        URL url = new URL(urlPath);
        HttpURLConnection urlConnection = null;
        BufferedReader in;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.getInputStream();
            int t = 0;
            in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));

        } catch (FileNotFoundException e) {
            in = new BufferedReader(new InputStreamReader(urlConnection.getErrorStream()));
        }

        String str;
        StringBuilder result = new StringBuilder();
        while ((str = in.readLine()) != null) {
            result.append(str);
        }
        in.close();

        return result.toString();
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

    private String substringHtml(String inputString) {
        if ((inputString.indexOf("<body><div>") != -1) && (inputString.indexOf("</div></body>") != -1)) {
            inputString = inputString.substring(inputString.indexOf("<body><div>") + 11, inputString.indexOf("</div></body>"));
        }
        return inputString;
    }


}
