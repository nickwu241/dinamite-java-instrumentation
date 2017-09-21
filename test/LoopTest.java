import com.craiig.jvm_method_trace.Agent;
import com.craiig.jvm_method_trace.Mtrace;

public class LoopTest {
	void test(int loop){
//		long start = System.nanoTime();
		for(int i=0; i<loop; i++){
			test(loop / 2);
		}
//		System.out.printf("loop done: %d\n", loop);
//		long end = System.nanoTime();
	}

	void test2(int loop){
		long start = System.nanoTime();
		for(int i=0; i<loop; i++){
			test2(loop / 2);
		}
		long end = System.nanoTime();
	}

	public static void main(String [] args){
		int loops = 128;
		LoopTest lt = new LoopTest();
		//Agent.setFilePath("./method.log");
		long start;
		long end;

		lt.test(loops);

		switch (args[0]) {
			case "baseline":
				start = System.nanoTime();
				lt.test(loops);
				end = System.nanoTime();
				System.out.printf("baseline: %dms\n", (end - start) / 1000000);

				start = System.nanoTime();
				lt.test(loops);
				end = System.nanoTime();
				System.out.printf("baseline: %dms\n", (end - start) / 1000000);
                                break;
			case "mtrace":
				Mtrace.start();
				start = System.nanoTime();
				lt.test(loops);
				end = System.nanoTime();
				Mtrace.stop();
				System.out.printf("JNI+bytecode instrumentation (enabled): %dms\n", (end - start) / 1000000);

				start = System.nanoTime();
				lt.test(loops);
				end = System.nanoTime();
				System.out.printf("JNI+bytecode instrumentation (disabled): %dms\n", (end - start) / 1000000);
				break;
			case "jvmti":
				Agent.start();
				start = System.nanoTime();
				lt.test(loops);
				Agent.stop();
				end = System.nanoTime();
				System.out.printf("JVMTI (enabled): %dms\n", (end - start) / 1000000);

				start = System.nanoTime();
				lt.test(loops);
				end = System.nanoTime();
				System.out.printf("JVMTI (disabled): %dms\n", (end - start) / 1000000);
				break;
                        default:
				start = System.nanoTime();
				lt.test(loops);
				end = System.nanoTime();

				System.out.printf("%s (disabled): %dms\n", args[0], (end - start) / 1000000);

                                Mtrace.start();
				start = System.nanoTime();
				lt.test(loops);
				end = System.nanoTime();
                                Mtrace.stop();

				System.out.printf("%s (enabled): %dms\n", args[0], (end - start) / 1000000);

				/*start = System.nanoTime();
				lt.test2(loops);
				end = System.nanoTime();
				System.out.printf("baseline (with time): %dms\n", (end - start) / 1000000);*/
				break;
		}
	}
}

