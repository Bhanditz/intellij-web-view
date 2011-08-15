package web.view.ukhorskaya;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import web.view.ukhorskaya.handlers.MyBaseHandler;
import web.view.ukhorskaya.handlers.MyMainHandler;

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


    public static Class<? extends MyBaseHandler> ourHandlerClass = MyMainHandler.class;

    public static IdeaHttpServer getInstance() {
        return ApplicationManager.getApplication().getComponent(IdeaHttpServer.class);
    }

    public void initComponent() {
        try {
            server = HttpServer.create(new InetSocketAddress(80), 10);
            if (myHandler == null) {
                myHandler = ourHandlerClass.newInstance();
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
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void disposeComponent() {
        server.stop(0);
        System.out.println("Server is stopped");
        isServerRunning = false;
    }


    public MyBaseHandler getMyHandler() {
        return myHandler;
    }

    @NotNull
    public String getComponentName() {
        return IdeaHttpServer.class.getName();
    }

    public boolean isServerRunning() {
        return isServerRunning;
    }

}
