package swiss.sib.swissprot.chistera.triples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GenerateJavaFromVoIDTest {

	@TempDir
	public File tempDir;

	@Test
	public void testTutorial() throws IOException {
		GenerateJavaFromVoID generate = new GenerateJavaFromVoID();
		URL resource = GenerateJavaFromVoID.class.getResource("/tutorial.ttl");
		generate.convert(new File(resource.getPath()), tempDir);
		try (Stream<Path> list = Files.list(tempDir.toPath())) {
			assertTrue(list.findAny().isPresent());
		}
	}

	@Test
	public void testPackageNames() {
		GenerateJavaFromVoID g = new GenerateJavaFromVoID();
		String s = g.getPackageName(URI.create("https://sparql.uniprot.org/uniprot"))
				.toString();
		assertEquals("org.uniprot.sparql.uniprot", s);
		
		String s2 = g.getPackageName(URI.create("http://www.w3.org/2002/07/owl#"))
				.toString();
		
		assertEquals("org.w3.www._2002._07.owl", s2);
	}
}
