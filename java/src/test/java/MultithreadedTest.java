// code from http://beginnersbook.com/2013/03/multithreading-in-java/
class Count extends Thread
{
    private int count;
    Count(int count)
    {
        super("my extending thread");
        this.count = count;
        System.out.println("my thread created" + this);
        start();
    }

    public void run()
    {
        for (int i=0 ;i<count;i++)
        {
            System.out.println("count Thread count: " + i);
        }
    }
}

class MultithreadedTest
{
    private static void test() {
        for (int i=0 ;i<200;i++)
        {
            System.out.println("main Thread count: " + i);
        }
    }

    public static void main(String args[])
    {
        new Count(10);
        test();
    }
}