/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package sinhaladictionarytools.lib;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Make a system call through a system shell in a platform-independent manner in Java. <br />
 * This class only demonstrate a 'dir' or 'ls' within current (execution) path, if no parameters are used.
 * If parameters are used, the first one is the system command to execute, the others are its system command parameters. <br />
 * To be system independent, an <b><a href="http://www.allapplabs.com/java_design_patterns/abstract_factory_pattern.htm">
 * Abstract Factory Pattern</a></b> will be used to build the right underlying system shell in which the system command will be executed.
 * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
 * @see <a href="http://stackoverflow.com/questions/236737#236873">
   How to make a system call that returns the stdout output as a string in various languages?</a>
 */
public final class JavaSystemCaller
{

	/**
	 * Asynchronously read the output of a given input stream. <br />
	 * Any exception during execution of the command in managed in this thread.
	 * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
	 */
	public static class StreamGobbler extends Thread
	{
		private InputStream is;
		private String type;
		private StringBuffer output = new StringBuffer();
                private ConcurrentHashMap<String, Integer> hashMap;

		public StreamGobbler(final InputStream anIs, final String aType)
		{
			this.is = anIs;
			this.type = aType;
		}

		public StreamGobbler(final InputStream anIs, final String aType, final ConcurrentHashMap<String, Integer> hashMap)
		{
			this(anIs, aType);
                        this.hashMap = hashMap;
		}

		/**
		 * Asynchronous read of the input stream. <br />
		 * Will report output as its its displayed.
		 * @see java.lang.Thread#run()
		 */
		@Override
		public final void run()
		{
			try
			{
				final InputStreamReader isr = new InputStreamReader(this.is);
				final BufferedReader br = new BufferedReader(isr);
                                String lineseperator = System.getProperty("line.separator");
				String line = null;
				while ( (line = br.readLine()) != null)
				{
                                    if (this.type.equals("MSG")){
                                        System.out.println(type + " > " + line);
                                    }

                                    this.output.append(line + lineseperator);

                                    if (hashMap != null){
                                        line = line.trim();
                                        Integer freq = hashMap.get(line);
                                        hashMap.put(line, (freq == null) ? 1 : freq + 1);
                                    }
				}
                                
                                isr.close();

			} catch (final IOException ioe)
			{
				ioe.printStackTrace();
			}
		}
		/**
		 * Get output filled asynchronously. <br />
		 * Should be called after execution
		 * @return final output
		 */
		public synchronized final String getOutput()
		{
			return this.output.toString();
		}

                
		/**
		 * Get output filled asynchronously. <br />
		 * Should be called after execution
		 * @return final output
		 */
		public synchronized final ConcurrentHashMap<String, Integer> getHashmap()
		{
			return this.hashMap;
		}

	}
	/**
	 * Execute a system command in the appropriate shell. <br />
	 * Read asynchronously stdout and stderr to report any result.
	 * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
	 */
	public static final class Exec
	{

		/**
		 * Execute a system command. <br />
		 * Listen asynchronously to stdout and stderr
		 * @param aCommand system command to be executed (must not be null or empty)
		 * @param someParameters parameters of the command (must not be null or empty)
		 * @return final output (stdout only)
		 */
		public static String execute(final String aCommand, File out, final String... someParameters)
		{
			String output = "";
			try
			{
                            ExecEnvironmentFactory anExecEnvFactory = getExecEnvironmentFactory(aCommand, someParameters);
                            final IShell aShell = anExecEnvFactory.createShell();
                            final String aCommandLine = anExecEnvFactory.createCommandLine();

                            final Runtime rt = Runtime.getRuntime();
                            System.out.println("Executing " + aCommandLine);
                            final Process proc = rt.exec(aCommandLine);

                            // any output?
                            final StreamGobbler outputGobbler = new
                                    StreamGobbler(proc.getInputStream(), "OUTPUT");

                            // any error message?
                            final StreamGobbler errorGobbler = new
                                    StreamGobbler(proc.getErrorStream(), "MSG");

                            //kick them off
                            errorGobbler.start();
                            outputGobbler.start();

                            // any error???
                            final int exitVal = proc.waitFor();
                            errorGobbler.join();
                            outputGobbler.join();
                            
                            //System.out.println("ExitValue: " + exitVal);
                            output = outputGobbler.getOutput();

                            if (out != null){
                                System.out.print(output);
                                FileWriter fw = new FileWriter(out, false);
                                fw.write(output);
                                fw.flush();
                                fw.close();
                            }

			} catch (final Throwable t)
			{
				t.printStackTrace();
			}
			return output;
		}

		private static ExecEnvironmentFactory getExecEnvironmentFactory(final String aCommand, final String... someParameters)
		{
			final String anOSName = System.getProperty("os.name" );
			if(anOSName.toLowerCase().startsWith("windows"))
			{
				return new WindowsExecEnvFactory(aCommand, someParameters);
			}
			return new UnixExecEnvFactory(aCommand, someParameters);
			// TODO be more specific for other OS.
		}

