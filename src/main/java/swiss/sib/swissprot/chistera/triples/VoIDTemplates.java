package swiss.sib.swissprot.chistera.triples;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class VoIDTemplates {
    
        public static final String POM_TEMPLATE;

        public static final String PREFIXES;
        public static final String FIND_METHODS_FOR_CLASSES;
        public static final String FIND_DATATYPE_PARTITIONS;
        public static final String FIND_ALL_NAMED_GRAPHS;
        public static final String FIND_PARTITION_TUPLE_QUERY;

        static {
                String pomTemplate = null;
                try (InputStream is = VoIDTemplates.class.getResourceAsStream("/pom_template.xml")) {
                        pomTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException ex) {
                        ex.printStackTrace();
                }
                POM_TEMPLATE = pomTemplate;

                String prefixes = null;
                try (InputStream is = VoIDTemplates.class.getResourceAsStream("/prefixes.txt")) {
                        prefixes = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException ex) {
                        ex.printStackTrace();
                }
                PREFIXES = prefixes;

                String find_methods_for_classes = PREFIXES;
                try (InputStream is = VoIDTemplates.class.getResourceAsStream("/find_methods_for_classes.txt")) {
                        find_methods_for_classes += new String(is.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException ex) {
                        ex.printStackTrace();
                }
                FIND_METHODS_FOR_CLASSES = find_methods_for_classes;

                String find_datatype_partitions = PREFIXES;
                try (InputStream is = VoIDTemplates.class.getResourceAsStream("/find_datatype_partitions.txt")) {
                        find_datatype_partitions += new String(is.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException ex) {
                        ex.printStackTrace();
                }
                FIND_DATATYPE_PARTITIONS = find_datatype_partitions;

                String find_all_named_graphs = PREFIXES;
                try (InputStream is = VoIDTemplates.class.getResourceAsStream("/find_all_named_graphs.txt")) {
                        find_all_named_graphs += new String(is.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException ex) {
                        ex.printStackTrace();
                }
                FIND_ALL_NAMED_GRAPHS = find_all_named_graphs;
                
                String find_partition_tuple_query = PREFIXES;
                try (InputStream is = VoIDTemplates.class.getResourceAsStream("/find_partition_tuple_query.txt")) {
                        find_partition_tuple_query += new String(is.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException ex) {
                        ex.printStackTrace();
                }
                FIND_PARTITION_TUPLE_QUERY = find_partition_tuple_query;

        }
}
