package com.talkingjack.generation.typescript;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention( RetentionPolicy.SOURCE )
@Documented
//@Target( ElementType.) I think not needed, default is applies to class?
@Inherited //needed?

public @interface GenerateTypescript {

}
