package local.cui;

public final class Main {
    public static void main(String[] args) {
        try {
            if (args.length == 0) throw new IllegalArgumentException("Use entity-a trust show | chain fetch/show/verify B/C [--json]");
            switch (args[0]) {
                case "init" -> Material.initialize();
                case "docs" -> Docs.start();
                case "serve" -> Publisher.start(args[1]);
                case "idle" -> Thread.currentThread().join();
                case "cli" -> System.exit(Reader.run(java.util.Arrays.copyOfRange(args, 1, args.length)));
                default -> throw new IllegalArgumentException("Unknown mode");
            }
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }
}
