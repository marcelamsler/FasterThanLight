import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Kirusanth Poopalasingam ( pkirusanth@gmail.com )
 */
class Playground {
    public void testName()  {
        List<String> s = Arrays.asList("Ja", "Nein");

        System.out.println("first");
        s.forEach(System.out::println);

        System.out.println("second");
        System.out.println("ja");

        System.out.println("third");
        s.forEach(new Consumer<String>() {
            @Override
            public void accept(String s) {
                System.out.println(s);
            }
        });

        new Test(){
            @Override
            public boolean isEqual(int a, int b) {
                return false;
            }

        };
    }

    public interface Test{
        boolean isEqual(int a, int b);

        default boolean isNotEqual(int a, int b){
            return Playground.doStuff(a, b);
        }
    }

    private static boolean doStuff(int a, int b) {
        return false;
    }
}
