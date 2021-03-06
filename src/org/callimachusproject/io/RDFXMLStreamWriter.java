/*
   Copyright (c) 2011 3 Round Stones Inc, Some Rights Reserved

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */
package org.callimachusproject.io;


import info.aduna.xml.XMLUtil;

import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.openrdf.http.object.io.Relativizer;
import org.openrdf.model.Literal;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.XMLSchema;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFWriter;
import org.openrdf.rio.RioSetting;
import org.openrdf.rio.WriterConfig;

public class RDFXMLStreamWriter implements RDFWriter {
	private static final Pattern ESCAPED = Pattern.compile("^\\s|<|&|>|\\s$");
	private final XMLStreamWriter writer;
	private final Relativizer base;
	private WriterConfig config = new WriterConfig();
	private Resource open;

	public RDFXMLStreamWriter(XMLStreamWriter writer, String systemId) throws URISyntaxException {
		this.writer = writer;
		this.base = new Relativizer(systemId);
	}

	public Collection<RioSetting<?>> getSupportedSettings() {
		return Collections.emptyList();
	}

	public WriterConfig getWriterConfig() {
		return config;
	}

	public void setWriterConfig(WriterConfig config) {
		this.config = config;
	}

	@Override
	public void startRDF() throws RDFHandlerException {
		try {
			startDocument();
		} catch (XMLStreamException e) {
			throw new RDFHandlerException(e);
		}
	}

	@Override
	public void endRDF() throws RDFHandlerException {
		try {
			endDocument();
		} catch (XMLStreamException e) {
			throw new RDFHandlerException(e);
		}
	}

	@Override
	public void handleNamespace(String prefix, String uri)
			throws RDFHandlerException {
		try {
			namespace(prefix, uri);
		} catch (XMLStreamException e) {
			throw new RDFHandlerException(e);
		}
	}

	@Override
	public void handleStatement(Statement st) throws RDFHandlerException {
		try {
			statement(st);
		} catch (XMLStreamException e) {
			throw new RDFHandlerException(e);
		}
	}

	@Override
	public void handleComment(String data) throws RDFHandlerException {
		try {
			comment(data);
		} catch (XMLStreamException e) {
			throw new RDFHandlerException(e);
		}
	}

	@Override
	public RDFFormat getRDFFormat() {
		return RDFFormat.RDFXML;
	}

	private void startDocument() throws XMLStreamException {
		writer.writeStartDocument();
		writer.writeStartElement("rdf", "RDF", RDF.NAMESPACE);
	}

	private synchronized void endDocument() throws XMLStreamException {
		if (open != null) {
			writer.writeEndElement();
			open = null;
		}
		writer.writeEndElement();
		writer.writeEndDocument();
		writer.flush();
	}

	private void namespace(String prefix, String uri)
			throws XMLStreamException {
		if (open == null && XMLUtil.isNCName(prefix)) {
			writer.writeNamespace(prefix, uri);
		}
	}

	private synchronized void subject(Resource subject) throws XMLStreamException {
		if (!subject.equals(open)) {
			if (open != null) {
				writer.writeEndElement();
				open = null;
			}
			writer.writeStartElement("rdf", "Description", RDF.NAMESPACE);
			if (subject instanceof URI) {
				writer.writeAttribute("rdf", RDF.NAMESPACE, "about", base.relativize(subject.stringValue()));
			} else {
				writer.writeAttribute("rdf", RDF.NAMESPACE, "nodeID", subject.stringValue());
			}
			open = subject;
		}
	}

	private synchronized void statement(Statement st) throws XMLStreamException {
		subject(st.getSubject());
		writer.writeStartElement(st.getPredicate().getNamespace(), st.getPredicate().getLocalName());
		if (st.getObject() instanceof Literal) {
			Literal lit = (Literal) st.getObject();
			if (lit.getLanguage() != null) {
				writer.writeAttribute("xml:lang", lit.getLanguage());
			} else if (lit.getDatatype() != null && !XMLSchema.STRING.equals(lit.getDatatype())) {
				writer.writeAttribute("rdf", RDF.NAMESPACE, "datatype", lit.getDatatype().stringValue());
			}
			if (ESCAPED.matcher(lit.stringValue()).find()) {
				writer.writeCData(lit.stringValue());
			} else {
				writer.writeCharacters(lit.stringValue());
			}
		} else if (st.getObject() instanceof URI) {
			writer.writeAttribute("rdf", RDF.NAMESPACE, "resource", base.relativize(st.getObject().stringValue()));
		} else {
			writer.writeAttribute("rdf", RDF.NAMESPACE, "nodeID", st.getObject().stringValue());
		}
		writer.writeEndElement();
	}

	private void comment(String data) throws XMLStreamException {
		writer.writeComment(data);
	}
	
}