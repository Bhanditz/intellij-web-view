package web.view.ukhorskaya.handlers;

import com.sun.net.httpserver.HttpExchange;
import web.view.ukhorskaya.sessions.HttpSession;
import web.view.ukhorskaya.sessions.MainHttpSession;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/10/11
 * Time: 1:23 PM
 */
public class MyMainHandler extends MyBaseHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        //if (!parseRequest(exchange)) {
        clearMaps();
        if (!parseRequest(exchange)) {
            HttpSession session = new MainHttpSession();
            session.handle(exchange);
        }
    }
}
