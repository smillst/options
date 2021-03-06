// The five files
//   Option.java
//   OptionGroup.java
//   Options.java
//   Unpublicized.java
//   OptionsDoclet.java
// together comprise the implementation of command-line processing.

package org.plumelib.options;

import java.io.File;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/*>>>
import org.checkerframework.checker.formatter.qual.*;
import org.checkerframework.checker.initialization.qual.*;
import org.checkerframework.checker.lock.qual.*;
import org.checkerframework.checker.nullness.qual.*;
import org.checkerframework.dataflow.qual.*;
*/

/**
 * The Options class:
 *
 * <ul>
 *   <li>parses command-line options and sets fields in your program accordingly,
 *   <li>creates usage messages (such as printed by a <span style="white-space: nowrap;">{@code
 *       --help}</span> option), and
 *   <li>creates documentation suitable for a manual or manpage.
 * </ul>
 *
 * Thus, the programmer is freed from writing duplicative, boilerplate code. The user documentation
 * is automatically generated and never gets out of sync with the rest of the program.
 *
 * <p>The programmer does not have to write any code, only declare and document variables. For each
 * field that you want to set from a command-line argument, you write Javadoc and an @{@link
 * org.plumelib.options.Option} annotation. Then, the field is automatically set from a command-line
 * option of the same name, and usage messages and printed documentation are generated
 * automatically.
 *
 * <p>The following code enables a user to invoke theprogram using the command-line arguments <span
 * style="white-space: nowrap;">{@code --outfile}</span>, <span style="white-space: nowrap;">{@code
 * -o}</span> (shorthand for <span style="white-space: nowrap;">{@code --outfile}</span>), <span
 * style="white-space: nowrap;">{@code --ignore-case}</span>, <span style="white-space:
 * nowrap;">{@code -i}</span>, (shorthand for <span style="white-space: nowrap;">{@code
 * --ignore-case}</span>), and <span style="white-space: nowrap;">{@code --temperature}</span>:
 *
 * <pre>
 * import org.plumelib.options.*;
 *
 * public class MyProgram {
 *
 *   &#64;Option("-o &lt;filename&gt; the output file ")
 *   public static File outfile = new File("/tmp/foobar");
 *
 *   &#64;Option("-i ignore case")
 *   public static boolean ignore_case;
 *
 *   &#64;Option("set the initial temperature")
 *   public static double temperature = 75.0;
 *
 *   public static void main(String[] args) {
 *     MyProgram myInstance = new MyProgram();
 *     Options options = new Options("MyProgram [options] infile outfile",
 *                                   myInstance, MyUtilityClass.class);
 *     String[] remainingArgs = options.parse(true, args);
 *     ...
 *   }
 * }
 * </pre>
 *
 * In the code above, the call to {@link #parse(boolean, String[])} sets fields in object {@code
 * myInstance} and sets static fields in class {@code MyUtilityClass}. It returns the original
 * command line, with all options removed. If a command-line argument is incorrect, it prints a
 * usage message and terminates the program.
 *
 * <p>For examples of usage, see the documentation for <a
 * href="https://types.cs.washington.edu/plume-lib/api/plume/Lookup.html#command-line-options">Lookup</a>,
 * <a href="https://randoop.github.io/randoop/manual/#command-line-options">Randoop</a>, and <a
 * href="https://types.cs.washington.edu/javari/javarifier/#command-line-opts">Javarifier</a>.
 *
 * <p><b>@Option indicates a command-line option</b>
 *
 * <p>The @{@link Option} annotation on a field specifies brief user documentation and, optionally,
 * a one-character short name that a user may supply on the command line. The long name is taken
 * from the name of the variable. When the name contains an underscore, the user may substitute a
 * hyphen on the command line instead; for example, the <span style="white-space: nowrap;">{@code
 * --multi-word-variable}</span> command-line option would set the variable {@code
 * multi_word_variable}.
 *
 * <p>On the command line, the values for options are specified in the form <span
 * style="white-space: nowrap;">"--name=value"</span> or <span style="white-space: nowrap;">"-name
 * value"</span>. The value (after the "=" or " ") is mandatory for all options except booleans.
 * Booleans are set to true if no value is specified. Booleans support <span style="white-space:
 * nowrap;">"--no-<em>optionname</em>"</span> which is equivalent to <span style="white-space:
 * nowrap;">"--optionname=false"</span>.
 *
 * <p>A user may provide an option multiple times on the command line. If the field is a list, each
 * entry is added to the list. If the field is not a list, then only the last occurrence is used
 * (subsequent occurrences override the previous value).
 *
 * <p>All arguments that start with <span style="white-space: nowrap;">"-"</span> are processed as
 * options. By default, the entire command line is scanned for options. To terminate option
 * processing at the first non-option argument, see {@link #setParseAfterArg(boolean)}. Also, the
 * special option <span style="white-space: nowrap;">"--"</span> always terminates option
 * processing; <span style="white-space: nowrap;">"--"</span> is discarded, but no subsequent parts
 * of the command line are scanned for options.
 *
 * <p><b>Unpublicized options</b>
 *
 * <p>The @{@link Unpublicized} annotation causes an option not to be displayed in the usage
 * message. This can be useful for options that are preliminary, experimental, or for internal
 * purposes only. The @{@link Unpublicized} annotation must be specified in addition to the @{@link
 * Option} annotation.
 *
 * <p>The usage message can optionally include unpublicized options; see {@link
 * #usage(boolean,String...)}.
 *
 * <p><b>Option groups</b>
 *
 * <p>In a usage message or manual, it is useful to group related options and give the group a name.
 * For examples of this, see the documentation for <a
 * href="https://types.cs.washington.edu/plume-lib/api/plume/Lookup.html#command-line-options">Lookup</a>,
 * <a href="https://randoop.github.io/randoop/manual/#command-line-options">Randoop</a>, and <a
 * href="https://types.cs.washington.edu/javari/javarifier/#command-line-opts">Javarifier</a>.
 *
 * <p>To specify option groups, declare related fields together in your {@code .java} file, then
 * write @{@link OptionGroup} on the first field in each group (including the first
 * {@code @Option}-annotated field of every class and object passed to the {@link #Options(String,
 * Object...)} constructor).
 *
 * <p>The group name (the first argument of an {@code @OptionGroup} annotation) must be unique among
 * all classes and objects passed to the {@link #Options(String, Object...)} constructor.
 *
 * <p>If an option group itself is unpublicized:
 *
 * <ul>
 *   <li>The default usage message omits the group and all options belonging to it.
 *   <li>An unpublicized option group (that has any publicized options) is included in documentation
 *       for a manual.
 * </ul>
 *
 * If an option group is not unpublicized but contains only unpublicized options, it will not be
 * included in the default usage message.
 *
 * <p><b>Option aliases</b>
 *
 * <p>The @{@link Option} annotation has an optional parameter {@code aliases}, which accepts an
 * array of strings. Each string in the array is an alias for the option being defined and can be
 * used in place of an option's long name or short name.
 *
 * <p>One example is that a program might support <span style="white-space:
 * nowrap;">"--optimize"</span> and <span style="white-space: nowrap;">"--optimise"</span> which are
 * interchangeable. Another example is that a program might support <span style="white-space:
 * nowrap;">"--help"</span> and <span style="white-space: nowrap;">"-help"</span> with the same
 * meaning:
 *
 * <pre>
 *     // The user may supply --help, -h, or -help, all of which mean the same thing and set this variable
 *     &#64;Option(value="-h Print a help message", aliases={"-help"})
 *     public static boolean help;</pre>
 *
 * Aliases should start with a single dash or double dash. If there is only a single, one-character
 * alias, it can be put at the beginning of the value field or in the aliases field. It is the
 * programmer's responsibility to ensure that no alias is the same as other options or aliases.
 *
 * <p><b>Generating documentation for a manual or manpage</b>
 *
 * <p>It is helpful to include a summary of all command-line options in amanual, manpage, or the
 * class Javadoc for a class that has a main method. The {@link org.plumelib.options.OptionsDoclet}
 * class generates HTML documentation.
 *
 * <p><b>Supported field types</b>
 *
 * <p>A field with an @{@link Option} annotation may be of the following types:
 *
 * <ul>
 *   <li>Primitive types: boolean, byte, char, short, int, long, float, double.
 *   <li>Primitive type wrappers: Boolean, Byte, Char, Short, Integer, Long, Float, Double. Use of a
 *       wrapper type allows the argument to have no default value.
 *   <li>Reference types that have a constructor with a single string parameter.
 *   <li>java.util.regex.Pattern.
 *   <li>enums.
 *   <li>Lists of any of the above reference types.
 * </ul>
 *
 * <p><b>Customization</b>
 *
 * <p>Option processing can be customized in a number of ways.
 *
 * <ul>
 *   <li>If {@link #setUseSingleDash(boolean)} is true, then the long names take the form <span
 *       style="white-space: nowrap;">"-longname"</span> instead of <span style="white-space:
 *       nowrap;">"--longname"</span>. It defaults to false.
 *   <li>If {@link #setParseAfterArg(boolean)} is true, then options are searched for throughout a
 *       command line, to its end. If it is false, then processing stops at the first non-option
 *       argument. It defaults to true.
 *   <li>If {@link #spaceSeparatedLists} is true, then when an argument contains spaces, it is
 *       treated as multiple elements to be added to a list. It defaults to false.
 *   <li>The programmer may set {@link #usageSynopsis} to masquerade as another program.
 *   <li>If {@link #useDashes} is false, then usage messages advertise long options with underscores
 *       (as in {@code --my_option_name}) instead of dashes (as in {@code --my-option-name}). The
 *       user can always specify either; this just affects usage messages. It defaults to false.
 * </ul>
 *
 * <p><b>Limitations</b>
 *
 * <ul>
 *   <li>Short options are only supported as separate entries (e.g., <span style="white-space:
 *       nowrap;">"-a -b"</span>) and not as a single group (e.g., <span style="white-space:
 *       nowrap;">"-ab"</span>).
 *   <li>If you have a boolean option named exactly "long", you must use <span style="white-space:
 *       nowrap;">"--long=false"</span> to turn it off; <span style="white-space:
 *       nowrap;">"--no-long"</span> is not supported.
 * </ul>
 *
 * @see org.plumelib.options.Option
 * @see org.plumelib.options.OptionGroup
 * @see org.plumelib.options.Unpublicized
 * @see org.plumelib.options.OptionsDoclet
 */
