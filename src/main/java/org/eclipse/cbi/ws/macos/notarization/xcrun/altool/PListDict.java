package org.eclipse.cbi.ws.macos.notarization.xcrun.altool;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ImmutableSet;

class PListDict extends ForwardingMap<String, Object> {

	private static final Logger LOGGER = LoggerFactory.getLogger(PListDict.class);

	private static final class PListDictHandler extends DefaultHandler {
		private static final ImmutableSet<String> VALID_CF_SIMPLE_TYPE_XML = ImmutableSet.of("string", "real",
				"integer", "date", "data");

		private Deque<String> simpleElementStack = new ArrayDeque<>();
		private Deque<Object> compositeStack = new ArrayDeque<>();
		private String lastSeenKeyName;
		private PListDict ret;

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes)
				throws SAXException {
			if ("dict".equals(qName)) {
				compositeStack.push(addValueToParentComposite(new HashMap<String, Object>()));
			} else if ("array".equals(qName)) {
				compositeStack.push(addValueToParentComposite(new ArrayList<String>()));
			} else if ("true".equals(qName) || "false".equals(qName)) {
				addValueToParentComposite(qName);
			} else {
				simpleElementStack.push(qName);
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			String str = new String(ch, start, length);
			String simpleElementPeek = simpleElementStack.peek();
			if ("key".equals(simpleElementPeek)) {
				lastSeenKeyName = str;
			} else if (VALID_CF_SIMPLE_TYPE_XML.contains(simpleElementPeek)) {
				addValueToParentComposite(str);
			}
		}

		@SuppressWarnings("unchecked")
		private Object addValueToParentComposite(Object obj) {
			Object compositePeek = compositeStack.peek();
			if (compositePeek instanceof Map) {
				((Map<String, Object>) compositePeek).put(lastSeenKeyName, obj);
			} else if (compositePeek instanceof Collection) {
				((Collection<Object>) compositePeek).add(obj);
			} 
			return obj;
		}

		@SuppressWarnings("unchecked")
		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			if ("dict".equals(qName)) {
				ret = new PListDict((Map<String, Object>) compositeStack.pop());
			} else if ("array".equals(qName)) {
				compositeStack.pop();
			} else if ("true".equals(qName) || "false".equals(qName)) {
				// do nothing
			} else if ("plist".equals(qName)) {
				assert "plist".equals(simpleElementStack.pop());
				assert compositeStack.isEmpty() && simpleElementStack.isEmpty();
				assert ret != null;
			} else {
				simpleElementStack.pop();
			}

		}

		public PListDict getResult() {
			return ret;
		}
	}

	private final Map<String, Object> delegate;

	private PListDict(Map<String, Object> delegate) {
		this.delegate = delegate;
	}

	static PListDict fromXML(InputStream xml) throws SAXException, IOException {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		try {
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
	        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		} catch (ParserConfigurationException e) {
			LOGGER.warn("Error while setting SAX parser factory feature", e);
		}

		SAXParser saxParser;
		try {
			saxParser = factory.newSAXParser();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException("Cannot parse PList file", e);
		}

		PListDictHandler pListDictHandler = new PListDictHandler();
		try (InputStream is = new BufferedInputStream(xml)) {
			saxParser.parse(is, pListDictHandler);
		}
		
		PListDict result = pListDictHandler.getResult();
		LOGGER.trace("Parsed plist=" + result);
		return result;
	}

	@Override
	protected Map<String, Object> delegate() {
		return delegate;
	}
}
