package swiss.sib.swissprot.chistera.triples;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleNamespace;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.AFN;
import org.eclipse.rdf4j.model.vocabulary.APF;
import org.eclipse.rdf4j.model.vocabulary.CONFIG;
import org.eclipse.rdf4j.model.vocabulary.DASH;
import org.eclipse.rdf4j.model.vocabulary.DC;
import org.eclipse.rdf4j.model.vocabulary.DCTERMS;
import org.eclipse.rdf4j.model.vocabulary.DOAP;
import org.eclipse.rdf4j.model.vocabulary.EARL;
import org.eclipse.rdf4j.model.vocabulary.FN;
import org.eclipse.rdf4j.model.vocabulary.FOAF;
import org.eclipse.rdf4j.model.vocabulary.GEO;
import org.eclipse.rdf4j.model.vocabulary.GEOF;
import org.eclipse.rdf4j.model.vocabulary.HYDRA;
import org.eclipse.rdf4j.model.vocabulary.LDP;
import org.eclipse.rdf4j.model.vocabulary.LIST;
import org.eclipse.rdf4j.model.vocabulary.LOCN;
import org.eclipse.rdf4j.model.vocabulary.ODRL2;
import org.eclipse.rdf4j.model.vocabulary.ORG;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.PROV;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.model.vocabulary.ROV;
import org.eclipse.rdf4j.model.vocabulary.RSX;
import org.eclipse.rdf4j.model.vocabulary.SD;
import org.eclipse.rdf4j.model.vocabulary.SESAME;
import org.eclipse.rdf4j.model.vocabulary.SESAMEQNAME;
import org.eclipse.rdf4j.model.vocabulary.SHACL;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.model.vocabulary.SKOSXL;
import org.eclipse.rdf4j.model.vocabulary.SP;
import org.eclipse.rdf4j.model.vocabulary.SPIF;
import org.eclipse.rdf4j.model.vocabulary.SPIN;
import org.eclipse.rdf4j.model.vocabulary.SPINX;
import org.eclipse.rdf4j.model.vocabulary.SPL;
import org.eclipse.rdf4j.model.vocabulary.TIME;
import org.eclipse.rdf4j.model.vocabulary.VANN;
import org.eclipse.rdf4j.model.vocabulary.VCARD4;
import org.eclipse.rdf4j.model.vocabulary.VOID;
import org.eclipse.rdf4j.model.vocabulary.WGS84;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryResult;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.JavaFile.Builder;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.TypeVariableName;

public class GenerateJavaFromVoID {

