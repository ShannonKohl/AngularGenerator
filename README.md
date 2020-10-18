# AngularGenerator

This project contains a simple Java Annotation processor that will create a Typescript class to match any Java pojo annotated with the @GenerateTypescript tag.

Warning: this is very much unfinished work, so if you use it, expect to have to do some extra steps. It will also write a bunch of debug information during your build.

To use it:
1. annotate your Java Pojos with @GenerateTypescript. You will need to include this project as a dependency.
2. it will generate a completely new set of Typescrtipt classes every time, located at the path ${user.dir}/target/generated-sources/pojos. From there you should copy them into your Angular project as desired.


If you're interested in contributing or find issues with this, feel free to contact us.