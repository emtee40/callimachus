package org.callimachusproject.api;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.ProtocolException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import junit.framework.TestCase;
import junit.framework.TestSuite;

public class RDFDeleteTest extends TestCase {

	private static Map<String, String[]> parameters = new LinkedHashMap<String, String[]>() {
        private static final long serialVersionUID = -4308917786147773821L;

        {
        	put("SKOSConcept", new String[] { "application/sparql-update",
        			"prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		    " prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n" +
        		    " prefix calli: <http://callimachusproject.org/rdf/2009/framework#> \n" +
        		    " prefix skos: <http://www.w3.org/2004/02/skos/core#> \n " + 
        			" INSERT DATA {  \n <concept> a </callimachus/Concept> ;  \n" +
        			" skos:prefLabel \"concept\" . }"
        	});
        	
        	put("Folder", new String[] { "application/sparql-update",
        			"prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		    " prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n" +
        		    " prefix calli: <http://callimachusproject.org/rdf/2009/framework#> \n" +
        			" INSERT DATA {  \n <test/> a </callimachus/Folder> ;  \n" +
        			" rdfs:label \"test\" . }"
        	});
        	
        	put("Group", new String[] { "application/sparql-update",
        			"prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		    " prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n" +
        		    " prefix calli: <http://callimachusproject.org/rdf/2009/framework#> \n" +
        			" INSERT DATA {  \n <testGroup/> a </callimachus/Group> ;  \n" +
        			" rdfs:label \"testGroup\" . }"
        	});
        	
        	put("Menu", new String[] { "application/sparql-update",
        			"prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		    " prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n" +
        		    " prefix calli: <http://callimachusproject.org/rdf/2009/framework#> \n" +
        			" INSERT DATA {  \n <menu/> a </callimachus/Menu> ;  \n" +
        			" rdfs:label \"menu\" . }"
        	});
        	
        	put("Theme", new String[] { "application/sparql-update",
        			"prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		    " prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n" +
        		    " prefix calli: <http://callimachusproject.org/rdf/2009/framework#> \n" +
        			" INSERT DATA {  \n <theme/> a </callimachus/Theme> ;  \n" +
        			" rdfs:label \"theme\" . }"
        	});
        	
        	put("User", new String[] { "application/sparql-update",
        			"prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
        		    " prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n" +
        		    " prefix calli: <http://callimachusproject.org/rdf/2009/framework#> \n" +
        			" INSERT DATA {  \n <user> a </callimachus/User> ;  \n" +
        			" rdfs:label \"user\" . }"
        	});
        }
    };
    
	public static TestSuite suite() throws Exception{
        TestSuite suite = new TestSuite(RDFDeleteTest.class.getName());
        for (String name : parameters.keySet()) {
            suite.addTest(new RDFDeleteTest(name));
        }
        return suite;
    }
	
	private static TemporaryServer temporaryServer = TemporaryServer.newInstance();
	private String connectionContentType;
	private String query;
	
	public RDFDeleteTest(String name) throws Exception {
		super(name);
		String [] args = parameters.get(name);
		connectionContentType = args[0];
		query = args[1];
	}

	public void setUp() throws Exception {
		super.setUp();
		temporaryServer.resume();
		Authenticator.setDefault(new Authenticator() {
		     protected PasswordAuthentication getPasswordAuthentication() {
		       return new PasswordAuthentication(temporaryServer.getUsername(), temporaryServer.getPassword()); 
		     }
		 });
	}

	public void tearDown() throws Exception {
		super.tearDown();
		temporaryServer.pause();
	}
	
	private String getRDFContents() throws MalformedURLException, IOException,
			ProtocolException {
		URL url = new java.net.URL(temporaryServer.getOrigin() + "/");
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("OPTIONS");
		assertEquals(connection.getResponseMessage(), 204, connection.getResponseCode());
		String header = connection.getHeaderField("LINK");
		int rel = header.indexOf("rel=\"describedby\"");
		int start = header.lastIndexOf("<", rel);
		int end = header.lastIndexOf(">", rel);
		String contents = header.substring(start + 1, end);
		return contents;
	}
	
	private String getLocation() throws Exception {
		URL url = new java.net.URL(getRDFContents());
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Content-Type", connectionContentType);
		connection.setDoOutput(true);
		OutputStream output = connection.getOutputStream();
		output.write(query.getBytes());
		output.close();
		assertEquals(connection.getResponseMessage(), 201, connection.getResponseCode());
		String header = connection.getHeaderField("Location");
		return header;
	}
	
	private String getDescribedBy() throws Exception {
		URL url = new java.net.URL(getLocation());
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("OPTIONS");
		assertEquals(connection.getResponseMessage(), 204, connection.getResponseCode());
		String header = connection.getHeaderField("LINK");
		int rel = header.indexOf("rel=\"describedby\"");
		int end = header.lastIndexOf(">", rel);
		int start = header.lastIndexOf("<", rel);
		String contents = header.substring(start + 1, end);
		return contents;
	}
	
	public void runTest() throws Exception {
		URL url = new java.net.URL(getDescribedBy());
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("DELETE");
		assertEquals(connection.getResponseMessage(), 204, connection.getResponseCode());
	}
}