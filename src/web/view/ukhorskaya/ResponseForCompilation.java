package web.view.ukhorskaya;

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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 10/20/11
 * Time: 1:41 PM
 */

public class ResponseForCompilation {

    private final boolean isOnlyCompilation;
    private final Project currentProject;

    private String finalResult;

    public ResponseForCompilation(boolean onlyCompilation, Project currentProject) {
        this.isOnlyCompilation = onlyCompilation;
        this.currentProject = currentProject;
    }

    public String getResult() {
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
                            finalResult = builder.toString();
                            //writeResponse(builder.toString(), HttpStatus.SC_OK, true);
                        } else {
                            if (builder.length() != 0) {
                                finalResult = builder.toString();
                                //writeResponse(builder.toString(), HttpStatus.SC_OK, true);
                            } else {
                                runProject();
                            }
                        }

                    }
                });

            }
        }, ModalityState.defaultModalityState());

        return finalResult;
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
                                    finalResult = result.toString();
                                    //writeResponse(result.toString(), HttpStatus.SC_OK, true);
                                }

                                @Override
                                public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
                                    finalResult = result.toString();
                                    //writeResponse(result.toString(), HttpStatus.SC_OK, true);
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
            finalResult = "Impossible to run configuration";
            //LOG.error("Impossible to run configuration");
        }
    }

}
