# Generate Java code to wrap SPARQL endpoints.

SPARQL is a lovely query language, but not everyone is comfortable with it. 
Many java programmers would like to just access the data without needing to understand the details of one more query language.

This project is a code generator *in progress* that aims to use the given/descovered schema of data available in a SPARQL endpoint,
and generate matching documented java code to access such data.

## STATUS

Extremely early code. Still a lot to do, but one can test it out now as we have valid java code that runs the right kind of SPARQL queries.

## GOAL

Imagine a small team, presenting a small open research dataset to the world. They might have some capabilities in their team to make 
a website, a rest API in one language. But certainly not all the languages. The code in this repository shows how people could access
this valuable data without learning SPARQL, in this case the people would be in the java community. However, the ideas here are an example
for other ecosystems.

## USE

First make the program (there are no releases yet)

```
git clone https://github.com/TRIPLE-CHIST-ERA/VoIDToJava.git
cd VoIDToJava
mvn package
java -jar target/VoIDToJava.jar ${inputVoidFile} ${outputJavaCodeDirectory}
```

The `${inputVoidFile}` can be generated e.g. with the [`void-generator`](https://github.com/JervenBolleman/void-generator).

## BACKGROUND

[See this presentation](https://docs.google.com/presentation/d/17w6wOagyE_bFuRr5zL5Ru66IBEmxeZBVKhSpZUQ0-qM/edit#slide=id.g337e9433fa7_0_34) for some related background,

and http://www.w3.org/TR/void/ to learn more about the VoID Vocabulary which this project is based on.
