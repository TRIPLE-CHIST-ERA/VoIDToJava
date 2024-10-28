package swiss.sib.swissprot.chistera.triples;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.base.CoreDatatype.RDF;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
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
		try (InputStream inputVoid = Files.newInputStream(Path.of(input))) {
			new GenerateJavaFromVoID().convert(inputVoid, new File(output));
		} catch (RDFParseException | RepositoryException | IOException e) {
			e.printStackTrace();
		}
	}

	public void convert(InputStream inputVoid, File outputDirectory)
			throws RDFParseException, RepositoryException, IOException {
		SailRepository ms = new SailRepository(new MemoryStore());
		ms.init();
		try (SailRepositoryConnection conn = ms.getConnection()) {
			conn.begin();
			conn.add(inputVoid, RDFFormat.TURTLE);
			conn.commit();

		}
		try (SailRepositoryConnection conn = ms.getConnection()) {
			RepositoryResult<Namespace> namespaces = conn.getNamespaces();

			try (Stream<Namespace> s = namespaces.stream()) {
				s.forEach((n) -> ns.put(n.getName(), n));
			}
		}
		makeUtils(ms, outputDirectory);
		makePackages(ms, outputDirectory);
		ms.shutDown();
	}

	private void makeUtils(SailRepository ms, File outputDirectory) throws IOException {
		
		TypeSpec.Builder b = TypeSpec.classBuilder(UTIL_CLASSNAME).addModifiers(Modifier.PUBLIC, Modifier.FINAL);
		TypeVariableName returnTypeBound = TypeVariableName.get("T");
		MethodSpec.Builder mb = MethodSpec.methodBuilder("iteratorToStream");
		mb.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
		mb.addParameter(RepositoryResult.class, "result");
		TypeName value = TypeVariableName.get(Value.class);
		
		ParameterSpec.Builder fb = ParameterSpec.builder(ParameterizedTypeName.get(ClassName.get(Function.class), value, returnTypeBound), "transformer");
		fb.addModifiers(Modifier.FINAL);
		mb.addParameter(fb.build());
		mb.addTypeVariable(returnTypeBound);
		ParameterizedTypeName returnType = ParameterizedTypeName.get(ClassName.get(Stream.class), returnTypeBound);
		
		mb.returns(returnType);
		mb.addStatement("return stream.map((bs) -> bs.getBinding(\"object\")).map($T::getValue)", Binding.class);
		
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
					String path = uri.getPath();
					String name = "Graph";
					String packageName = getPackageName(uri).toString();
					buildClassForAGraph(outputDirectory, name, packageName, resource, conn);
					Map<IRI, TypeSpec.Builder> buildTypeClasssesForAGraph = buildTypeClasssesForAGraph(outputDirectory, name,
							packageName, conn, resource);
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
		buildIdField(conn, graphName, classBuilders);
		buildMethodsReturningObjects(conn, graphName, classBuilders);
		buildMethodsReturningLiterals(conn, graphName, classBuilders);
	}

	private void buildIdField(SailRepositoryConnection conn, IRI graphName, Map<IRI, TypeSpec.Builder> classBuilders) {
		for (Entry<IRI, TypeSpec.Builder> en : classBuilders.entrySet()) {
			TypeSpec.Builder cb = en.getValue();
			cb.addField(IRI.class, "id", Modifier.PRIVATE, Modifier.FINAL);
			cb.addField(RepositoryConnection.class, "conn", Modifier.PRIVATE, Modifier.FINAL);
			cb.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC)
					.addParameter(IRI.class, "id", Modifier.FINAL).addStatement("this.id = id")
					.addParameter(RepositoryConnection.class, "conn", Modifier.FINAL).addStatement("this.conn = conn").build());
		}
	}

	public void buildMethodsReturningObjects(SailRepositoryConnection conn, IRI graphName,
			Map<IRI, TypeSpec.Builder> classBuilders) {
		TupleQuery tq = conn.prepareTupleQuery(PREFIXES + """
				SELECT ?classPartition ?classType ?predicate ?class2Partition
				WHERE {
				  ?graph sd:graph/void:classPartition ?classPartition .
				  ?classPartition void:class ?classType .
				  ?classPartition void:propertyPartition ?predicatePartition .
				  ?predicatePartition void:property ?predicate .
				  [] a void:Linkset ;
				  	 void:objectsTarget ?class2Partition ;
				  	 void:linkPredicate ?predicate ;
				  	 void:subjectsTarget ?classPartition ;
				  	 void:subset ?graph .
				}
				""");
		tq.setBinding("graph", graphName);
		for (Entry<IRI, TypeSpec.Builder> en : classBuilders.entrySet()) {
			tq.setBinding("classPartition", en.getKey());
			TypeSpec.Builder cb = classBuilders.get(en.getKey());
		
			try (TupleQueryResult tqr = tq.evaluate()) {
				while (tqr.hasNext()) {
					BindingSet binding = tqr.next();
					Binding predicate = binding.getBinding("predicate");
					Binding otherClass = binding.getBinding("class2Partition");

					if (otherClass == null) {

					}
					assert cb != null : "ClassBuilder not found for " + otherClass.getValue().stringValue();
					String predicateString = predicate.getValue().stringValue();
					String methodName = fixJavaKeywords(
							predicateString.substring(predicateString.lastIndexOf('/') + 1));
					MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(fixJavaKeywords(methodName));
					methodBuilder.addModifiers(Modifier.PUBLIC);
					TypeSpec.Builder v = classBuilders.get(otherClass.getValue());
					
					ClassName returnType = ClassName.get("", v.build().name());
					ParameterizedTypeName streamType = ParameterizedTypeName.get(ClassName.get(Stream.class), returnType);
					methodBuilder.returns(streamType);
					CodeBlock.Builder cbb = CodeBlock.builder();
//					FieldSpec fieldBuilder = FieldSpec.builder(IRI.class, fixJavaKeywords(methodName)+"_field", Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
//							.initializer("$T.getInstance().createIri($S)", SimpleValueFactory.class, predicateString).build();
//					
//					cb.addField(fieldBuilder);
					String qn = methodName+"_query";
					FieldSpec qf = FieldSpec.builder(String.class, qn, Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
							.initializer("\"SELECT ?object WHERE { GRAPH <$L> { ?id <$L> ?object . FILTER(isIRI(?object))}}\"", graphName.stringValue(), predicateString)
							.build();
					cb.addField(qf);			
					
					cbb.addStatement("$T tq = conn.prepareTupleQuery($L)", TupleQuery.class, qf.name());
					cbb.addStatement("tq.setBinding($S, id)", "id");
					cbb.addStatement("return iteratatorToStream(tq.evaluate(), (v) -> new $T(($T) v, conn))", returnType, IRI.class);

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
		tq.setBinding("graph", graphName);
		for (Entry<IRI, TypeSpec.Builder> en : classBuilders.entrySet()) {
			tq.setBinding("classPartition", en.getKey());
			try (TupleQueryResult tqr = tq.evaluate()) {
				while (tqr.hasNext()) {
					BindingSet binding = tqr.next();
					Binding predicate = binding.getBinding("predicate");
					Binding classToAddTo = binding.getBinding("classPartition");
					IRI datatypeB = (IRI) binding.getBinding("datatype").getValue();
					TypeSpec.Builder cb = classBuilders.get(classToAddTo.getValue());
					

					
					assert cb != null : "ClassBuilder not found for " + classToAddTo.getValue().stringValue();
					String predicateString = predicate.getValue().stringValue();
					String methodName = fixJavaKeywords(
							predicateString.substring(predicateString.lastIndexOf('/') + 1));
					

//					FieldSpec fieldBuilder = FieldSpec.builder(IRI.class, methodName+"_field", Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
//							.initializer("$T.getInstance().createIri($S)", SimpleValueFactory.class, predicateString).build();
//					cb.addField(fieldBuilder);
					
					String qn = methodName+"_query";
					FieldSpec qf = FieldSpec.builder(String.class, qn, Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
							.initializer("\"SELECT ?object WHERE { GRAPH <$L> { ?id <$L> ?object . FILTER(datatype(?object) = <$L>)}}\"", graphName.stringValue(), predicateString, datatypeB.stringValue())
							.build();
					cb.addField(qf);
					
					
					MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName);
					methodBuilder.addModifiers(Modifier.PUBLIC);
					CodeBlock.Builder cbb = CodeBlock.builder();
					Class<?> returnType = literalToClassMap.get(datatypeB);
					cbb.addStatement("$T tq = conn.prepareTupleQuery($L)", TupleQuery.class, qn);
					cbb.addStatement("tq.setBinding($S, id)", "id");
					cbb.addStatement("return resultToStream(tq.evaluate(), "+returnTheRightKindOfData(returnType)+")");
					methodBuilder.addCode(cbb.build());

					ParameterizedTypeName streamType = ParameterizedTypeName.get(ClassName.get(Stream.class), ClassName.get(returnType));
					methodBuilder.returns(streamType);
//					type
					cb.addMethod(methodBuilder.build());
					
				}
			}
		}
	}

	public String returnTheRightKindOfData(Class<?> returnType) {
		if (returnType == String.class) {
			return "Value::stringValue";
		} else if (returnType == Integer.class){
			return "Value::integerValue";
		} else if (returnType == Boolean.class) {
			return "Value::booleanValue";
		} else if (returnType == Double.class) {
			return "Value::doubleValue";
		} else if (returnType == Float.class) {
			return "Value::floatValue";
		} else if (returnType == Short.class) {
			return "Value::shortValue";
		} else if (returnType == Long.class) {
			return "Value::longValue";
		} else if (returnType == Byte.class) {
			return "Value::byteValue";
		} else if (returnType == URI.class) {
			return "Value::uriValue";
		} else if (returnType == LocalDate.class) {
			return "(v) -> v.calendarValue().toGregorianCalendar().toZonedDateTime().toLocalDate()";
		} else if (returnType == LocalDateTime.class) {
			return "(v) -> v.calendarValue().toGregorianCalendar().toZonedDateTime().toLocalDateTime()";
		} else if (returnType == Instant.class) {
			return "(v) -> v.getValue().calendarValue().toGregorianCalendar().toZonedDateTime().toInstant()";
		} else if (returnType == Duration.class) {
			return "(v) -> v.object.getValue().calendarValue().toGregorianCalendar().toZonedDateTime().toInstant().toEpochMilli()";
		} else if (returnType == Year.class) {
			return "(v) -> v..calendarValue().toGregorianCalendar().toZonedDateTime().toLocalDate().getYear()";
		} else if (returnType == YearMonth.class) {
			return "(v) -> v.calendarValue().toGregorianCalendar().toZonedDateTime().toLocalDate().getYear()";
		} else {
			return "Value::stringValue";
		}
	}

	// TODO needs to be expanded to all known literals. And lang string needs to be considered.

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
			Map.entry(XSD.SHORT, Short.class),
			Map.entry(RDF.LANGSTRING.getIri(), String.class));

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

				TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC, Modifier.FINAL);
				FieldSpec graphfield = FieldSpec.builder(IRI.class, "graph", Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC).initializer("$T.getInstance().createIri($S)", SimpleValueFactory.class, graphName.stringValue()).build();
				classBuilder.addField(graphfield);
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

	private void buildClassForAGraph(File outputDirectory, String name, String packageName, IRI resource, RepositoryConnection conn) throws IOException {
		TypeSpec.Builder graphC = TypeSpec.classBuilder(name).addModifiers(Modifier.PUBLIC, Modifier.FINAL);
		
		buildClassFindMethodsForAGraph(graphC, resource, conn);

		JavaFile javaFile = JavaFile.builder(packageName, graphC.build()).build();
		javaFile.writeTo(outputDirectory);
	}

	private void buildClassFindMethodsForAGraph(TypeSpec.Builder graphC, IRI graphName, RepositoryConnection conn) {
		graphC.addField(FieldSpec.builder(RepositoryConnection.class, "conn", Modifier.PRIVATE, Modifier.FINAL).build());
		graphC.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC)
				.addParameter(RepositoryConnection.class, "conn", Modifier.FINAL).addStatement("this.conn = conn").build());
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
				MethodSpec.Builder mb = MethodSpec.methodBuilder("all"+className).returns(returnType).addModifiers(Modifier.PUBLIC);
				
				String qn = "all_"+className+"_query";
				FieldSpec qf = FieldSpec.builder(String.class, qn, Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
						.initializer("\"SELECT ?object WHERE { GRAPH <$L> { ?object a <$L> }}\"", graphName.stringValue(), cIri.getValue().stringValue())
						.build();
				graphC.addField(qf);
				CodeBlock.Builder cbb = CodeBlock.builder();
				cbb.addStatement("$T tq = conn.prepareTupleQuery($L)", TupleQuery.class, qf.name());
				cbb.addStatement("return iteratatorToStream(tq.evaluate(), (v) -> new $T(($T) v, conn))", type, IRI.class);

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
