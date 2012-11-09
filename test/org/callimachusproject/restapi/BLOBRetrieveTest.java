package org.callimachusproject.restapi;

import java.io.IOException;
import java.io.InputStream;
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

public class BLOBRetrieveTest extends TestCase {

	private static Map<String, String[]> parameters = new LinkedHashMap<String, String[]>() {
        private static final long serialVersionUID = -4308917786147773821L;

        {
        	put("article", new String[] { "read-article.docbook", "application/docbook+xml",
					"<article version='5.0'  xmlns='http://docbook.org/ns/docbook' xmlns:xl='http://www.w3.org/1999/xlink'> \n" +
					"<title>LS command</title> \n " +
					"<para>This command is a synonym for command. \n" +
					"</para> \n </article>", //End original
					"<article version='5.0'  xmlns='http://docbook.org/ns/docbook' xmlns:xl='http://www.w3.org/1999/xlink'> \n" +
					"<title>UPDATED LS command</title> \n " + //Begin update
					"<para>This command (UPDATED) is a synonym for command. \n" +
					"</para> \n </article>"
        	});
        	
        	put("font", new String[] { "read-font.woff", "application/font-woff",
        					"@font-face { \n font-family: GentiumTest; \n" +
        					"src: url(fonts/GenR102.woff) format(\"woff\")," +
        					"url(fonts/GenR102.ttf) format(\"truetype\"); }"
        	});
        	
        	put("namedQuery", new String[] { "read-query.rq", "application/sparql-query",
							"SELECT ?title WHERE { \n" +
							"<http://example.org/book/book1> <http://purl.org/dc/elements/1.1/title> ?title . \n" +
        					"}" 
			});
        	
        	put("page", new String[] { "read-page.xhtml", "application/xhtml+xml",
							"<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\"> \n" +
							"<head> <title> Wikipedia </title> </head> \n" +
							"<body> <p> Wikipedia is a great website. </p> </body> </html>" 
			});
        	
        	put("animatedGraphic", new String[] { "read-graphic.gif", "image/gif",
							"binary" 
			});
        	
        	put("photo", new String[] { "read-photo.jpg", "image/jpeg",
							"binary" 
			});
        	
        	put("networkGraphic", new String[] { "read-network.png", "image/png",
							"binary" 
			});
        	
        	put("vectorGraphic", new String[] { "read-vector.svg", "image/svg+xml",
							"<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\">" +
							"<circle cx=\"100\" cy=\"50\" r=\"40\" stroke=\"black\"" +
							"stroke-width=\"2\" fill=\"red\" /> </svg> \n " 
			});
        	
        	put("iconGraphic", new String[] { "read-logo.ico", "image/vnd.microsoft.icon",
							"binary" 
			});
        	
        	put("style", new String[] { "read-style.css", "text/css",
							"hr {color:sienna;} \n" +
						    "p {margin-left:20px;} \n" +
						    "body {background-color:blue}" 
			});
        	
            put("hypertext", new String[] { "read-file.html", "text/html",
                            "<html><head><title>Output Page</title></head><body></body></html>",
            });
            
            put("script", new String[] { "read-script.js", "text/javascript",
							"if (response == true) { \n" +
							"return true; \n" +
							"}" 
			});
            
            put("text", new String[] { "read-file.txt", "text/plain",
            				"Body of a text document" 
            });
            
            put("graphDocument", new String[] { "read-file.ttl", "text/turtle",
							"@prefix foaf: <http://xmlns.com/foaf/0.1/> . \n" +
							"<http://example.org/joe#me> a foaf:Person . \n" +
							"<http://example.org/joe#me> foaf:homepage <http://example.org/joe/INDEX.html> . \n" +
							"<http://example.org/joe#me> foaf:mbox <mailto:joe@example.org> . \n" +
							"<http://example.org/joe#me> foaf:name \"Joe Lambda\" ." 
			});
            
            put("transform", new String[] { "read-transform.xsl", "text/xsl",
							"<?xml version=\"1.0\"?>" +
						    "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns=\"http://www.w3.org/1999/xhtml\" version=\"1.0\"> \n" +
						    "<xsl:template match=\"/\"> <html> <body> <h2>My CD Collection</h2> \n" +
						    "<table border=\"1\"> <tr bgcolor=\"#9acd32\"> <th>Title</th> \n" +
						    "<th>Artist</th>  </tr> </table> </body> </html> </xsl:template> \n" +
						    "</xsl:stylesheet>" 
			});
            
            put("pipeline", new String[] { "read-pipeline.xpl", "application/xproc+xml",
            		"<?xml version=\"1.0\"?> \n" +
            	    "<p:pipeline version=\"1.0\" \n" +
                    "xmlns:p=\"http://www.w3.org/ns/xproc\" \n " + 
                    "xmlns:c=\"http://www.w3.org/ns/xproc-step\" \n" +
                    "xmlns:l=\"http://xproc.org/library\"> \n " +
                    "<p:identity /> </p:pipeline> " 
            });
        }
    };

