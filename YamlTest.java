import java.io.*;
import java.util.*;
import org.yaml.snakeyaml.*;

public final class YamlTest {
    public static void main(String[] args) throws IOException {
        try (FileInputStream input = new FileInputStream(args[0])) {
            Yaml yaml = new Yaml();

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) yaml.load(input);
            
            System.out.println("Read yaml keys");
            data.forEach((k, v) -> {
                System.out.println(k);
            });
        }
    }
}

