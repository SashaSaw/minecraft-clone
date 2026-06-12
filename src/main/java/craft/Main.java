package craft;

public class Main {
    public static void main(String[] args) {
        long seed = args.length > 0 ? parseSeed(args[0]) : System.nanoTime();
        new Game(seed).run();
    }

    private static long parseSeed(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return s.hashCode();
        }
    }
}
