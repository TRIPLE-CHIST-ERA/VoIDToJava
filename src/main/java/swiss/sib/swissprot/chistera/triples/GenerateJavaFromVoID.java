package swiss.sib.swissprot.chistera.triples;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.model.vocabulary.XSD.Datatype;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeSpec.Builder;

public class GenerateJavaFromVoID {
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
		makePackages(ms, outputDirectory);
		ms.shutDown();
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
					String name = fixJavaKeywords(path.substring(path.lastIndexOf('/') + 1)) + "Graph";
					String packageName = getPackageName(uri).toString();
					buildClassForAGraph(outputDirectory, name, packageName);
					Map<IRI, Builder> buildTypeClasssesForAGraph = buildTypeClasssesForAGraph(outputDirectory, name,
							packageName, conn, resource);
					buildMethodsOnTypesInAGraph(outputDirectory, name, packageName, conn, resource,
							buildTypeClasssesForAGraph);
					for (Builder b : buildTypeClasssesForAGraph.values()) {
						JavaFile javaFile = JavaFile.builder(packageName, b.build()).build();
						javaFile.writeTo(outputDirectory);
					}
				}
			}
		}

	}

	private void buildMethodsOnTypesInAGraph(File outputDirectory, String name, String packageName,
			SailRepositoryConnection conn, IRI graphName, Map<IRI, Builder> classBuilders) {

		buildMethodsReturningObjects(conn, graphName, classBuilders);
		buildMethodsReturningLiterals(conn, graphName, classBuilders);
	}

	public void buildMethodsReturningObjects(SailRepositoryConnection conn, IRI graphName,
			Map<IRI, Builder> classBuilders) {
		TupleQuery tq = conn.prepareTupleQuery(PREFIXES + """
				SELECT ?classPartition ?classType ?predicate ?classType2 ?class2Partition
				WHERE {
				  ?graph sd:graph/void:classPartition ?classPartition .
				  ?classPartition void:class ?classType .
				  ?classPartition void:propertyPartition ?predicatePartition .
				  ?predicatePartition void:property ?predicate .
				  ?predicatePartition void:classPartition ?class2Partition .
				  ?class2Partition void:class ?classType2 .
				}
				""");
		tq.setBinding("graph", graphName);
		for (Entry<IRI, Builder> en : classBuilders.entrySet()) {
			tq.setBinding("classPartition", en.getKey());
			try (TupleQueryResult tqr = tq.evaluate()) {
				while (tqr.hasNext()) {
					BindingSet binding = tqr.next();
					Binding predicate = binding.getBinding("predicate");
					Binding classToAddTo = binding.getBinding("classPartition");
					Binding otherClass = binding.getBinding("class2Partition");
					
					if (otherClass == null) {
						
					}
					Builder cb = classBuilders.get(classToAddTo.getValue());
					assert cb != null : "ClassBuilder not found for " + otherClass.getValue().stringValue();
					String predicateString = predicate.getValue().stringValue();
					String methodName = fixJavaKeywords(
							predicateString.substring(predicateString.lastIndexOf('/') + 1));
					MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(fixJavaKeywords(methodName));
					methodBuilder.addModifiers(Modifier.PUBLIC);
					Builder v = classBuilders.get(classToAddTo.getValue());

					methodBuilder.returns(Void.class);
//					type
					cb.addMethod(methodBuilder.build());
				}
			}
		}
	}
	
	public void buildMethodsReturningLiterals(SailRepositoryConnection conn, IRI graphName,
			Map<IRI, Builder> classBuilders) {
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
		for (Entry<IRI, Builder> en : classBuilders.entrySet()) {
			tq.setBinding("classPartition", en.getKey());
			try (TupleQueryResult tqr = tq.evaluate()) {
				while (tqr.hasNext()) {
					BindingSet binding = tqr.next();
					Binding predicate = binding.getBinding("predicate");
					Binding classToAddTo = binding.getBinding("classPartition");
					IRI datatypeB = (IRI) binding.getBinding("datatype").getValue();
					
					
					Builder cb = classBuilders.get(classToAddTo.getValue());
					assert cb != null : "ClassBuilder not found for " + classToAddTo.getValue().stringValue();
					String predicateString = predicate.getValue().stringValue();
					String methodName = fixJavaKeywords(
							predicateString.substring(predicateString.lastIndexOf('/') + 1));
					MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(fixJavaKeywords(methodName));
					methodBuilder.addModifiers(Modifier.PUBLIC);
					Builder v = classBuilders.get(classToAddTo.getValue());

					methodBuilder.returns(literalToClassMap.get(datatypeB));
//					type
					cb.addMethod(methodBuilder.build());
				}
			}
		}
	}
	
	private Map<IRI, Class<?>> literalToClassMap = Map.of(XSD.STRING, String.class, XSD.INT, Integer.class, XSD.INTEGER, Integer.class);

	private Map<IRI, Builder> buildTypeClasssesForAGraph(File outputDirectory, String name, String packageName,
			SailRepositoryConnection conn, IRI graphName) throws IOException {
		Map<IRI, Builder> classBuilders = new HashMap<>();
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
//				String className = fixJavaKeywords(path.substring(path.lastIndexOf('/') + 1));
				//
				// String classPackageName = getPackageName(URI.create(path)).toString();

				className = fixJavaKeywords(extract(className));

				Builder classBuilder = TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC, Modifier.FINAL);
				IRI classIri = (IRI) binding.getBinding("classPartition").getValue();
				classBuilders.put(classIri, classBuilder);
				System.err.println("class: " + className + " created " + classIri);
//				var jc = classBuilder.build();
//				
//				JavaFile javaFile = JavaFile.builder(packageName, jc).build();
//				javaFile.writeTo(outputDirectory);
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
				return ns.getPrefix() + path.substring(ns.getName().length());
			} else {
				// If there is no namespace, return the last part to have higher chance of
				// having a good class name.
				return path.substring(path.lastIndexOf('/') + 1);
			}
		}
	}

	private void buildClassForAGraph(File outputDirectory, String name, String packageName) throws IOException {
		TypeSpec helloWorld = TypeSpec.classBuilder(name).addModifiers(Modifier.PUBLIC, Modifier.FINAL).build();

		JavaFile javaFile = JavaFile.builder(packageName, helloWorld).build();
		javaFile.writeTo(outputDirectory);
	}

	StringBuilder getPackageName(URI uri) {
		StringBuilder packageName = new StringBuilder();
		if (uri.getHost() != null) {
			List<String> hostPartsAsList = Arrays.asList(uri.getHost().split("\\."));
			for (int i = hostPartsAsList.size() - 1; i >= 0; i--) {
				packageName.append(fixJavaKeywords(hostPartsAsList.get(i)));
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
				packageName.append(fixJavaKeywords(partsDirsAsList.get(i)));
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
