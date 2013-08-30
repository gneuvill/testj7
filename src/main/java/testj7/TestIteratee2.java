package testj7;

import fj.*;
import fj.control.Trampoline;
import fj.data.Array;
import fj.data.Either;
import fj.data.Stream;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.io.ICsvBeanWriter;
import org.supercsv.prefs.CsvPreference;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Paths;

import static fj.data.Array.array;
import static fj.data.Either.left;
import static fj.data.Either.right;
import static fj.data.Iteratee.Input;
import static fj.data.Iteratee.Input.el;
import static fj.data.Iteratee.IterV;
import static fj.data.Iteratee.IterV.cont;
import static fj.data.Iteratee.IterV.done;
import static fj.data.Stream.cycle;
import static fj.data.Stream.stream;
import static testj7.TestIteratee2.Person.pers;

class TestIteratee2 {

    /**
     * Takes a list and an iteratee and feeds the list’s elements to the iteratee.
     */
    static F2<Stream<String>, IterV<String, Integer>, IterV<String, Integer>> oldEnumerate =
        new F2<Stream<String>, IterV<String, Integer>, IterV<String, Integer>>() {
            public IterV<String, Integer> f(final Stream<String> xs, final IterV<String, Integer> i) {
                if (xs.isEmpty()) return i;
                else return i.fold(new F<P2<Integer, Input<String>>, IterV<String, Integer>>() {
                                       public IterV<String, Integer> f(P2<Integer, Input<String>> t) {
                                           return i;
                                       }
                                   }, new F<F<Input<String>, IterV<String, Integer>>, IterV<String, Integer>>() {
                                       public IterV<String, Integer> f(F<Input<String>, IterV<String, Integer>> fi) {
                                           return oldEnumerate.f(xs.tail()._1(), fi.f(el(xs.head())));
                                       }
                                   }
                );
            }
        };

    static F2<Stream<String>, IterV<String, Integer>, Trampoline<IterV<String, Integer>>> enumerate =
        new F2<Stream<String>, IterV<String, Integer>, Trampoline<IterV<String, Integer>>>() {
            public Trampoline<IterV<String, Integer>> f(final Stream<String> xs, final IterV<String, Integer> i) {
                if (xs.isEmpty()) return Trampoline.pure(i);
                else return Trampoline.suspend(new P1<Trampoline<IterV<String, Integer>>>() {
                    public Trampoline<IterV<String, Integer>> _1() {
                        return i.fold(new F<P2<Integer, Input<String>>, Trampoline<IterV<String, Integer>>>() {
                                          public Trampoline<IterV<String, Integer>> f(P2<Integer, Input<String>> t) {
                                              return Trampoline.pure(i);
                                          }
                                      }, new F<F<Input<String>, IterV<String, Integer>>, Trampoline<IterV<String, Integer>>>() {
                                          public Trampoline<IterV<String, Integer>> f(F<Input<String>, IterV<String, Integer>> fi) {
                                              return enumerate.f(xs.tail()._1(), fi.f(el(xs.head())));
                                          }
                                      }
                        );
                    }
                });
            }
        };

    /**
     * An iteratee that counts the number of strings it has seen
     */
    static IterV<String, Integer> counter() {
        final class Step {
            F<Integer, F<Input<String>, IterV<String, Integer>>> step =
                new F<Integer, F<Input<String>, IterV<String, Integer>>>() {
                    public F<Input<String>, IterV<String, Integer>> f(final Integer n) {
                        return new F<Input<String>, IterV<String, Integer>>() {
                            public IterV<String, Integer> f(final Input<String> i) {
                                return i.apply(
                                    new P1<IterV<String, Integer>>() {
                                        public IterV<String, Integer> _1() {
                                            return cont(step.f(n));
                                        }
                                    },
                                    new P1<F<String, IterV<String, Integer>>>() {
                                        public F<String, IterV<String, Integer>> _1() {
                                            return new F<String, IterV<String, Integer>>() {
                                                public IterV<String, Integer> f(String s) {
                                                    return cont(step.f(n + 1));
                                                }
                                            };
                                        }
                                    },
                                    new P1<IterV<String, Integer>>() {
                                        public IterV<String, Integer> _1() {
                                            return done(n, Input.<String>eof());
                                        }
                                    }
                                );
                            }
                        };
                    }
                };
        }
        return cont(new Step().step.f(0));
    }

