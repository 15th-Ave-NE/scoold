/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.scoold.util;

import com.scoold.core.Search;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import name.aikesommer.authenticator.Registry;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.node.NodeBuilder;


/**
 * Web application lifecycle listener.
 * @author alexb
 */

public class ScooldAppListener implements ServletContextListener, HttpSessionListener {

	private static final Logger logger = Logger.getLogger(ScooldAppListener.class.getName());
	private static boolean ELASTICSEARCH_ON = true;
	public static String SEARCH_CLIENT = "search-client";
	public static String SHOWDOWN_CONV = "showdown-converter";
	
	public void contextInitialized(ServletContextEvent sce) {
		//logger.info("initializing context.");
		ServletContext sc = sce.getServletContext();
		
		// authentic roast
		Registry.forContext(sc).register(new com.scoold.util.ScooldAuthModule());
		
		// showdown.js - markdown 
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine jsEngine = manager.getEngineByName("js");
		Object showdownConverter = null;
		try{
			jsEngine.eval(new InputStreamReader(sc.getResourceAsStream("/scripts/showdown.js")));
			showdownConverter = jsEngine.eval("new Showdown.converter()");
		}catch (Exception e){
			logger.log(Level.SEVERE, "could not create showdown converter", e);
		}
		sc.setAttribute("showdownConverter", showdownConverter);
		 
		// elasticsearch init   
		if(ELASTICSEARCH_ON){
			NodeBuilder nb = NodeBuilder.nodeBuilder();
			nb.clusterName(Search.INDEX_NAME);
			
			nb.settings().put("node.river", "_none_"); 
			nb.settings().put("client.transport.sniff", true);
			
			nb.settings().put("network.host", "_vmnet8:ipv4_"); 
			nb.settings().put("network.tcp.keep_alive", true);
//			
//			nb.settings().put("gateway.type", "s3"); 
//			nb.settings().put("gateway.s3.bucket", "com.scoold.elasticsearch");
//			
//			nb.settings().put("discovery.type", "ec2");
//			nb.settings().put("discovery.ec2.groups", "elasticsearch");
			  
//			nb.settings().put("discovery.zen.ping.multicast.enabled", false);
//			nb.settings().put("discovery.zen.ping.unicast.hosts", "172.16.151.129:9300");

			nb.settings().put("cloud.aws.region", "eu-west-1");
			nb.settings().put("cloud.aws.access_key", AmazonQueue.ACCESSKEY);
			nb.settings().put("cloud.aws.secret_key", AmazonQueue.SECRETKEY);
			nb.settings().put("cloud.aws.sqs.queue_url", AmazonQueue.SQS_URL.
					concat(AmazonQueue.SQS_ACCOUNT_ID).concat("/").
					concat(QueueFactory.SCOOLD_INDEX));
			
			
			Client client = new TransportClient(nb.settings()).addTransportAddress(
					new InetSocketTransportAddress("localhost", 9300));
			sc.setAttribute(SEARCH_CLIENT, client);
			
//			nb.client(true);
//			nb.data(false);
//			sc.setAttribute(SEARCH_CLIENT, nb.node().client());
		}
	}

	public void contextDestroyed(ServletContextEvent sce) {
		//logger.info("context is destroyed."); 
		Client searchClient = (Client) sce.getServletContext().getAttribute(SEARCH_CLIENT);
		if(ELASTICSEARCH_ON && searchClient != null){
			searchClient.close();
		}
	}

	public void sessionCreated(HttpSessionEvent se) {

//		File file = new File("/Users/alexb/Desktop/schools.txt");
//		int i = 1;
//		try {
//			List<String> lines = FileUtils.readLines(file, "UTF-8");
//			List<String> test = new ArrayList();
//			HashMap<String, String> cities = new HashMap();
//
//			School s = new School();
//			for (String line : lines) {
//				line = line.trim();
//				switch (i){
//					case 1:	s.setName(line); break;
//					case 2: s.setTypeString(line); break;
//					case 3: s.setLocation(line);
//
//					if(cities.containsKey(line)){
//						s.setLocation(cities.get(line));
//					}else{
//						List t = DAOUtils.readLocationForKeyword(line, Style.SHORT);
//						if(t.isEmpty()){
//							s.setLocation(line);
//							cities.put(line, line);
//						}else{
//							Toponym top = (Toponym) t.get(0);
//							String newloc = top.getName()+", Bulgaria";
//							cities.put(line, newloc);
//							test.add(s.getName()+" "+line+"="+newloc);
//							Thread.sleep(1000);
//							s.setLocation(newloc);
//						}
//
//					}
//
//					break;
//					case 4: s.setAddress(line); break;
//				}
//				i++;
//				if(i == 5){
//					i = 1;
//					logger.info("adding "+s.getLocation());
//					//schools.add(s);
//					s.create();
//					s = new School();
//				}
//			}
//			se.getSession().setAttribute("testis", test);
//		} catch (IOException ex) {
//			logger.log(Level.SEVERE, null, ex);
//		}
//		catch (InterruptedException e) {
//			logger.log(Level.SEVERE, null, e);
//		}

		// Set the time the user was last seen
//		HttpSession seshun = se.getSession();
//
//		SimplePrincipal userPricipal = SimplePrincipal.getPrincipal(seshun);
//		User authUser = null;
//		if (userPricipal != null) {
//			authUser = userPricipal.getUser();
//			if(authUser != null){
//				//update lastseen
//				authUser.setLastSeen(new Timestamp(System.currentTimeMillis()));
//				authUser.update();
//			}
//		}
		
	}

	public void sessionDestroyed(HttpSessionEvent se) {
	}
}