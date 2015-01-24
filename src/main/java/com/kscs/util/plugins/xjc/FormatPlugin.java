/*
 * MIT License
 *
 * Copyright (c) 2014 Klemm Software Consulting, Mirko Klemm
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.kscs.util.plugins.xjc;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JConditional;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.model.CPluginCustomization;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * @author Mirko Klemm 2015-01-22
 */
public class FormatPlugin extends Plugin {

	public static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(FormatPlugin.class.getName());
	public static final String OPTION_NAME = "-Xformat";
	public static final String FORMATTER_OPTION_NAME = "-formatter";
	public static final String METHOD_OPTION_NAME = "-method";
	public static final String CUSTOMIZATION_NS = "http://www.kscs.com/util/jaxb/format";
	public static final String EXPRESSION_ELEMENT_NAME = "expression";
	public static final String FORMATTER_CLASS_ELEMENT_NAME = "formatter";
	public static final String FORMATTING_METHOD_ELEMENT_NAME = "method";
	public static final String FORMATTING_METHOD_NAME = "toString";
	public static final List<String> CUSTOM_ELEMENTS = Arrays.asList(FormatPlugin.EXPRESSION_ELEMENT_NAME, FormatPlugin.FORMATTER_CLASS_ELEMENT_NAME, FormatPlugin.FORMATTING_METHOD_ELEMENT_NAME);
	private String objectFormatterClassName = null;
	private String formattingMethodName = FormatPlugin.FORMATTING_METHOD_NAME;
	public final Map<String, Setter<String>> setters = new HashMap<String, Setter<String>>() {{
		put(FormatPlugin.FORMATTER_OPTION_NAME, new Setter<String>() {
			public void set(final String val) {
				FormatPlugin.this.objectFormatterClassName = val;
			}
		});
		put(FormatPlugin.METHOD_OPTION_NAME, new Setter<String>() {
			public void set(final String val) {
				FormatPlugin.this.formattingMethodName = val;
			}
		});
	}};

	@Override
	public String getOptionName() {
		return FormatPlugin.OPTION_NAME.substring(1);
	}

	@Override
	public int parseArgument(final Options opt, final String[] args, final int i) throws BadCommandLineException, IOException {
		int currentIndex = i;
		if ((FormatPlugin.OPTION_NAME).equals(args[i])) {
			currentIndex = parseOptions(args, i, this.setters);
		}
		if (this.objectFormatterClassName == null) {
			throw new BadCommandLineException(FormatPlugin.RESOURCE_BUNDLE.getString("exception.missingFormatter"));
		}
		return currentIndex - i + 1;
	}

	@Override
	public List<String> getCustomizationURIs() {
		return Collections.singletonList(FormatPlugin.CUSTOMIZATION_NS);
	}

	@Override
	public boolean isCustomizationTagName(final String nsUri, final String localName) {
		return FormatPlugin.CUSTOMIZATION_NS.equals(nsUri) && FormatPlugin.CUSTOM_ELEMENTS.contains(localName);
	}

	@Override
	public String getUsage() {
		return FormatPlugin.RESOURCE_BUNDLE.getString("usageText");
	}

	@Override
	public boolean run(final Outline outline, final Options opt, final ErrorHandler errorHandler) throws SAXException {
		for (final ClassOutline classOutline : outline.getClasses()) {
			final String expression = getCustomizationValue(errorHandler, classOutline, FormatPlugin.EXPRESSION_ELEMENT_NAME, "select");
			if (expression != null && expression.length() > 0) {
				generateToStringMethod(errorHandler, outline.getCodeModel(), classOutline, expression);
			}
		}
		return false;
	}

	private void generateToStringMethod(final ErrorHandler errorHandler, final JCodeModel model, final ClassOutline classOutline, final String expression) throws SAXException {
		final String formatterClassName = getCustomizationValue(errorHandler, classOutline, FormatPlugin.FORMATTER_CLASS_ELEMENT_NAME, "class");
		final JClass objectFormatterClass = model.ref(formatterClassName == null ? this.objectFormatterClassName : formatterClassName);
		final String formattingMethodName = getCustomizationValue(errorHandler, classOutline, FormatPlugin.FORMATTING_METHOD_ELEMENT_NAME, "name");
		final JDefinedClass definedClass = classOutline.implClass;
		final JFieldVar objectFormatterField = definedClass.field(JMod.PRIVATE | JMod.TRANSIENT, objectFormatterClass, "__objectFormatter", JExpr._null());
		final JMethod toStringMethod = definedClass.method(JMod.PUBLIC, String.class, formattingMethodName == null ? this.formattingMethodName : formattingMethodName);
		//toStringMethod.annotate(Override.class);
		final JConditional ifStatement = toStringMethod.body()._if(objectFormatterField.eq(JExpr._null()));
		ifStatement._then().assign(objectFormatterField, JExpr._new(objectFormatterClass).arg(expression));
		toStringMethod.body()._return(objectFormatterField.invoke("format").arg(JExpr._this()));
	}

	private int parseOptions(final String[] args, int i, final Map<String, Setter<String>> setters) throws BadCommandLineException {
		for (final String name : setters.keySet()) {
			if (args.length > i + 1) {
				if (args[i + 1].equalsIgnoreCase(name)) {
					if (args.length > i + 2 && !args[i + 2].startsWith("-")) {
						setters.get(name).set(args[i + 2]);
						i += 2;
					} else {
						throw new BadCommandLineException(MessageFormat.format(FormatPlugin.RESOURCE_BUNDLE.getString("exception.missingArgument"), name));
					}
				} else if (args[i + 1].toLowerCase().startsWith(name + "=")) {
					setters.get(name).set(args[i + 1].substring(name.length() + 1));
					i++;
				}
			} else {
				return 0;
			}
		}
		return i;
	}

	private String getCustomizationValue(final ErrorHandler errorHandler, final ClassOutline classOutline, final String elementName, final String attributeName) throws SAXException {
		final CPluginCustomization annotation = classOutline.target.getCustomizations().find(FormatPlugin.CUSTOMIZATION_NS, elementName);
		if (annotation != null) {
			final String attributeValue = annotation.element.getAttribute(attributeName);
			if (attributeValue != null && attributeValue.length() > 0) {
				annotation.markAsAcknowledged();
				return attributeValue;
			} else {
				errorHandler.error(new SAXParseException(MessageFormat.format(FormatPlugin.RESOURCE_BUNDLE.getString("exception.missingCustomizationAttribute"), attributeName, elementName), annotation.locator));
				return null;
			}
		}
		return null;
	}

	private static interface Setter<T> {
		void set(final T val);
	}
}
