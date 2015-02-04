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
import com.sun.codemodel.JType;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.model.CPluginCustomization;
import com.sun.tools.xjc.model.Model;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * XJC plugin to generate a "toString"-like method by generating an invocation
 * of a delegate object formatter class. Delegate class, method names, method return
 * types and modifiers can be customized on the XJC command line or as binding
 * customizations.
 * @author Mirko Klemm 2015-01-22
 */
public class FormatPlugin extends Plugin {

	public static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(FormatPlugin.class.getName());
	public static final String OPTION_NAME = "-Xformat";
	public static final String CUSTOMIZATION_NS = "http://www.kscs.com/util/jaxb/format";
	public static final String EXPRESSION_CUSTOMIZATION_NAME = "expression";
	public static final String OBJECT_FORMATTER_CUSTOMIZATION_NAME = "formatter";
	public static final String GENERATED_METHOD_CUSTOMIZATION_NAME = "method";
	public static final String DEFAULT_GENERATED_METHOD_NAME = "toString";
	public static final String DEFAULT_GENERATED_METHOD_MODIFIERS = "public";
	public static final List<String> CUSTOM_ELEMENTS = Arrays.asList(FormatPlugin.EXPRESSION_CUSTOMIZATION_NAME, FormatPlugin.OBJECT_FORMATTER_CUSTOMIZATION_NAME, FormatPlugin.GENERATED_METHOD_CUSTOMIZATION_NAME);
	public static final String DEFAULT_OBJECT_FORMATTER_METHOD_NAME = "format";
	public static final String DEFAULT_OBJECT_FORMATTER_FIELD_NAME = "__objectFormatter";
	public static final String DEFAULT_OBJECT_FORMATTER_FIELD_MODIFIERS = "private transient";
	public static final String DEFAULT_GENERATED_METHOD_RETURN_TYPE_NAME = "java.lang.String";

	private String objectFormatterClassName = null;
	private String objectFormatterMethodName = FormatPlugin.DEFAULT_OBJECT_FORMATTER_METHOD_NAME;
	private String objectFormatterFieldName = FormatPlugin.DEFAULT_OBJECT_FORMATTER_FIELD_NAME;
	private String objectFormatterFieldModifiers = FormatPlugin.DEFAULT_OBJECT_FORMATTER_FIELD_MODIFIERS;

	private String generatedMethodName = FormatPlugin.DEFAULT_GENERATED_METHOD_NAME;
	private String generatedMethodModifiers = FormatPlugin.DEFAULT_GENERATED_METHOD_MODIFIERS;
	private String generatedMethodReturnTypeName = FormatPlugin.DEFAULT_GENERATED_METHOD_RETURN_TYPE_NAME;

