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
import static fj.data.Iteratee.Input;
import static fj.data.Iteratee.Input.el;
import static fj.data.Iteratee.IterV;
import static fj.data.Iteratee.IterV.cont;
import static fj.data.Iteratee.IterV.done;
import static fj.data.Stream.cycle;
import static fj.data.Stream.stream;
import static testj7.TestIterateeOK.Person.pers;

class TestIterateeOK {

    static abstract class SuperCSV<A> {

        protected SuperCSV() {}

        abstract Trampoline<Either<IOException, A>> run();

        static <B> SuperCSV<B> superCSV(final B b) {
            return new SuperCSV<B>() {
                public Trampoline<Either<IOException, B>> run() {
                    return Trampoline.pure(Either.<IOException, B>right(b));
                }
            };
        }

        <B> SuperCSV<B> map(final F<A, B> fun) {
            return bind(new F<A, SuperCSV<B>>() {
                public SuperCSV<B> f(A a) {
                    return superCSV(fun.f(a));
                }
            });
        }

        <B> SuperCSV<B> bind(final F<A, SuperCSV<B>> fun) {
            return new SuperCSV<B>() {
                Trampoline<Either<IOException, B>> run() {
                    return Trampoline.suspend(new P1<Trampoline<Either<IOException, B>>>() {
                        public Trampoline<Either<IOException, B>> _1() {
                            return SuperCSV.this.run().bind(new F<Either<IOException, A>, Trampoline<Either<IOException, B>>>() {
                                public Trampoline<Either<IOException, B>> f(Either<IOException, A> ei) {
                                    return ei.either(
                                        new F<IOException, Trampoline<Either<IOException, B>>>() {
                                            public Trampoline<Either<IOException, B>> f(IOException e) {
                                                return Trampoline.pure(Either.<IOException, B>left(e));
                                            }
                                        },
                                        new F<A, Trampoline<Either<IOException, B>>>() {
                                            public Trampoline<Either<IOException, B>> f(A a) {
                                                return fun.f(a).run();
                                            }
                                        }
                                    );
                                }
                            });
                        }
                    });
                }
            };
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
                                                            Trampoline<Either<IOException, ICsvBeanWriter>> run() {
                                                                try {
                                                                    w.write(t, mappings.array(String[].class));
                                                                    w.flush();
                                                                    return Trampoline.pure(Either.<IOException, ICsvBeanWriter>right(w));
                                                                } catch (IOException e) {
                                                                    return Trampoline.pure(Either.<IOException, ICsvBeanWriter>left(e));
                                                                }
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
                                        return done(csv.map(new F<ICsvBeanWriter, Unit>() {
                                            public Unit f(ICsvBeanWriter writer) {
                                                return Unit.unit();
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
            Trampoline<Either<IOException, ICsvBeanWriter>> run() {
                try {
                    writer.writeHeader(headers.array(String[].class));
                    return Trampoline.pure(Either.<IOException, ICsvBeanWriter>right(writer));
                } catch (IOException e) {
                    return Trampoline.pure(Either.<IOException, ICsvBeanWriter>left(e));
                }
            }
        }));
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

    /**
     * À préférer à {@link fj.control.Trampoline#run()} qui provoque des java.lang.OutOfMemoryError: Java heap space
     */
    static public <T> T runTramp(final Trampoline<T> tramp) {
        Trampoline<T> current = tramp;
        while (true) {
            final Either<P1<Trampoline<T>>, T> x = current.resume();
            if (x.isLeft()) current = x.left().value()._1();
            if (x.isRight()) return x.right().value();
        }
    }

    public static void main(String[] args) throws IOException {
        try (Writer w = new FileWriter(Paths.get("/tmp/test.csv").toFile());
             ICsvBeanWriter iw = new CsvBeanWriter(w, CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE)) {
            final Stream<Person> source =
                cycle(stream(pers("Dupont", "Marcel"), pers("Durand", "Georges"), pers("Duchmol", "Gontran"))).take(50000000);
            Either<IOException, Unit> result =
                runTramp(enumStream(source, TestIterateeOK.<Person>write(iw, array("FIRSTNAME", "NAME"), array("firstname", "name"))).run().run());
            if (result.isLeft()) throw result.left().value();
        }
    }
}