        public static void readErrors(Process pr){
            try {
                BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getErrorStream()));
                String line = "";
                while ((line = buf.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException ex) {
                Logger.getLogger(JavaSystemCaller.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

		private Exec() { /**/ }
	}
	private JavaSystemCaller() { /**/ }

	/*
	 * ABSTRACT FACTORY PATTERN
	 */
	/**
	 * Environment needed to be build for the Exec class to be able to execute the system command. <br />
	 * Must have the right shell and the right command line. <br />
	 * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
	 */
	public abstract static class ExecEnvironmentFactory
	{
		private String command = null;
		private ArrayList<String> parameters = new ArrayList<String>();
		final String getCommand() { return this.command; }
		final ArrayList<String> getParameters() { return this.parameters; }
		/**
		 * Builds an execution environment for a system command to be played. <br />
		 * Independent from the OS.
		 * @param aCommand system command to be executed (must not be null or empty)
		 * @param someParameters parameters of the command (must not be null or empty)
		 */
		public ExecEnvironmentFactory(final String aCommand, final String... someParameters)
		{
			if(aCommand == null || aCommand.length() == 0) { throw new IllegalArgumentException("Command must not be empty"); }
			this.command = aCommand;
			for (int i = 0; i < someParameters.length; i++) {
				final String aParameter = someParameters[i];
				if(aParameter == null || aParameter.length() == 0) { throw new IllegalArgumentException("Parameter n° '"+i+"' must not be empty"); }
				this.parameters.add(aParameter);
			}
		}
		/**
		 * Builds the right Shell for the current OS. <br />
		 * Allow for independent platform execution.
		 * @return right shell, NEVER NULL
		 */
		public abstract IShell createShell();
		/**
		 * Builds the right command line for the current OS. <br />
		 * Means that a command might be translated, if it does not fit the right OS ('dir' => 'ls' on unix)
		 * @return  right complete command line, with parameters added (NEVER NULL)
		 */
		public abstract String createCommandLine();

		protected final String buildCommandLine(final String aCommand, final ArrayList<String> someParameters)
		{
			final StringBuilder aCommandLine = new StringBuilder();
			aCommandLine.append(aCommand);
			for (String aParameter : someParameters) {
				aCommandLine.append(" ");
				aCommandLine.append(aParameter);
			}
			return aCommandLine.toString();
		}
	}

	/**
	 * Builds a Execution Environment for Windows. <br />
	 * Cmd with windows commands
	 * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
	 */
	public static final class WindowsExecEnvFactory extends ExecEnvironmentFactory
	{

		/**
		 * Builds an execution environment for a Windows system command to be played. <br />
		 * Any command not from windows will be translated in its windows equivalent if possible.
		 * @param aCommand system command to be executed (must not be null or empty)
		 * @param someParameters parameters of the command (must not be null or empty)
		 */
		public WindowsExecEnvFactory(final String aCommand, final String... someParameters)
		{
			super(aCommand, someParameters);
		}
		/**
		 * @see test.JavaSystemCaller.ExecEnvironmentFactory#createShell()
		 */
		@Override
		public IShell createShell() {
			return new WindowsShell();
		}

		/**
		 * @see test.JavaSystemCaller.ExecEnvironmentFactory#createCommandLine()
		 */
		@Override
		public String createCommandLine() {
			String aCommand = getCommand();
			if(aCommand.toLowerCase().trim().equals("ls")) { aCommand = "dir"; }
			// TODO translates other Unix commands
			return buildCommandLine(aCommand, getParameters());
		}
	}

	/**
	 * Builds a Execution Environment for Unix. <br />
	 * Sh with Unix commands
	 * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
	 */
	public static final class UnixExecEnvFactory extends ExecEnvironmentFactory
	{

		/**
		 * Builds an execution environment for a Unix system command to be played. <br />
		 * Any command not from Unix will be translated in its Unix equivalent if possible.
		 * @param aCommand system command to be executed (must not be null or empty)
		 * @param someParameters parameters of the command (must not be null or empty)
		 */
		public UnixExecEnvFactory(final String aCommand, final String... someParameters)
		{
			super(aCommand, someParameters);
		}
		/**
		 * @see test.JavaSystemCaller.ExecEnvironmentFactory#createShell()
		 */
		@Override
		public IShell createShell() {
			return new UnixShell();
		}

		/**
		 * @see test.JavaSystemCaller.ExecEnvironmentFactory#createCommandLine()
		 */
		@Override
		public String createCommandLine() {
			String aCommand = getCommand();
			if(aCommand.toLowerCase().trim().equals("dir")) { aCommand = "ls"; }
			// TODO translates other Windows commands
			return buildCommandLine(aCommand, getParameters());
		}
	}

	/**
	 * System Shell with its right OS command. <br />
	 * 'cmd' for Windows or 'sh' for Unix, ...
	 * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
	 */
	public interface IShell
	{
		/**
		 * Get the right shell command. <br />
		 * Used to launch a new shell
		 * @return command used to launch a Shell (NEVEL NULL)
		 */
		String getShellCommand();
	}
	/**
	 * Windows shell (cmd). <br />
	 * More accurately 'cmd /C'
	 * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
	 */
	public static class WindowsShell implements IShell
	{
		/**
		 * @see test.JavaSystemCaller.IShell#getShellCommand()
		 */
		@Override
		public final String getShellCommand() {
			final String osName = System.getProperty("os.name" );
			if( osName.equals( "Windows 95" ) ) { return "command.com /C"; }
			return "cmd.exe /C";
		}
	}
	/**
	 * Unix shell (sh). <br />
	 * More accurately 'sh -C'
	 * @author <a href="http://stackoverflow.com/users/6309/vonc">VonC</a>
	 */
	public static class UnixShell implements IShell
	{
		/**
		 * @see test.JavaSystemCaller.IShell#getShellCommand()
		 */
		@Override
		public final String getShellCommand() {
			return "/bin/sh -c";
		}
	}
}
