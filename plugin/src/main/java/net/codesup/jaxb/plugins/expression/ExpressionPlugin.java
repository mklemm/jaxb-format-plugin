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

package net.codesup.jaxb.plugins.expression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.kscs.util.plugins.xjc.base.AbstractPlugin;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JConditional;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.model.Aspect;
import com.sun.tools.xjc.model.CBuiltinLeafInfo;
import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.model.CCustomizable;
import com.sun.tools.xjc.model.CEnumLeafInfo;
import com.sun.tools.xjc.model.CPluginCustomization;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.xml.bind.v2.model.core.MaybeElement;

/**
 * XJC plugin to generate a "toString"-like method by generating an invocation
 * of a delegate object formatter class. Delegate class, method names, method return
 * types and modifiers can be customized on the XJC command line or as binding
 * customizations.
 * @author Mirko Klemm 2015-01-22
 */
public class ExpressionPlugin extends AbstractPlugin {
	private static final JAXBContext JAXB_CONTEXT;

	static {
		try {
			JAXB_CONTEXT = JAXBContext.newInstance(Expression.class, Expressions.class, Evaluator.class, Evaluators.class);
		} catch (final JAXBException e) {
			throw new RuntimeException(e);
		}
	}

	public static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(ExpressionPlugin.class.getName());
	public static final String OPTION_NAME = "-Xexpression";
	public static final String CUSTOMIZATION_NS = "http://www.codesup.net/jaxb/plugins/expression";
	public static final String EXPRESSION_CUSTOMIZATION_NAME = "expression";
	public static final String EXPRESSIONS_CUSTOMIZATION_NAME = "expressions";
	public static final String EVALUATOR_CUSTOMIZATION_NAME = "evaluator";
	public static final String EVALUATOR_REF_CUSTOMIZATION_NAME = "evaluator-ref";
	public static final String EVALUATORS_CUSTOMIZATION_NAME = "evaluators";
	public static final String METHOD_CUSTOMIZATION_NAME = "method";
	public static final String DEFAULT_GENERATED_METHOD_MODIFIERS = "public";
	public static final List<String> CUSTOM_ELEMENTS = Arrays.asList(
			ExpressionPlugin.EXPRESSION_CUSTOMIZATION_NAME,
			ExpressionPlugin.EXPRESSIONS_CUSTOMIZATION_NAME,
			ExpressionPlugin.EVALUATOR_CUSTOMIZATION_NAME,
			ExpressionPlugin.EVALUATOR_REF_CUSTOMIZATION_NAME,
			ExpressionPlugin.EVALUATORS_CUSTOMIZATION_NAME,
			ExpressionPlugin.METHOD_CUSTOMIZATION_NAME);
	public static final String DEFAULT_EVALUATOR_METHOD_NAME = "evaluate";
	public static final String DEFAULT_EVALUATOR_FIELD_NAME = "__evaluator%s";

	@Override
	public String getOptionName() {
		return ExpressionPlugin.OPTION_NAME.substring(1);
	}

	@Override
	public List<String> getCustomizationURIs() {
		return Collections.singletonList(ExpressionPlugin.CUSTOMIZATION_NS);
	}

	@Override
	public boolean isCustomizationTagName(final String nsUri, final String localName) {
		return ExpressionPlugin.CUSTOMIZATION_NS.equals(nsUri) && ExpressionPlugin.CUSTOM_ELEMENTS.contains(localName);
	}

	@Override
	public String getUsage() {
		return ExpressionPlugin.RESOURCE_BUNDLE.getString("usageText");
	}

	@Override
	public boolean run(final Outline outline, final Options opt, final ErrorHandler errorHandler) throws SAXException {
		try {
			final Map<String, Evaluator> evaluatorMap = getEvaluators(outline.getModel());
			for (final ClassOutline classOutline : outline.getClasses()) {
				final List<Expression> expressions = getExpressions(classOutline.target);
				if (!expressions.isEmpty()) {
					generateMethod(errorHandler, classOutline, evaluatorMap, expressions);
				}
			}
			return false;
		} catch(final JAXBException e) {
			throw new SAXException(e);
		}
	}

