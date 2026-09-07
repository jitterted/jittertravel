package dev.ted.jittertravel.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This date or time field may be left blank. Read by {@link RequiredEntryAdvice}, which requires
 * every other one.
 *
 * <p>The default is the safe direction. A new field that ought to be optional and forgets this
 * annotation makes the form say "Required" — visible, and fixed in one line. A new field that ought
 * to be required and had to remember an annotation would bind as null and reach the write path,
 * which is the 500 this whole arrangement exists to remove.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface OptionalEntry {
}