    public static TestSuite suite() throws Exception{
        TestSuite suite = new TestSuite(BLOBRetrieveTest.class.getName());
        for (String name : parameters.keySet()) {
            suite.addTest(new BLOBRetrieveTest(name));
        }
        return suite;
    }
	
	private static TemporaryServer temporaryServer = TemporaryServerFactory.getInstance().createServer();
	private String requestSlug;
	private String requestContentType;
	private String outputString;

	public BLOBRetrieveTest(String name) throws Exception {
		super(name);
		String [] args = parameters.get(name);
		requestSlug = args[0];
		requestContentType = args[1];
		outputString = args[2];
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
		Authenticator.setDefault(null);
	}
	
	private String getCollection() throws Exception {
		String contents = getRelContents();
		
		URL contentsURL = new java.net.URL(contents);
		HttpURLConnection contentsConnection = (HttpURLConnection) contentsURL.openConnection();
		contentsConnection.setRequestMethod("GET");
		contentsConnection.setRequestProperty("ACCEPT", "application/atom+xml");
		assertEquals(contentsConnection.getResponseMessage(), 203, contentsConnection.getResponseCode());
		InputStream stream = contentsConnection.getInputStream();
		String text = new java.util.Scanner(stream).useDelimiter("\\A").next();
		int app = text.indexOf("<app:collection");
		int start = text.indexOf("\"", app);
		int stop = text.indexOf("\"", start + 1);
		String result = text.substring(start + 1, stop);
		return result;
	}

	private String getRelContents() throws MalformedURLException, IOException,
			ProtocolException {
		URL url = new java.net.URL(temporaryServer.getOrigin() + "/");
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("OPTIONS");
		assertEquals(connection.getResponseMessage(), 204, connection.getResponseCode());
		String header = connection.getHeaderField("LINK");
		int rel = header.indexOf("rel=\"contents\"");
		int end = header.lastIndexOf(">", rel);
		int start = header.lastIndexOf("<", rel);
		String contents = header.substring(start + 1, end);
		return contents;
	}
	
	private String getLocation() throws Exception {
		URL url = new java.net.URL(getCollection());
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Slug", requestSlug);
		connection.setRequestProperty("Content-Type", requestContentType);
		connection.setDoOutput(true);
		OutputStream output = connection.getOutputStream();
		output.write(outputString.getBytes());
		output.close();
		assertEquals(connection.getResponseMessage(), 201, connection.getResponseCode());
		String header = connection.getHeaderField("Location");
		return header;
	}
	
	private String getEditMedia() throws Exception {
		URL url = new java.net.URL(getLocation());
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("OPTIONS");
		assertEquals(connection.getResponseMessage(), 204, connection.getResponseCode());
		String header = connection.getHeaderField("LINK");
		int rel = header.indexOf("rel=\"edit-media\"");
		int end = header.lastIndexOf(">", rel);
		int start = header.lastIndexOf("<", rel);
		String contents = header.substring(start + 1, end);
		return contents;
	}
	
	public void runTest() throws Exception {
		URL url = new java.net.URL(getEditMedia());
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");
		connection.setRequestProperty("ACCEPT", requestContentType);
		InputStream stream = connection.getInputStream();
		String text = new java.util.Scanner(stream).useDelimiter("\\A").next();
		assertEquals(connection.getResponseMessage(), outputString, text);
	}

}