public class Options {

  // User-settable fields

  /**
   * When true, long options take the form <span style="white-space: nowrap;">{@code
   * -longOption}</span> with a single dash, rather than the default <span style="white-space:
   * nowrap;">{@code --longOption}</span> with two dashes.
   */
  public boolean useSingleDash = false;

  /**
   * Whether to parse options after a non-option command-line argument. If false, option processing
   * stops at the first non-option command-line argument. If true, options specified even at the end
   * of the command line are processed.
   *
   * @see #setParseAfterArg(boolean)
   */
  private boolean parseAfterArg = true;

  /**
   * Whether to treat arguments to lists as space-separated. Defaults to false.
   *
   * <p>When true, an argument to an option of list type is split, on whitespace, into multiple
   * arguments each of which is added to the list. When false, each argument to an option of list
   * type is treated as a single element, no matter what characters it contains.
   *
   * <p>For example, when this is true, a command line containing <span style="white-space:
   * nowrap;">{@code --my-option="foo bar"}</span> is equivalent to <span style="white-space:
   * nowrap;">{@code --my-option="foo" --my-option="bar"}</span>. Both of them have the effect of
   * adding two elements, "foo" and "bar", to the list {@code my_option}.
   */
  public static boolean spaceSeparatedLists = false;

  /**
   * Synopsis of usage. Example: "prog [options] arg1 arg2 ..."
   *
   * <p>This field is public so that clients can reset it. Setting it enables one program to
   * masquerade as another program, based on parsed options.
   */
  public /*@Nullable*/ String usageSynopsis = null;

  /**
   * In usage messages, use dashes (hyphens) to split words in option names. This only applies to
   * fields whose name contains an underscore. On the command line, a user may use either the
   * underscores or dashes in the option name; this only controls which one is advertised in usage
   * messages.
   */
  public boolean useDashes = true;

  // Private fields

  /** First specified class. Void stands for "not yet initialized". */
  private Class<?> mainClass = Void.TYPE;

  /** List of all of the defined options. */
  private final List<OptionInfo> options = new ArrayList<OptionInfo>();

  /** Map from short or long option names (with leading dashes) to option information. */
  private final Map<String, OptionInfo> nameMap = new LinkedHashMap<String, OptionInfo>();

  /** Map from option group name to option group information. */
  private final Map<String, OptionGroupInfo> groupMap =
      new LinkedHashMap<String, OptionGroupInfo>();

  /**
   * If true, then the user is using {@code @OptionGroup} annotations correctly (as per the
   * requirement specified above). If false, then {@code @OptionGroup} annotations have not been
   * specified on any {@code @Option}-annotated fields. When {@code @OptionGroup} annotations are
   * used incorrectly, an Error is thrown by the Options constructor.
   *
   * @see OptionGroup
   */
  private boolean hasGroups;

  /** String describing "[+]" (copied from Mercurial). */
  private static final String LIST_HELP = "[+] marked option can be specified multiple times";

  // // Debug loggers
  // // Does nothing if not enabled.
  // private final SimpleLog debugOptions = new SimpleLog(false);
  private boolean debugEnabled = false;

