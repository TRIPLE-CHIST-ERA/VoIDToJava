package swiss.sib.swissprot.chistera.triples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
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
		generate.convert(GenerateJavaFromVoID.class.getResourceAsStream("/tutorial.ttl"), tempDir);
		try (Stream<Path> list = Files.list(tempDir.toPath())) {
			assertTrue(list.findAny().isPresent());
		}
	}

	@Test
	public void testPackageNames() {
		String s = new GenerateJavaFromVoID().getPackageName(URI.create("https://sparql.uniprot.org/uniprot"))
				.toString();
		assertEquals("org.uniprot.sparql.uniprot", s);

	}
}