	private Map<String, Evaluator> getEvaluators(final CCustomizable customizable) throws JAXBException {
		final Map<String,Evaluator> evaluatorMap = new LinkedHashMap<>();
		final Unmarshaller unmarshaller = ExpressionPlugin.JAXB_CONTEXT.createUnmarshaller();
		final CPluginCustomization evaluatorsCustomization = getCustomizationElement(customizable, ExpressionPlugin.EVALUATORS_CUSTOMIZATION_NAME);
		if (evaluatorsCustomization != null) {
			evaluatorsCustomization.markAsAcknowledged();
			final Evaluators evaluators = unmarshaller.unmarshal(evaluatorsCustomization.element, Evaluators.class).getValue();
			for(final Evaluator evaluator:evaluators.getEvaluator()) {
				if(evaluator.getName() == null) {
					evaluator.setName(getEvaluatorName(evaluator));
				}
				fillEvaluatorReferences(evaluator);
				evaluatorMap.put(getEvaluatorName(evaluator), evaluator);
			}
		}
		final CPluginCustomization evaluatorCustomization = getCustomizationElement(customizable, ExpressionPlugin.EVALUATOR_CUSTOMIZATION_NAME);
		if (evaluatorCustomization != null) {
			evaluatorCustomization.markAsAcknowledged();
			final Evaluator evaluator = unmarshaller.unmarshal(evaluatorCustomization.element, Evaluator.class).getValue();
			if(evaluator.getName() == null) {
				evaluator.setName(getEvaluatorName(evaluator));
			}
			fillEvaluatorReferences(evaluator);
			evaluatorMap.put(getEvaluatorName(evaluator), evaluator);
		}
		return evaluatorMap;
	}

	private void fillEvaluatorReferences(final Evaluator evaluator) {
		for(final Expression expression:evaluator.getExpression()) {
			if (expression.getEvaluatorRef() == null) {
				final EvaluatorRef evaluatorRef = new EvaluatorRef();
				evaluatorRef.setName(evaluator.getName());
				expression.setEvaluatorRef(evaluatorRef);
			} else {
				if (expression.getEvaluatorRef().getName() == null) {
					expression.getEvaluatorRef().setName(evaluator.getName());
				}
				if (expression.getEvaluatorRef().getMethod() == null && !evaluator.getMethod().isEmpty()) {
					expression.getEvaluatorRef().setMethod(evaluator.getMethod().get(0).getName());
				}
			}
		}
	}

	private List<Expression> getExpressions(final CCustomizable customizable) throws JAXBException {
		final List<Expression> expressionMap = new ArrayList<>();
		final Unmarshaller unmarshaller = ExpressionPlugin.JAXB_CONTEXT.createUnmarshaller();
		final CPluginCustomization expressionsCustomization = getCustomizationElement(customizable, ExpressionPlugin.EXPRESSIONS_CUSTOMIZATION_NAME);
		if (expressionsCustomization != null) {
			expressionsCustomization.markAsAcknowledged();
			final Expressions expressions = unmarshaller.unmarshal(expressionsCustomization.element, Expressions.class).getValue();
			for(final Expression expression:expressions.getExpression()) {
				if(expression.getEvaluatorRef() == null) {
					expression.setEvaluatorRef(expressions.getEvaluatorRef());
				} else if(expressions.getEvaluatorRef() != null) {
					if(expression.getEvaluatorRef().getName() == null) {
						expression.getEvaluatorRef().setName(expressions.getEvaluatorRef().getName());
					}
					if(expression.getEvaluatorRef().getMethod() == null) {
						expression.getEvaluatorRef().setMethod( expressions.getEvaluatorRef().getMethod());
					}
				}
				expressionMap.add(expression);
			}
		}
		final CPluginCustomization expressionCustomization = getCustomizationElement(customizable, ExpressionPlugin.EXPRESSION_CUSTOMIZATION_NAME);
		if (expressionCustomization != null) {
			expressionCustomization.markAsAcknowledged();
			final Expression expression = unmarshaller.unmarshal(expressionCustomization.element, Expression.class).getValue();
			expressionMap.add(expression);
		}
		return expressionMap;
	}

