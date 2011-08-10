package web.view.ukhorskaya;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/5/11
 * Time: 10:05 AM
 */
public class IdeaHttpServer implements ApplicationComponent {
    private boolean isServerRunning = false;
    private HttpServer server;
    private MyBaseHandler myHandler;


    public static IdeaHttpServer getInstance() {
        return ApplicationManager.getApplication().getComponent(IdeaHttpServer.class);
    }

    public void initComponent() {
        startServer();
    }

    public void disposeComponent() {
        stopServer();
    }

    private void startServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(80), 10);
            if (myHandler == null) {
                myHandler = new MyMainHandler();
            }
            server.createContext("/", myHandler);
            server.setExecutor(null);
            server.start();

            isServerRunning = true;
            System.out.println("Server is started");

        } catch (IOException e) {
            isServerRunning = false;
            System.err.println("Server didn't start");
            e.printStackTrace();
        }
    }

    private void stopServer() {
        server.stop(10);
        System.out.println("Server is stopped");
        isServerRunning = false;
    }

    @NotNull
    public String getComponentName() {
        return IdeaHttpServer.class.getName();
    }

    public boolean isServerRunning() {
        return isServerRunning;
    }

    public void setMyHandler(MyBaseHandler myHandler) {
        stopServer();

        this.myHandler = myHandler;
        startServer();
    }
}
