package csc201.gsms;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public final class Main {

    public static void main(String[] args) throws IOException {
        List<String> files = new ArrayList<>();

        if (args.length > 0) {
            files.addAll(Arrays.asList(args));
        } else {
            Path list = Paths.get("input_files.txt");
            if (Files.exists(list)) {
                files = Files.readAllLines(list).stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                        .collect(Collectors.toList());
            } else {
                System.err.println("""
                        Usage:
                          java csc201.gsms.Main <file1> <file2> ...
                        or place paths one per line in input_files.txt
                        """);
                return;
            }
        }

        Processor p = new Processor();
        boolean first = true;
        for (String f : files) {
            if (!first) System.out.println(); // blank line between different files' outputs
            first = false;
            p.processFile(f);
        }
    }
}
