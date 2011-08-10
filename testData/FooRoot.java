package com.jetbrains.ukhorskaya;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/5/11
 * Time: 10:05 AM
 * To change this template use File | Settings | File Templates.
 */
public class IdeaHttpServer implements ApplicationComponent {
    private boolean isServerRunning = false;
    private HttpServer server;

    public static void main(String[] args) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(80), 0);
            server.createContext("/", new MyHandler());
            server.setExecutor(null);
            server.start();

            System.out.println("Server is started");


        } catch (IOException e) {
            System.err.println("Server didn't start");
            e.printStackTrace();
        }
    }

    public static IdeaHttpServer getInstance() {
        return ApplicationManager.getApplication().getComponent(IdeaHttpServer.class);
    }

    public void initComponent() {
        try {
            server = HttpServer.create(new InetSocketAddress(80), 10);
            server.createContext("/", new MyHandler());
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

    public void disposeComponent() {
        server.stop(server.hashCode());
        System.out.println("Server is stopped");
        isServerRunning = false;
    }

    @NotNull
    public String getComponentName() {
        return IdeaHttpServer.class.getName();
    }

    public boolean isServerRunning() {
        return isServerRunning;  //To change body of created methods use File | Settings | File Templates.
    }
}
