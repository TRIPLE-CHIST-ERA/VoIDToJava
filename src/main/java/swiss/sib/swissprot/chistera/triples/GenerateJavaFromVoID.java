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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleNamespace;
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

	private static final String POM_TEMPLATE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
			  <modelVersion>4.0.0</modelVersion>
			  <parent>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-starter-parent</artifactId>
				<version>3.3.5</version>
				<relativePath/>
			  </parent>
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

	private static final String FIND_ALL_NAMED_GRAPHS = PREFIXES + """
			SELECT ?namedGraph
			WHERE {
				?dataset a sd:Dataset ;
			               sd:namedGraph ?namedGraph .
			}
			""";
	Map<String, Namespace> ns = new HashMap<>();

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

	public void convert(File inputVoid, File outputDirectory)
			throws RDFParseException, RepositoryException, IOException {
		SailRepository ms = new SailRepository(new MemoryStore());
		ms.init();
		try (SailRepositoryConnection conn = ms.getConnection()) {
			conn.begin();
			conn.add(inputVoid, Rio.getParserFormatForFileName(inputVoid.getName()).orElse(RDFFormat.TURTLE));
			conn.commit();

		}
		try (SailRepositoryConnection conn = ms.getConnection()) {
			RepositoryResult<Namespace> namespaces = conn.getNamespaces();

			try (Stream<Namespace> s = namespaces.stream()) {
				s.forEach((n) -> ns.put(n.getName(), n));
			}
			addDefaultNamespaces();
		}
		File sourceDir = new File(outputDirectory, "src/main/java/");
		sourceDir.mkdirs();
		makeUtils(ms, sourceDir);
		makePackages(ms, sourceDir);
		makePom(outputDirectory);
		ms.shutDown();
	}

	protected void addDefaultNamespaces() {
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

	private void makePom(File outputDirectory) throws IOException {
		String pom = POM_TEMPLATE.replace("${groupId}", "org.example").replace("${artifactId}", "example")
				.replace("${version}", "1.0.0");
		Files.writeString(new File(outputDirectory, "pom.xml").toPath(), pom, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);

	}

	private void makeUtils(SailRepository ms, File outputDirectory) throws IOException {

		TypeSpec.Builder b = TypeSpec.classBuilder(UTIL_CLASSNAME).addModifiers(Modifier.PUBLIC, Modifier.FINAL);
		TypeVariableName returnTypeBound = TypeVariableName.get("T");
		MethodSpec.Builder mb = MethodSpec.methodBuilder("resultToStream");
		mb.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
		TypeName bindingSet = ClassName.get(BindingSet.class);
		mb.addParameter(ParameterizedTypeName.get(ClassName.get(QueryResult.class), bindingSet), "result");
		TypeName value = TypeVariableName.get(Value.class);

		ParameterSpec.Builder fb = ParameterSpec.builder(
				ParameterizedTypeName.get(ClassName.get(Function.class), value, returnTypeBound), "transformer");
		fb.addModifiers(Modifier.FINAL);
		mb.addParameter(fb.build());
		mb.addTypeVariable(returnTypeBound);
		ParameterizedTypeName returnType = ParameterizedTypeName.get(ClassName.get(Stream.class), returnTypeBound);

		mb.returns(returnType);
		mb.addStatement(
				"return result.stream().map((bs) -> bs.getBinding(\"object\")).map($T::getValue).map(transformer)",
				Binding.class);

		b.addMethod(mb.build());
		JavaFile javaFile = JavaFile.builder(UTIL_PACKAGE, b.build()).build();
		javaFile.writeTo(outputDirectory);

	}

	private void makePackages(SailRepository ms, File outputDirectory) throws IOException {
		try (SailRepositoryConnection conn = ms.getConnection()) {
			TupleQuery tupleQuery = conn.prepareTupleQuery(FIND_ALL_NAMED_GRAPHS);
			try (var res = tupleQuery.evaluate()) {
				while (res.hasNext()) {
					Value namedGraph = res.next().getBinding("namedGraph").getValue();
					IRI resource = (IRI) namedGraph;
					URI uri = URI.create(resource.stringValue());
					String name = "Graph";
					String packageName = getPackageName(uri).toString();
					buildClassForAGraph(outputDirectory, name, packageName, resource, conn);
					Map<IRI, TypeSpec.Builder> buildTypeClasssesForAGraph = buildTypeClasssesForAGraph(outputDirectory,
							name, packageName, conn, resource);
					buildMethodsOnTypesInAGraph(outputDirectory, name, packageName, conn, resource,
							buildTypeClasssesForAGraph);
					for (TypeSpec.Builder b : buildTypeClasssesForAGraph.values()) {
						Builder jb = JavaFile.builder(packageName, b.build());
						jb.addStaticImport(ClassName.get(UTIL_PACKAGE, UTIL_CLASSNAME), "resultToStream");
						jb.build().writeTo(outputDirectory);
					}
				}
			}
		}

	}

	private void buildMethodsOnTypesInAGraph(File outputDirectory, String name, String packageName,
			SailRepositoryConnection conn, IRI graphName, Map<IRI, TypeSpec.Builder> classBuilders) {
//		buildIdField(conn, graphName, classBuilders);
		buildClassEquals(conn, graphName, classBuilders);
		buildClassHashCode(conn, graphName, classBuilders);
		buildMethodsReturningObjects(conn, graphName, classBuilders);
		buildMethodsReturningLiterals(conn, graphName, classBuilders);
	}

	private void buildClassEquals(SailRepositoryConnection conn, IRI graphName,
			Map<IRI, TypeSpec.Builder> classBuilders) {
		for (Entry<IRI, TypeSpec.Builder> en : classBuilders.entrySet()) {
			TypeSpec.Builder cb = en.getValue();

			ClassName returnType = ClassName.get("", cb.build().name());
			CodeBlock.Builder cbb = CodeBlock.builder();
			cbb.beginControlFlow("if (this == other)").addStatement("return true").endControlFlow()
					.beginControlFlow("else if (other instanceof $T t)", returnType).addStatement("return id.equals(t.id)")
					.endControlFlow()
					.addStatement("return false");

			cb.addMethod(MethodSpec.methodBuilder("equals").addModifiers(Modifier.PUBLIC)
					.addParameter(Object.class, "other", Modifier.FINAL).addCode(cbb.build()).returns(boolean.class)
					.build());
		}
	}
	
	private void buildClassHashCode(SailRepositoryConnection conn, IRI graphName,
			Map<IRI, TypeSpec.Builder> classBuilders) {
		for (Entry<IRI, TypeSpec.Builder> en : classBuilders.entrySet()) {
			TypeSpec.Builder cb = en.getValue();

			cb.addMethod(MethodSpec.methodBuilder("hashCode").addModifiers(Modifier.PUBLIC)
					.addStatement("return id.hashCode()").returns(int.class)
					.build());
		}
	}

	public void buildMethodsReturningObjects(SailRepositoryConnection conn, IRI graphName,
			Map<IRI, TypeSpec.Builder> classBuilders) {
		TupleQuery tq = conn.prepareTupleQuery(PREFIXES + """
				SELECT ?classPartition ?classType ?predicate ?class2Partition ?class2Type
				WHERE {
				  ?graph sd:graph/void:classPartition ?classPartition .
				  ?classPartition void:class ?classType .
				  ?classPartition void:propertyPartition ?predicatePartition .
				  ?predicatePartition void:property ?predicate .
				  ?class2Partition void:class ?class2Type .
				  [] a void:Linkset ;
				  	 void:objectsTarget ?class2Partition ;
				  	 void:linkPredicate ?predicate ;
				  	 void:subjectsTarget ?classPartition ;
				  	 void:subset ?graph .
				}
				""");
		for (Entry<IRI, TypeSpec.Builder> en : classBuilders.entrySet()) {
			tq.setBinding("graph", graphName);
			tq.setBinding("classPartition", en.getKey());
			TypeSpec.Builder cb = classBuilders.get(en.getKey());

			try (TupleQueryResult tqr = tq.evaluate()) {
				while (tqr.hasNext()) {
					BindingSet binding = tqr.next();
					Binding predicate = binding.getBinding("predicate");
					Binding otherClassPartition = binding.getBinding("class2Partition");
					Binding otherClass = binding.getBinding("class2Type");

					assert cb != null : "ClassBuilder not found for " + otherClassPartition.getValue().stringValue();
					IRI predicateIri = (IRI) predicate.getValue();
					String predicateString = predicateIri.stringValue();

					IRI otherClassString = (IRI) otherClass.getValue();
					String methodName = coreMethodName(otherClassString, predicateIri);
					MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(fixJavaKeywords(methodName));
					methodBuilder.addModifiers(Modifier.PUBLIC);
					TypeSpec.Builder v = classBuilders.get(otherClassPartition.getValue());

					ClassName returnType = ClassName.get("", v.build().name());
					ParameterizedTypeName streamType = ParameterizedTypeName.get(ClassName.get(Stream.class),
							returnType);
					methodBuilder.returns(streamType);
					CodeBlock.Builder cbb = CodeBlock.builder();

					String qn = methodName.toUpperCase() + "_QUERY";
					FieldSpec qf = FieldSpec
							.builder(String.class, qn, Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
							.initializer("\"SELECT ?object WHERE { GRAPH <$L> { ?id <$L> ?object . ?object a <$L>}}\"",
									graphName.stringValue(), predicateString, otherClassString)
							.build();
					cb.addField(qf);

					cbb.addStatement("$T tq = conn.prepareTupleQuery($L)", TupleQuery.class, qf.name());
					cbb.addStatement("tq.setBinding($S, id)", "id");
					cbb.addStatement("return resultToStream(tq.evaluate(), (v) -> new $T(($T) v, conn))", returnType,
							IRI.class);

					methodBuilder.addCode(cbb.build());
//					type
					cb.addMethod(methodBuilder.build());
				}
			}
		}
	}

	public void buildMethodsReturningLiterals(SailRepositoryConnection conn, IRI graphName,
			Map<IRI, TypeSpec.Builder> classBuilders) {
		TupleQuery tq = conn.prepareTupleQuery(PREFIXES + """
				SELECT *
				WHERE {
				  ?graph sd:graph/void:classPartition ?classPartition .
				  ?classPartition void:class ?classType .
				  ?classPartition void:propertyPartition ?predicatePartition .
				  ?predicatePartition void:property ?predicate .
				  ?predicatePartition void_ext:datatypePartition ?datatypePartition .
				  ?datatypePartition void_ext:datatype ?datatype .
				}
				""");
		for (Entry<IRI, TypeSpec.Builder> en : classBuilders.entrySet()) {
			tq.setBinding("graph", graphName);
			tq.setBinding("classPartition", en.getKey());
			try (TupleQueryResult tqr = tq.evaluate()) {
				while (tqr.hasNext()) {
					BindingSet binding = tqr.next();
					Binding predicate = binding.getBinding("predicate");
					Binding classToAddTo = binding.getBinding("classPartition");
					IRI datatypeB = (IRI) binding.getBinding("datatype").getValue();
					TypeSpec.Builder cb = classBuilders.get(classToAddTo.getValue());

					assert cb != null : "ClassBuilder not found for " + classToAddTo.getValue().stringValue();
					IRI predicateIri = (IRI) predicate.getValue();
					String methodName = coreMethodName(datatypeB, predicateIri);
//					FieldSpec fieldBuilder = FieldSpec.builder(IRI.class, methodName+"_field", Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
//							.initializer("$T.getInstance().createIRI($S)", SimpleValueFactory.class, predicateString).build();
//					cb.addField(fieldBuilder);
					String qn = methodName.toUpperCase() + "_QUERY";
					FieldSpec qf = FieldSpec
							.builder(String.class, qn, Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
							.initializer(
									"\"SELECT ?object WHERE { GRAPH <$L> { ?id <$L> ?object . FILTER(datatype(?object) = <$L>)}}\"",
									graphName.stringValue(), predicateIri.stringValue(), datatypeB.stringValue())
							.build();
					cb.addField(qf);

					MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName);
					methodBuilder.addModifiers(Modifier.PUBLIC);
					CodeBlock.Builder cbb = CodeBlock.builder();
					Class<?> returnType = literalToClassMap.get(datatypeB);
					assert returnType != null : datatypeB.stringValue() + " not found in map";
					cbb.addStatement("$T tq = conn.prepareTupleQuery($L)", TupleQuery.class, qn);
					cbb.addStatement("tq.setBinding($S, id)", "id");
					cbb.addStatement(
							"return resultToStream(tq.evaluate(), " + returnTheRightKindOfData(returnType) + ")",
							Literal.class);
					methodBuilder.addCode(cbb.build());

					ParameterizedTypeName streamType = ParameterizedTypeName.get(ClassName.get(Stream.class),
							ClassName.get(returnType));
					methodBuilder.returns(streamType);
//					type
					cb.addMethod(methodBuilder.build());

				}
			}
		}
	}

	protected String coreMethodName(IRI datatypeB, IRI predicateIri) {
		String predicateString = predicateIri.getLocalName();
		{
			Optional<Namespace> first = ns.entrySet().stream().map(Entry::getValue)
					.filter((e) -> e.getName().equals(predicateIri.getNamespace())).findFirst();
			if (first.isPresent()) {
				predicateString = first.get().getPrefix() + "_" + predicateString;
			}
		}
		String methodNamePrefix = fixJavaKeywords(predicateString.substring(predicateString.lastIndexOf('/') + 1));
		String datatypeString = datatypeB.getLocalName();
		{
			Optional<Namespace> first = ns.entrySet().stream().map(Entry::getValue)
					.filter((e) -> e.getName().equals(datatypeB.getNamespace())).findFirst();
			if (first.isPresent()) {
				datatypeString = first.get().getPrefix() + "_" + datatypeString;
			}
		}
		String methodNamePostFix = fixJavaKeywords(datatypeString.substring(datatypeString.lastIndexOf('#') + 1));
		String methodName = methodNamePrefix + "_" + methodNamePostFix;
		return methodName;
	}

	public String returnTheRightKindOfData(Class<?> returnType) {
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
			return "(v) -> Year.of((($T) v).calendarValue().toGregorianCalendar().get(1))"; // 1 is the Calendar.YEAR constant
		} else if (returnType == YearMonth.class) {
			return "(v) -> { var cv = (($T) v).calendarValue().toGregorianCalendar(); return YearMonth.of(cv.get(1), cv.get(2)); }"; //as above but 2 is the month constant
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

	private Map<IRI, TypeSpec.Builder> buildTypeClasssesForAGraph(File outputDirectory, String name, String packageName,
			SailRepositoryConnection conn, IRI graphName) throws IOException {
		Map<IRI, TypeSpec.Builder> classBuilders = new HashMap<>();
		TupleQuery tq = conn.prepareTupleQuery(PREFIXES + """
				SELECT ?classPartition ?class
				WHERE {
					?graph sd:graph/void:classPartition ?classPartition .
					?classPartition void:class ?class .
				}
				""");
		tq.setBinding("graph", graphName);
		try (TupleQueryResult tqr = tq.evaluate()) {
			while (tqr.hasNext()) {
				BindingSet binding = tqr.next();
				Binding cIri = binding.getBinding("class");
				String className = cIri.getValue().stringValue();

				className = fixJavaKeywords(extract(className));

				TypeSpec.Builder classBuilder = TypeSpec.recordBuilder(className).addModifiers(Modifier.PUBLIC);
				classBuilder.recordConstructor(MethodSpec.constructorBuilder().addParameter(IRI.class, "id")
						.addParameter(RepositoryConnection.class, "conn").build());
//				FieldSpec graphField = FieldSpec
//						.builder(IRI.class, "graph", Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
//						.initializer("$T.getInstance().createIRI($S)", SimpleValueFactory.class,
//								graphName.stringValue())
//						.build();
//				classBuilder.addField(graphField);
				IRI classIri = (IRI) binding.getBinding("classPartition").getValue();
				classBuilders.put(classIri, classBuilder);
			}
		}
		return classBuilders;

	}

	String extract(String path) {
		try (Stream<Namespace> stream = ns.values().stream()) {
			Optional<Namespace> any = stream.filter(ns -> {
				return path.startsWith(ns.getName());
			}).findAny();
			if (any.isPresent()) {
				Namespace ns = any.get();
				return ns.getPrefix() + path.substring(ns.getName().length()).replace('.', '_');
			} else {
				// If there is no namespace, return the last part to have higher chance of
				// having a good class name.
				return path.substring(path.lastIndexOf('/') + 1).replace('.', '_');
			}
		}
	}

	private void buildClassForAGraph(File outputDirectory, String name, String packageName, IRI resource,
			RepositoryConnection conn) throws IOException {
		TypeSpec.Builder graphC = TypeSpec.recordBuilder(name).addModifiers(Modifier.PUBLIC);

		buildClassFindMethodsForAGraph(graphC, resource, conn);
		JavaFile.Builder javaFile = JavaFile.builder(packageName, graphC.build());
		javaFile.addStaticImport(ClassName.get(UTIL_PACKAGE, UTIL_CLASSNAME), "resultToStream");
		javaFile.build().writeTo(outputDirectory);
	}

	private void buildClassFindMethodsForAGraph(TypeSpec.Builder graphC, IRI graphName, RepositoryConnection conn) {
		graphC.recordConstructor(
				MethodSpec.constructorBuilder().addParameter(RepositoryConnection.class, "conn").build());
//				FieldSpec.builder(RepositoryConnection.class, "conn", Modifier.PRIVATE, Modifier.FINAL).build());
//		graphC.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC)
//				.addParameter(RepositoryConnection.class, "conn", Modifier.FINAL).addStatement("this.conn = conn")
//				.build());
		TupleQuery tq = conn.prepareTupleQuery(PREFIXES + """
				SELECT ?classPartition ?class
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
				String className = fixJavaKeywords(extract(cIri.getValue().stringValue()));

				ClassName type = ClassName.get("", className);
				ParameterizedTypeName returnType = ParameterizedTypeName.get(ClassName.get(Stream.class), type);
				MethodSpec.Builder mb = MethodSpec.methodBuilder("all" + className).returns(returnType)
						.addModifiers(Modifier.PUBLIC);

				String qn = "all_" + className + "_query";
				FieldSpec qf = FieldSpec.builder(String.class, qn, Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
						.initializer("\"SELECT ?object WHERE { GRAPH <$L> { ?object a <$L> }}\"",
								graphName.stringValue(), cIri.getValue().stringValue())
						.build();
				graphC.addField(qf);
				CodeBlock.Builder cbb = CodeBlock.builder();
				cbb.addStatement("$T tq = conn.prepareTupleQuery($L)", TupleQuery.class, qf.name());
				cbb.addStatement("return resultToStream(tq.evaluate(), (v) -> new $T(($T) v, conn))", type, IRI.class);

				mb.addCode(cbb.build());
				graphC.addMethod(mb.build());

			}
		}
	}

	StringBuilder getPackageName(URI uri) {
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
		return packageName;
	}

	private String fixJavaKeywords(String raw) {

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