  /**
   * Enable or disable debug logging.
   *
   * @param enabled whether to enable or disable logging
   */
  public void enableDebugLogging(boolean enabled) {
    debugEnabled = enabled;
  }

  /** All of the argument options as a single string. Used for debugging. */
  private String optionsString = "";

  /** The system-dependent line separator. */
  private static String lineSeparator = System.getProperty("line.separator");

  /** Information about an option. */
  class OptionInfo {

    /** What variable the option sets. */
    Field field;

    //    /** Option annotation on the field. */
    //    Option option;

    /** Object containing the field. Null if the field is static. */
    /*@UnknownInitialization*/ /*@Raw*/ /*@Nullable*/ Object obj;

    /** Short (one-character) argument name. */
    /*@Nullable*/ String shortName;

    /** Long argument name. */
    String longName;

    /** Aliases for this option. */
    String[] aliases;

    /** Argument description: the first line. */
    String description;

    /** Full Javadoc description. */
    /*@Nullable*/ String jdoc;

    /**
     * Maps names of enum constants to their corresponding Javadoc. This is used by OptionsDoclet to
     * generate documentation for enum-type options. Null if the baseType is not an Enum.
     */
    /*@MonotonicNonNull*/ Map<String, String> enumJdoc;

    /**
     * Name of the argument type. Defaults to the type of the field, but user can override this in
     * the option string.
     */
    String typeName;

    /** Class type of this field. If the field is a list, the basetype of the list. */
    Class<?> baseType;

    /** Default value of the option as a string. */
    /*@Nullable*/ String defaultStr = null;

    /**
     * If true, the default value string for this option will be excluded from OptionsDoclet
     * documentation.
     */
    boolean noDocDefault = false;

    /** If the option is a list, this references that list. */
    /*@MonotonicNonNull*/ List<Object> list = null;

    /** Constructor that takes one String for the type. */
    /*@Nullable*/ Constructor<?> constructor = null;

    /**
     * Factory that takes a string (some classes don't have a string constructor) and always returns
     * non-null.
     */
    /*@Nullable*/ Method factory = null;

    /**
     * If true, this OptionInfo is not output when printing documentation.
     *
     * @see #usage()
     */
    boolean unpublicized;

    /**
     * Create a new OptionInfo. The short name, type name, and description are taken from the option
     * parameter. The long name is the name of the field. The default value is the current value of
     * the field.
     *
     * @param field the field to set
     * @param option the option
     * @param obj the object whose field will be set; if obj is null, the field must be static
     * @param unpublicized whether the option is unpublicized
     */
    OptionInfo(
        Field field,
        Option option,
        /*@UnknownInitialization*/ /*@Raw*/ /*@Nullable*/ Object obj,
        boolean unpublicized) {
      this.field = field;
      //      this.option = option;
      this.obj = obj;
      this.baseType = field.getType();
      this.unpublicized = unpublicized;
      this.aliases = option.aliases();
      this.noDocDefault = option.noDocDefault();

      // The long name is the name of the field
      longName = field.getName();
      if (useDashes) {
        longName = longName.replace('_', '-');
      }

      // Get the default value (if any)
      Object defaultObj = null;
      if (!Modifier.isPublic(field.getModifiers())) {
        throw new Error("option field is not public: " + field);
      }
      try {
        defaultObj = field.get(obj);
        if (defaultObj != null) {
          defaultStr = defaultObj.toString();
        }
      } catch (Exception e) {
        throw new Error("Unexpected error getting default for " + field, e);
      }

      if (field.getType().isArray()) {
        throw new Error("@Option may not annotate a variable of array type: " + field);
      }

      // Handle lists.  When a list argument is specified multiple times,
      // each argument value is appended to the list.
      Type genType = field.getGenericType();
      if (genType instanceof ParameterizedType) {
        ParameterizedType pt = (ParameterizedType) genType;
        Type rawType = pt.getRawType();
        if (!rawType.equals(List.class)) {
          throw new Error(
              "@Option supports List<...> but no other parameterized type; it does not support type "
                  + pt
                  + " for field "
                  + field);
        }
        if (defaultObj == null) {
          List<Object> newList = new ArrayList<Object>();
          try {
            field.set(obj, newList);
          } catch (Exception e) {
            throw new Error("Unexpected error setting default for " + field, e);
          }
          defaultObj = newList;
        }
        if (((List<?>) defaultObj).isEmpty()) {
          defaultStr = null;
        }
        @SuppressWarnings("unchecked")
        List<Object> defaultObjAsList = (List<Object>) defaultObj;
        this.list = defaultObjAsList;
        // System.out.printf ("list default = %s%n", list);
        Type[] listTypeArgs = pt.getActualTypeArguments();
        this.baseType = (Class<?>) (listTypeArgs.length == 0 ? Object.class : listTypeArgs[0]);

        // System.out.printf ("Param type for %s = %s%n", field, pt);
        // System.out.printf ("raw type = %s, type = %s%n", pt.getRawType(),
        //                   pt.getActualTypeArguments()[0]);
      }

      // Get the short name, type name, and description from the annotation
      ParseResult pr;
      try {
        pr = parseOption(option.value());
      } catch (Throwable e) {
        throw new Error(
            "Error while processing @Option(\"" + option.value() + "\") on '" + field + "'", e);
      }
      shortName = pr.shortName;
      if (pr.typeName != null) {
        typeName = pr.typeName;
      } else {
        typeName = typeShortName(baseType);
      }
      description = pr.description;

      // Get a constructor for non-primitive base types
      if (!baseType.isPrimitive() && !baseType.isEnum()) {
        try {
          if (baseType == Pattern.class) {
            factory = Pattern.class.getMethod("compile", String.class);
          } else { // look for a string constructor
            constructor = baseType.getConstructor(String.class);
          }
        } catch (Exception e) {
          throw new Error(
              "@Option does not support type "
                  + baseType
                  + " for field "
                  + field
                  + " because it does not have a string constructor",
              e);
        }
      }
    }

    /**
     * Return whether or not this option has a required argument.
     *
     * @return whether or not this option has a required argument
     */
    public boolean argumentRequired() {
      Class<?> type = field.getType();
      return (type != Boolean.TYPE) && (type != Boolean.class);
    }

    /**
     * Returns a short synopsis of the option in the form <span style="white-space: nowrap;">{@code
     * -s --long=<type>}</span>.
     */
    public String synopsis() {
      String prefix = useSingleDash ? "-" : "--";
      String name = prefix + longName;
      if (shortName != null) {
        name = String.format("-%s %s", shortName, name);
      }
      name += String.format("=<%s>", typeName);
      if (list != null) {
        name += " [+]";
      }
      return name;
    }

