package web.view.ukhorskaya;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 10/14/11
 * Time: 3:49 PM
 */
public class IdeaInitializer implements DataProvider {

    public static String directoryId;

    private static IdeaProjectTestFixture myProjectFixture = null;
    private Project myProject;

    long startTime = System.currentTimeMillis();

    public IdeaInitializer() {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    IdeaTestCase.initPlatformPrefix();
                }
            });
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            System.err.println("Impossible to start IntellijIdea");
            e.printStackTrace();
        }

        try {
            CodeInsightSettings defaultSettings = new CodeInsightSettings();
            Element oldS = new Element("temp");
            defaultSettings.writeExternal(oldS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        //String myTempDir = ORIGINAL_TEMP_DIR + "/" + getName() + PRNG.nextInt(Integer.MAX_VALUE);
        String myTempDir = FileUtil.getTempDirectory() + File.separatorChar + getName();
        FileUtil.resetCanonicalTempPathCache(myTempDir);

        try {
            final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder();
            myProjectFixture = testFixtureBuilder.getFixture();

            //Module module = ModuleManager.getInstance(getProject()).newModule(getProjectDir(), EmptyModuleType.getInstance());
            //TempDirTestFixture myTempDirFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
            //myTempDirFixture.setUp();

            JavaTestFixtureFactory.getFixtureFactory();
            final JavaModuleFixtureBuilder javaModuleFixtureBuilder = testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class);
            javaModuleFixtureBuilder.addLibrary("jdk", PathUtil.getJarPathForClass(Long.class));

            javaModuleFixtureBuilder.addContentRoot(myTempDir).addSourceRoot("src");
            FileUtil.copyDir(new File("C:/Development/KotlinCompiler/testData/runConfiguration/module1"), new File(myTempDir), false);
            final File moduleFile = new File(myTempDir.replace('/', File.separatorChar), getName() + ModuleFileType.DOT_DEFAULT_EXTENSION);

            //javaModuleFixtureBuilder.addContentRoot(myTempDirFixture.getTempDirPath()).addSourceRoot("src");
            //FileUtil.copyDir(new File("C:/Development/KotlinCompiler/testData/runConfiguration/module1"), new File(myTempDirFixture.getTempDirPath()), false);
            //final File moduleFile = new File(myTempDirFixture.getTempDirPath().replace('/', File.separatorChar), getName() + ModuleFileType.DOT_DEFAULT_EXTENSION);

            FileUtil.createIfDoesntExist(moduleFile);
            System.out.println("createIfDoesntExist " + (System.currentTimeMillis() - startTime));
            myProjectFixture.setUp();
            myProject = myProjectFixture.getProject();
            //IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myProjectFixture).setUp();
            System.out.println("setUp " + (System.currentTimeMillis() - startTime));
            //directoryId = "" + startTime;
            directoryId = "";
            //directoryId = myTempDirFixture.getTempDirPath().substring(myTempDirFixture.getTempDirPath().indexOf("unitTest"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void tearDown() {
        final Editor[] allEditors = EditorFactory.getInstance().getAllEditors();
        if (allEditors.length > 0) {
            for (Editor editor : allEditors) {
                EditorFactory.getInstance().releaseEditor(editor);
            }
        }
        try {
            myProjectFixture.tearDown();
        } catch (Exception e) {
            System.out.println("Error in tearDown method");
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    private String getName() {
        // return "newProject" + startTime;
        return "newProject";
    }

    private Project getProject() {
        return myProjectFixture.getProject();
    }

    @Nullable
    private String getProjectDir() {
        VirtualFile baseDir = getProject().getBaseDir();
        if (baseDir != null) {
            return baseDir.getPath();
        }
        System.err.println("Impossible to find basedir for project");
        return null;
    }

    @Override
    public Object getData(@NonNls String dataId) {
        if (PlatformDataKeys.PROJECT.is(dataId)) {
            return myProject;
        } else if (PlatformDataKeys.EDITOR.is(dataId)) {
            return FileEditorManager.getInstance(myProject).getSelectedTextEditor();
        } else {
            Editor editor = (Editor) getData(PlatformDataKeys.EDITOR.getName());
            if (editor != null) {
                FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(myProject);
                return manager.getData(dataId, editor, manager.getSelectedFiles()[0]);
            }
            return null;
        }
    }
}