	private String getEvaluatorName(final Evaluator evaluator) {
		if(evaluator.getName() != null) return evaluator.getName();
		if(evaluator.getClazz() == null) return null;
		final int simpleNameIndex = evaluator.getClazz().lastIndexOf('.') + 1;
		return evaluator.getClazz().substring(simpleNameIndex);
	}

	private void generateMethod(final ErrorHandler errorHandler,
			final ClassOutline classOutline,
			final Map<String,Evaluator> globalEvaluators,
			final List<Expression> expressions)
			throws JAXBException,SAXException {
		final Outline outline = classOutline.parent();
		final JCodeModel model = outline.getCodeModel();
		final Map<String,Evaluator> evaluators = new LinkedHashMap<>();
		final Map<String, Evaluator> localEvaluators = getEvaluators(classOutline.target);
		final List<Expression> localExpressions = new ArrayList<>(expressions);
		for(final Evaluator evaluator:localEvaluators.values()) {
			localExpressions.addAll(evaluator.getExpression());
		}
		evaluators.putAll(localEvaluators);
		evaluators.putAll(globalEvaluators);
		if (evaluators.isEmpty()) {
			errorHandler.error(new SAXParseException(ExpressionPlugin.RESOURCE_BUNDLE.getString("exception.missingFormatter"), classOutline.target.getLocator()));
		}

		final Map<String,JFieldVar> evaluatorFields = new LinkedHashMap<>();
		final JDefinedClass implClass = classOutline.implClass;
		for(final Expression expression:localExpressions) {
			final Evaluator evaluator = expression.getEvaluatorRef() == null || expression.getEvaluatorRef().getName() == null
					? evaluators.values().iterator().next() : evaluators.get(expression.getEvaluatorRef().getName());
			if(evaluator == null) {
				errorHandler.error(new SAXParseException(ExpressionPlugin.RESOURCE_BUNDLE.getString("exception.missingFormatter"), classOutline.target.getLocator()));
				continue;
			}
			final JType methodReturnType = translateType(outline, expression);
			final String methodName = coalesce(expression.getMethodName(), createMethodName(outline, expression), "toString");
			final JMethod generatedMethod = implClass.method(JMod.PUBLIC, methodReturnType, methodName);
			final JInvocation simpleInvoke;
			if(evaluator.getClazz() == null) {
				generatedMethod.body()._return(JExpr.direct(expression.getSelect()));
			} else {
				final Method method = expression.getEvaluatorRef() == null || expression.getEvaluatorRef().getMethod() == null
						? evaluator.getMethod().get(0) : findMethod(evaluator, expression.getEvaluatorRef().getMethod());
				if (method == null) {
					errorHandler.error(new SAXParseException(ExpressionPlugin.RESOURCE_BUNDLE.getString("exception.missingMethod"), classOutline.target.getLocator()));
					continue;
				}
				if (evaluator.isStatic()) {
					simpleInvoke = model.ref(evaluator.getClazz()).staticInvoke(method.getName()).arg(JExpr._this());
				} else {
					JFieldVar evaluatorField = evaluatorFields.get(evaluator.getName());
					if (evaluatorField == null) {
						final JClass fieldType = model.ref(evaluator.getClazz());
						evaluatorField = implClass.field(
								JMod.PRIVATE | JMod.TRANSIENT,
								fieldType,
								evaluator.getField() == null
										? String.format(ExpressionPlugin.DEFAULT_EVALUATOR_FIELD_NAME, outline.getModel().getNameConverter().toClassName(evaluator.getName()))
										: evaluator.getField()
						);
						evaluatorFields.put(evaluator.getName(), evaluatorField);
					}
					final JConditional ifStatement = generatedMethod.body()._if(evaluatorField.eq(JExpr._null()));
					ifStatement._then().assign(evaluatorField, JExpr._new(evaluatorField.type()).arg(JExpr._this()));
					simpleInvoke = evaluatorField.invoke(method.getName());
				}
				generatedMethod.body()._return(generateInvocation(expression, method, methodReturnType, simpleInvoke));
			}
		}
	}

