public class LoopTest {
    private void test(int loop) {
        for (int i = 0; i < loop; i++) {
            test(loop / 2);
        }
    }

    public static void bubbleSort(int[] numArray) {
        int n = numArray.length;
        int temp = 0;

        for (int i = 0; i < n; i++) {
            for (int j = 1; j < (n - i); j++) {
                if (numArray[j - 1] > numArray[j]) {
                    temp = numArray[j - 1];
                    numArray[j - 1] = numArray[j];
                    numArray[j] = temp;
                }
            }
        }
    }

    private native static void  nativeMethod();

    public static void main(String... args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "provide exactly ONE argument of 'helloworld' or 'baseline'");
        }

        switch (args[0]) {
            case "helloworld":
                System.out.println("Hello World");
                break;
            case "baseline":
                final int NUM_LOOPS = 4;

                LoopTest lt = new LoopTest();
                long start = System.nanoTime();
                lt.test(NUM_LOOPS);
                long end = System.nanoTime();
                System.out.printf("baseline: %dms\n", (end - start) / 1000000);

                start = System.nanoTime();
                lt.test(NUM_LOOPS);
                end = System.nanoTime();
                System.out.printf("baseline: %dms\n", (end - start) / 1000000);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unrecognized argument: " + args[0]);
        }
    }
}
