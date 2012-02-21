# class-finder

A utility to find classes, designed for use at the REPL

## Motivation

When writing code in an IDE I can simply let the IDE figure out which
package it needs to import the class from. I miss that experience when
writing code at the REPL. class-finder is the solution.

## Usage

Leiningen

```
:dependencies [[com.offbytwo.class-finder/class-finder "1.0.0"]]
```

Maven

```
<dependency>
  <groupId>org.offbytwo.class-finder</groupId>
  <artifactId>class-finder</groupId>
  <version>1.0.0</version>
</dependency>
```

Require class-finder

```
(require '[class-finder.core :as cf])
```

Find all classes called Pattern

```
(cf/find-classes "Pattern")
```

Find all classes that match a pattern

```
(cf/find-classes "*List")
```

Restrict search to a specific package

```
(cf/find-classes "*List" "java.util")
```

## License

Copyright (C) 2012 Cosmin Stejerean

Distributed under the Eclipse Public License, the same as Clojure.
