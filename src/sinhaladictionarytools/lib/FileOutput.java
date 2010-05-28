/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package sinhaladictionarytools.lib;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author buddhika
 */
public class FileOutput extends Thread{

    FileWriter fw;
    String out;

    public FileOutput(File f, String out) {
        try {
            this.fw = new FileWriter(f);
            this.out = out;
        } catch (IOException ex) {
            System.err.println("Couldn't create the file output.");
            Logger.getLogger(FileOutput.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void run(){
        if (fw != null){            
            try {
                fw.write(out);
                fw.flush();
                fw.close();
            } catch (IOException ex) {
                System.err.println("Problem in writing to the given file.");
                Logger.getLogger(FileOutput.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static void copyFile(File sourceFile, File destFile) throws IOException {
        if(!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        }
        finally {
            if(source != null) {
            source.close();
            }
            if(destination != null) {
                destination.close();
            }
        }

    }
}