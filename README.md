# JIT Tree

This tool will help you to analyze the output of the [JVM](http://www.oracle.com/technetwork/java/javase/overview/index.html)'s [-XX:+LogCompilation](https://wikis.oracle.com/display/HotSpotInternals/LogCompilation+overview) option.

To enable this option you also need to pass: ```-XX:+UnlockDiagnosticVMOptions``` to the program under JIT logging.

Optionally you can specify the name of the output file with ```-XX:LogFile=FILENAME```, the default is hotspot.log.

The parsing of the output is done with OpenJDK's [LogCompilation](http://hg.openjdk.java.net/jdk7u/jdk7u-dev/hotspot/file/tip/src/share/tools/LogCompilation/) tool with small modifications.

### Build
```mvn clean package```

### Run
Double-click on ```target/jittree.jar```

or

```java -jar target/jittree.jar <logfile>```


### Requirements
I have only tested it with the output of JDK version 1.7.0_07.

### License
JIT Tree is distributed under the [MIT License](http://opensource.org/licenses/MIT)

LogCompilation is part of the OpenJDK and distributed under [GNU GPLv2](http://opensource.org/licenses/gpl-2.0.php)
