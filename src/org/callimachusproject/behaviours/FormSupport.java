/*
 * Portions Copyright (c) 2009-10 Zepheira LLC and James Leigh, Some
  Rights Reserved
 * Portions Copyright (c) 2010-11 Talis Inc, Some Rights Reserved 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.callimachusproject.behaviours;

import static org.callimachusproject.engine.helpers.SPARQLWriter.toSPARQL;
import static org.openrdf.query.QueryLanguage.SPARQL;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;

import org.callimachusproject.annotations.header;
import org.callimachusproject.annotations.method;
import org.callimachusproject.annotations.query;
import org.callimachusproject.annotations.requires;
import org.callimachusproject.annotations.type;
import org.callimachusproject.client.HTTPObjectClient;
import org.callimachusproject.concepts.Page;
import org.callimachusproject.engine.RDFEventReader;
import org.callimachusproject.engine.RDFParseException;
import org.callimachusproject.engine.RDFaReader;
import org.callimachusproject.engine.Template;
import org.callimachusproject.engine.TemplateEngine;
import org.callimachusproject.engine.events.TriplePattern;
import org.callimachusproject.engine.helpers.ClusterCounter;
import org.callimachusproject.engine.helpers.DeDupedResultSet;
import org.callimachusproject.engine.helpers.OrderedSparqlReader;
import org.callimachusproject.engine.helpers.RDFaProducer;
import org.callimachusproject.engine.helpers.SPARQLPosteditor;
import org.callimachusproject.engine.helpers.SPARQLProducer;
import org.callimachusproject.engine.helpers.XMLEventList;
import org.callimachusproject.engine.model.VarOrTerm;
import org.callimachusproject.server.exceptions.BadRequest;
import org.callimachusproject.server.exceptions.InternalServerError;
import org.callimachusproject.xml.XMLEventReaderFactory;
import org.callimachusproject.xslt.XSLTransformer;
import org.callimachusproject.xslt.XSLTransformerFactory;
import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.impl.MapBindingSet;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.object.RDFObject;

/**
 * Implements the construct search method to lookup resources by label prefix
 * and options method to list all possible values.
 * 
 * @author James Leigh 
 * @author Steve Battle
 * 
 */
public abstract class FormSupport implements Page, RDFObject {
	private static final TemplateEngine ENGINE = TemplateEngine.newInstance();
	
	
	static final XSLTransformer HTML_XSLT;


	static {
		String path = "org/callimachusproject/xsl/page-to-html.xsl";
		ClassLoader cl = ViewSupport.class.getClassLoader();
		String url = cl.getResource(path).toExternalForm();
		InputStream input = cl.getResourceAsStream(path);
		InputStreamReader reader = new InputStreamReader(input);
		HTML_XSLT = XSLTransformerFactory.getInstance().createTransformer(reader, url);
	}

	@Override
	public String calliConstructHTML(Object target) throws Exception {
		return asHtmlString(calliConstruct(target));
	}

	/**
	 * Extracts an element from the template (without variables) and populates
	 * the element with the properties of the about resource.
	 */
	@method("GET")
	@query("construct")
	@requires("http://callimachusproject.org/rdf/2009/framework#reader")
	@type("text/html")
	@header("cache-control:no-store")
	public InputStream construct(
			@query("resource") @type("text/uri-list") URI about,
			@query("element") String element) throws Exception {
		if (about != null && (element == null || element.equals("/1")))
			throw new BadRequest("Missing element parameter");
		if (about != null && element != null)
			return dataConstruct(about, element);
		if (about == null && element == null) {
			ValueFactory vf = getObjectConnection().getValueFactory();
			about = vf.createURI(this.toString());
		}
		XMLEventReader xhtml = calliConstructXhtml(about, element);
		return HTML_XSLT.transform(xhtml, this.toString()).asInputStream();
	}

	/**
	 * See data-options, defining an HTML select/option fragment
	 */
	@method("GET")
	@query("options")
	@requires("http://callimachusproject.org/rdf/2009/framework#reader")
	@type("text/html")
	@header("cache-control:no-store")
	public InputStream options(@query("element") String element)
			throws Exception {
		String base = getResource().stringValue();
		XMLEventList template = new XMLEventList(xslt(element));
		SPARQLProducer rq = new SPARQLProducer(new RDFaReader(base, template.iterator(), toString()));
		SPARQLPosteditor ed = new SPARQLPosteditor(rq);
		
		// only pass object vars (excluding prop-exps and content) beyond a certain depth: 
		// ^(/\d+){3,}$|^(/\d+)*\s.*$
		ed.addEditor(ed.new TriplePatternCutter());
		
		RepositoryConnection con = getObjectConnection();
		String sparql = toSafeOrderedSparql(ed) + "\nLIMIT 1000";
		TupleQuery qry = con.prepareTupleQuery(SPARQL, sparql, base);
		RDFaProducer xhtml = new RDFaProducer(template.iterator(), qry.evaluate(), rq.getOrigins());
		return HTML_XSLT.transform(xhtml, this.toString()).asInputStream();
	}