    /**
     * Return a one-line description of the option.
     *
     * @return a one-line description of the option
     */
    @Override
    /*@SideEffectFree*/
    public String toString(/*>>>@GuardSatisfied OptionInfo this*/) {
      String prefix = useSingleDash ? "-" : "--";
      String shortNameStr = "";
      if (shortName != null) {
        shortNameStr = "-" + shortName + " ";
      }
      return String.format("%s%s%s field %s", shortNameStr, prefix, longName, field);
    }

    /**
     * Returns the class that declares this option.
     *
     * @return the class that declares this option
     */
    public Class<?> getDeclaringClass() {
      return field.getDeclaringClass();
    }
  }

  /** Information about an option group. */
  static class OptionGroupInfo {

    /** The name of this option group. */
    String name;

    /**
     * If true, this group of options will not be printed in usage output by default. However, the
     * usage information for this option group can be printed by specifying the group explicitly in
     * the call to {@link #usage}.
     */
    boolean unpublicized;

    /** List of options that belong to this group. */
    List<OptionInfo> optionList;

    OptionGroupInfo(String name, boolean unpublicized) {
      optionList = new ArrayList<OptionInfo>();
      this.name = name;
      this.unpublicized = unpublicized;
    }

    OptionGroupInfo(OptionGroup optionGroup) {
      optionList = new ArrayList<OptionInfo>();
      this.name = optionGroup.value();
      this.unpublicized = optionGroup.unpublicized();
    }

