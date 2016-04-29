package net.codesup.jaxb.plugins.expression;

import javax.xml.namespace.QName;

/**
 * @author Mirko Klemm 2016-04-29
 */
public final class LiteralEvaluator {
	private LiteralEvaluator() {}

	public static <T extends Number> T check(final Object instance, final T expression, final Class<T> returnType) {
		return expression;
	}

	public static Object loop(final Object instance, final String expression, final QName returnType) {
		return instance;
	}
}
