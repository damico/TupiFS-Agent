package org.jdamico.tupifs.dproxy.runtime;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jdamico.tupifs.dproxy.web.Connector;
import org.jdamico.tupifs.utils.ManageProperties;

public class StartDProxy {


	public static String proxyHost = null;
	public static int srvPort = 80;
	public static int cliPort = 8080;
	
	public static void main(String[] args) throws Exception {
		
		String propPath = args[0];
		proxyHost = ManageProperties.getInstance().read(propPath, "host");
		String srvProxyPort = ManageProperties.getInstance().read(propPath, "srv.port");
		String cliProxyPort = ManageProperties.getInstance().read(propPath, "cli.port");
		srvPort = Integer.parseInt(srvProxyPort);
		cliPort = Integer.parseInt(cliProxyPort);

		System.out.println("TupiFs.dProxy started as "+proxyHost+":"+cliPort);
		
		Server server = new Server(cliPort);
		 
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);
 
        context.addServlet(new ServletHolder(new Connector()),"/*");
 
        server.start();
        server.join();
        
        
	    
	}

}