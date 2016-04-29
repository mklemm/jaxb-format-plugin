# jaxb-expression-plugin
Plugin for the JAXB (Java API for XML Binding) Schema-to-Source compiler (XJC) that generates
methods that evaluate a specific expression on an instance of a generated class.
The expressions can be of any format, provided there is an evaluator class that fulfils the
properties below, and provided the expression can be written in string form in
the XML Schema definition (XSD) file.

## Motivation
There are several plugins for XJC currently to generate "toString()", "hashCode()", "equals()"
and similar methods in the generated classes. However, these implementations
are based on a generic approach that includes all properties of the generated class in the respective operation.
On the other hand, there is the "code injection" plugin that just copies arbitrary java code to
the generated class' body.
The expression plugin tries to fill the gap between these two extremes. XSD and binding config files
should be kept free of programming-language-specific content, and entering literal java code there
is not acceptable if you want to share your XSDs as part of an interface description of e.g. a REST
service. But sometimes yopu need more flexibility in generating certain additional methods than just
the generic toString(), hashCode(), and equals() logic.
The expression plugin lets you implement simple additional methods for a generated class while
keeping most of your XSDs and binding files language-independent. It only allows to generate
read-only logic, which is good enough in a functional environment.
For example, when combined with the [jaxb-object-formatter](http://github.com/mklemm/jaxb-object-formatter) module,
which uses a fork of the apache [commons-jxpath](http://github.com/mklemm/commons-jxpath) project,
you can express certain read-only logic in terms of XPath-expressions that are evaluated on the live
object graph, thus staying within the scope of W3C XML standards, so you can easily
share your annotated XSDs, and third-party client code may even use the annotations
to add useful logic for itself.

## Usage
- Add an evaluator class (see below) to the compile and runtime classpath of your application.
- Add jaxb-expression-plugin.jar to the classpath of the XJC. See below on examples about how to do that with Maven.
- Enable extension processing in XJC by specifying the "-extension" command line option. See below for Maven example.
- Enable jaxb-expressiont-plugin with "-Xexpression" on the XJC command line.
- Specify the fully qualified name of the evaluator class in the XSD or binding-config file with the <evaluator>
  binding customization on the global or complexType level.
- Add "expression" binding customizations to complexType definitions in your XSD or separate binding customization file.
  See [reference](#reference) below.

## Reference
### Plugin artifact
groupId: net.codesup.util
artifactId: jaxb-expression-plugin

### Plugin Activation
	-Xexpression


### Binding customizations
For binding customization elements, see the attached XSD.

The evaluator class does not need to be in the classpath at the time code is generated. It also does
not need to implement a specific interface. It must, however, be in the classpath when
the generated code is compiled by the java compiler.

There are two different patterns you can implement an evaluator class as:
1. Instance evaluator
2. Static (utility class) evaluator

#### Instance Evaluator
The evaluator class must have the following properties:

1. Public constructor taking the instance of the generated entity class
    as single argument. The evaluator class will be instantiated once for
    every instance of a generated class that is given an "expression"
    customization.
2. Public instance methods that take an expression as a string and optionally
    the requested return type as parameters.
	The return type of this method must be compatible with the return
	type of the generated method.
	Usually, an evaluator class will compile the expression on the first
	invocation and will cache the compiled version for repeated usage.

#### Static (Utility) Evaluator
The evaluator class must have the following properties:

1. It is never instantiated, so no accessible constructors are needed.
2. Public instance methods that take as a first argument the instance of
    the target class, as second arg an expression as a string and optionally
    the requested return type as parameters.
	The return type of this method must be compatible with the return
	type of the generated method.


## Examples
### Using with Maven and [jxpath-object-formatter](http://github.com/mklemm/jxpath-object-formatter)
This shows you how to generate "toString()" methods for your generated classes, which return a
string representation of the object based on an XPath expression that evaluates to a string.

Based on apache [commons-jxpath](http://github.com/mklemm/commons-jxpath), you can specify
an XPath expression on every complexType definition, which will then be evaluated against
the java object tree in memory, NOT the serialized XML representation of your JAXB object.
This way, generated toString methods can be used anywhere in your code at runtime without
serializing/deserializing your object.

The [jxpath-object-formatter](http://github.com/mklemm/jxpath-object-formatter) implementation
uses a modified version of jxpath that lets you write your XPath expressions using the XML names
of object's properties, represented as XML elements and attributes, as opposed to the standard
commons-jxpath that can evaluate expressions only if node references are given as JavaBeans property
names. This way, there should be no syntactical difference in your XPath expressions, whether they
are processing the serialized XML document or the object graph in memory.
Additionally, jxpath-object-formatter defines custom XPath functions to format java.util.Date values etc.

#### Maven setup
1. Add runtime dependency to jxpath-object-formatter:

		<dependencies>
			<!-- ... other dependencies -->
			<dependency>
                <groupId>com.kscs.util</groupId>
                <artifactId>jxpath-object-formatter</artifactId>
                <version>1.1.0</version>
            </dependency>
			<!-- ... other dependencies -->
        </depenendcies>

2. Enable the jaxb2-maven-plugin to generate java code from XSD:

		<build>
			<!-- ... other build stuff -->
			<plugins>
				<!-- ... other plugins -->
				<plugin>
                	<groupId>org.jvnet.jaxb2.maven2</groupId>
                    <artifactId>maven-jaxb2-plugin</artifactId>
                    <version>0.11.0</version>
                    <executions>
                        <execution>
                            <id>xsd-generate-2.2</id>
                            <phase>generate-sources</phase>
                            <goals>
                                <goal>generate</goal>
                            </goals>
                        </execution>
                    </executions>
                    <configuration>
                        <schemaIncludes>
                            <schemaInclude>**/*.xsd</schemaInclude>
                        </schemaIncludes>
                        <strict>true</strict>
                        <verbose>true</verbose>
                        <extension>true</extension>
                        <removeOldOutput>true</removeOldOutput>
                        <specVersion>2.2</specVersion>
                        <episode>true</episode>
                        <useDependenciesAsEpisodes>true</useDependenciesAsEpisodes>
                        <scanDependenciesForBindings>false</scanDependenciesForBindings>
                        <args>
							<!-- ... other XJC plugin args -->
                            <arg>-Xexpression</arg> <!-- expression plugin activation -->
                        </args>
                        <plugins>
							<!-- ... other XJC plugin references -->
                            <plugin>
                                <!-- format plugin reference -->
                                <groupId>net.codesup.util</groupId>
                                <artifactId>jaxb-expression-plugin</artifactId>
                                <version>1.0.0</version>
                            </plugin>
                        </plugins>
                    </configuration>
                </plugin>
			</plugins>
		</build>

#### Use it in XSD
Ths is an example how to specify the binding customizations inline in the XSD file,
please refer to the JAXB/XJC documentation on how to do that in a separate binding
file.
In any case, you must declare a namespace prefix for the "http://www.codesup.net/jaxb/plugins/expression"
namespace, and then use (at least) one "evaluator" and one "expression" customization. Also note the declaration of
the JAXB namespace, and the jxb:version and jxb:extensionBindingPrefixes attributes.

		<schema xmlns="http://www.w3.org/2001/XMLSchema" version="1.0"
			targetNamespace="http://my.namespace.org/myschema"
			xmlns:jxb="http://java.sun.com/xml/ns/jaxb"
			jxb:version="2.1"
			jxb:extensionBindingPrefixes="expression"
			xmlns:expression="http://www.codesup.net/jaxb/plugins/expression">

            <annotation>
                <appInfo>
                    <!-- declare evaluator class ->
                    <expression:evaluator class="com.kscs.util.jaxb.Evaluator"/>
                </appInfo>
            </annotation>
			<!-- ... other definitions -->

			<complexType name="my-type">
				<annotation>
					<appInfo>
						<expression:expression method-name="toString" select="concat('My Object is ', @name, ', created at: ', format:isoDate(created-at))"/>
					</appInfo>
				</annotation>
				<sequence>
					<element name="created-at" type="datetime"/>
				</sequence>
				<attribute name="name" type="string"/>
			</complexType>
		</schema>

There can be multiple evaluators, in which case an expression element can reference an appropriate evaluator in the following way:

		<schema xmlns="http://www.w3.org/2001/XMLSchema" version="1.0"
			targetNamespace="http://my.namespace.org/myschema"
			xmlns:jxb="http://java.sun.com/xml/ns/jaxb"
			jxb:version="2.1"
			jxb:extensionBindingPrefixes="expression"
			xmlns:expression="http://www.codesup.net/jaxb/plugins/expression">

            <annotation>
                <appInfo>
                    <!-- declare evaluator class ->
                    <expression:evaluator name="toStringEval" class="com.kscs.util.jaxb.Evaluator">
                        <expression:method name="evaluate"/>
                    </expression:evaluator>
                    <expression:evaluator name="my-second-eval" static="true" class="com.acme.EvaluatorUtility">
                        <expression:method name="convert" type-passing="xml-schema" literal="true"/>
                    </expression:evaluator>
                </appInfo>
            </annotation>
			<!-- ... other definitions -->

			<complexType name="my-type">
				<annotation>
					<appInfo>
						<expression:expression method-name="toString" select="concat('My Object is ', @name, ', created at: ', format:isoDate(created-at))">
						    <expression:evaluator name="toStringEval"/>
						</expression:expression>
					</appInfo>
				</annotation>
				<sequence>
					<element name="created-at" type="datetime"/>
				</sequence>
				<attribute name="name" type="string"/>
			</complexType>
		</schema>

There can be evaluator configurations local to a specific complexType, in which case <expression> elements can be specified as direct children of the evaluator
configuration:

		<schema xmlns="http://www.w3.org/2001/XMLSchema" version="1.0"
			targetNamespace="http://my.namespace.org/myschema"
			xmlns:jxb="http://java.sun.com/xml/ns/jaxb"
			jxb:version="2.1"
			jxb:extensionBindingPrefixes="expression"
			xmlns:expression="http://www.codesup.net/jaxb/plugins/expression">

			<!-- ... other definitions -->

			<complexType name="my-type">
				<annotation>
					<appInfo>
                        <expression:evaluator name="toStringEval" class="com.kscs.util.jaxb.Evaluator">
                            <expression:method name="evaluate"/>
                            <expression:expression method-name="toString" select="concat('My Object is ', @name, ', created at: ', format:isoDate(created-at))"/>
                            <expression:expression method-name="hashCode" select="generate-id(name)"/>
                        </expression:evaluator>
					</appInfo>
				</annotation>
				<sequence>
					<element name="created-at" type="datetime"/>
				</sequence>
				<attribute name="name" type="string"/>
			</complexType>
		</schema>

If multiple expression elements are given on a complexType, they must be wrapped inside and <expressions> element, since JAXB
XJC doesn't allow multiple customization elements of the same type on one target element.
The <expressions> element can also contain a reference to an evaluator class and optionally a specific evaluator method in the same way
a single <expression> element can have. This reference is then applied to all contained <expression> elements that
don't specify a reference of their own.

#### Use the generated "toString()" method
You can now write something like this:

		MyType myObject = new MyType();
		myObject.setName("First instance");
		myObject.setCreatedAt(new Date());
		System.out.println(myObject);

And it will print something like:

		My object is First instance, created at: 2015-01-26T11:30:00Z