    static abstract class SuperCSV<A> {

        protected SuperCSV() {}

        abstract A run() throws IOException;

        static <B> SuperCSV<B> superCSV(final B b) {
            return new SuperCSV<B>() {
                public B run() throws IOException {
                    return b;
                }
            };
        }

        <B> SuperCSV<B> map(final F<A, B> f) {
            return new Bind<>(this, new F<A, SuperCSV<B>>() {
                public SuperCSV<B> f(A a) {
                    return superCSV(f.f(a));
                }
            });
        }

        <B> SuperCSV<B> bind(final F<A, SuperCSV<B>> f) {
            return new Bind<>(this, f);
        }

        // To prevent stack overflow errors in deep nested calls to bind
        private class Bind<B, C> extends SuperCSV<C> {
            private final Either<IOException, C> result;
            public Bind(final SuperCSV<B> s, final F<B, SuperCSV<C>> f) {
                Either<IOException, C> tmp;
                try {
                    tmp = right(f.f(s.run()).run());
                } catch (IOException e) {
                    tmp = left(e);
                }
                result = tmp;
            }
            @Override
            C run() throws IOException {
                if (result.isRight()) return result.right().value();
                else throw result.left().value();
            }
        }

        static <T> IterV<T, SuperCSV<Unit>> write(final ICsvBeanWriter writer, final Array<String> headers, final Array<String> mappings) {
            F<SuperCSV<ICsvBeanWriter>, F<Input<T>, IterV<T, SuperCSV<Unit>>>> step =
                new F<SuperCSV<ICsvBeanWriter>, F<Input<T>, IterV<T, SuperCSV<Unit>>>>() {
                    F<SuperCSV<ICsvBeanWriter>, F<Input<T>, IterV<T, SuperCSV<Unit>>>> self = this;
                    public F<Input<T>, IterV<T, SuperCSV<Unit>>> f(final SuperCSV<ICsvBeanWriter> csv) {
                        return new F<Input<T>, IterV<T, SuperCSV<Unit>>>() {
                            public IterV<T, SuperCSV<Unit>> f(Input<T> i) {
                                return i.apply(
                                    new P1<IterV<T, SuperCSV<Unit>>>() {
                                        public IterV<T, SuperCSV<Unit>> _1() {
                                            return cont(self.f(csv));
                                        }
                                    },
                                    new P1<F<T, IterV<T, SuperCSV<Unit>>>>() {
                                        public F<T, IterV<T, SuperCSV<Unit>>> _1() {
                                            return new F<T, IterV<T, SuperCSV<Unit>>>() {
                                                public IterV<T, SuperCSV<Unit>> f(final T t) {
                                                    return cont(self.f(csv.bind(new F<ICsvBeanWriter, SuperCSV<ICsvBeanWriter>>() {
                                                        public SuperCSV<ICsvBeanWriter> f(final ICsvBeanWriter w) {
                                                            return new SuperCSV<ICsvBeanWriter>() {
                                                                ICsvBeanWriter run() throws IOException {
                                                                    w.write(t, mappings.array(String[].class));
                                                                    return w;
                                                                }
                                                            };
                                                        }
                                                    })));
                                                }
                                            };
                                        }
                                    },
                                    new P1<IterV<T, SuperCSV<Unit>>>() {
                                        public IterV<T, SuperCSV<Unit>> _1() {
                                            return done(csv.bind(new F<ICsvBeanWriter, SuperCSV<Unit>>() {
                                                public SuperCSV<Unit> f(final ICsvBeanWriter w) {
                                                    return new SuperCSV<Unit>() {
                                                        Unit run() throws IOException {
                                                            w.flush();
                                                            return Unit.unit();
                                                        }
                                                    };
                                                }
                                            }), Input.<T>eof());
                                        }
                                    }
                                );
                            }
                        };
                    }
                };
            return cont(step.f(new SuperCSV<ICsvBeanWriter>() {
                ICsvBeanWriter run() throws IOException {
                    writer.writeHeader(headers.array(String[].class));
                    return writer;
                }
            }));
        }

    }

