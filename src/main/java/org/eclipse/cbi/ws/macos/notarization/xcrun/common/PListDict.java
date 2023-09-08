/*******************************************************************************
 * Copyright (c) 2019 Eclipse Foundation and others.
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License 2.0
 * which is available at http://www.eclipse.org/legal/epl-v20.html
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.ws.macos.notarization.xcrun.common;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class PListDict extends ForwardingMap<String, Object> {

	private static final Logger LOGGER = LoggerFactory.getLogger(PListDict.class);

	private static final class PListDictHandler extends DefaultHandler {
		private static final ImmutableSet<String> VALID_CF_SIMPLE_TYPE_XML = ImmutableSet.of("string", "real",
				"integer", "date", "data");

		private final Deque<String> simpleElementStack = new ArrayDeque<>();
		private final Deque<Object> compositeStack = new ArrayDeque<>();
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

	public static PListDict fromXML(InputStream xml) throws SAXException, IOException {
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

	Optional<Map<?,?>> firstProductErrors() {
		Object rawProductErrors = get("product-errors");
		if (rawProductErrors instanceof List<?>) {
			List<?> productErrors = (List<?>) rawProductErrors;
			if (!productErrors.isEmpty()) {
				Object rawFirstError = productErrors.get(0);
				if (rawFirstError instanceof Map<?, ?>) {
					return Optional.of((Map<?,?>)rawFirstError);
				}
			}
		}
		return Optional.empty();
	}
	
	public OptionalInt firstProductErrorCode() {
		Optional<Map<?, ?>> firstProductError = firstProductErrors();
		if (firstProductError.isPresent()) {
			Object rawCode = firstProductError.get().get("code");
			if (rawCode instanceof String) {
				try {
					return OptionalInt.of(Integer.parseInt((String) rawCode));
				} catch (NumberFormatException e) {
					LOGGER.debug("Error code from plist is not a parseable integer: " + rawCode);
				}
			}
		}
		return OptionalInt.empty();
	}
	
	public Optional<String> messageFromFirstProductError() {
		Optional<Map<?, ?>> firstError = firstProductErrors();
		if (firstError.isPresent()) {
			Object message = firstError.get().get("message");
			LOGGER.trace("firstMessageFromProductErrors.message=" + message);
			if (message instanceof String) {
				return Optional.of((String) message);
			}
		}
		LOGGER.debug("Unable to retrieve first 'message' from product-errors in " + toString());
		return Optional.empty();
	}

	public Optional<String> requestUUIDFromNotarizationUpload() {
		Object rawNotarizationUpload = get("notarization-upload");
		if (rawNotarizationUpload instanceof Map<?, ?> notarizationUpload) {
			Object requestUUID = notarizationUpload.get("RequestUUID");
			if (requestUUID instanceof String) {
				return Optional.of((String)requestUUID);
			}
		}
		LOGGER.debug("Unable to retrieve 'RequestUUID' from notarization-upload in " + toString());
		return Optional.empty();
	}

	@Override
	protected Map<String, Object> delegate() {
		return delegate;
	}
}