	private String createMethodName(final Outline outline, final Expression expression) {
		return expression == null || expression.getName() == null ? null : "get" + outline.getModel().getNameConverter().toPropertyName(expression.getName());
	}

	private JExpression generateInvocation(final Expression expression, final Method method, final JType methodReturnType, final JInvocation invocation) {
		final JExpression expressionLiteral = !method.isLiteral()
				? JExpr.lit(expression.getSelect())
				: JExpr.direct(expression.getSelect());
		invocation.arg(expressionLiteral);
		if (method.getTypePassing() == Language.JAVA) {
			if (methodReturnType instanceof JClass) {
				return invocation.arg(JExpr.dotclass((JClass)methodReturnType));
			} else {
				return JExpr.cast(methodReturnType, invocation.arg(methodReturnType.boxify().dotclass()));
			}
		} else if (method.getTypePassing() == Language.XML_SCHEMA) {
			final JClass qNameType = methodReturnType.owner().ref(QName.class);
			return JExpr.cast(methodReturnType, invocation.arg(JExpr._new(qNameType).arg(expression.getType().getNamespaceURI()).arg(expression.getType().getLocalPart()).arg(expression.getType().getPrefix())));
		} else {
			return JExpr.cast(methodReturnType, invocation);
		}
	}

	private JType translateType(final Outline model, final Expression expression) {
		if(expression.getType() == null) return model.getCodeModel().ref(String.class);
		for(final CBuiltinLeafInfo cinfo:model.getModel().builtins().values()) {
			if(typeMatches(expression, cinfo)) {
				return cinfo.toType(model, Aspect.EXPOSED);
			}
			for(final QName typeName : cinfo.getTypeNames()) {
				if(typeMatches(expression, typeName)) {
					return cinfo.toType(model, Aspect.EXPOSED);
				}
			}
		}
		for(final CClassInfo cinfo:model.getModel().beans().values()) {
			if(typeMatches(expression, cinfo)) {
				return cinfo.toType(model, Aspect.EXPOSED);
			}
		}
		for(final CEnumLeafInfo cinfo:model.getModel().enums().values()) {
			if(typeMatches(expression, cinfo)) {
				return cinfo.toType(model, Aspect.EXPOSED);
			}
		}
		return model.getCodeModel().ref(String.class);
	}

	private boolean typeMatches(final Expression expression, final MaybeElement<?, ?> cinfo) {
		final QName relevantName= cinfo.isElement() ? cinfo.getElementName() : cinfo.getTypeName();
		return typeMatches(expression, relevantName);
	}

	private boolean typeMatches(final Expression expression, final QName relevantName) {
		return relevantName != null && relevantName.getNamespaceURI() != null && relevantName.getLocalPart() != null
				&& relevantName.getNamespaceURI().equals(expression.getType().getNamespaceURI())
				&& relevantName.getLocalPart().equals(expression.getType().getLocalPart());
	}

	private Method findMethod(final Evaluator evaluator, final String name) {
		for(final Method method : evaluator.getMethod()) {
			if(method.getName().equals(name)) {
				return method;
			}
		}
		return null;
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

	private CPluginCustomization getCustomizationElement(final CCustomizable elem, final String elementName) {
		return elem.getCustomizations().find(ExpressionPlugin.CUSTOMIZATION_NS, elementName);
	}


	private <T> T coalesce(final T... vals) {
		for (final T val : vals) {
			if (val != null)
				return val;
		}
		return null;
	}

}
