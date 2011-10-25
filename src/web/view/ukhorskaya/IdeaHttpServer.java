package web.view.ukhorskaya;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import web.view.ukhorskaya.handlers.BaseHandler;
import web.view.ukhorskaya.handlers.MainHandler;

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
    private static HttpServer server;
    private BaseHandler myHandler;


    public static Class<? extends BaseHandler> ourHandlerClass = MainHandler.class;

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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void disposeComponent() {
        server.stop(0);
        System.out.println("Server is stopped");
        isServerRunning = false;
    }

    public static void stopServer() {
        IdeaHttpServer.server.stop(0);
        System.out.println("Server is stopped");
    }


    public BaseHandler getMyHandler() {
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
