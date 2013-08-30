package testj7;

import fj.F2;
import fj.P1;
import fj.Show;
import fj.control.Trampoline;
import fj.data.List;
import fj.data.List.Buffer;

import static fj.data.List.Buffer.empty;
import static fj.data.List.cons_;

public class TrampoTest {

    static List<Integer> bigRange(int from, int to) {
        Buffer<Integer> buf = empty();
        int i = from;
        while (i <= to) {
            buf.snoc(i);
            i++;
        }
        return buf.toList();
    }

    static Trampoline<List<Integer>> trampoRange(final Integer from, final Integer to) {
        return Trampoline.suspend(new P1<Trampoline<List<Integer>>>() {
            public Trampoline<List<Integer>> _1() {
                return from >= to ? Trampoline.pure(List.<Integer>nil()) : trampoRange(from + 1, to).map(cons_(from));
            }
        });
    }

    public static void main(String[] args) {
        F2<Integer, Integer, Integer> plus = new F2<Integer, Integer, Integer>() {
            public Integer f(Integer i1, Integer i2) {
                return i1 + i2;
            }
        };

        Show.intShow.println(bigRange(1, 1000000).foldRightC(plus, 0).run());
        Show.intShow.println(trampoRange(1, 1000000).run().foldRightC(plus, 0).run());
        //Show.intShow.println(bigRange(1, 1000000).foldRight(plus, 0));
    }

}
