package craft;

public class Main {
    public static void main(String[] args) {
        Long seed = null;
        if (args.length > 0) {
            seed = parseSeed(args[0]);
        } else if (System.getProperty("craft.shot") != null && System.getProperty("craft.menu") == null) {
            seed = System.nanoTime();   // autopilot runs skip the menus unless craft.menu is set
        }
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
