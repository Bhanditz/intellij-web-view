package web.view.ukhorskaya;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 10/14/11
 * Time: 3:35 PM
 */
public class Main {


    public static void main(String[] args) {
        try {
            new IdeaInitializer();
            //new NewIdeaInitializer().setUp();
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        IdeaHttpServer ideaHttpServer = new IdeaHttpServer();
        ideaHttpServer.initComponent();
    }
}
