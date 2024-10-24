# Generate Java code to wrap SPARQL endpoints.

SPARQL is a lovely query language, but not everyone is comfortable with it. 
Many java programmers would like to just access the data without needing to understand the details of one more query language.

This project is a code generator *in progress* that aims to use the given/descovered schema of data available in a SPARQL endpoint,
and generate matching documented java code to access such data.

# STATUS

Extremely early and non functional code.

# Use

First make the program (there are no releases yet)

```
git clone https://github.com/TRIPLE-CHIST-ERA/VoIDToJava.git
cd VoIDToJava
mvn package
java -jar target/VoIDToJava.jar ${inputVoidFile} ${outputJavaCodeDirectory}
```
