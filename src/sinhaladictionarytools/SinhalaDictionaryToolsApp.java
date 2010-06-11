/*
 * SinhalaDictionaryToolsApp.java
 */

package sinhaladictionarytools;

import org.jconfig.ConfigurationManager;
import org.jconfig.handler.XMLFileHandler;
import org.jdesktop.application.Application;
import org.jdesktop.application.SingleFrameApplication;

/**
 * The main class of the application.
 */
public class SinhalaDictionaryToolsApp extends SingleFrameApplication {
    
    /**
     * At startup create and show the main frame of the application.
     */
    @Override protected void startup() {
        getConfiguration();
        show(new SinhalaDictionaryToolsView(this));
    }

    /**
     * This method is to initialize the specified window by injecting resources.
     * Windows shown in our application come fully initialized from the GUI
     * builder, so this additional configuration is not needed.
     */
    @Override protected void configureWindow(java.awt.Window root) {
    }

    /**
     * A convenient static getter for the application instance.
     * @return the instance of SinhalaDictionaryToolsApp
     */
    public static SinhalaDictionaryToolsApp getApplication() {
        return Application.getInstance(SinhalaDictionaryToolsApp.class);
    }

    /**
     * Main method launching the application.
     */
    public static void main(String[] args) {
        launch(SinhalaDictionaryToolsApp.class, args);
    }

    public static ConfigurationManager getConfiguration(){

        if (cm == null){
            cm = ConfigurationManager.getInstance();
            XMLFileHandler handler = new XMLFileHandler("config/config.xml");
            XMLFileHandler vocHandler = new XMLFileHandler("config/vocabulary_catagory.xml");

            try {
                System.out.println("Trying to load file");
                //main config
                cm.load(handler,"config");
                //vocabulary config
                cm.load(vocHandler,"voconfig");
                System.out.println("Settings successfully loaded");
            }
            catch (Exception e) {
                e.printStackTrace();
            }

        }

        return cm;
    }

    private static ConfigurationManager cm;
}
