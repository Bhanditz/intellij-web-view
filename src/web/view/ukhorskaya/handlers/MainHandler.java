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
public class MainHandler extends BaseHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        //if (!sendNonSourceFile(exchange)) {
        if (!sendNonSourceFile(exchange)) {
            HttpSession session = new MainHttpSession();
            session.handle(exchange);
        }
    }
}
