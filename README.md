# jaxb-format-plugin
Plugin for the JAXB (Java API for XML Binding) Schema-to-Source compiler (XJC) that generates code
to format instances of generated classes via an arbitrary helper class.

## Motivation
There are several plugins for XJC currently to generate a "toString()" method in generated JAXB class files.
Unfortunately, however, most of these plugins are based on the assumption that a "toString()" or other formatting
method should return a generic string representation of the object they are called on.
This plugin, however, gives you full control over the shape of the string representation of an object, and
also lets you specify the name of the generated method (defaults to "toString") and a helper class
that does the actual formatting.
An example helper class that uses XPath expressions as the building blocks of the formatting engine
is given in the jaxb-jxpath github repository, which is a fork of the apache commons-jxpath project, modified
to support XPath expressions using the actual XML names of JAXB-bindable properties, by processing JAXB-specific source-level
annotations.

## Usage
- Add jaxb-format-plugin.jar to the classpath of the XJC. See below on examples about how to do that with Maven.
- Enable extension processing in XJC by specifying the "-extension" command line option. See below for Maven example.
- Enable jaxb-format-plugin by giving "-Xformat" on the XJC command line, followed by "-formatter=<formatter class>" and
  optionally "-method=<method name>". <formatter class> is the fully qualified name of a utility class used to format
  an instance according to a given expression. This utility class does not need to implement a specific interface, but
  it must have a public one-argument constructor "C(String expression)" taking a given formatting expression as its
  single argument, and it must have a public instance method "format(Object instance)", which is called to actually
  return the formatted string representation. The utility class or its dependencies do not need to be in the classpath
  at the time XJC generates code, but they must be in the compile-time classpath of the generated source tree.
  If <method name> is given, it specifies the name of the generated formatting method, "toString" by default. The generated
  method always is public, returns a java.lang.String, and has no parameters. Both "-formatter" and "-method" settings
  can be overridden on complexType or element level by JAXB binding customizations <formatter> and <method>.

## Reference
### Plugin artifact
groupId: com.kscs.util
artifactId: jaxb-format-plugin

### Plugin Activation
<table>
<tr><th colspan="2">-Xformat -format=<formatter class name> [-method=<generated method name>]</th></tr>
<tr><th>-Xformat:</th><td>Plugin activation.</td></tr>
<tr><th>-formatter</th><td>Mandatory. Fully qualified class name of the class doing the actual formatting.
					The class does not need to be in the classpath at the time code is generated. It also does
					not need to implement a specific interface.
					It must have the following properties:
						1. Public constructor taking the expression string as single argument. The helper
							class will be instantiated once for every generated class that is given an
							"expression" customization. The expression will be passed into this constructor,
							the implementation should the compile or otherwise process the expression to
							an internal state.
						2. Public instance method "String format(Object instance)" returning
							a string representation of the given instance according to the expression
							given in the constructor.</td></tr>
<tr><th>-method</th><td>Name of the method to be generated, defaults to "toString". The generated method
					will always be public, have a return value of type java.lang.String, and will have no parameters.</td></tr>
</table>
## Examples
### Setting up your maven project