	public final Map<String, Setter<String>> setters = new HashMap<String, Setter<String>>() {{
		put("-formatter", new Setter<String>() {
			public void set(final String val) {
				FormatPlugin.this.objectFormatterClassName = val;
			}
		});
		put("-formatter-method", new Setter<String>() {
			public void set(final String val) {
				FormatPlugin.this.objectFormatterMethodName = val;
			}
		});
		put("-formatter-field", new Setter<String>() {
			public void set(final String val) {
				FormatPlugin.this.objectFormatterFieldName = val;
			}
		});
		put("-formatter-field-modifiers", new Setter<String>() {
			public void set(final String val) {
				FormatPlugin.this.objectFormatterFieldModifiers = val;
			}
		});
		put("-generated-method", new Setter<String>() {
			public void set(final String val) {
				FormatPlugin.this.generatedMethodName = val;
			}
		});
		put("-generated-method-type", new Setter<String>() {
			public void set(final String val) {
				FormatPlugin.this.generatedMethodReturnTypeName = val;
			}
		});
		put("-generated-method-modifiers", new Setter<String>() {
			public void set(final String val) {
				FormatPlugin.this.generatedMethodModifiers = val;
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
		final CPluginCustomization objectFormatterCustomization = getGlobalCustomization(outline.getModel(), FormatPlugin.OBJECT_FORMATTER_CUSTOMIZATION_NAME);
		if(objectFormatterCustomization != null) {
			objectFormatterCustomization.markAsAcknowledged();
			this.objectFormatterClassName = getCustomizationAttribute(objectFormatterCustomization, "class", this.objectFormatterClassName);
			this.objectFormatterMethodName = getCustomizationAttribute(objectFormatterCustomization, "method", this.objectFormatterMethodName);
			this.objectFormatterFieldName = getCustomizationAttribute(objectFormatterCustomization, "field", this.objectFormatterFieldName);
			this.objectFormatterFieldModifiers = getCustomizationAttribute(objectFormatterCustomization, "modifiers", this.objectFormatterFieldModifiers);
		}

		final CPluginCustomization generatedMethodCustomization = getGlobalCustomization(outline.getModel(), FormatPlugin.GENERATED_METHOD_CUSTOMIZATION_NAME);
		if(generatedMethodCustomization != null) {
			generatedMethodCustomization.markAsAcknowledged();
			this.generatedMethodName = getCustomizationAttribute(generatedMethodCustomization, "name", this.generatedMethodName);
			this.generatedMethodReturnTypeName = getCustomizationAttribute(generatedMethodCustomization, "type", this.generatedMethodReturnTypeName);
			this.generatedMethodModifiers = getCustomizationAttribute(generatedMethodCustomization, "modifiers", this.generatedMethodModifiers);
		}
		for (final ClassOutline classOutline : outline.getClasses()) {
			final String expression = getCustomizationValue(errorHandler, classOutline, FormatPlugin.EXPRESSION_CUSTOMIZATION_NAME, "select");
			if (expression != null && expression.length() > 0) {
				generateToStringMethod(errorHandler, outline.getCodeModel(), classOutline, expression);
			}
		}
		return false;
	}

	private void generateToStringMethod(final ErrorHandler errorHandler, final JCodeModel model, final ClassOutline classOutline, final String expression) throws SAXException {
		final String formatterClassName;
		final String formatterMethodName;
		final String formatterFieldName;
		final int formatterFieldModifiers;
		final CPluginCustomization classCustomization = getCustomizationElement(classOutline, FormatPlugin.OBJECT_FORMATTER_CUSTOMIZATION_NAME);
		if (classCustomization != null) {
			classCustomization.markAsAcknowledged();
			formatterClassName = getCustomizationAttribute(classCustomization, "class", this.objectFormatterClassName);
			formatterMethodName = getCustomizationAttribute(classCustomization, "method", this.objectFormatterMethodName);
			formatterFieldName = getCustomizationAttribute(classCustomization, "field", this.objectFormatterFieldName);
			formatterFieldModifiers = parseModifiers(getCustomizationAttribute(classCustomization, "modifiers", this.objectFormatterFieldModifiers));
		} else {
			formatterClassName = this.objectFormatterClassName;
			formatterMethodName = this.objectFormatterMethodName;
			formatterFieldName = this.objectFormatterFieldName;
			formatterFieldModifiers = parseModifiers(this.objectFormatterFieldModifiers);
		}
		if (this.objectFormatterClassName == null) {
			errorHandler.error(new SAXParseException(FormatPlugin.RESOURCE_BUNDLE.getString("exception.missingFormatter"), classOutline.target.getLocator()));
		}
		final CPluginCustomization generatedMethodCustomization = getCustomizationElement(classOutline, FormatPlugin.GENERATED_METHOD_CUSTOMIZATION_NAME);
		final String methodName;
		final int methodModifiers;
		final JType methodReturnType;
		if (generatedMethodCustomization != null) {
			generatedMethodCustomization.markAsAcknowledged();
			methodName = getCustomizationAttribute(generatedMethodCustomization, "name", this.generatedMethodName);
			methodModifiers = parseModifiers(getCustomizationAttribute(generatedMethodCustomization, "modifiers", this.generatedMethodModifiers));
			methodReturnType = model.ref(getCustomizationAttribute(generatedMethodCustomization, "type", this.generatedMethodReturnTypeName));
		} else {
			methodName = this.generatedMethodName;
			methodModifiers = parseModifiers(this.generatedMethodModifiers);
			methodReturnType = model.ref(this.generatedMethodReturnTypeName);
		}
		final JClass objectFormatterClass = model.ref(formatterClassName);
		final JDefinedClass definedClass = classOutline.implClass;
		final JFieldVar objectFormatterField = definedClass.field(formatterFieldModifiers, objectFormatterClass, formatterFieldName, JExpr._null());
		final JMethod toStringMethod = definedClass.method(methodModifiers, methodReturnType, methodName);
		//toStringMethod.annotate(Override.class);
		final JConditional ifStatement = toStringMethod.body()._if(objectFormatterField.eq(JExpr._null()));
		ifStatement._then().assign(objectFormatterField, JExpr._new(objectFormatterClass).arg(expression));
		toStringMethod.body()._return(objectFormatterField.invoke(formatterMethodName).arg(JExpr._this()));
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

	private int parseModifiers(final String modifiers) {
		int mod = JMod.NONE;
		for (final String token : modifiers.split("\\s+")) {
			switch (token.toLowerCase()) {
				case "public":
					mod |= JMod.PUBLIC;
					break;
				case "protected":
					mod |= JMod.PROTECTED;
					break;
				case "private":
					mod |= JMod.PRIVATE;
					break;
				case "final":
					mod |= JMod.FINAL;
					break;
				case "static":
					mod |= JMod.STATIC;
					break;
				case "abstract":
					mod |= JMod.ABSTRACT;
					break;
				case "native":
					mod |= JMod.NATIVE;
					break;
				case "synchronized":
					mod |= JMod.SYNCHRONIZED;
					break;
				case "transient":
					mod |= JMod.TRANSIENT;
					break;
				case "volatile":
					mod |= JMod.VOLATILE;
					break;
			}
		}
		return mod;
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

	private String getCustomizationAttribute(final CPluginCustomization annotation, final String attributeName, final String defaultValue) {
		if (annotation != null) {
			final String attributeValue = annotation.element.getAttribute(attributeName);
			if (attributeValue != null && attributeValue.length() > 0) {
				return attributeValue;
			} else {
				return defaultValue;
			}
		}
		return null;
	}

	private CPluginCustomization getCustomizationElement(final ClassOutline classOutline, final String elementName) {
		return classOutline.target.getCustomizations().find(FormatPlugin.CUSTOMIZATION_NS, elementName);
	}

	private CPluginCustomization getGlobalCustomization(final Model model, final String elementName) {
		return model.getCustomizations().find(FormatPlugin.CUSTOMIZATION_NS, elementName);
	}

	private static interface Setter<T> {
		void set(final T val);
	}
}
