package no.ssb.raml.graphql;

import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class RamltoGraphQLSchemaConverterTest {
    @Test
    public void thatPrintUsageWorks() throws IOException {
        String usage = RamlToGraphQLSchemaConverter.convertSchemas(new String[]{"too-few-arguments"});
        assertTrue(usage.startsWith("Usage: "));
    }

    @Test
    public void thatMainConvertsSchemas() throws IOException {
        String outputFolder = "target/schemas";
        deleteFolder(Paths.get(outputFolder));

        String result = RamlToGraphQLSchemaConverter.convertSchemas(new String[]{outputFolder, "src/test/resources/raml/schemas"});

        assertEquals(result, "");
        assertTrue(Files.exists(Paths.get(outputFolder, "schema.graphql")));
    }

    @Test
    public void thatMainConvertsEmbeddedSchemas() throws IOException {
        String outputFolder = "target/embedded-schemas";
        deleteFolder(Paths.get(outputFolder));

        String result = RamlToGraphQLSchemaConverter.convertSchemas(new String[]{outputFolder, "src/test/resources/raml/embeddedobject/schemas"});

        assertEquals(result, "");
        assertTrue(Files.exists(Paths.get(outputFolder, "schema.graphql")));
    }

    private void deleteFolder(Path pathToBeDeleted) throws IOException {
        if (Files.exists(pathToBeDeleted)) {
            Files.walk(pathToBeDeleted)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            assertFalse(Files.exists(pathToBeDeleted));
        }
    }
}