    static <T> IterV<T, SuperCSV<Unit>> enumStream_1(Stream<T> source, IterV<T, SuperCSV<Unit>> it) {
        F2<Stream<T>, IterV<T, SuperCSV<Unit>>, IterV<T, SuperCSV<Unit>>> loop =
            new F2<Stream<T>, IterV<T, SuperCSV<Unit>>, IterV<T, SuperCSV<Unit>>>() {
                F2<Stream<T>, IterV<T, SuperCSV<Unit>>, IterV<T, SuperCSV<Unit>>> self = this;
                public IterV<T, SuperCSV<Unit>> f(final Stream<T> ts, final IterV<T, SuperCSV<Unit>> iv) {
                    return ts.isEmpty() ?
                        iv :
                        iv.fold(
                            new F<P2<SuperCSV<Unit>, Input<T>>, IterV<T, SuperCSV<Unit>>>() {
                                public IterV<T, SuperCSV<Unit>> f(P2<SuperCSV<Unit>, Input<T>> t) {
                                    return iv;
                                }
                            },
                            new F<F<Input<T>, IterV<T, SuperCSV<Unit>>>, IterV<T, SuperCSV<Unit>>>() {
                                public IterV<T, SuperCSV<Unit>> f(F<Input<T>, IterV<T, SuperCSV<Unit>>> fi) {
                                    return self.f(ts.tail()._1(), fi.f(el(ts.head()))); // Stack Overflow !
                                }
                            }
                        );
                }
            };
        return loop.f(source, it);
    }

    static <T> IterV<T, SuperCSV<Unit>> enumStream(Stream<T> source, IterV<T, SuperCSV<Unit>> it) {
        F2<Stream<T>, IterV<T, SuperCSV<Unit>>, Trampoline<IterV<T, SuperCSV<Unit>>>> loop =
            new F2<Stream<T>, IterV<T, SuperCSV<Unit>>, Trampoline<IterV<T, SuperCSV<Unit>>>>() {
                F2<Stream<T>, IterV<T, SuperCSV<Unit>>, Trampoline<IterV<T, SuperCSV<Unit>>>> self = this;
                public Trampoline<IterV<T, SuperCSV<Unit>>> f(final Stream<T> ts, final IterV<T, SuperCSV<Unit>> iv) {
                    return ts.isEmpty() ?
                        Trampoline.pure(iv) :
                        Trampoline.suspend(new P1<Trampoline<IterV<T, SuperCSV<Unit>>>>() {
                            public Trampoline<IterV<T, SuperCSV<Unit>>> _1() {
                                return iv.fold(
                                    new F<P2<SuperCSV<Unit>, Input<T>>, Trampoline<IterV<T, SuperCSV<Unit>>>>() {
                                        public Trampoline<IterV<T, SuperCSV<Unit>>> f(P2<SuperCSV<Unit>, Input<T>> t) {
                                            return Trampoline.pure(iv);
                                        }
                                    },
                                    new F<F<Input<T>, IterV<T, SuperCSV<Unit>>>, Trampoline<IterV<T, SuperCSV<Unit>>>>() {
                                        public Trampoline<IterV<T, SuperCSV<Unit>>> f(F<Input<T>, IterV<T, SuperCSV<Unit>>> fi) {
                                            return self.f(ts.tail()._1(), fi.f(el(ts.head())));
                                        }
                                    }
                                );
                            }
                        });
                }
            };
        return loop.f(source, it).run();
    }

    public static final class Person {
        private final String name;
        private final String firstname;

        private Person(String name, String firstname) {
            this.name = name;
            this.firstname = firstname;
        }

        static Person pers(String name, String firstname) {
            return new Person(name, firstname);
        }

        public String getName() { return name; }
        public String getFirstname() { return firstname; }
    }

    public static void main(String[] args) throws IOException {
//        System.out.println(enumerate.f(cycle(stream("toto", "tata", "tutu")).take(100000), counter()).run().run());

        try (Writer w = new FileWriter(Paths.get("/tmp/test.csv").toFile());
             ICsvBeanWriter iw = new CsvBeanWriter(w, CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE)) {
            final Stream<Person> source =
                cycle(stream(pers("Dupont", "Marcel"), pers("Durand", "Georges"), pers("Duchmol", "Gontran"))).take(50000000);
            enumStream(source, SuperCSV.<Person>write(iw, array("FIRSTNAME", "NAME"), array("firstname", "name"))).run().run();
        }
    }
}
