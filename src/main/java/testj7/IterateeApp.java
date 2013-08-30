package testj7;

import fj.*;
import fj.data.Stream;

import static fj.Bottom.error;
import static fj.Function.constant;
import static fj.data.Iteratee.Input;
import static fj.data.Iteratee.IterV;
import static fj.data.Stream.stream;

public class IterateeApp {
    public static void main( String[] args ) {

        final F<P2<Effect<String>, Input<String>>, P1<Boolean>> done = constant(P.p(true));
        final F<F<Input<String>, IterV<String, Effect<String>>>, P1<Boolean>> cont = constant(P.p(false));

        final F<IterV<String, Effect<String>>, Boolean> isDone =
            new F<IterV<String, Effect<String>>, Boolean>() {
                public Boolean f(final IterV<String, Effect<String>> i) {
                    return i.fold(done, cont)._1();
                }
            };

        final IterV<String, Effect<String>> iter = IterV.cont(new F<Input<String>, IterV<String, Effect<String>>>() {
            final F<Input<String>, IterV<String, Effect<String>>> step = this;
            public IterV<String, Effect<String>> f(final Input<String> input) {

                P1<IterV<String, Effect<String>>> empty = new P1<IterV<String, Effect<String>>>() {
                    public IterV<String, Effect<String>> _1() {
                        throw error("EMPTY !!");
                    }
                };

                P1<F<String, IterV<java.lang.String, Effect<String>>>> el =
                    new P1<F<String, IterV<String, Effect<String>>>>() {
                        public F<String, IterV<String, Effect<String>>> _1() {
                            return new F<String, IterV<String, Effect<String>>>() {
                                public IterV<String, Effect<String>> f(String s) {
                                    throw error("CONT !!");
                                }
                            };
                        }
                    };

                P1<IterV<java.lang.String, Effect<String>>> eof =
                    new P1<IterV<String, Effect<String>>>() {
                        public IterV<String, Effect<String>> _1() {
                            return IterV.<String, Effect<String>>done(
                                new Effect<String>() {
                                    public void e(String s) {
                                        System.out.println("DONE !! => " + s);
                                    }
                                },
                                input);
                        }
                    };

                return input.apply(empty, el, eof);
            }
        });

        final F<Stream<String>, F<IterV<String, Effect<String>>, Unit>> streamReader =
            new F<Stream<String>, F<IterV<String, Effect<String>>, Unit>>() {
                public F<IterV<String, Effect<String>>, Unit> f(final Stream<String> strings) {
                    return new F<IterV<String, Effect<String>>, Unit>() {
                        public Unit f(IterV<String, Effect<String>> it) {
                            return strings.foreach(it.run().e());
                        }
                    };
                }
            };

        streamReader.f(stream("toto", "tata", "tutu")).f(iter);
    }
}