	// TODO: move to an external file and read it in.
	private static final String POM_TEMPLATE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
			  <modelVersion>4.0.0</modelVersion>
			  <groupId>${groupId}</groupId>
			  <artifactId>${artifactId}</artifactId>
			  <version>${version}</version>
			  <properties>
			    <rdf4j.version>5.0.2</rdf4j.version>
			    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
			  </properties>
			  <dependencyManagement>
			    <dependencies>
			      <dependency>
			        <groupId>org.eclipse.rdf4j</groupId>
			        <artifactId>rdf4j-bom</artifactId>
			        <version>${rdf4j.version}</version>
			        <type>pom</type>
			        <scope>import</scope>
			      </dependency>
			    </dependencies>
			  </dependencyManagement>
			  <dependencies>
			    <dependency>
			      <groupId>org.slf4j</groupId>
			      <artifactId>slf4j-api</artifactId>
			    </dependency>
			    <dependency>
			      <groupId>org.eclipse.rdf4j</groupId>
			      <artifactId>rdf4j-sail-base</artifactId>
			    </dependency>
			    <dependency>
			      <groupId>org.eclipse.rdf4j</groupId>
			      <artifactId>rdf4j-sail-memory</artifactId>
			    </dependency>
			    <dependency>
			      <groupId>org.eclipse.rdf4j</groupId>
			      <artifactId>rdf4j-rio-api</artifactId>
			      <exclusions>
			        <exclusion>
			          <groupId>org.apache.httpcomponents</groupId>
			          <artifactId>httpclient-osgi</artifactId>
			        </exclusion>
			        <exclusion>
			          <groupId>org.apache.httpcomponents</groupId>
			          <artifactId>httpcore-osgi</artifactId>
			        </exclusion>
			      </exclusions>
			    </dependency>
			    <dependency>
			      <groupId>org.eclipse.rdf4j</groupId>
			      <artifactId>rdf4j-rio-rdfxml</artifactId>
			    </dependency>
			    <dependency>
			      <groupId>org.eclipse.rdf4j</groupId>
			      <artifactId>rdf4j-queryresultio-sparqljson</artifactId>
			    </dependency>
			    <dependency>
			      <groupId>org.eclipse.rdf4j</groupId>
			      <artifactId>rdf4j-queryresultio-sparqlxml</artifactId>
			    </dependency>
			    <dependency>
			      <groupId>org.eclipse.rdf4j</groupId>
			      <artifactId>rdf4j-rio-turtle</artifactId>
			    </dependency>
			    <dependency>
			      <groupId>org.eclipse.rdf4j</groupId>
			      <artifactId>rdf4j-repository-sail</artifactId>
			    </dependency>
			    <dependency>
			      <groupId>org.eclipse.rdf4j</groupId>
			      <artifactId>rdf4j-repository-sparql</artifactId>
			    </dependency>
			  </dependencies>
			  <build>
			    <plugins>
			     <plugin>
			        <groupId>org.apache.maven.plugins</groupId>
			        <artifactId>maven-compiler-plugin</artifactId>
			        <version>3.8.0</version>
			        <configuration>
			          <release>21</release>
			          <debug>true</debug>
			          <debuglevel>lines,vars,source</debuglevel>
			        </configuration>
			      </plugin>
			      <plugin>
			        <groupId>org.apache.maven.plugins</groupId>
			        <artifactId>maven-surefire-plugin</artifactId>
			        <version>3.0.0</version>
			        <configuration>
			          <testFailureIgnore>false</testFailureIgnore>
			        </configuration>
			      </plugin>
			      <plugin>
			        <groupId>org.apache.maven.plugins</groupId>
			        <artifactId>maven-site-plugin</artifactId>
			        <version>3.7.1</version>
			      </plugin>
			      <plugin>
					<groupId>org.springframework.boot</groupId>
					<artifactId>spring-boot-maven-plugin</artifactId>
				  </plugin>
			    </plugins>
			  </build>
			  <reporting>
			    <plugins>
			      <plugin>
			        <groupId>org.codehaus.mojo</groupId>
			        <artifactId>versions-maven-plugin</artifactId>
			        <version>2.8.1</version>
			        <reportSets>
			          <reportSet>
			            <reports>
			              <report>dependency-updates-report</report>
			              <report>plugin-updates-report</report>
			              <report>property-updates-report</report>
			            </reports>
			          </reportSet>
			        </reportSets>
			      </plugin>
			    </plugins>
			  </reporting>
			</project>""";
	private static final String UTIL_CLASSNAME = "Sparql";

	private static final String UTIL_PACKAGE = "swiss.sib.swissprot.chistera.triples.sparql";

	private static final String PREFIXES = """
			PREFIX dcterms: <http://purl.org/dc/terms/>
			PREFIX foaf: <http://xmlns.com/foaf/0.1/>
			PREFIX owl: <http://www.w3.org/2002/07/owl#>
			PREFIX pav: <http://purl.org/pav/>
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX void: <http://rdfs.org/ns/void#>
			PREFIX void_ext: <http://ldf.fi/void-ext#>
			PREFIX sd:<http://www.w3.org/ns/sparql-service-description#>
			""";
	private static final String FIND_DATATYPE_PARTITIONS = PREFIXES + """
			SELECT DISTINCT ?classType ?predicate ?datatype
			WHERE {
			  ?graph sd:graph/void:classPartition ?classPartition .
			  ?classPartition void:class ?classType .
			  ?classPartition void:propertyPartition ?predicatePartition .
			  ?predicatePartition void:property ?predicate .
			  ?predicatePartition void_ext:datatypePartition ?datatypePartition .
			  ?datatypePartition void_ext:datatype ?datatype .
			}
			""";

	private static final String FIND_ALL_NAMED_GRAPHS = PREFIXES + """
			SELECT ?namedGraph
			WHERE {
				?dataset a sd:Dataset ;
			               sd:namedGraph ?namedGraph .
			}
			""";
	Map<String, Namespace> ns = new HashMap<>();

	// TODO replace this with a nice picocli command
	public static void main(String[] args) {
		String input = args[0];
		String output = args[1];

		try {
			new GenerateJavaFromVoID().convert(new File(input), new File(output));
		} catch (RDFParseException | RepositoryException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * Convert the VoID file to a set of Java classes, with a pom so that it is a
	 * nice maven project.
	 * 
	 * @param inputVoid       the VoID file that was generated with Void-generator
	 *                        or equivalent.
	 * @param outputDirectory where to write the generated code
	 * @throws RDFParseException   if we can't parse the VoID file
	 * @throws RepositoryException if we can't read the VoID file
	 * @throws IOException         if we can't write the generated code
	 */
	public void convert(File inputVoid, File outputDirectory)
			throws RDFParseException, RepositoryException, IOException {
		SailRepository repository = new SailRepository(new MemoryStore());
		repository.init();
		try (SailRepositoryConnection conn = repository.getConnection()) {
			conn.begin();
			conn.add(inputVoid, Rio.getParserFormatForFileName(inputVoid.getName()).orElse(RDFFormat.TURTLE));
			conn.commit();

		}
		try (SailRepositoryConnection conn = repository.getConnection()) {
			RepositoryResult<Namespace> namespaces = conn.getNamespaces();

			try (Stream<Namespace> s = namespaces.stream()) {
				s.forEach((n) -> ns.put(n.getName(), n));
			}
			addDefaultNamespaces();
		}
		File sourceDir = new File(outputDirectory, "src/main/java/");
		sourceDir.mkdirs();
		makeUtils(sourceDir);
		makePackages(repository, sourceDir);
		makePom(outputDirectory);
		repository.shutDown();
	}

	/**
	 * We attach a set of default namespaces to the ns map. This is to ensure that
	 * we can generate code that uses these namespaces default prefixes to generate
	 * more readable code. E.g. we generate a java Class named RdfsClass for the RDF
	 * class instead of a java class named
	 * http://www.w3.org/2000/01/rdf-schema#Class which would not be valid and would
	 * need to be mangled in some way.
	 */
	void addDefaultNamespaces() {
		for (Class<?> v : List.of(AFN.class, APF.class, CONFIG.class, DASH.class, DC.class, DCTERMS.class, DOAP.class,
				EARL.class, FN.class, FOAF.class, GEO.class, GEOF.class, HYDRA.class, LDP.class, LIST.class, LOCN.class,
				ODRL2.class, ORG.class, OWL.class, PROV.class, RDF.class, RDFS.class, ROV.class, RSX.class, SD.class,
				SESAME.class, SESAMEQNAME.class, SHACL.class, SKOS.class, SKOSXL.class, SP.class, SPIF.class,
				SPIN.class, SPINX.class, SPL.class, TIME.class, VANN.class, VCARD4.class, VOID.class, WGS84.class,
				XSD.class)) {
			try {
				String prefix = (String) v.getField("PREFIX").get(null);
				Object rns = v.getField("NAMESPACE").get(null);
				if (rns instanceof String namespace) {
					ns.putIfAbsent(prefix, new SimpleNamespace(prefix, namespace));
				} else if (rns instanceof Namespace namespace) {
					ns.putIfAbsent(prefix, namespace);
				} else {
					String namespace = (String) v.getField("NAMESPACE").get(null);
					ns.putIfAbsent(prefix, new SimpleNamespace(prefix, namespace));
				}
			} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
				System.err.println("Failed to get prefix and namespace for " + v.getName());
			}
		}
	}

	/**
	 * We generate a pom file so that the generated project can be built with maven.
	 * A gradle file would be possible too, but I am more familiar with maven
	 * 
	 * @param outputDirectory directory to save the pom.xml file
	 * @throws IOException if we can't write the file
	 **/
	void makePom(File outputDirectory) throws IOException {
		// TODO: find reasonable values to put in here from the VoID/Service description
		// file.
		String pom = POM_TEMPLATE.replace("${groupId}", "org.example").replace("${artifactId}", "example")
				.replace("${version}", "1.0.0-SNAPSHOT");
		Files.writeString(new File(outputDirectory, "pom.xml").toPath(), pom, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);

	}

	/**
	 * We need one small utility class, just generate it so that we don't have yet
	 * another dependency to publish
	 * 
	 * @param outputDirectory
	 * @throws IOException
	 */
	void makeUtils(File outputDirectory) throws IOException {

		// First build a class, which we make public and final so that it can be
		// used in the generated code but not modified/overriden.

		TypeSpec.Builder b = TypeSpec.classBuilder(UTIL_CLASSNAME).addModifiers(Modifier.PUBLIC, Modifier.FINAL);

		// Adding a private method means that we can't construct this class. That is ok
		// because we just want
		// the static method on it.
		b.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

		// This type variable is to make the return type of the method generic.
		TypeVariableName returnTypeBound = TypeVariableName.get("T");
		MethodSpec.Builder mb = MethodSpec.methodBuilder("resultToStream");
		mb.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
		TypeName bindingSet = ClassName.get(BindingSet.class);
		mb.addParameter(ParameterizedTypeName.get(ClassName.get(QueryResult.class), bindingSet), "result");

		// This is a function that will be used to extract the value from the binding
		// set.
		TypeName valueExtractor = TypeVariableName.get(Value.class);
		ParameterSpec.Builder veparam = ParameterSpec.builder(
				ParameterizedTypeName.get(ClassName.get(Function.class), valueExtractor, returnTypeBound),
				"transformer");
		veparam.addModifiers(Modifier.FINAL);
		mb.addParameter(veparam.build());
		mb.addTypeVariable(returnTypeBound);
		ParameterizedTypeName returnType = ParameterizedTypeName.get(ClassName.get(Stream.class), returnTypeBound);

		mb.returns(returnType);
		// one liner that does the actual code, should be a multiline code block but
		// this is easier to write.
		mb.addStatement(
				"return result.stream().map((bs) -> bs.getBinding(\"object\")).map($T::getValue).map(transformer)",
				Binding.class);

		b.addMethod(mb.build());

		// Now we build the java file and write it to the output directory
		JavaFile javaFile = JavaFile.builder(UTIL_PACKAGE, b.build()).build();
		javaFile.writeTo(outputDirectory);

	}

	/**
	 * We need to generate a package per named graph in the VoID file. There we will
	 * have all the classes in use in that graph and a special class called Graph.
	 * This class will be the root of the graph and will have methods to get all the
	 * other classes and is the entry point for your code.
	 * 
	 * @param repository      to extract the VoID data from
	 * @param outputDirectory where to write the java code to
	 * @throws IOException if we can't write the java files
	 */
	void makePackages(SailRepository repository, File outputDirectory) throws IOException {
		try (SailRepositoryConnection conn = repository.getConnection()) {
			TupleQuery tupleQuery = conn.prepareTupleQuery(FIND_ALL_NAMED_GRAPHS);
			try (var res = tupleQuery.evaluate()) {
				while (res.hasNext()) {
					IRI namedGraph = (IRI) res.next().getBinding("namedGraph").getValue();
					URI uri = URI.create(namedGraph.stringValue());

					// TODO: It might be worth looking at if we can find a better name than just
					// graph
					// Maybe if the Graph has a dc:title we could use that.
					String name = "Graph";
					String packageName = getPackageName(uri);
					buildClassForAGraph(outputDirectory, name, packageName, namedGraph, conn);
				}
			}

            Map<IRI, TypeSpec.Builder> typeClasses = buildTypeClassses(conn);
            buildMethodsOnTypesInAGraph(conn, typeClasses);
            for (Entry<IRI, com.palantir.javapoet.TypeSpec.Builder> e : typeClasses.entrySet()) {
                Builder jb = JavaFile.builder(packageNameMaker(e.getKey().stringValue()), e.getValue().build());
                jb.addStaticImport(ClassName.get(UTIL_PACKAGE, UTIL_CLASSNAME), "resultToStream");
                jb.build().writeTo(outputDirectory);
            }
		}

	}

	/**
	 * Generate the methods to access the data and also the equals and hashCode.
	 * 
	 * @param conn          to query the data
	 * @param graphName     in which the data resides
	 * @param classBuilders to add the methods to
	 */
	void buildMethodsOnTypesInAGraph(SailRepositoryConnection conn,
			Map<IRI, TypeSpec.Builder> classBuilders) {
		buildMethodsReturningObjects(conn, classBuilders);
		buildMethodsReturningLiterals(conn, classBuilders);
		buildClassEquals(conn, classBuilders);
		buildClassHashCode(conn, classBuilders);
	}

	/**
	 * We can't use the standard record equals, as we don't want to compare the
	 * connection, but only the id of the record.
	 * 
	 * @param connection    to query the data
	 * @param graphName     in which the data resides
	 * @param classBuilders to add the methods to
	 */
	void buildClassEquals(SailRepositoryConnection connection,
			Map<IRI, TypeSpec.Builder> classBuilders) {
		for (Entry<IRI, TypeSpec.Builder> en : classBuilders.entrySet()) {
			TypeSpec.Builder cb = en.getValue();

			ClassName returnType = ClassName.get("", cb.build().name());
			CodeBlock.Builder cbb = CodeBlock.builder();
			cbb.beginControlFlow("if (this == other)").addStatement("return true").endControlFlow()
					.beginControlFlow("else if (other instanceof $T t)", returnType)
					.addStatement("return _id.equals(t._id)").endControlFlow().addStatement("return false");

			cb.addMethod(MethodSpec.methodBuilder("equals").addModifiers(Modifier.PUBLIC)
					.addParameter(Object.class, "other", Modifier.FINAL).addCode(cbb.build()).returns(boolean.class)
					.build());
		}
	}

	/**
	 * The hashCode should not look at the connection, but only at the id of the
	 * record
	 * 
	 * @param connection    to query the data
	 * @param graphName     in which the data resides
	 * @param classBuilders to add the methods to
	 */
	void buildClassHashCode(SailRepositoryConnection connection,
			Map<IRI, TypeSpec.Builder> classBuilders) {
		for (Entry<IRI, TypeSpec.Builder> en : classBuilders.entrySet()) {
			TypeSpec.Builder cb = en.getValue();

			cb.addMethod(MethodSpec.methodBuilder("hashCode").addModifiers(Modifier.PUBLIC)
					.addStatement("return _id.hashCode()").returns(int.class).build());
		}
	}

	/**
	 * We need to add methods for the classes for each type in the graph.
	 * 
	 * @param connection    to query the data
	 * @param graphName     in which the data resides
	 * @param classBuilders to add the methods to
	 */
	void buildMethodsReturningObjects(SailRepositoryConnection connection,
			Map<IRI, TypeSpec.Builder> classBuilders) {
		TupleQuery tq = connection.prepareTupleQuery(PREFIXES + """
				SELECT ?classType ?predicate ?class2Type
				WHERE {
				  ?graph sd:graph/void:classPartition ?classPartition .
				  ?classPartition void:class ?classType .
                  ?graph sd:graph/void:classPartition ?class2Partition .
				  ?class2Partition void:class ?class2Type .
				  [] a void:Linkset ;
				  	 void:objectsTarget ?class2Partition ;
				  	 void:linkPredicate ?predicate ;
				  	 void:subjectsTarget ?classPartition ;
				  	 void:subset ?graph .
				}
				""");
		for (Entry<IRI, TypeSpec.Builder> en : classBuilders.entrySet()) {
			tq.setBinding("classType", en.getKey());
			TypeSpec.Builder cb = classBuilders.get(en.getKey());

			Map<Binding, Set<Binding>> returnTypes = new HashMap<>();

			try (TupleQueryResult tqr = tq.evaluate()) {
				while (tqr.hasNext()) {
					BindingSet binding = tqr.next();
					Binding predicate = binding.getBinding("predicate");
					Binding otherClass = binding.getBinding("class2Type");
					returnTypes.computeIfAbsent(predicate, k -> new HashSet<>()).add(otherClass);
				}
			}

			for(Entry<Binding, Set<Binding>> e : returnTypes.entrySet())
                buildClassMethodFindInstances(classBuilders, cb, e.getKey(), e.getValue());
		}
	}

	/**
	 * We need to generate a method to retrieve instances of a class.
	 * 
	 * @param graphName           in which the data resides
	 * @param classBuilders       to find the return types of the other classes
	 * @param classBuilder        the builder for the class that needs the method
	 * @param predicate           that links a class instance with the other class
	 * @param otherClassPartition partition of the other class
	 * @param set          the other class
	 */
	void buildClassMethodFindInstances(Map<IRI, TypeSpec.Builder> classBuilders,
			TypeSpec.Builder classBuilder, Binding predicate, Set<Binding> otherClassSet) {
	    if(otherClassSet.size() != 1)
	       return;

	    Binding otherClass = otherClassSet.iterator().next();

		IRI predicateIri = (IRI) predicate.getValue();
		String predicateString = predicateIri.stringValue();

		IRI otherClassString = (IRI) otherClass.getValue();
		String methodName = methodNameMaker(predicateIri.toString());
		MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(fixJavaKeywords(methodName));
		methodBuilder.addModifiers(Modifier.PUBLIC);
		TypeSpec.Builder v = classBuilders.get(otherClass.getValue());

		ClassName returnType = ClassName.get(packageNameMaker(otherClass.getValue().toString()), v.build().name());
		ParameterizedTypeName streamType = ParameterizedTypeName.get(ClassName.get(Stream.class), returnType);
		methodBuilder.returns(streamType);
		CodeBlock.Builder cbb = CodeBlock.builder();

		// Generate a nice static final field to hold the query object.
		String qn = methodName + "_QUERY";
		FieldSpec qf = FieldSpec.builder(String.class, qn, Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
				.initializer("\"SELECT ?object WHERE { GRAPH ?G { ?id <$L> ?object . ?object a <$L>}}\"",
						predicateString, otherClassString)
				.build();
		classBuilder.addField(qf);

		cbb.addStatement("$T tq = conn.prepareTupleQuery($L)", TupleQuery.class, qf.name());
		cbb.addStatement("tq.setBinding($S, _id)", "id");

		// First T is the type of the class in Java, the second is to make the cast from
		// value work.
		cbb.addStatement("return resultToStream(tq.evaluate(), (v) -> new $T(($T) v, conn))", returnType, IRI.class);

		methodBuilder.addCode(cbb.build());
//					type
		classBuilder.addMethod(methodBuilder.build());
	}

	/**
	 * We need to generate a methods to retrieve any kind of literal values.
	 * 
	 * @param connection    to query the data
	 * @param graphName     in which the data resides
	 * @param classBuilders to add the methods to
	 */
	void buildMethodsReturningLiterals(SailRepositoryConnection connection,
			Map<IRI, TypeSpec.Builder> classBuilders) {
		TupleQuery tq = connection.prepareTupleQuery(FIND_DATATYPE_PARTITIONS);
		for (Entry<IRI, TypeSpec.Builder> en : classBuilders.entrySet()) {
			tq.setBinding("classType", en.getKey());
			TypeSpec.Builder cb = classBuilders.get(en.getKey());

			Map<Binding, Set<Binding>> returnTypes = new HashMap<>();

			try (TupleQueryResult tqr = tq.evaluate()) {
				while (tqr.hasNext()) {
					BindingSet binding = tqr.next();
					Binding predicate = binding.getBinding("predicate");
					Binding datatype = binding.getBinding("datatype");

					returnTypes.computeIfAbsent(predicate, k -> new HashSet<>()).add(datatype);
				}
			}

	        for(Entry<Binding, Set<Binding>> e : returnTypes.entrySet()) {
	            if(e.getValue().size() != 1)
	                continue;

                IRI datatypeB = (IRI) e.getValue().iterator().next().getValue();

                MethodSpec.Builder methodBuilder = buildClassMethodFindLiteral(e.getKey(), datatypeB, cb);
                cb.addMethod(methodBuilder.build());
	        }
		}
	}

	/**
	 * We need to generate a method to retrieve any kind of literal values.
	 * 
	 * @param graphName    in which the data resides
	 * @param predicate    that links a class instance with the literal value
	 * @param datatypeB    of the field, used to generate the correct return type
	 *                     and queries
	 * @param classBuilder the builder for the class that needs the method
	 * @return a method builder that can be added to the class
	 */
	MethodSpec.Builder buildClassMethodFindLiteral(Binding predicate, IRI datatypeB,
			TypeSpec.Builder classBuilder) {

		IRI predicateIri = (IRI) predicate.getValue();
		String methodName = methodNameMaker(predicateIri.toString());

		String qn = methodName + "_QUERY";

		// Generate a static query string that is referred to in the method
		FieldSpec qf = FieldSpec.builder(String.class, qn, Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
				.initializer(
						"\"SELECT ?object WHERE { GRAPH ?G { ?id <$L> ?object . FILTER(datatype(?object) = <$L>)}}\"",
						predicateIri.stringValue(), datatypeB.stringValue())
				.build();
		classBuilder.addField(qf);

		// Build a public method that returns a stream of the correct type
		MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName);
		methodBuilder.addModifiers(Modifier.PUBLIC);
		CodeBlock.Builder cbb = CodeBlock.builder();
		Class<?> returnType = literalToClassMap.getOrDefault(datatypeB, String.class);
		assert returnType != null : datatypeB.stringValue() + " not found in map";
		cbb.addStatement("$T tq = conn.prepareTupleQuery($L)", TupleQuery.class, qn);
		cbb.addStatement("tq.setBinding($S, _id)", "id");
		cbb.addStatement("return resultToStream(tq.evaluate(), " + returnTheRightKindOfData(returnType) + ")",
				Literal.class);
		methodBuilder.addCode(cbb.build());

		// Return type needs to be a nice Stream<String>
		// TODO: LongStream and IntStream returns.
		ParameterizedTypeName streamType = ParameterizedTypeName.get(ClassName.get(Stream.class),
				ClassName.get(returnType));
		methodBuilder.returns(streamType);
		return methodBuilder;
	}

	/**
	 * Knowing the data-type on the RDF/SPARQL side we now need to convert it in one
	 * line/lambda into a Java type
	 * 
	 * @param returnType of the method.
	 * @return the lambda to convert the RDF Literal into a Java
	 */
	private String returnTheRightKindOfData(Class<?> returnType) {
		if (returnType == String.class) {
			return "(v) -> (($T) v).stringValue()";
		} else if (returnType == Integer.class) {
			return "(v) -> (($T) v).intValue()";
		} else if (returnType == Boolean.class) {
			return "(v) -> (($T) v).booleanValue()";
		} else if (returnType == BigInteger.class) {
			return "(v) -> (($T) v).integerValue()";
		} else if (returnType == Double.class) {
			return "(v) -> (($T) v).doubleValue()";
		} else if (returnType == Float.class) {
			return "(v) -> (($T) v).floatValue()";
		} else if (returnType == Short.class) {
			return "(v) -> (($T) v).shortValue()";
		} else if (returnType == Long.class) {
			return "(v) -> (($T) v).longValue()";
		} else if (returnType == Byte.class) {
			return "(v) -> (($T) v).byteValue()";
		} else if (returnType == URI.class) {
			return "(v) -> (($T) v).uriValue()";
		} else if (returnType == LocalDate.class) {
			return "(v) -> (($T) v).calendarValue().toGregorianCalendar().toZonedDateTime().toLocalDate()";
		} else if (returnType == LocalDateTime.class) {
			return "(v) -> (($T) v).calendarValue().toGregorianCalendar().toZonedDateTime().toLocalDateTime()";
		} else if (returnType == Instant.class) {
			return "(v) -> (($T) v).getValue().calendarValue().toGregorianCalendar().toZonedDateTime().toInstant()";
		} else if (returnType == Duration.class) {
			return "(v) -> (($T) v).object.getValue().calendarValue().toGregorianCalendar().toZonedDateTime().toInstant().toEpochMilli()";
		} else if (returnType == Year.class) {
			// 1 is the Calendar.YEAR constant, should realy use Calendar.YEAR but that is
			// not easy
			// because then we also need to import the type Calendar. Year is imported
			// already because
			// it is the return type.
			return "(v) -> Year.of((($T) v).calendarValue().toGregorianCalendar().get(1))";
		} else if (returnType == YearMonth.class) {
			// as above but 2 is the month constant.
			return "(v) -> { var cv = (($T) v).calendarValue().toGregorianCalendar(); return YearMonth.of(cv.get(1), cv.get(2)); }";
		} else {
			return "($T)::stringValue";
		}
	}

	// TODO needs to be expanded to all known literals. And lang string needs to be
	// considered.

	private Map<IRI, Class<?>> literalToClassMap = Map.ofEntries(Map.entry(XSD.STRING, String.class),
			Map.entry(XSD.INT, Integer.class), Map.entry(XSD.INTEGER, Integer.class), Map.entry(XSD.ANYURI, URI.class),
			Map.entry(XSD.BASE64BINARY, String.class), Map.entry(XSD.BOOLEAN, Boolean.class),
			Map.entry(XSD.BYTE, Byte.class), Map.entry(XSD.DATE, LocalDate.class),
			Map.entry(XSD.DATETIME, LocalDateTime.class), Map.entry(XSD.DATETIMESTAMP, Instant.class),
			Map.entry(XSD.DAYTIMEDURATION, Duration.class), Map.entry(XSD.DECIMAL, Double.class),
			Map.entry(XSD.DOUBLE, Double.class), Map.entry(XSD.DURATION, Duration.class),
			Map.entry(XSD.FLOAT, Float.class), Map.entry(XSD.GYEAR, Year.class),
			Map.entry(XSD.GYEARMONTH, YearMonth.class), Map.entry(XSD.LONG, Long.class),
			Map.entry(XSD.NEGATIVE_INTEGER, Integer.class), Map.entry(XSD.NON_NEGATIVE_INTEGER, Integer.class),
			Map.entry(XSD.NON_POSITIVE_INTEGER, Integer.class), Map.entry(XSD.POSITIVE_INTEGER, Integer.class),
			Map.entry(XSD.SHORT, Short.class), Map.entry(RDF.LANGSTRING, String.class),
			Map.entry(RDF.HTML, String.class));

	/**
	 * For all types in the graph we generate a class that represents the type.
	 * Those classes will then have methods for all the predicate partitions.
	 *
	 * @param connection to query the void with
	 * @param graphName  to select only the types in this graph
	 * @return a map for the rdf types and their java class builders
	 */
	private Map<IRI, TypeSpec.Builder> buildTypeClassses(SailRepositoryConnection connection) {
		Map<IRI, TypeSpec.Builder> classBuilders = new HashMap<>();
		TupleQuery tq = connection.prepareTupleQuery(PREFIXES + """
				SELECT DISTINCT ?class
				WHERE {
					?graph sd:graph/void:classPartition ?classPartition .
					?classPartition void:class ?class .
				}
				""");
		try (TupleQueryResult tqr = tq.evaluate()) {
			while (tqr.hasNext()) {
				BindingSet binding = tqr.next();
				Binding cIri = binding.getBinding("class");
				String className = cIri.getValue().stringValue();

				// We need to fix the class name to be a valid java class name
				className = classNameMaker(className);

				// Generate a java record for the class
				TypeSpec.Builder classBuilder = TypeSpec.recordBuilder(className).addModifiers(Modifier.PUBLIC);
				// Pass the variable identifier and a connection to the repository in which the
				// data proxied resides.

				// TODO: extract what the class represents by downloading the IRI's information
				// and looking for rdfs:comment fields and
				// labels.
				classBuilder.addJavadoc("""
						@param id of the class instance
						@param conn connection to the backend SPARQL endpoint from which information is extracted
						""");
				classBuilder.recordConstructor(MethodSpec.constructorBuilder().addParameter(IRI.class, "_id")
						.addParameter(RepositoryConnection.class, "conn").build());

				IRI classIri = (IRI) cIri.getValue();
				classBuilders.put(classIri, classBuilder);
			}
		}
		return classBuilders;

	}

    /**
     * Extract a valid java package name for an iri/path
     * 
     * @param path the iri to extract the package name from
     * @return a valid java package name that is close to the original iri
     */
    String packageNameMaker(String path) {
        if(path.contains("#"))
            return getPackageName(URI.create(path.substring(0, path.lastIndexOf('#'))));
        else
            return getPackageName(URI.create(path.substring(0, path.lastIndexOf('/'))));
    }

	/**
     * Extract a valid java class name for an iri/path
     * 
     * @param path the iri to extract the class name from
     * @return a valid java class name that is close to the original iri
     */
    String classNameMaker(String path) {
        if(path.contains("#"))
            return fixJavaKeywords(path.substring(path.lastIndexOf('#') + 1).replace('.', '_'));
        else
            return fixJavaKeywords(path.substring(path.lastIndexOf('/') + 1).replace('.', '_'));
    }

    /**
     * Extract a valid java class name for an iri/path
     * 
     * @param predicateIri iri of the predicate
     * @return a method name inspired by the predicate and datatype/class
     */
    String methodNameMaker(String path) {
        if(path.contains("#"))
            return fixJavaKeywords(path.substring(path.lastIndexOf('#') + 1).replace('.', '_'));
        else
            return fixJavaKeywords(path.substring(path.lastIndexOf('/') + 1).replace('.', '_'));
    }

	/**
	 * This method builds a class that represents a graph. This will have the basic
	 * search classes
	 * 
	 * @param outputDirectory where to write the java file
	 * @param name            of the class
	 * @param packageName     name for the package
	 * @param resource        the IRI of the named graph
	 * @param conn            to extract more data from the VoID file
	 * @throws IOException if we can't write the file
	 */
	void buildClassForAGraph(File outputDirectory, String name, String packageName, IRI resource,
			RepositoryConnection conn) throws IOException {
		TypeSpec.Builder graphC = TypeSpec.recordBuilder(name).addModifiers(Modifier.PUBLIC);

		buildClassFindMethodsForAGraph(graphC, resource, conn);
		JavaFile.Builder javaFile = JavaFile.builder(packageName, graphC.build());
		javaFile.addStaticImport(ClassName.get(UTIL_PACKAGE, UTIL_CLASSNAME), "resultToStream");
		javaFile.build().writeTo(outputDirectory);
	}

	/**
	 * Generate a record class for the Graph, this record class will have methods to
	 * get all the classes in the graph
	 * 
	 * @param graphC    the builder for the class code
	 * @param graphName IRI of the graph
	 * @param conn      to extract more data from the VoID file
	 */
	void buildClassFindMethodsForAGraph(TypeSpec.Builder graphC, IRI graphName, RepositoryConnection conn) {
		// This constructor method has just one parameter, the connection to the
		// repository
		MethodSpec constructorAcceptingConnection = MethodSpec.constructorBuilder()
				.addParameter(RepositoryConnection.class, "conn").build();
		graphC.recordConstructor(constructorAcceptingConnection);

		// For each class in the graph we will generate a method that returns a stream
		// of all instances of that class
		TupleQuery tq = conn.prepareTupleQuery(PREFIXES + """
				SELECT DISTINCT ?class
				WHERE {
					?graph sd:graph/void:classPartition ?classPartition .
					?classPartition void:class ?class .
				}
				""");
		tq.setBinding("graph", graphName);

		try (TupleQueryResult tqr = tq.evaluate()) {
			while (tqr.hasNext()) {
				BindingSet bs = tqr.next();
				Binding cIri = bs.getBinding("class");

				buildFindAllInstancesOfAClass(graphC, graphName, cIri.getValue().stringValue());
				buildFindAllInstancesOfAClassByString(graphC, graphName, (IRI) cIri.getValue(),
						cIri.getValue().stringValue(), conn);
			}
		}
	}

	/**
	 * Generate finder methods to list all instances of a class in the graph
	 * 
	 * @param graphC    the class builder
	 * @param graphName used in the constant query
	 * @param cIri      the iri of the class to find as a string
	 */
	void buildFindAllInstancesOfAClass(TypeSpec.Builder graphC, IRI graphName, String cIri) {
		// We need to fix the class name to be a valid java class name, but for now we
		// want it snakecase for the constant field.
		String className = classNameMaker(cIri);
		ClassName type = ClassName.get(packageNameMaker(cIri), className);
		// We generate a Stream<T> where T is the className we just got.
		ParameterizedTypeName returnType = ParameterizedTypeName.get(ClassName.get(Stream.class), type);
		MethodSpec.Builder mb = MethodSpec.methodBuilder("all" + className).returns(returnType)
				.addModifiers(Modifier.PUBLIC);

		// We generate a constant field for the query string. Makes it easier to read.
		String qn = ("ALL_" + className + "_QUERY");
		// TODO: find out how to generate multiline strings in JavaPoet as this does not
		// read nicely
		FieldSpec qf = FieldSpec.builder(String.class, qn, Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
				.initializer("\"SELECT ?object WHERE { GRAPH <$L> { ?object a <$L> }}\"", graphName.stringValue(), cIri)
				.build();
		graphC.addField(qf);
		CodeBlock.Builder cbb = CodeBlock.builder();
		// Run the query and return the stream of instances
		cbb.addStatement("$T tq = conn.prepareTupleQuery($L)", TupleQuery.class, qf.name());
		// Important this order of constuctor parameters must match the order generated
		// later
		cbb.addStatement("return resultToStream(tq.evaluate(), (v) -> new $T(($T) v, conn))", type, IRI.class);

		mb.addCode(cbb.build());
		graphC.addMethod(mb.build());
	}

	/*
	 * Generate finder methods to list all instances of a class in the graph for a
	 * string. i.e. poor mans free text search
	 * 
	 * @param graphC the class builder
	 * 
	 * @param graphName used in the constant query
	 * 
	 * @param cIri the iri of the class to find as a string
	 */
	void buildFindAllInstancesOfAClassByString(TypeSpec.Builder graphC, IRI graphName, IRI iri, String cIri,
			RepositoryConnection conn) {
		// We need to fix the class name to be a valid java class name, but for now we
		// want it snakecase for the constant field.
		String className = classNameMaker(cIri);
		ClassName type = ClassName.get(packageNameMaker(cIri), className);
		// We generate a Stream<T> where T is the className we just got.
		ParameterizedTypeName returnType = ParameterizedTypeName.get(ClassName.get(Stream.class), type);
		MethodSpec.Builder mb = MethodSpec.methodBuilder("search" + className).returns(returnType)
				.addParameter(String.class, "searchFor").addModifiers(Modifier.PUBLIC);

		// We generate a constant field for the query string. Makes it easier to read.
		String qn = ("FREETEXT_" + className + "_QUERY");
		// TODO: find out how to generate multiline strings in JavaPoet as this does not
		// read nicely
		String queryWithoutValues = "\"SELECT ?object WHERE { GRAPH <$L> { ?object a <$L> ; ?predicate ?literal . FILTER(contains(lcase(?literal), ?searchFor )}} VALUES ?predicate {";
		Set<String> predicates = new HashSet<>();
		TupleQuery tq = conn.prepareTupleQuery(FIND_DATATYPE_PARTITIONS);
		tq.setBinding("classType", iri);
		tq.setBinding("graph", graphName);
		try (TupleQueryResult r = tq.evaluate()) {
			while (r.hasNext()) {
				BindingSet bs = r.next();
				IRI predicate = (IRI) bs.getBinding("predicate").getValue();
				predicates.add('<' + predicate.stringValue() + '>');
			}
		}
		// If we did not find a literal field to search in don't add a fake method that
		// won't work
		if (!predicates.isEmpty()) {
			queryWithoutValues += predicates.stream().collect(Collectors.joining(" "));
			queryWithoutValues += "}\"";
			FieldSpec qf = FieldSpec.builder(String.class, qn, Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
					.initializer(queryWithoutValues, graphName.stringValue(), cIri).build();
			graphC.addField(qf);
			CodeBlock.Builder cbb = CodeBlock.builder();
			// Run the query and return the stream of instances
			cbb.addStatement("$T tq = conn.prepareTupleQuery($L)", TupleQuery.class, qf.name());
			cbb.addStatement("tq.setBinding(\"searchFor\", $T.getInstance().createLiteral(searchFor.toLowerCase()))",
					SimpleValueFactory.class);
			// Important this order of constuctor parameters must match the order generated
			// later
			cbb.addStatement("return resultToStream(tq.evaluate(), (v) -> new $T(($T) v, conn))", type, IRI.class);

			mb.addCode(cbb.build());

			graphC.addMethod(mb.build());
		}
	}

	/**
	 * Try to generate a reasonable package name from the URI. This is not perfect!
	 * 
	 * @param uri of the namedgraph
	 * @return a valid package name
	 */
	String getPackageName(URI uri) {
		StringBuilder packageName = new StringBuilder();
		if (uri.getHost() != null) {
			List<String> hostPartsAsList = Arrays.asList(uri.getHost().split("\\."));
			for (int i = hostPartsAsList.size() - 1; i >= 0; i--) {
				packageName.append(fixJavaKeywords(hostPartsAsList.get(i)).replace('.', '_'));
				if (i > 0) {
					packageName.append('.');
				}
			}
		}
		List<String> partsDirsAsList = Arrays.asList(uri.getPath().split("/"));
		if (partsDirsAsList.size() > 1) {
			if (!packageName.isEmpty()) {
				packageName.append(".");
			}
			for (int i = 1; i < partsDirsAsList.size(); i++) {
				packageName.append(fixJavaKeywords(partsDirsAsList.get(i)).replace('.', '_'));
				if (i < partsDirsAsList.size() - 1) {
					packageName.append('.');
				}
			}
		}
		return packageName.toString().replaceAll("\\.([0-9])", "._$1");
	}

	/**
	 * We can't have java classes with names that are not valid java identifiers.
	 * This is to escape the keywords.
	 * 
	 * @param raw
	 * @return a prefixed keyword which should avoid the issue.
	 */
	String fixJavaKeywords(String raw) {

		String string = raw.replace('-', '_').replace('#', '_');
		return switch (string) {
		case "abstract" -> "_abstract";
		case "continue" -> "_continue";
		case "for" -> "_for";
		case "new" -> "_new";
		case "switch" -> "_switch";
		case "assert" -> "_assert";
		case "default" -> "_default";
		case "if" -> "_if";
		case "package" -> "_package";
		case "synchronized" -> "_synchronized";
		case "boolean" -> "_boolean";
		case "do" -> "_do";
		case "goto" -> "_goto";
		case "private" -> "_private";
		case "this" -> "_this";
		case "break" -> "_break";
		case "double" -> "_double";
		case "implements" -> "_implements";
		case "protected" -> "_protected";
		case "throw" -> "_throw";
		case "byte" -> "_byte";
		case "else" -> "_else";
		case "import" -> "_import";
		case "public" -> "_public";
		case "throws" -> "_throws";
		case "case" -> "_case";
		case "enum" -> "_enum";
		case "instanceof" -> "_instanceof";
		case "return" -> "_return";
		case "transient" -> "_transient";
		case "catch" -> "_catch";
		case "extends" -> "_extends";
		case "int" -> "_int";
		case "short" -> "_short";
		case "try" -> "_try";
		case "char" -> "_char";
		case "final" -> "_final";
		case "interface" -> "_interface";
		case "static" -> "_static";
		case "void" -> "_void";
		case "class" -> "_class";
		case "finally" -> "_finally";
		case "long" -> "_long";
		case "strictfp" -> "_strictfp";
		case "volatile" -> "_volatile";
		case "const" -> "_const";
		case "float" -> "_float";
		case "native" -> "_native";
		case "super" -> "_super";
		case "while" -> "_while";
		default -> string;
		};
	}
}