	/**
	 * Returns an HTML page listing suggested resources for the given element.
	 * See data-search
	 */
	@method("GET")
	@query("search")
	@requires("http://callimachusproject.org/rdf/2009/framework#reader")
	@type("text/html")
	@header("cache-control:no-validate,max-age=60")
	public InputStream constructSearch(@query("element") String element,
			@query("q") String q) throws Exception {
		String base = getResource().stringValue();
		XMLEventList template = new XMLEventList(xslt(element));
		SPARQLProducer rq = new SPARQLProducer(new RDFaReader(base, template.iterator(), toString()));
		SPARQLPosteditor ed = new SPARQLPosteditor(rq);
		
		// filter out the outer rel (the button may add additional bnodes that need to be cut out)
		ed.addEditor(ed.new TriplePatternCutter());
		
		// add soundex conditions to @about siblings on the next level only
		// The regex should not match properties and property-expressions with info following the xptr
		ed.addEditor(ed.new PhoneMatchInsert(q));
		
		// add filters to soundex labels at the next level (only direct properties of the top-level subject)
		//ed.addEditor(ed.new FilterInsert("^(/\\d+){2}$",LABELS,regexStartsWith(q)));
		ed.addEditor(ed.new FilterKeywordExists(q));
	
		RepositoryConnection con = getObjectConnection();
		String sparql = toSafeOrderedSparql(ed);
		TupleQuery qry = con.prepareTupleQuery(SPARQL, sparql, base);
		// The edited query may return multiple and/or empty solutions
		TupleQueryResult results = new DeDupedResultSet(qry.evaluate(),true);
		RDFaProducer xhtml = new RDFaProducer(template.iterator(), results, rq.getOrigins());
		return HTML_XSLT.transform(xhtml, this.toString()).asInputStream();
	}

	private String toSafeOrderedSparql(RDFEventReader ed) throws RDFParseException,
			IOException {
		ClusterCounter counter = new ClusterCounter(new OrderedSparqlReader(ed));
		String sparql = toSPARQL(counter);
		if (counter.getNumberOfVariableClusters() > 1)
			throw new InternalServerError("Variables not connected: " + counter.getSmallestCluster());
		return sparql;
	}

	private String asHtmlString(XMLEventReader xhtml) throws Exception {
		return HTML_XSLT.transform(xhtml, this.toString()).asString();
	}
	
	private XMLEventReader calliConstructXhtml(URI about, String element) 
	throws Exception {
		String url = url("xslt", element);
		InputStream in = openRequest(url);
		try {
			Template temp = ENGINE.getTemplate(in, url);
			MapBindingSet bindings = new MapBindingSet();
			bindings.addBinding("this", about);
			return temp.openResult(bindings, getObjectConnection());
		} finally {
			in.close();
		}
	}
	
	private InputStream dataConstruct(URI about, String element) throws Exception {
		String base = getResource().stringValue();
		XMLEventList template = new XMLEventList(xslt(element));
		SPARQLProducer rq = new SPARQLProducer(new RDFaReader(base, template.iterator(), toString()));
		SPARQLPosteditor ed = new SPARQLPosteditor(rq);
		
		// only pass object vars (excluding prop-exps and content) beyond a certain depth: 
		// ^(/\d+){3,}$|^(/\d+)*\s.*$
		ed.addEditor(ed.new TriplePatternCutter());
		
		// find top-level new subjects to bind
		SPARQLPosteditor.TriplePatternRecorder rec;
		ed.addEditor(rec = ed.new TriplePatternRecorder());
		
		RepositoryConnection con = getObjectConnection();
		TupleQuery qry = con.prepareTupleQuery(SPARQL, toSafeOrderedSparql(ed), base);
		for (TriplePattern t: rec.getTriplePatterns()) {
			VarOrTerm vt = t.getSubject();
			if (vt.isVar())
				qry.setBinding(vt.asVar().stringValue(), about);
		}
		RDFaProducer xhtml = new RDFaProducer(template.iterator(), qry.evaluate(), rq.getOrigins());
		return HTML_XSLT.transform(xhtml, this.toString()).asInputStream();		
	}

	protected XMLEventReader xslt(String element)
			throws IOException, XMLStreamException {
		XMLEventReaderFactory factory = XMLEventReaderFactory.newInstance();
		String url = url("xslt", element);
		InputStream in = openRequest(url);
		return factory.createXMLEventReader(url, in);
	}

	private String url(String operation, String element)
			throws UnsupportedEncodingException {
		String uri = getResource().stringValue();
		StringBuilder sb = new StringBuilder();
		sb.append(uri);
		sb.append("?");
		sb.append(URLEncoder.encode(operation, "UTF-8"));
		if (element != null) {
			sb.append("&element=");
			sb.append(URLEncoder.encode(element, "UTF-8"));
		}
		return sb.toString();
	}

	private InputStream openRequest(String url) throws IOException {
		HTTPObjectClient client = HTTPObjectClient.getInstance();
		return client.get(url).getEntity().getContent();
	}

}
