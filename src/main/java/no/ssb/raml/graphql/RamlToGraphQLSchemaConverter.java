package no.ssb.raml.graphql;

import graphql.introspection.Introspection;
import graphql.scalars.ExtendedScalars;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaPrinter;
import org.raml.v2.api.RamlModelBuilder;
import org.raml.v2.api.RamlModelResult;
import org.raml.v2.api.model.v10.api.Library;
import org.raml.v2.api.model.v10.datamodel.ObjectTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static no.ssb.raml.utils.DirectoryUtils.createFolder;
import static no.ssb.raml.utils.DirectoryUtils.resolveRelativeFilePath;

public class RamlToGraphQLSchemaConverter {

    private static String printUsage() {
        return String.format("Usage: java raml-to-graphql-schema.jar OUTFOLDER FILE|FOLDER [FILE|FOLDER]...%n"
                + "Convert all raml FILE(s) and all raml files in FOLDER(s) to GraphQL Schema and put in OUTFOLDER" +
                ".%n%n");
    }

    public static void main(String[] args) throws IOException {
        String output = convertSchemas(args);
        System.out.println(output);
    }

    static String convertSchemas(String[] args) throws IOException {

        //check arguments are passed while running jar
        if (args.length < 2) {
            return printUsage();
        }

        Path outputFolder = resolveRelativeFilePath(args[0]);

        //create output folder to store converted GraphQL schema
        Path outputFolderPath = createFolder(outputFolder);

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            Path path = Paths.get(arg);

            if (!Files.exists(path)) {
                System.err.format("Parameter '%s' does not exist on the file-system.\n", arg);
                continue;
            }
            if (!Files.isReadable(path)) {
                System.err.format("File or folder '%s' cannot be read.\n", arg);
                continue;
            }
            if (!(Files.isRegularFile(path) || Files.isDirectory(path))) {
                System.err.format("Parameter '%s' is not a file or directory.\n", arg);
                continue;
            }
            try {
                convertToGraphQLSchema(outputFolderPath, path);
            } catch (RuntimeException e) {
                System.err.println("FILE: " + arg);
                throw e;
            }
        }

        return "";
    }

    public static String convertToGraphQLSchema(Path outputFolder, Path root) throws IOException {

        List<TypeDeclaration> models = new ArrayList<>();
        Files.walkFileTree(root, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try (FileReader fileReader = new FileReader(file.toFile())) {
                    RamlModelResult modelResult = new RamlModelBuilder().buildApi(
                            fileReader,
                            file.toAbsolutePath().toString()
                    );
                    Library library = modelResult.getLibrary();
                    if (library != null) {
                        models.addAll(library.types());
                    }
                    return FileVisitResult.CONTINUE;
                }
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.TERMINATE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        GraphQLSchema.Builder schema = GraphQLSchema.newSchema();

        RuntimeWiring.newRuntimeWiring()
                .scalar(ExtendedScalars.DateTime)
                .scalar(ExtendedScalars.Date)
                .scalar(ExtendedScalars.Time);

        GraphQLOutputTypeVisitor visitor = new GraphQLOutputTypeVisitor();

        SchemaPrinter printer = new SchemaPrinter(SchemaPrinter.Options.defaultOptions()
                .includeScalarTypes(true)
                .includeDirectives(true)
                .includeExtendedScalarTypes(true)
        );

        GraphQLObjectType.Builder query = GraphQLObjectType.newObject().name("Query");

        Set<GraphQLType> types = new HashSet<>();

        // TODO: Find a better way to detect interfaces.
        Set<String> interfaces = new HashSet<>();
        for (TypeDeclaration model : models) {
            if (model instanceof ObjectTypeDeclaration) {
                ObjectTypeDeclaration objectTypeDeclaration = (ObjectTypeDeclaration) model;
                for (TypeDeclaration parentType : objectTypeDeclaration.parentTypes()) {
                    interfaces.add(parentType.name());
                }
            }
        }
        models.removeIf(typeDeclaration -> interfaces.contains(typeDeclaration.name()));

        for (TypeDeclaration typeDeclaration : models) {
            GraphQLOutputType type = visitor.visit(typeDeclaration);
            types.add(type);
            query.field(GraphQLFieldDefinition.newFieldDefinition()
                    .name(type.getName() + "ById")
                    .type(GraphQLList.list(type))
                    .build());
        }

        schema.additionalDirective(GraphQLDirective.newDirective()
                .name("domain")
                .validLocation(Introspection.DirectiveLocation.OBJECT)
                .build()).additionalDirective(GraphQLDirective.newDirective()
                .name("link")
                .validLocation(Introspection.DirectiveLocation.FIELD_DEFINITION)
                .build());

        schema.additionalTypes(types);
        schema.additionalType(ExtendedScalars.DateTime);
        schema.additionalType(ExtendedScalars.Date);
        schema.additionalType(ExtendedScalars.Time);
        schema.query(query);
        System.out.println(printer.print(schema.build()));

        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(outputFolder.resolve("schema.graphql").toFile()), StandardCharsets.UTF_8))) {
            writer.write(printer.print(schema.build()));
        }
        return printer.print(schema.build());
    }
}