    /**
     * If false, this group of options does not contain any publicized options, so it will not be
     * included in the default usage message.
     */
    boolean anyPublicized() {
      for (OptionInfo oi : optionList) {
        if (!oi.unpublicized) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Prepare for option processing. Creates an object that will set fields in all the given
   * arguments. An argument to this method may be a Class, in which case its static fields are set.
   * The names of all the options (that is, the fields annotated with &#064;{@link Option}) must be
   * unique across all the arguments.
   *
   * @param args the classes whose options to process
   */
  public Options(/*@UnknownInitialization*/ /*@Raw*/ Object... args) {
    this("", args);
  }

  /**
   * Prepare for option processing. Creates an object that will set fields in all the given
   * arguments. An argument to this method may be a Class, in which case it must be fully initalized
   * and its static fields are set. The names of all the options (that is, the fields annotated with
   * &#064;{@link Option}) must be unique across all the arguments.
   *
   * @param usageSynopsis a synopsis of how to call your program
   * @param args the classes whose options to process
   */
  public Options(String usageSynopsis, /*@UnknownInitialization*/ /*@Raw*/ Object... args) {

    if (args.length == 0) {
      throw new Error("Must pass at least one object to Options constructor");
    }

    this.usageSynopsis = usageSynopsis;

    this.hasGroups = false;

    // true once the first @Option annotation is observed, false until then.
    boolean seenFirstOpt = false;

    // Loop through each specified object or class
    for (Object obj : args) {
      boolean isClass = obj instanceof Class<?>;
      String currentGroup = null;

      @SuppressWarnings({
        "rawness", // if isClass is true, obj is a non-null initialized Class
        "initialization" // if isClass is true, obj is a non-null initialized Class
      })
      /*@Initialized*/ /*@NonRaw*/ /*@NonNull*/ Class<?> clazz =
          (isClass ? (/*@Initialized*/ /*@NonRaw*/ /*@NonNull*/ Class<?>) obj : obj.getClass());
      if (mainClass == Void.TYPE) {
        mainClass = clazz;
      }
      Field[] fields = clazz.getDeclaredFields();

      for (Field f : fields) {
        try {
          // Possible exception because "obj" is not yet initialized; catch it and proceed
          @SuppressWarnings("cast")
          Object objNonraw = (/*@Initialized*/ /*@NonRaw*/ Object) obj;
          if (debugEnabled) {
            System.err.printf("Considering field %s of object %s%n", f, objNonraw);
          }
        } catch (Throwable t) {
          if (debugEnabled) {
            System.err.printf("Considering field %s of object of type %s%n", f, obj.getClass());
          }
        }
        try {
          if (debugEnabled) {
            System.err.printf(
                "  with annotations %s%n", Arrays.toString(f.getDeclaredAnnotations()));
          }
        } catch (java.lang.ArrayStoreException e) {
          if (e.getMessage() != null
              && Objects.equals(
                  e.getMessage(), "sun.reflect.annotation.TypeNotPresentExceptionProxy")) {
            if (debugEnabled) {
              System.err.printf("  with TypeNotPresentExceptionProxy while getting annotations%n");
            }
          } else {
            throw e;
          }
        }
        Option option = safeGetAnnotation(f, Option.class);
        if (option == null) {
          continue;
        }

        boolean unpublicized = safeGetAnnotation(f, Unpublicized.class) != null;

        if (isClass && !Modifier.isStatic(f.getModifiers())) {
          throw new Error("non-static option " + f + " in class " + obj);
        }

        @SuppressWarnings(
            "initialization") // new C(underInit) yields @UnderInitialization; @Initialized is safe
        /*@Initialized*/ OptionInfo oi =
            new OptionInfo(f, option, isClass ? null : obj, unpublicized);
        options.add(oi);

        OptionGroup optionGroup = safeGetAnnotation(f, OptionGroup.class);

        if (!seenFirstOpt) {
          seenFirstOpt = true;
          // This is the first @Option annotation encountered so we can decide
          // now if the user intends to use option groups.
          if (optionGroup != null) {
            hasGroups = true;
          } else {
            continue;
          }
        }

        if (!hasGroups) {
          if (optionGroup != null) {
            // The user included an @OptionGroup annotation in their code
            // without including an @OptionGroup annotation on the first
            // @Option-annotated field, hence violating the requirement.

            // NOTE: changing this error string requires changes to TestPlume
            throw new Error(
                "missing @OptionGroup annotation on the first "
                    + "@Option-annotated field of class "
                    + mainClass);
          } else {
            continue;
          }
        }

        // hasGroups is true at this point.  The variable currentGroup is set
        // to null at the start of every iteration through 'args'.  This is so
        // we can check that the first @Option-annotated field of every
        // class/object in 'args' has an @OptionGroup annotation when hasGroups
        // is true, as required.
        if (currentGroup == null && optionGroup == null) {
          // NOTE: changing this error string requires changes to TestPlume
          throw new Error("missing @OptionGroup annotation in field " + f + " of class " + obj);
        } else if (optionGroup != null) {
          String name = optionGroup.value();
          if (groupMap.containsKey(name)) {
            throw new Error("option group " + name + " declared twice");
          }
          OptionGroupInfo gi = new OptionGroupInfo(optionGroup);
          groupMap.put(name, gi);
          currentGroup = name;
        } // currentGroup is non-null at this point
        @SuppressWarnings("nullness") // map key
        /*@NonNull*/ OptionGroupInfo ogi = groupMap.get(currentGroup);
        ogi.optionList.add(oi);
      } // loop through fields
    } // loop through args

    String prefix = useSingleDash ? "-" : "--";

    // Add each option to the option name map
    for (OptionInfo oi : options) {
      if (oi.shortName != null) {
        if (nameMap.containsKey("-" + oi.shortName)) {
          throw new Error("short name " + oi + " appears twice");
        }
        nameMap.put("-" + oi.shortName, oi);
      }
      if (nameMap.containsKey(prefix + oi.longName)) {
        throw new Error("long name " + oi + " appears twice");
      }
      nameMap.put(prefix + oi.longName, oi);
      if (useDashes && oi.longName.contains("-")) {
        nameMap.put(prefix + oi.longName.replace('-', '_'), oi);
      }
      if (oi.aliases.length > 0) {
        for (String alias : oi.aliases) {
          if (nameMap.containsKey(alias)) {
            throw new Error("alias " + oi + " appears twice");
          }
          nameMap.put(alias, oi);
        }
      }
    }
  }

  /**
   * Like getAnnotation, but returns null (and prints a warning) rather than throwing an exception.
   */
  private static <T extends Annotation> /*@Nullable*/ T safeGetAnnotation(
      Field f, Class<T> annotationClass) {
    /*@Nullable*/ T annotation;
    try {
      @SuppressWarnings("cast") // cast is redundant (except for type annotations)
      /*@Nullable*/ T cast = f.getAnnotation((Class</*@NonNull*/ T>) annotationClass);
      annotation = cast;
    } catch (Exception e) {
      // Can get
      //   java.lang.ArrayStoreException: sun.reflect.annotation.TypeNotPresentExceptionProxy
      // when an annotation is not present at run time (example: @NonNull)
      System.out.printf(
          "Exception in call to f.getAnnotation(%s)%n  for f=%s%n  %s%nClasspath =%n",
          annotationClass, f, e.getMessage());
      // e.printStackTrace();
      printClassPath();
      annotation = null;
    }

    return annotation;
  }

  /** Print the classpath. */
  static void printClassPath() {
    URLClassLoader sysLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
    if (sysLoader == null) {
      System.out.println(
          "No system class loader. (Maybe means bootstrap class loader is being used?)");
    } else {
      System.out.println("Classpath:");
      for (URL url : sysLoader.getURLs()) {
        System.out.println(url.getFile());
      }
    }
  }

  /**
   * If true, Options will parse arguments even after a non-option command-line argument. Setting
   * this to true is useful to permit users to write options at the end of a command line. Setting
   * this to false is useful to avoid processing arguments that are actually options/arguments for
   * another program that this one will invoke. The default is true.
   *
   * @param val whether to parse arguments after a non-option command-line argument
   */
  public void setParseAfterArg(boolean val) {
    parseAfterArg = val;
  }

  /**
   * If true, long options (those derived from field names) are expected with a single dash prefix
   * as in <span style="white-space: nowrap;">{@code -long-option}</span> rather than <span
   * style="white-space: nowrap;">{@code --long-option}</span>. The default is false and long
   * options will be parsed with a double dash prefix as in <span style="white-space:
   * nowrap;">{@code --longOption}</span>.
   *
   * @param val whether to parse long options with a single dash, as in <span style="white-space:
   *     nowrap;">{@code -longOption}</span>
   */
  public void setUseSingleDash(boolean val) {
    useSingleDash = val;
  }

  /**
   * Sets option variables from the given command line.
   *
   * @param args the commandline to be parsed
   * @return all non-option arguments
   * @throws ArgException if the command line contains unknown option or misused options
   */
  @SuppressWarnings("index") // https://github.com/kelloggm/checker-framework/issues/169
  public String[] parse(String[] args) throws ArgException {

    List<String> nonOptions = new ArrayList<String>();
    // If true, then "--" has been seen and any argument starting with "-"
    // is processed as an ordinary argument, not as an option.
    boolean ignoreOptions = false;

    // Loop through each argument
    String tail = "";
    String arg;
    for (int ii = 0; ii < args.length; ) {
      // If there was a ',' separator in previous arg, use the tail as
      // current arg; otherwise, fetch the next arg from args list.
      if (tail.length() > 0) {
        arg = tail;
        tail = "";
      } else {
        arg = args[ii];
      }

      if (arg.equals("--")) {
        ignoreOptions = true;
      } else if ((arg.startsWith("--") || arg.startsWith("-")) && !ignoreOptions) {
        String argName;
        String argValue;

        // Allow ',' as an argument separator to get around
        // some command line quoting problems.  (markro)
        int splitPos = arg.indexOf(",-");
        if (splitPos == 0) {
          // Just discard the ',' if ",-" occurs at begining of string
          arg = arg.substring(1);
          splitPos = arg.indexOf(",-");
        }
        if (splitPos > 0) {
          tail = arg.substring(splitPos + 1);
          arg = arg.substring(0, splitPos);
        }

        int eqPos = arg.indexOf('=');
        if (eqPos == -1) {
          argName = arg;
          argValue = null;
        } else {
          argName = arg.substring(0, eqPos);
          argValue = arg.substring(eqPos + 1);
        }
        OptionInfo oi = nameMap.get(argName);
        if (oi == null) {
          StringBuilder msg = new StringBuilder();
          msg.append(String.format("unknown option name '%s' in arg '%s'", argName, arg));
          if (false) { // for debugging
            msg.append("; known options:");
            for (String optionName : sortedKeySet(nameMap)) {
              msg.append(" ");
              msg.append(optionName);
            }
          }
          throw new ArgException(msg.toString());
        }
        if (oi.argumentRequired() && (argValue == null)) {
          ii++;
          if (ii >= args.length) {
            throw new ArgException("option %s requires an argument", arg);
          }
          argValue = args[ii];
        }
        // System.out.printf ("argName = '%s', argValue='%s'%n", argName,
        //                    argValue);
        setArg(oi, argName, argValue);
      } else { // not an option
        if (!parseAfterArg) {
          ignoreOptions = true;
        }
        nonOptions.add(arg);
      }

      // If no ',' tail, advance to next args option
      if (tail.length() == 0) {
        ii++;
      }
    }
    String[] result = nonOptions.toArray(new String[nonOptions.size()]);
    return result;
  }

  /**
   * Splits the argument string into an array of tokens (command-line flags and arguments),
   * respecting single and double quotes.
   *
   * <p>This method is only appropriate when the {@code String[]} version of the arguments is not
   * available &mdash; for example, for the {@code premain} method of a Java agent.
   *
   * @param args the command line to be tokenized
   * @return a string array analogous to the argument to {@code main}.
   */
  // TODO: should this throw some exceptions?
  public static String[] tokenize(String args) {

    // Split the args string on whitespace boundaries accounting for quoted
    // strings.
    args = args.trim();
    List<String> argList = new ArrayList<String>();
    String arg = "";
    char activeQuote = 0;
    for (int ii = 0; ii < args.length(); ii++) {
      char ch = args.charAt(ii);
      if ((ch == '\'') || (ch == '"')) {
        arg += ch;
        ii++;
        while ((ii < args.length()) && (args.charAt(ii) != ch)) {
          arg += args.charAt(ii++);
        }
        arg += ch;
      } else if (Character.isWhitespace(ch)) {
        // System.out.printf ("adding argument '%s'%n", arg);
        argList.add(arg);
        arg = "";
        while ((ii < args.length()) && Character.isWhitespace(args.charAt(ii))) {
          ii++;
        }
        if (ii < args.length()) {
          // Encountered a non-whitespace character.
          // Back up to process it on the next loop iteration.
          ii--;
        }
      } else { // must be part of current argument
        arg += ch;
      }
    }
    if (!arg.equals("")) {
      argList.add(arg);
    }

    String[] argsArray = argList.toArray(new String[argList.size()]);
    return argsArray;
  }

  /**
   * Sets option variables from the given command line; if any command-line argument is illegal,
   * prints the given message and terminates the program.
   *
   * <p>If an error occurs, prints the exception's message, prints the given message, and then
   * terminates the program. The program is terminated rather than throwing an error to create
   * cleaner output.
   *
   * @param message a message to print, such as "Pass --help for a list of all command-line
   *     arguments."
   * @param args the command line to parse
   * @return all non-option arguments
   * @see #parse(String[])
   */
  public String[] parse(String message, String[] args) {

    String[] nonOptions = null;

    try {
      nonOptions = parse(args);
    } catch (ArgException ae) {
      String exceptionMessage = ae.getMessage();
      if (exceptionMessage != null) {
        System.out.println(exceptionMessage);
      }
      System.out.println(message);
      System.exit(-1);
      // throw new Error ("message error: ", ae);
    }
    return nonOptions;
  }

  /**
   * Sets option variables from the given command line; if any command-line argument is illegal,
   * prints the usage message and terminates the program.
   *
   * <p>If an error occurs and {@code showUsageOnError} is true, prints the exception's message,
   * prints usage inoframtion, and then terminates the program. The program is terminated rather
   * than throwing an error to create cleaner output.
   *
   * @param showUsageOnError if a command-line argument is incorrect, print a usage message
   * @param args the command line to parse
   * @return all non-option arguments
   * @see #parse(String[])
   */
  public String[] parse(boolean showUsageOnError, String[] args) {

    String[] nonOptions = null;

    try {
      nonOptions = parse(args);
    } catch (ArgException ae) {
      String exceptionMessage = ae.getMessage();
      if (exceptionMessage != null) {
        System.out.println(exceptionMessage);
      }
      printUsage();
      System.exit(-1);
      // throw new Error ("usage error: ", ae);
    }
    return nonOptions;
  }

  /**
   * True if some documented option accepts a list as a parameter. Used and set by {code usage()}
   * methods and their callees.
   */
  private boolean hasListOption = false;

  /**
   * Prints usage information to the given PrintStream. Uses the usage synopsis passed into the
   * constructor, if any.
   *
   * @param ps where to print usage information
   */
  public void printUsage(PrintStream ps) {
    hasListOption = false;
    if (usageSynopsis != null) {
      ps.printf("Usage: %s%n", usageSynopsis);
    }
    ps.println(usage());
    if (hasListOption) {
      ps.println();
      ps.println(LIST_HELP);
    }
  }

  /** Prints, to standard output, usage information. */
  public void printUsage() {
    printUsage(System.out);
  }

  /**
   * Returns a usage message for command-line options.
   *
   * @return the command-line usage message
   * @param groupNames the list of option groups to include in the usage message. If empty and
   *     option groups are being used, will return usage for all option groups that are not
   *     unpublicized. If empty and option groups are not being used, will return usage for all
   *     options that are not unpublicized.
   */
  public String usage(String... groupNames) {
    return usage(false, groupNames);
  }

  /**
   * Returns a usage message for command-line options.
   *
   * @return the command-line usage message
   * @param showUnpublicized if true, treat all unpublicized options and option groups as publicized
   * @param groupNames the list of option groups to include in the usage message. If empty and
   *     option groups are being used, will return usage for all option groups that are not
   *     unpublicized. If empty and option groups are not being used, will return usage for all
   *     options that are not unpublicized.
   */
  public String usage(boolean showUnpublicized, String... groupNames) {
    if (!hasGroups) {
      if (groupNames.length > 0) {
        throw new IllegalArgumentException(
            "This instance of Options does not have any option groups defined");
      }
      return formatOptions(options, maxOptionLength(options, showUnpublicized), showUnpublicized);
    }

    List<OptionGroupInfo> groups = new ArrayList<OptionGroupInfo>();
    if (groupNames.length > 0) {
      for (String groupName : groupNames) {
        if (!groupMap.containsKey(groupName)) {
          throw new IllegalArgumentException("invalid option group: " + groupName);
        }
        OptionGroupInfo gi = groupMap.get(groupName);
        if (!showUnpublicized && !gi.anyPublicized()) {
          throw new IllegalArgumentException(
              "group does not contain any publicized options: " + groupName);
        } else {
          groups.add(groupMap.get(groupName));
        }
      }
    } else { // return usage for all groups that are not unpublicized
      for (OptionGroupInfo gi : groupMap.values()) {
        if ((gi.unpublicized || !gi.anyPublicized()) && !showUnpublicized) {
          continue;
        }
        groups.add(gi);
      }
    }

    List<Integer> lengths = new ArrayList<Integer>();
    for (OptionGroupInfo gi : groups) {
      lengths.add(maxOptionLength(gi.optionList, showUnpublicized));
    }
    int maxLength = Collections.max(lengths);

    StringBuilderDelimited buf = new StringBuilderDelimited(lineSeparator);
    for (OptionGroupInfo gi : groups) {
      buf.add(String.format("%n%s:", gi.name));
      buf.add(formatOptions(gi.optionList, maxLength, showUnpublicized));
    }

    return buf.toString();
  }

  /**
   * Format a list of options for use in generating usage messages. Also sets {@link #hasListOption}
   * if any option has list type.
   */
  private String formatOptions(List<OptionInfo> optList, int maxLength, boolean showUnpublicized) {
    StringBuilderDelimited buf = new StringBuilderDelimited(lineSeparator);
    for (OptionInfo oi : optList) {
      if (oi.unpublicized && !showUnpublicized) {
        continue;
      }
      String defaultStr = "";
      if (oi.defaultStr != null) {
        defaultStr = String.format(" [default %s]", oi.defaultStr);
      }

      @SuppressWarnings("formatter") // format string computed from maxLength argument
      String use =
          String.format("  %-" + maxLength + "s - %s%s", oi.synopsis(), oi.description, defaultStr);
      buf.add(use);

      if (oi.list != null) {
        hasListOption = true;
      }
    }
    return buf.toString();
  }

  /**
   * Return the length of the longest synopsis message in a list of options. Useful for aligning
   * options in usage strings.
   *
   * @return the length of the longest synopsis message in a list of options
   */
  private int maxOptionLength(List<OptionInfo> optList, boolean showUnpublicized) {
    int maxLength = 0;
    for (OptionInfo oi : optList) {
      if (oi.unpublicized && !showUnpublicized) {
        continue;
      }
      int len = oi.synopsis().length();
      if (len > maxLength) {
        maxLength = len;
      }
    }
    return maxLength;
  }

  // Package-private accessors/utility methods that are needed by the OptionsDoclet class to
  // generate HTML documentation.

  /*@Pure*/
  boolean hasGroups() {
    return hasGroups;
  }

  /*@Pure*/
  boolean getUseSingleDash() {
    return useSingleDash;
  }

  List<OptionInfo> getOptions() {
    return options;
  }

  Collection<OptionGroupInfo> getOptionGroups() {
    return groupMap.values();
  }

  /**
   * Set the specified option to the value specified in argValue.
   *
   * @param oi the option to set
   * @param argName the name of the argument as passed on the command line; used only for debugging
   * @param argValue a string representation of the value
   * @throws ArgException if there are any errors
   */
  private void setArg(OptionInfo oi, String argName, /*@Nullable*/ String argValue)
      throws ArgException {

    Field f = oi.field;
    Class<?> type = oi.baseType;

    // Keep track of all of the options specified
    if (optionsString.length() > 0) {
      optionsString += " ";
    }
    optionsString += argName;
    if (argValue != null) {
      if (!argValue.contains(" ")) {
        optionsString += "=" + argValue;
      } else if (!argValue.contains("'")) {
        optionsString += "='" + argValue + "'";
      } else if (!argValue.contains("\"")) {
        optionsString += "=\"" + argValue + "\"";
      } else {
        throw new ArgException("Can't quote for internal debugging: " + argValue);
      }
    }
    // Argument values are required for everything but booleans
    if (argValue == null) {
      if ((type != Boolean.TYPE) || (type != Boolean.class)) {
        argValue = "true";
      } else {
        throw new ArgException("Value required for option " + argName);
      }
    }

    try {
      if (type.isPrimitive()) {
        if (type == Boolean.TYPE) {
          boolean val;
          String argValueLowercase = argValue.toLowerCase();
          if (argValueLowercase.equals("true") || (argValueLowercase.equals("t"))) {
            val = true;
          } else if (argValueLowercase.equals("false") || argValueLowercase.equals("f")) {
            val = false;
          } else {
            throw new ArgException(
                "Value \"%s\" for argument %s is not a boolean", argValue, argName);
          }
          argValue = (val) ? "true" : "false";
          // System.out.printf ("Setting %s to %s%n", argName, val);
          f.setBoolean(oi.obj, val);
        } else if (type == Byte.TYPE) {
          byte val;
          try {
            val = Byte.decode(argValue);
          } catch (Exception e) {
            throw new ArgException("Value \"%s\" for argument %s is not a byte", argValue, argName);
          }
          f.setByte(oi.obj, val);
        } else if (type == Character.TYPE) {
          if (argValue.length() != 1) {
            throw new ArgException(
                "Value \"%s\" for argument %s is not a single character", argValue, argName);
          }
          char val = argValue.charAt(0);
          f.setChar(oi.obj, val);
        } else if (type == Short.TYPE) {
          short val;
          try {
            val = Short.decode(argValue);
          } catch (Exception e) {
            throw new ArgException(
                "Value \"%s\" for argument %s is not a short integer", argValue, argName);
          }
          f.setShort(oi.obj, val);
        } else if (type == Integer.TYPE) {
          int val;
          try {
            val = Integer.decode(argValue);
          } catch (Exception e) {
            throw new ArgException(
                "Value \"%s\" for argument %s is not an integer", argValue, argName);
          }
          f.setInt(oi.obj, val);
        } else if (type == Long.TYPE) {
          long val;
          try {
            val = Long.decode(argValue);
          } catch (Exception e) {
            throw new ArgException(
                "Value \"%s\" for argument %s is not a long integer", argValue, argName);
          }
          f.setLong(oi.obj, val);
        } else if (type == Float.TYPE) {
          Float val;
          try {
            val = Float.valueOf(argValue);
          } catch (Exception e) {
            throw new ArgException(
                "Value \"%s\" for argument %s is not a float", argValue, argName);
          }
          f.setFloat(oi.obj, val);
        } else if (type == Double.TYPE) {
          Double val;
          try {
            val = Double.valueOf(argValue);
          } catch (Exception e) {
            throw new ArgException(
                "Value \"%s\" for argument %s is not a double", argValue, argName);
          }
          f.setDouble(oi.obj, val);
        } else { // unexpected type
          throw new Error("Unexpected type " + type);
        }
      } else { // reference type

        // If the argument is a list, add repeated arguments or multiple
        // blank separated arguments to the list, otherwise just set the
        // argument value.
        if (oi.list != null) {
          if (spaceSeparatedLists) {
            String[] aarr = argValue.split("  *");
            for (String aval : aarr) {
              Object val = getRefArg(oi, argName, aval);
              oi.list.add(val); // uncheck cast
            }
          } else {
            Object val = getRefArg(oi, argName, argValue);
            oi.list.add(val);
          }
        } else {
          Object val = getRefArg(oi, argName, argValue);
          f.set(oi.obj, val);
        }
      }
    } catch (ArgException ae) {
      throw ae;
    } catch (Exception e) {
      throw new Error("Unexpected error ", e);
    }
  }

  /**
   * Create an instance of the correct type by passing the argument value string to the constructor.
   * The only expected error is some sort of parse error from the constructor.
   */
  private /*@NonNull*/ Object getRefArg(OptionInfo oi, String argName, String argValue)
      throws ArgException {

    Object val;
    try {
      if (oi.constructor != null) {
        val = oi.constructor.newInstance(new Object[] {argValue});
      } else if (oi.baseType.isEnum()) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object tmpVal = getEnumValue((Class<Enum>) oi.baseType, argValue);
        val = tmpVal;
      } else {
        if (oi.factory == null) {
          throw new Error("No constructor or factory for argument " + argName);
        }
        @SuppressWarnings("nullness") // static method, so null first arg is OK: oi.factory
        /*@NonNull*/ Object tmpVal = oi.factory.invoke(null, argValue);
        val = tmpVal;
      }
    } catch (Exception e) {
      throw new ArgException("Invalid argument (%s) for argument %s", argValue, argName);
    }

    return val;
  }

  /**
   * Behaves like {@link java.lang.Enum#valueOf}, except that {@code name} is case-insensitive and
   * hyphen-insensitive (hyphens can be used in place of underscores). This allows for greater
   * flexibility when specifying enum types as command-line arguments.
   *
   * @param <T> the enum type
   */
  private <T extends Enum<T>> T getEnumValue(Class<T> enumType, String name) {
    T[] constants = enumType.getEnumConstants();
    if (constants == null) {
      throw new IllegalArgumentException(enumType.getName() + " is not an enum type");
    }
    for (T constant : constants) {
      if (constant.name().equalsIgnoreCase(name.replace('-', '_'))) {
        return constant;
      }
    }
    // same error that's thrown by Enum.valueOf()
    throw new IllegalArgumentException(
        "No enum constant " + enumType.getCanonicalName() + "." + name);
  }

  /**
   * Return a short name for the specified type for use in messages.
   *
   * @return a short name for the specified type for use in messages
   */
  private static String typeShortName(Class<?> type) {

    if (type.isPrimitive()) {
      return type.getName();
    } else if (type == File.class) {
      return "filename";
    } else if (type == Pattern.class) {
      return "regex";
    } else if (type.isEnum()) {
      return "enum";
    } else {
      return type.getSimpleName().toLowerCase();
    }
  }

  /**
   * Returns a string containing all of the options that were set and their arguments. This is
   * essentially the contents of args[] with all non-options removed. It can be used for calling a
   * subprocess or for debugging.
   *
   * @return options, similarly to supplied on the command line
   * @see #settings()
   */
  public String getOptionsString() {
    return optionsString;
  }

  // TODO: document what this is good for.  Debugging?  Invoking other programs?
  /**
   * Returns a string containing the current setting for each option, in command-line format that
   * can be parsed by Options. Contains every known option even if the option was not specified on
   * the command line. Never contains duplicates.
   *
   * @return a command line that can be tokenized with {@link #tokenize}, containing the current
   *     setting for each option
   */
  public String settings() {
    return settings(false);
  }

  // TODO: document what this is good for.  Debugging?  Invoking other programs?
  /**
   * Returns a string containing the current setting for each option, in command-line format that
   * can be parsed by Options. Contains every known option even if the option was not specified on
   * the command line. Never contains duplicates.
   *
   * @param showUnpublicized if true, treat all unpublicized options and option groups as publicized
   * @return a command line that can be tokenized with {@link #tokenize}, containing the current
   *     setting for each option
   */
  public String settings(boolean showUnpublicized) {
    StringBuilderDelimited out = new StringBuilderDelimited(lineSeparator);

    // Determine the length of the longest name
    int maxLength = maxOptionLength(options, showUnpublicized);

    // Create the settings string
    for (OptionInfo oi : options) {
      @SuppressWarnings("formatter") // format string computed from maxLength
      String use = String.format("%-" + maxLength + "s = ", oi.longName);
      try {
        use += oi.field.get(oi.obj);
      } catch (Exception e) {
        throw new Error("unexpected exception reading field " + oi.field, e);
      }
      out.add(use);
    }

    return out.toString();
  }

  /**
   * Return a description of all of the known options. Each option is described on its own line in
   * the output.
   *
   * @return a description of all of the known options
   */
  @Override
  @SuppressWarnings({
    "purity",
    "method.guarantee.violated"
  }) // side effect to local state (string creation)
  /*@SideEffectFree*/
  public String toString(/*>>>@GuardSatisfied Options this*/) {
    StringBuilderDelimited out = new StringBuilderDelimited(lineSeparator);

    for (OptionInfo oi : options) {
      out.add(oi.toString());
    }

    return out.toString();
  }

  /**
   * Indicates an exception encountered during argument processing. Contains no information other
   * than the message string.
   */
  public static class ArgException extends Exception {
    static final long serialVersionUID = 20051223L;

    public ArgException(String s) {
      super(s);
    }

    /*@FormatMethod*/
    public ArgException(String format, /*@Nullable*/ Object... args) {
      super(String.format(format, args));
    }
  }

  private static class ParseResult {
    /*@Nullable*/ String shortName;
    /*@Nullable*/ String typeName;
    String description;

    ParseResult(/*@Nullable*/ String shortName, /*@Nullable*/ String typeName, String description) {
      this.shortName = shortName;
      this.typeName = typeName;
      this.description = description;
    }
  }

  /**
   * Parse an option value and return its three components (shortName, typeName, and description).
   * The shortName and typeName are null if they are not specified in the string.
   */
  private static ParseResult parseOption(String val) {

    // Get the short name, long name, and description
    String shortName;
    String typeName;
    /*@NonNull*/ String description;

    // Get the short name (if any)
    if (val.startsWith("-")) {
      if (val.length() < 4 || !val.substring(2, 3).equals(" ")) {
        throw new Error(
            "Malformed @Option argument \""
                + val
                + "\".  An argument that starts with '-' should contain a short name, a space, and a description.");
      }
      shortName = val.substring(1, 2);
      description = val.substring(3);
    } else {
      shortName = null;
      description = val;
    }

    // Get the type name (if any)
    if (description.startsWith("<")) {
      typeName = description.substring(1).replaceFirst(">.*", "");
      description = description.replaceFirst("<.*> ", "");
    } else {
      typeName = null;
    }

    // Return the result
    return new ParseResult(shortName, typeName, description);
  }

  //   /**
  //    * Test class with some defined arguments.
  //    */
  //   private static class Test {
  //
  //     @Option("generic") List<Pattern> lp = new ArrayList<Pattern>();
  //     @Option("-a <filename> argument 1") String arg1 = "/tmp/foobar";
  //     @Option("argument 2") String arg2;
  //     @Option("-d double value") double temperature;
  //     @Option("-f the input file") File input_file;
  //   }
  //
  //   /**
  //    * Simple example
  //    */
  //   private static void main (String[] args) throws ArgException {
  //
  //     Options options = new Options("test", new Test());
  //     System.out.printf("Options:%n%s", options);
  //     options.parse(true, args);
  //     System.out.printf("Results:%n%s", options.settings());
  //   }

  /**
   * Returns a sorted version of m.keySet().
   *
   * @param <K> type of the map keys
   * @param <V> type of the map values
   * @param m a map whose keyset will be sorted
   * @return a sorted version of m.keySet()
   */
  private static <K extends Comparable<? super K>, V> Collection</*@KeyFor("#1")*/ K> sortedKeySet(
      Map<K, V> m) {
    ArrayList</*@KeyFor("#1")*/ K> theKeys = new ArrayList</*@KeyFor("#1")*/ K>(m.keySet());
    Collections.sort(theKeys);
    return theKeys;
  }
}
